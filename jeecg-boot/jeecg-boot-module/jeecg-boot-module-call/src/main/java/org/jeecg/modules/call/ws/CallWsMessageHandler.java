package org.jeecg.modules.call.ws;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.call.config.CallProperties;
import org.jeecg.modules.call.entity.AgentProfile;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.enums.AgentStatusEnum;
import org.jeecg.modules.call.service.FreeSwitchClient;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.jeecg.modules.call.service.ICallSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CallWsMessageHandler {

    private static IAgentProfileService agentProfileService;
    private static ICallSessionService callSessionService;
    private static CallProperties callProperties;

    @Autowired
    private void setAgentProfileService(IAgentProfileService service) {
        CallWsMessageHandler.agentProfileService = service;
    }

    @Autowired
    private void setCallSessionService(ICallSessionService service) {
        CallWsMessageHandler.callSessionService = service;
    }

    @Autowired
    private void setCallProperties(CallProperties callProperties) {
        CallWsMessageHandler.callProperties = callProperties;
    }

    public static void handle(String userId, JSONObject msg) {
        String type = msg.getString("type");

        switch (type) {
            case "call_response":
                handleCallResponse(userId, msg);
                break;
            case "agent_status_update":
                handleStatusUpdate(userId, msg);
                break;
            case "auth":
                // token 认证已在连接时通过 query param 处理，此处忽略
                break;
            default:
                log.warn("【话务WS】未知消息类型: type={}, userId={}", type, userId);
                break;
        }
    }

    private static void handleCallResponse(String userId, JSONObject msg) {
        String callId = msg.getString("call_id");
        String action = msg.getString("action");

        if ("accept".equals(action)) {
            callSessionService.updateStatus(callId, "TALKING");
            agentProfileService.changeStatus(userId, AgentStatusEnum.TALKING, "坐席接听");
            CallWebSocket.pushCallState(userId, "active");
            CallWebSocket.pushCallSession(userId, callId);

            // 通知 FreeSwitch 桥接并开始流式传输
            CallSession session = callSessionService.getById(callId);
            if (session != null && session.getFsCallId() != null) {
                AgentProfile agent = agentProfileService.getByUserId(userId);
                if (agent != null && agent.getExtension() != null) {
                    String fsCallId = session.getFsCallId();
                    String extension = agent.getExtension();
                    String rtmpUrl = buildRtmpUrl(callId);
                    FreeSwitchClient.bridge(fsCallId, callId, extension);
                    FreeSwitchClient.startStreaming(fsCallId, callId, rtmpUrl);
                } else {
                    log.warn("【话务WS】坐席接听后未找到分机号: userId={}", userId);
                }
            }
        } else if ("reject".equals(action)) {
            // 通知 FreeSwitch 挂断
            notifyFsHangup(callId, "REJECTED");

            callSessionService.updateStatus(callId, "QUEUING");
            agentProfileService.changeStatus(userId, AgentStatusEnum.ONLINE, "坐席拒接");
            CallWebSocket.pushCallState(userId, "idle");
        } else if ("hangup".equals(action)) {
            // 通知 FreeSwitch 挂断
            notifyFsHangup(callId, "NORMAL_CLEARING");

            callSessionService.updateStatus(callId, "ENDED");
            agentProfileService.changeStatus(userId, AgentStatusEnum.WRAP_UP, "坐席挂断");
            CallWebSocket.pushCallState(userId, "idle");
        }
    }

    private static void notifyFsHangup(String callId, String cause) {
        CallSession session = callSessionService.getById(callId);
        if (session != null && session.getFsCallId() != null) {
            FreeSwitchClient.hangup(session.getFsCallId(), callId, cause);
        } else {
            log.warn("【话务WS】挂断时未找到通话会话或 fsCallId: callId={}", callId);
        }
    }

    private static String buildRtmpUrl(String callSessionId) {
        String baseUrl = callProperties.getRtmp().getBaseUrl();
        // 去掉末尾斜杠，确保拼接正确
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/" + callSessionId;
    }

    private static void handleStatusUpdate(String userId, JSONObject msg) {
        String status = msg.getString("status");
        AgentStatusEnum statusEnum = mapFrontendStatus(status);
        if (statusEnum != null) {
            agentProfileService.changeStatus(userId, statusEnum, "坐席手动切换");
            CallWebSocket.pushAgentStatus(userId, status);
        }
    }

    private static AgentStatusEnum mapFrontendStatus(String frontendStatus) {
        switch (frontendStatus) {
            case "idle": return AgentStatusEnum.ONLINE;
            case "busy": return AgentStatusEnum.REST;
            case "offline": return AgentStatusEnum.OFFLINE;
            case "on_call": return AgentStatusEnum.TALKING;
            default: return null;
        }
    }
}

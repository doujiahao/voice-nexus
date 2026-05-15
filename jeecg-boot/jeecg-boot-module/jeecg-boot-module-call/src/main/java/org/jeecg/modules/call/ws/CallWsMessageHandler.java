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
        log.info("[CallWS] 处理客户端消息: userId={}, type={}, msg={}", userId, type, msg);

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
            log.info("[CallWS] 坐席接听来电: userId={}, callId={}, msg={}", userId, callId, msg);
            callSessionService.updateStatus(callId, "TALKING");
            log.info("[CallWS] 已更新会话为 TALKING: userId={}, callId={}", userId, callId);
            agentProfileService.changeStatus(userId, AgentStatusEnum.TALKING, "坐席接听");
            CallWebSocket.pushAgentStatus(userId, "on_call");
            CallWebSocket.pushCallState(userId, "active");
            CallWebSocket.pushCallSession(userId, callId);
            log.info("[CallWS] 已推送坐席接听状态: userId={}, callId={}, agentStatus=on_call, callState=active", userId, callId);

            // 通知 FreeSwitch 桥接并开始流式传输
            CallSession session = callSessionService.getById(callId);
            if (session != null && session.getFsCallId() != null) {
                AgentProfile agent = agentProfileService.getByUserId(userId);
                if (agent != null && agent.getExtension() != null) {
                    String fsCallId = session.getFsCallId();
                    String extension = agent.getExtension();
                    String rtmpUrl = buildRtmpUrl(callId);
                    log.info("[CallWS] 准备通知 FreeSwitch 桥接: userId={}, callId={}, fsCallId={}, extension={}, rtmpUrl={}",
                            userId, callId, fsCallId, extension, rtmpUrl);
                    FreeSwitchClient.bridge(fsCallId, callId, extension);
                    log.info("[CallWS] FreeSwitch 桥接调用完成: userId={}, callId={}, fsCallId={}, extension={}",
                            userId, callId, fsCallId, extension);
                    FreeSwitchClient.startStreaming(fsCallId, callId, rtmpUrl);
                    log.info("[CallWS] FreeSwitch 推流调用完成: userId={}, callId={}, fsCallId={}, rtmpUrl={}",
                            userId, callId, fsCallId, rtmpUrl);
                } else {
                    log.warn("【话务WS】坐席接听后未找到分机号: userId={}", userId);
                }
            }
        } else if ("reject".equals(action)) {
            log.info("[CallWS] 坐席拒接来电: userId={}, callId={}, msg={}", userId, callId, msg);
            notifyFsHangup(callId, "REJECTED");

            callSessionService.updateStatus(callId, "QUEUING");
            log.info("[CallWS] 已更新拒接会话为 QUEUING: userId={}, callId={}", userId, callId);
            agentProfileService.changeStatus(userId, AgentStatusEnum.ONLINE, "坐席拒接");
            CallWebSocket.pushAgentStatus(userId, "idle");
            CallWebSocket.pushCallState(userId, "idle");
            log.info("[CallWS] 已推送坐席拒接状态: userId={}, callId={}, agentStatus=idle, callState=idle", userId, callId);
        } else if ("hangup".equals(action)) {
            log.info("[CallWS] 坐席挂断通话: userId={}, callId={}, msg={}", userId, callId, msg);
            notifyFsHangup(callId, "NORMAL_CLEARING");

            callSessionService.updateStatus(callId, "ENDED");
            log.info("[CallWS] 已更新挂断会话为 ENDED: userId={}, callId={}", userId, callId);
            agentProfileService.changeStatus(userId, AgentStatusEnum.WRAP_UP, "坐席挂断");
            CallWebSocket.pushAgentStatus(userId, "wrap_up");
            CallWebSocket.pushCallState(userId, "idle");
            log.info("[CallWS] 已推送坐席挂断状态: userId={}, callId={}, agentStatus=wrap_up, callState=idle", userId, callId);
        } else {
            log.warn("[CallWS] 未知来电响应动作: userId={}, callId={}, action={}, msg={}", userId, callId, action, msg);
        }
    }

    private static void notifyFsHangup(String callId, String cause) {
        log.info("[CallWS] 准备通知 FreeSwitch 挂断: callId={}, cause={}", callId, cause);
        CallSession session = callSessionService.getById(callId);
        if (session != null && session.getFsCallId() != null) {
            FreeSwitchClient.hangup(session.getFsCallId(), callId, cause);
            log.info("[CallWS] FreeSwitch 挂断调用完成: callId={}, fsCallId={}, cause={}", callId, session.getFsCallId(), cause);
        } else {
            log.warn("【话务WS】挂断时未找到通话会话或 fsCallId: callId={}, session={}", callId, session);
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
            log.info("[CallWS] 坐席手动切换状态: userId={}, frontendStatus={}, targetStatus={}",
                    userId, status, statusEnum.getCode());
            agentProfileService.changeStatus(userId, statusEnum, "坐席手动切换");
            CallWebSocket.pushAgentStatus(userId, status);
            log.info("[CallWS] 坐席手动切换状态完成: userId={}, frontendStatus={}, targetStatus={}",
                    userId, status, statusEnum.getCode());
        } else {
            log.warn("[CallWS] 坐席手动切换状态失败，未知前端状态: userId={}, frontendStatus={}, msg={}", userId, status, msg);
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

package org.jeecg.modules.call.ws;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.call.enums.AgentStatusEnum;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.jeecg.modules.call.service.ICallSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CallWsMessageHandler {

    private static IAgentProfileService agentProfileService;
    private static ICallSessionService callSessionService;

    @Autowired
    private void setAgentProfileService(IAgentProfileService service) {
        CallWsMessageHandler.agentProfileService = service;
    }

    @Autowired
    private void setCallSessionService(ICallSessionService service) {
        CallWsMessageHandler.callSessionService = service;
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
        } else if ("reject".equals(action)) {
            callSessionService.updateStatus(callId, "QUEUING");
            agentProfileService.changeStatus(userId, AgentStatusEnum.ONLINE, "坐席拒接");
            CallWebSocket.pushCallState(userId, "idle");
        }
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

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
        String requestId = msg.getString("request_id");

        switch (type) {
            case "call_accept":
                handleCallAccept(userId, msg, requestId);
                break;
            case "call_reject":
                handleCallReject(userId, msg, requestId);
                break;
            case "call_hangup":
                handleCallHangup(userId, msg, requestId);
                break;
            case "agent_status_change":
                handleStatusChange(userId, msg, requestId);
                break;
            default:
                sendAck(userId, requestId, false, "unknown message type: " + type);
                break;
        }
    }

    private static void handleCallAccept(String userId, JSONObject msg, String requestId) {
        String sessionId = msg.getString("session_id");
        callSessionService.updateStatus(sessionId, "TALKING");
        agentProfileService.changeStatus(userId, AgentStatusEnum.TALKING, "坐席接听");
        sendAck(userId, requestId, true, null);
    }

    private static void handleCallReject(String userId, JSONObject msg, String requestId) {
        String sessionId = msg.getString("session_id");
        callSessionService.updateStatus(sessionId, "QUEUING");
        agentProfileService.changeStatus(userId, AgentStatusEnum.ONLINE, "坐席拒接");
        sendAck(userId, requestId, true, null);
    }

    private static void handleCallHangup(String userId, JSONObject msg, String requestId) {
        String sessionId = msg.getString("session_id");
        agentProfileService.changeStatus(userId, AgentStatusEnum.WRAP_UP, "坐席挂断");
        sendAck(userId, requestId, true, null);
    }

    private static void handleStatusChange(String userId, JSONObject msg, String requestId) {
        String newStatus = msg.getString("status");
        try {
            AgentStatusEnum statusEnum = AgentStatusEnum.fromCode(newStatus);
            agentProfileService.changeStatus(userId, statusEnum, "坐席手动切换");
            sendAck(userId, requestId, true, null);
        } catch (IllegalArgumentException e) {
            sendAck(userId, requestId, false, "invalid status: " + newStatus);
        }
    }

    private static void sendAck(String userId, String requestId, boolean success, String error) {
        JSONObject ack = new JSONObject();
        ack.put("type", "ack");
        ack.put("request_id", requestId);
        ack.put("success", success);
        if (error != null) {
            ack.put("error", error);
        }
        CallWebSocket.sendMessage(userId, ack.toJSONString());
    }
}

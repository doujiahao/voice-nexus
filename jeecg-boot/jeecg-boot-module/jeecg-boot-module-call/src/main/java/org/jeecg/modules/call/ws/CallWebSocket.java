package org.jeecg.modules.call.ws;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@ServerEndpoint("/call/ws/{userId}")
public class CallWebSocket {

    private static final ConcurrentHashMap<String, Session> SESSION_POOL = new ConcurrentHashMap<>();
    private static final String REDIS_WS_PREFIX = "call:ws:conn:";

    private static RedisUtil redisUtil;

    @Autowired
    private void setRedisUtil(RedisUtil redisUtil) {
        CallWebSocket.redisUtil = redisUtil;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        SESSION_POOL.put(userId, session);
        redisUtil.set(REDIS_WS_PREFIX + userId, "1");
        log.info("【话务WS】连接建立: userId={}, 当前连接数={}", userId, SESSION_POOL.size());
        sendMessage(userId, "{\"type\":\"connected\",\"userId\":\"" + userId + "\"}");
    }

    @OnClose
    public void onClose(@PathParam("userId") String userId) {
        SESSION_POOL.remove(userId);
        redisUtil.del(REDIS_WS_PREFIX + userId);
        log.info("【话务WS】连接断开: userId={}, 当前连接数={}", userId, SESSION_POOL.size());
    }

    @OnMessage
    public void onMessage(String message, @PathParam("userId") String userId) {
        log.debug("【话务WS】收到消息: userId={}, msg={}", userId, message);
        try {
            JSONObject msg = JSON.parseObject(message);
            String type = msg.getString("type");

            if ("ping".equals(type)) {
                JSONObject pong = new JSONObject();
                pong.put("type", "pong");
                pong.put("ts", msg.getLong("ts"));
                sendMessage(userId, pong.toJSONString());
                return;
            }

            CallWsMessageHandler.handle(userId, msg);
        } catch (Exception e) {
            log.error("【话务WS】消息处理异常: userId={}", userId, e);
        }
    }

    @OnError
    public void onError(Session session, Throwable error, @PathParam("userId") String userId) {
        log.error("【话务WS】连接异常: userId={}", userId, error);
        SESSION_POOL.remove(userId);
        redisUtil.del(REDIS_WS_PREFIX + userId);
    }

    public static void sendMessage(String userId, String message) {
        Session session = SESSION_POOL.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.error("【话务WS】发送消息失败: userId={}", userId, e);
            }
        }
    }

    public static void pushIncomingCall(String agentUserId, String callId, String phone, String callerName, String fsCallId) {
        JSONObject msg = new JSONObject();
        msg.put("type", "incoming_call");
        msg.put("call_id", callId);
        msg.put("phone", phone);
        msg.put("caller_name", callerName);
        msg.put("fs_call_id", fsCallId);
        sendMessage(agentUserId, msg.toJSONString());
    }

    public static void pushCallSession(String agentUserId, String callSessionId) {
        JSONObject msg = new JSONObject();
        msg.put("type", "call_session");
        msg.put("call_session_id", callSessionId);
        sendMessage(agentUserId, msg.toJSONString());
    }

    public static void pushAgentStatus(String agentUserId, String status) {
        JSONObject msg = new JSONObject();
        msg.put("type", "agent_status");
        msg.put("status", status);
        sendMessage(agentUserId, msg.toJSONString());
    }

    public static void pushCallState(String agentUserId, String state) {
        JSONObject msg = new JSONObject();
        msg.put("type", "call_state");
        msg.put("state", state);
        sendMessage(agentUserId, msg.toJSONString());
    }

    public static void pushAsrResult(String agentUserId, String correctedText, String speakerRole, String speakerName, String intent) {
        JSONObject msg = new JSONObject();
        msg.put("type", "asr_result");
        msg.put("corrected_text", correctedText);
        msg.put("speaker_role", speakerRole);
        msg.put("speaker_name", speakerName);
        msg.put("ts", java.time.Instant.now().toString());
        if (intent != null) msg.put("intent", intent);
        sendMessage(agentUserId, msg.toJSONString());
    }

    public static boolean isOnline(String userId) {
        return SESSION_POOL.containsKey(userId);
    }
}

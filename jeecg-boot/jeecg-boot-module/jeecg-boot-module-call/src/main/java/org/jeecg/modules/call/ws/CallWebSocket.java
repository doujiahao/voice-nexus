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

        // 发送连接确认
        sendMessage(userId, buildAck("connected", userId));
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

            if ("heartbeat".equals(type)) {
                sendMessage(userId, "{\"type\":\"heartbeat_ack\"}");
                return;
            }

            // 坐席操作消息交给 handler 处理
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

    public static void sendToAgent(String agentUserId, String eventType, Object data) {
        JSONObject msg = new JSONObject();
        msg.put("type", eventType);
        msg.put("data", data);
        msg.put("timestamp", System.currentTimeMillis());
        sendMessage(agentUserId, msg.toJSONString());
    }

    public static boolean isOnline(String userId) {
        return SESSION_POOL.containsKey(userId);
    }

    private String buildAck(String type, String userId) {
        JSONObject ack = new JSONObject();
        ack.put("type", type);
        ack.put("userId", userId);
        ack.put("timestamp", System.currentTimeMillis());
        return ack.toJSONString();
    }
}

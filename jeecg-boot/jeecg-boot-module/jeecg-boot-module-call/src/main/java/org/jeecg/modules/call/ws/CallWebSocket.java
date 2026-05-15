package org.jeecg.modules.call.ws;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.jeecg.common.system.util.JwtUtil;
import org.jeecg.common.system.vo.UserAccountInfo;
import org.jeecg.common.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@ServerEndpoint("/call/ws")
public class CallWebSocket {

    private static final ConcurrentHashMap<String, Session> SESSION_POOL = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Session, String> SESSION_USER_MAP = new ConcurrentHashMap<>();
    private static final String REDIS_WS_PREFIX = "call:ws:conn:";

    private static RedisUtil redisUtil;
    private static ISysBaseAPI sysBaseAPI;

    @Autowired
    private void setRedisUtil(RedisUtil redisUtil) {
        CallWebSocket.redisUtil = redisUtil;
    }

    @Autowired
    private void setSysBaseAPI(ISysBaseAPI sysBaseAPI) {
        CallWebSocket.sysBaseAPI = sysBaseAPI;
    }

    @OnOpen
    public void onOpen(Session session) {
        String userId = resolveUserId(session);
        if (userId == null) {
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "token invalid"));
            } catch (IOException ignored) {}
            return;
        }
        Session oldSession = SESSION_POOL.put(userId, session);
        SESSION_USER_MAP.put(session, userId);
        redisUtil.set(REDIS_WS_PREFIX + userId, "1");
        log.info("[CallWS] 连接建立: userId={}, sessionId={}, query={}, oldSessionId={}, 当前连接数={}, onlineUsers={}",
                userId, session.getId(), session.getQueryString(), oldSession != null ? oldSession.getId() : null,
                SESSION_POOL.size(), SESSION_POOL.keySet());
        if (oldSession != null && oldSession.isOpen() && oldSession != session) {
            try {
                log.info("[CallWS] 关闭同用户旧连接: userId={}, oldSessionId={}, newSessionId={}",
                        userId, oldSession.getId(), session.getId());
                oldSession.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "new connection opened"));
            } catch (IOException e) {
                log.warn("[CallWS] 关闭同用户旧连接失败: userId={}, oldSessionId={}, newSessionId={}",
                        userId, oldSession.getId(), session.getId(), e);
            }
        }
        sendMessage(userId, "{\"type\":\"connected\",\"userId\":\"" + userId + "\"}");
    }

    @OnClose
    public void onClose(Session session) {
        String userId = SESSION_USER_MAP.remove(session);
        if (userId != null) {
            boolean removed = SESSION_POOL.remove(userId, session);
            if (removed) {
                redisUtil.del(REDIS_WS_PREFIX + userId);
            }
            log.info("[CallWS] 连接断开: userId={}, sessionId={}, removedCurrent={}, 当前连接数={}, onlineUsers={}",
                    userId, session.getId(), removed, SESSION_POOL.size(), SESSION_POOL.keySet());
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        String userId = SESSION_USER_MAP.get(session);
        if (userId == null) return;

        log.debug("[CallWS] 收到消息: userId={}, msg={}", userId, message);
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
            log.error("[CallWS] 消息处理异常: userId={}", userId, e);
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        String userId = SESSION_USER_MAP.remove(session);
        if (userId != null) {
            boolean removed = SESSION_POOL.remove(userId, session);
            if (removed) {
                redisUtil.del(REDIS_WS_PREFIX + userId);
            }
            log.error("[CallWS] 连接异常: userId={}, sessionId={}, removedCurrent={}, 当前连接数={}, onlineUsers={}",
                    userId, session.getId(), removed, SESSION_POOL.size(), SESSION_POOL.keySet(), error);
        }
    }

    private String resolveUserId(Session session) {
        URI uri = session.getRequestURI();
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                String token = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                String username = JwtUtil.getUsername(token);
                return resolveUserIdByUsername(username);
            }
        }
        return null;
    }

    private String resolveUserIdByUsername(String username) {
        if (username == null) {
            return null;
        }
        if (sysBaseAPI == null) {
            log.warn("[CallWS] 系统用户查询接口未初始化: username={}", username);
            return null;
        }
        try {
            List<UserAccountInfo> users = sysBaseAPI.queryUserByNames(new String[]{username});
            if (users == null || users.isEmpty()) {
                log.warn("[CallWS] token 用户不存在: username={}", username);
                return null;
            }
            String userId = users.get(0).getId();
            log.info("[CallWS] token 用户映射成功: username={}, userId={}", username, userId);
            return userId;
        } catch (Exception e) {
            log.warn("[CallWS] token 用户查询失败: username={}", username, e);
            return null;
        }
    }

    public static void sendMessage(String userId, String message) {
        Session session = SESSION_POOL.get(userId);
        if (session == null || !session.isOpen()) {
            log.warn("[CallWS] 发送消息跳过，用户 WS 不在线: userId={}, hasSession={}, sessionOpen={}, onlineUsers={}, msg={}",
                    userId, session != null, session != null && session.isOpen(), SESSION_POOL.keySet(), message);
            return;
        }
        try {
            log.info("[CallWS] 准备发送消息: userId={}, sessionId={}, msg={}", userId, session.getId(), message);
            session.getBasicRemote().sendText(message);
            if (!message.contains("\"type\":\"pong\"")) {
                log.info("[CallWS] 消息发送成功: userId={}, sessionId={}, msg={}", userId, session.getId(), message);
            }
        } catch (IOException e) {
            log.error("[CallWS] 发送消息失败: userId={}, sessionId={}, msg={}", userId, session.getId(), message, e);
        }
    }

    public static void pushIncomingCall(String agentUserId, String callId, String phone, String callerName, String fsCallId) {
        Session session = SESSION_POOL.get(agentUserId);
        log.info("[CallWS] 推送来电开始: agentUserId={}, callId={}, phone={}, callerName={}, fsCallId={}, online={}, sessionId={}, onlineUsers={}",
                agentUserId, callId, phone, callerName, fsCallId, session != null && session.isOpen(),
                session != null ? session.getId() : null, SESSION_POOL.keySet());
        JSONObject msg = new JSONObject();
        msg.put("type", "incoming_call");
        msg.put("call_id", callId);
        msg.put("phone", phone);
        msg.put("caller_name", callerName);
        msg.put("fs_call_id", fsCallId);
        sendMessage(agentUserId, msg.toJSONString());
        log.info("[CallWS] 推送来电结束: agentUserId={}, callId={}, fsCallId={}", agentUserId, callId, fsCallId);
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

    public static void pushAsrResult(String agentUserId, String correctedText, String speakerRole,
                                      String speakerName, String intent, String turnId, Integer durationMs) {
        JSONObject msg = new JSONObject();
        msg.put("type", "asr_result");
        msg.put("text", correctedText);
        msg.put("corrected_text", correctedText);
        msg.put("role", speakerRole);
        msg.put("speaker_role", speakerRole);
        msg.put("speakerName", speakerName);
        msg.put("speaker_name", speakerName);
        msg.put("ts", java.time.Instant.now().toString());
        msg.put("timestamp", System.currentTimeMillis());
        if (intent != null) msg.put("intent", intent);
        if (turnId != null) msg.put("turnId", turnId);
        if (durationMs != null) msg.put("durationMs", durationMs);
        sendMessage(agentUserId, msg.toJSONString());
    }

    public static boolean isOnline(String userId) {
        return SESSION_POOL.containsKey(userId);
    }
}

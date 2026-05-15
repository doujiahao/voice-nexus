package org.jeecg.modules.call.ws;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.call.config.CallProperties;
import org.jeecg.modules.call.service.IAudioPipelineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@ServerEndpoint("/call/audio/{sessionId}")
public class AudioWebSocket {

    private static final ConcurrentHashMap<String, Session> SESSION_POOL = new ConcurrentHashMap<>();

    private static IAudioPipelineService audioPipelineService;
    private static CallProperties callProperties;

    @Autowired
    private void setAudioPipelineService(IAudioPipelineService service) {
        AudioWebSocket.audioPipelineService = service;
    }

    @Autowired
    private void setCallProperties(CallProperties callProperties) {
        AudioWebSocket.callProperties = callProperties;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("sessionId") String sessionId) {
        // 校验 X-Internal-Key
        String key = resolveQueryParam(session, "key");
        String expectedKey = callProperties.getInternal().getSecretKey();
        if (key == null || !key.equals(expectedKey)) {
            log.warn("[AudioWS] 认证失败，关闭连接: sessionId={}", sessionId);
            try {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "invalid key"));
            } catch (IOException ignored) {}
            return;
        }

        SESSION_POOL.put(sessionId, session);
        session.setMaxBinaryMessageBufferSize(512 * 1024);
        log.info("[AudioWS] 连接建立: sessionId={}", sessionId);
    }

    @OnClose
    public void onClose(@PathParam("sessionId") String sessionId) {
        SESSION_POOL.remove(sessionId);
        log.info("[AudioWS] 连接断开: sessionId={}", sessionId);
    }

    @OnMessage
    public void onMessage(ByteBuffer buffer, @PathParam("sessionId") String sessionId) {
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        try {
            audioPipelineService.processAudioFrame(sessionId, data);
        } catch (Exception e) {
            log.error("[AudioWS] 音频帧处理失败: sessionId={}", sessionId, e);
        }
    }

    @OnMessage
    public void onTextMessage(String message, @PathParam("sessionId") String sessionId) {
        // 文本消息仅用于心跳
        if (message.contains("ping")) {
            Session session = SESSION_POOL.get(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    session.getBasicRemote().sendText("{\"type\":\"pong\"}");
                } catch (Exception e) {
                    log.error("[AudioWS] pong 发送失败: sessionId={}", sessionId, e);
                }
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error, @PathParam("sessionId") String sessionId) {
        log.error("[AudioWS] 连接异常: sessionId={}", sessionId, error);
        SESSION_POOL.remove(sessionId);
    }

    private String resolveQueryParam(Session session, String paramName) {
        URI uri = session.getRequestURI();
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && paramName.equals(kv[0])) {
                return java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}

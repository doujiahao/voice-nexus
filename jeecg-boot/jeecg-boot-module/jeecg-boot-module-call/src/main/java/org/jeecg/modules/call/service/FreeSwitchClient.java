package org.jeecg.modules.call.service;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.call.config.CallProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class FreeSwitchClient {

    private static RestTemplate restTemplate;
    private static CallProperties callProperties;

    @Autowired
    private void setRestTemplate(RestTemplate restTemplate) {
        FreeSwitchClient.restTemplate = restTemplate;
    }

    @Autowired
    private void setCallProperties(CallProperties callProperties) {
        FreeSwitchClient.callProperties = callProperties;
    }

    public static void answer(String fsCallId, String callSessionId) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "answer");
            body.put("fs_call_id", fsCallId);
            body.put("call_session_id", callSessionId);
            postAction(body);
            log.info("[FSClient] answer 指令已发送: fsCallId={}, sessionId={}", fsCallId, callSessionId);
        } catch (Exception e) {
            log.error("[FSClient] answer 调用失败: fsCallId={}, sessionId={}", fsCallId, callSessionId, e);
        }
    }

    public static void bridge(String fsCallId, String callSessionId, String agentExtension) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "bridge");
            body.put("fs_call_id", fsCallId);
            body.put("call_session_id", callSessionId);
            body.put("target_extension", agentExtension);
            postAction(body);
            log.info("[FSClient] bridge 指令已发送: fsCallId={}, sessionId={}, extension={}",
                    fsCallId, callSessionId, agentExtension);
        } catch (Exception e) {
            log.error("[FSClient] bridge 调用失败: fsCallId={}, sessionId={}, extension={}",
                    fsCallId, callSessionId, agentExtension, e);
        }
    }

    public static void startStreaming(String fsCallId, String callSessionId, String rtmpUrl) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "start_streaming");
            body.put("fs_call_id", fsCallId);
            body.put("call_session_id", callSessionId);
            body.put("rtmp_url", rtmpUrl);

            JSONObject streamOptions = new JSONObject();
            streamOptions.put("codec", "aac");
            streamOptions.put("sample_rate", 44100);
            streamOptions.put("channels", 2);
            body.put("stream_options", streamOptions);

            postAction(body);
            log.info("[FSClient] start_streaming 指令已发送: fsCallId={}, sessionId={}, rtmpUrl={}",
                    fsCallId, callSessionId, rtmpUrl);
        } catch (Exception e) {
            log.error("[FSClient] start_streaming 调用失败: fsCallId={}, sessionId={}",
                    fsCallId, callSessionId, e);
        }
    }

    public static void hangup(String fsCallId, String callSessionId, String hangupCause) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", "hangup");
            body.put("fs_call_id", fsCallId);
            body.put("call_session_id", callSessionId);
            body.put("hangup_cause", hangupCause);
            postAction(body);
            log.info("[FSClient] hangup 指令已发送: fsCallId={}, sessionId={}, cause={}",
                    fsCallId, callSessionId, hangupCause);
        } catch (Exception e) {
            log.error("[FSClient] hangup 调用失败: fsCallId={}, sessionId={}",
                    fsCallId, callSessionId, e);
        }
    }

    private static void postAction(JSONObject body) {
        String url = callProperties.getFreeswitch().getBaseUrl() + "/api/v1/calls/action";
        log.info("[FSClient] 发送指令: url={}, action={}", url, body.getString("action"));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Key", callProperties.getInternal().getSecretKey());
        HttpEntity<JSONObject> entity = new HttpEntity<>(body, headers);
        try {
            org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            log.info("[FSClient] 指令响应: url={}, action={}, statusCode={}, body={}",
                    url, body.getString("action"), response.getStatusCode(),
                    response.getBody() != null && response.getBody().length() > 200 ? response.getBody().substring(0, 200) + "..." : response.getBody());
        } catch (Exception e) {
            log.error("[FSClient] 指令调用失败: url={}, action={}", url, body.getString("action"), e);
            throw e;
        }
    }
}

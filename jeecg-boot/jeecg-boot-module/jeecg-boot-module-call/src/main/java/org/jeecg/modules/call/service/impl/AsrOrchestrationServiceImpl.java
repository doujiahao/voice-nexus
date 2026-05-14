package org.jeecg.modules.call.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.MinioUtil;
import org.jeecg.modules.call.config.CallProperties;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.entity.CallTurn;
import org.jeecg.modules.call.mapper.CallTurnMapper;
import org.jeecg.modules.call.service.IAsrOrchestrationService;
import org.jeecg.modules.call.service.ICallContextService;
import org.jeecg.modules.call.service.ICallSessionService;
import org.jeecg.modules.call.service.INlpOrchestrationService;
import org.jeecg.modules.call.ws.CallWebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Date;

@Slf4j
@Service
public class AsrOrchestrationServiceImpl implements IAsrOrchestrationService {

    @Autowired
    private CallProperties callProperties;
    @Autowired
    private CallTurnMapper callTurnMapper;
    @Autowired
    private ICallSessionService callSessionService;
    @Autowired
    private ICallContextService callContextService;
    @Autowired
    private INlpOrchestrationService nlpOrchestrationService;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void transcribe(String sessionId, String audioPath, String speakerId, String speakerName, String speakerRole) {
        byte[] audioBytes = downloadFromMinio(audioPath);
        if (audioBytes == null) {
            return;
        }

        String gatewayUrl = callProperties.getGateway().getBaseUrl() + "/api/v1/asr/transcribe";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return audioPath.substring(audioPath.lastIndexOf('/') + 1);
            }
        });
        body.add("language", "zh");
        body.add("enable_correction", "true");
        body.add("speaker_id", speakerId);
        body.add("speaker_name", speakerName);
        body.add("speaker_role", speakerRole);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        JSONObject result;
        try {
            ResponseEntity<String> response = restTemplate.exchange(gatewayUrl, HttpMethod.POST, entity, String.class);
            result = JSON.parseObject(response.getBody());
        } catch (Exception e) {
            log.error("[ASR] Gateway 调用失败: sessionId={}", sessionId, e);
            return;
        }

        if (result == null || result.getIntValue("code") != 0) {
            log.warn("[ASR] Gateway 返回异常: sessionId={}, resp={}", sessionId, result);
            return;
        }

        JSONObject data = result.getJSONObject("data");
        saveTurnAndPush(sessionId, audioPath, speakerId, speakerName, speakerRole, data);
    }

    private void saveTurnAndPush(String sessionId, String audioPath, String speakerId, String speakerName, String speakerRole, JSONObject data) {
        Long turnCount = callTurnMapper.selectCount(
                new LambdaQueryWrapper<CallTurn>().eq(CallTurn::getSessionId, sessionId));

        CallTurn turn = new CallTurn();
        turn.setSessionId(sessionId);
        turn.setTurnIndex(turnCount.intValue() + 1);
        turn.setSpeakerId(speakerId);
        turn.setSpeakerRole(speakerRole);
        turn.setText(data.getString("raw_text"));
        turn.setCorrectedText(data.getString("corrected_text"));
        turn.setDurationMs(data.getInteger("duration_ms"));
        turn.setAudioUrl(audioPath);
        turn.setIntent(data.getString("intent"));
        turn.setEmotion(data.getString("emotion"));
        turn.setStartTime(new Date());
        if (data.containsKey("intent_confidence")) {
            turn.setIntentConfidence(BigDecimal.valueOf(data.getDoubleValue("intent_confidence")));
        }
        if (data.containsKey("entities")) {
            turn.setEntities(data.getString("entities"));
        }
        turn.setCreateTime(new Date());
        callTurnMapper.insert(turn);

        JSONObject turnCtx = new JSONObject();
        turnCtx.put("turn_id", turn.getId());
        turnCtx.put("speaker_role", speakerRole);
        turnCtx.put("speaker_name", speakerName);
        turnCtx.put("corrected_text", turn.getCorrectedText() != null ? turn.getCorrectedText() : turn.getText());
        turnCtx.put("intent", turn.getIntent());
        turnCtx.put("emotion", turn.getEmotion());
        callContextService.appendTurn(sessionId, turnCtx);

        pushToFrontend(sessionId, turn, data);

        nlpOrchestrationService.analyzeAgentAssist(sessionId, turn.getId());
    }

    private void pushToFrontend(String sessionId, CallTurn turn, JSONObject data) {
        CallSession session = callSessionService.getById(sessionId);
        if (session == null || session.getAgentId() == null) {
            return;
        }
        CallWebSocket.pushAsrResult(
                session.getAgentId(),
                turn.getCorrectedText() != null ? turn.getCorrectedText() : turn.getText(),
                turn.getSpeakerRole(),
                data.getString("speaker_name"),
                turn.getIntent()
        );
    }

    private byte[] downloadFromMinio(String objectPath) {
        try {
            InputStream is = MinioUtil.getMinioFile(MinioUtil.getBucketName(), objectPath);
            if (is == null) return null;
            byte[] bytes = is.readAllBytes();
            is.close();
            return bytes;
        } catch (Exception e) {
            log.error("[ASR] MinIO 下载失败: path={}", objectPath, e);
            return null;
        }
    }
}

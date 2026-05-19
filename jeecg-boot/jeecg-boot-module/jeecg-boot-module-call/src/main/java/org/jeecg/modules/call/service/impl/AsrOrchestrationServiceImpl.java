package org.jeecg.modules.call.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.MinioUtil;
import org.jeecg.modules.call.config.CallProperties;
import org.jeecg.modules.call.entity.AgentProfile;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.entity.CallTurn;
import org.jeecg.modules.call.mapper.AgentProfileMapper;
import org.jeecg.modules.call.mapper.CallSessionMapper;
import org.jeecg.modules.call.mapper.CallTurnMapper;
import org.jeecg.modules.call.service.IAsrOrchestrationService;
import org.jeecg.modules.call.service.ICallContextService;
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
    private CallSessionMapper callSessionMapper;
    @Autowired
    private AgentProfileMapper agentProfileMapper;
    @Autowired
    private ICallContextService callContextService;
    @Autowired
    private INlpOrchestrationService nlpOrchestrationService;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void transcribe(String sessionId, String audioPath, String speakerId, String speakerName, String speakerRole) {
        log.info("[ASR] 开始转写: sessionId={}, audioPath={}, speakerId={}, speakerName={}, speakerRole={}",
                sessionId, audioPath, speakerId, speakerName, speakerRole);
        byte[] audioBytes = downloadFromMinio(audioPath);
        if (audioBytes == null) {
            log.warn("[ASR] 音频下载失败，跳过转写: sessionId={}, audioPath={}", sessionId, audioPath);
            return;
        }
        log.info("[ASR] 音频下载成功: sessionId={}, audioPath={}, bytes={}", sessionId, audioPath, audioBytes.length);

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
        String normalizedSpeakerRole = hasText(speakerRole) ? speakerRole.trim().toLowerCase() : null;
        if (hasText(speakerId)) {
            body.add("speaker_id", speakerId.trim());
        }
        if (hasText(speakerName)) {
            body.add("speaker_name", speakerName.trim());
        }
        if (normalizedSpeakerRole != null) {
            body.add("speaker_role", normalizedSpeakerRole);
        }
        log.info("[ASR] 准备调用 Gateway: sessionId={}, path={}, speakerId={}, speakerName={}, rawSpeakerRole={}, requestSpeakerRole={}",
                sessionId, audioPath, speakerId, speakerName, speakerRole, normalizedSpeakerRole);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        JSONObject result;
        try {
            ResponseEntity<String> response = restTemplate.exchange(gatewayUrl, HttpMethod.POST, entity, String.class);
            result = JSON.parseObject(response.getBody());
        } catch (Exception e) {
            if (isEmptyTextResponse(e)) {
                log.debug("[ASR] 空音频识别结果已跳过: sessionId={}, path={}", sessionId, audioPath);
                return;
            }
            log.error("[ASR] Gateway 调用失败: sessionId={}", sessionId, e);
            return;
        }

        if (result == null || result.getIntValue("code") != 0) {
            log.warn("[ASR] Gateway 返回异常: sessionId={}, resp={}", sessionId, result);
            return;
        }

        JSONObject data = result.getJSONObject("data");
        if (data == null) {
            log.warn("[ASR] Gateway 返回缺少 data: sessionId={}, resp={}", sessionId, result);
            return;
        }
        log.info("[ASR] Gateway 识别成功: sessionId={}, speakerRole={}, correctedText={}, intent={}, emotion={}, nlpEnabled={}, utteranceSummary={}",
                sessionId,
                data.getString("speaker_role"),
                abbreviate(data.getString("corrected_text"), 80),
                data.getString("intent"),
                data.getString("emotion"),
                data.getString("nlp_enabled"),
                abbreviate(data.getString("utterance_summary"), 80));
        saveTurnAndPush(sessionId, audioPath, speakerId, speakerName, speakerRole, data);
    }

    private void saveTurnAndPush(String sessionId, String audioPath, String speakerId, String speakerName, String speakerRole, JSONObject data) {
        log.info("[ASR] 保存转写轮次: sessionId={}, speakerRole={}, correctedText={}",
                sessionId, data.getString("speaker_role"), abbreviate(data.getString("corrected_text"), 80));
        Long turnCount = callTurnMapper.selectCount(
                new LambdaQueryWrapper<CallTurn>().eq(CallTurn::getSessionId, sessionId));

        CallTurn turn = new CallTurn();
        turn.setSessionId(sessionId);
        turn.setTurnIndex(turnCount.intValue() + 1);
        turn.setSpeakerId(speakerId);
        turn.setSpeakerRole(hasText(data.getString("speaker_role")) ? data.getString("speaker_role") : speakerRole);
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
        log.info("[ASR] 轮次已入库: sessionId={}, turnId={}, turnIndex={}, speakerRole={}, intent={}",
                sessionId, turn.getId(), turn.getTurnIndex(), turn.getSpeakerRole(), turn.getIntent());

        JSONObject turnCtx = new JSONObject();
        turnCtx.put("turn_id", turn.getId());
        turnCtx.put("speaker_role", turn.getSpeakerRole());
        turnCtx.put("speaker_name", speakerName);
        turnCtx.put("corrected_text", turn.getCorrectedText() != null ? turn.getCorrectedText() : turn.getText());
        turnCtx.put("intent", turn.getIntent());
        turnCtx.put("intent_confidence", data.getDouble("intent_confidence"));
        turnCtx.put("keywords", data.getJSONArray("keywords"));
        turnCtx.put("entities", data.getJSONObject("entities"));
        turnCtx.put("emotion", turn.getEmotion());
        turnCtx.put("utterance_summary", data.getString("utterance_summary"));
        turnCtx.put("need_clarify", data.getBoolean("need_clarify"));
        turnCtx.put("domain", data.getString("domain"));
        turnCtx.put("domain_confidence", data.getDouble("domain_confidence"));
        callContextService.appendTurn(sessionId, turnCtx);

        CallSession session = callSessionMapper.selectById(sessionId);
        pushToFrontend(session, turn, data);

        if (session != null && "customer".equalsIgnoreCase(turn.getSpeakerRole())) {
            nlpOrchestrationService.analyzeAgentAssist(session, turn.getId());
        }
    }

    private void pushToFrontend(CallSession session, CallTurn turn, JSONObject data) {
        if (session == null || session.getAgentId() == null) {
            log.warn("[ASR] 跳过前端推送，会话或坐席为空: sessionId={}", session != null ? session.getId() : null);
            return;
        }
        AgentProfile agent = agentProfileMapper.selectById(session.getAgentId());
        if (agent == null || agent.getUserId() == null) {
            log.warn("[ASR] 跳过前端推送，坐席用户不存在: sessionId={}, agentId={}", session.getId(), session.getAgentId());
            return;
        }
        log.info("[ASR] 推送转写结果到前端: sessionId={}, agentUserId={}, turnId={}, speakerRole={}",
                session.getId(), agent.getUserId(), turn.getId(), turn.getSpeakerRole());
        CallWebSocket.pushAsrResult(
                agent.getUserId(),
                turn.getCorrectedText() != null ? turn.getCorrectedText() : turn.getText(),
                turn.getSpeakerRole(),
                data.getString("speaker_name"),
                turn.getIntent(),
                turn.getId(),
                turn.getDurationMs(),
                data.getDouble("intent_confidence"),
                data.getJSONArray("keywords"),
                data.getJSONObject("entities"),
                turn.getEmotion(),
                data.getString("utterance_summary"),
                data.getBoolean("need_clarify")
        );
    }

    private boolean isEmptyTextResponse(Exception e) {
        String message = e.getMessage();
        return message != null && message.contains("empty text");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private byte[] downloadFromMinio(String objectPath) {
        try {
            InputStream is = MinioUtil.getMinioFile(MinioUtil.getBucketName(), objectPath);
            if (is == null) {
                log.warn("[ASR] MinIO 文件不存在: path={}", objectPath);
                return null;
            }
            byte[] bytes = is.readAllBytes();
            is.close();
            log.info("[ASR] MinIO 下载成功: path={}, bytes={}", objectPath, bytes.length);
            return bytes;
        } catch (Exception e) {
            log.error("[ASR] MinIO 下载失败: path={}", objectPath, e);
            return null;
        }
    }
}

package org.jeecg.modules.call.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.entity.CallTag;
import org.jeecg.modules.call.mapper.CallSessionMapper;
import org.jeecg.modules.call.mapper.CallTagMapper;
import org.jeecg.modules.call.service.ICallContextService;
import org.jeecg.modules.call.service.INlpOrchestrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;

@Slf4j
@Component
public class CallEndProcessor {

    @Autowired
    private INlpOrchestrationService nlpOrchestrationService;
    @Autowired
    private ICallContextService callContextService;
    @Autowired
    private CallSessionMapper callSessionMapper;
    @Autowired
    private CallTagMapper callTagMapper;

    @Async
    public void processCallEnd(CallSession session) {
        String sessionId = session.getId();
        try {
            JSONObject summary = nlpOrchestrationService.generateSessionSummary(session);
            if (summary != null) {
                session.setSummary(summary.toJSONString());
                callSessionMapper.updateById(session);
                generateTags(sessionId, summary);
            }
        } catch (Exception e) {
            log.error("[CallEnd] 摘要生成失败: sessionId={}", sessionId, e);
        } finally {
            callContextService.clearContext(sessionId);
        }
    }

    private void generateTags(String sessionId, JSONObject summary) {
        JSONArray keywords = summary.getJSONArray("keywords");
        if (keywords != null) {
            for (int i = 0; i < keywords.size(); i++) {
                insertTag(sessionId, keywords.getString(i), "NLP_KEYWORD");
            }
        }

        String intent = summary.getString("customer_intent");
        if (intent != null && !intent.isEmpty()) {
            insertTag(sessionId, intent, "NLP_INTENT");
        }

        String emotion = summary.getString("emotion");
        if (emotion != null && !emotion.isEmpty()) {
            insertTag(sessionId, emotion, "NLP_EMOTION");
        }
    }

    private void insertTag(String sessionId, String tagName, String source) {
        CallTag tag = new CallTag();
        tag.setSessionId(sessionId);
        tag.setTagName(tagName);
        tag.setSource(source);
        tag.setConfidence(BigDecimal.ONE);
        tag.setCreateTime(new Date());
        callTagMapper.insert(tag);
    }
}

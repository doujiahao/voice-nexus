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
        log.info("[CallEnd] 开始通话结束后处理: sessionId={}, fsCallId={}, agentId={}, status={}",
                sessionId, session.getFsCallId(), session.getAgentId(), session.getStatus());
        try {
            log.info("[CallEnd] 准备生成会话摘要: sessionId={}", sessionId);
            JSONObject summary = nlpOrchestrationService.generateSessionSummary(session);
            if (summary != null) {
                log.info("[CallEnd] 摘要生成成功: sessionId={}, customerIntent={}, emotion={}, keywordCount={}",
                        sessionId, summary.getString("customer_intent"), summary.getString("emotion"),
                        summary.getJSONArray("keywords") != null ? summary.getJSONArray("keywords").size() : 0);
                session.setSummary(summary.toJSONString());
                callSessionMapper.updateById(session);
                log.info("[CallEnd] 摘要已写入会话: sessionId={}", sessionId);
                generateTags(sessionId, summary);
            } else {
                log.warn("[CallEnd] 摘要生成返回 null: sessionId={}", sessionId);
            }
        } catch (Exception e) {
            log.error("[CallEnd] 摘要生成失败: sessionId={}", sessionId, e);
        } finally {
            callContextService.clearContext(sessionId);
            log.info("[CallEnd] 通话结束后处理完成: sessionId={}", sessionId);
        }
    }

    private void generateTags(String sessionId, JSONObject summary) {
        log.info("[CallEnd] 开始生成标签: sessionId={}", sessionId);
        int tagCount = 0;
        JSONArray keywords = summary.getJSONArray("keywords");
        if (keywords != null) {
            for (int i = 0; i < keywords.size(); i++) {
                insertTag(sessionId, keywords.getString(i), "NLP_KEYWORD");
                tagCount++;
            }
        }

        String intent = summary.getString("customer_intent");
        if (intent != null && !intent.isEmpty()) {
            insertTag(sessionId, intent, "NLP_INTENT");
            tagCount++;
        }

        String emotion = summary.getString("emotion");
        if (emotion != null && !emotion.isEmpty()) {
            insertTag(sessionId, emotion, "NLP_EMOTION");
            tagCount++;
        }
        log.info("[CallEnd] 标签生成完成: sessionId={}, tagCount={}", sessionId, tagCount);
    }

    private void insertTag(String sessionId, String tagName, String source) {
        CallTag tag = new CallTag();
        tag.setSessionId(sessionId);
        tag.setTagName(tagName);
        tag.setSource(source);
        tag.setConfidence(BigDecimal.ONE);
        tag.setCreateTime(new Date());
        callTagMapper.insert(tag);
        log.info("[CallEnd] 插入标签: sessionId={}, tagId={}, tagName={}, source={}", sessionId, tag.getId(), tagName, source);
    }
}

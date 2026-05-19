package org.jeecg.modules.call.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.call.config.CallProperties;
import org.jeecg.modules.call.entity.AgentProfile;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.mapper.AgentProfileMapper;
import org.jeecg.modules.call.service.ICallContextService;
import org.jeecg.modules.call.service.INlpOrchestrationService;
import org.jeecg.modules.call.ws.CallWebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
public class NlpOrchestrationServiceImpl implements INlpOrchestrationService {

    @Autowired
    private CallProperties callProperties;
    @Autowired
    private ICallContextService callContextService;
    @Autowired
    private AgentProfileMapper agentProfileMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    @Async
    @Override
    public void analyzeAgentAssist(CallSession session, String triggerTurnId) {
        log.info("[NLP] 开始坐席辅助分析: sessionId={}, triggerTurnId={}, agentId={}",
                session.getId(), triggerTurnId, session.getAgentId());
        List<JSONObject> turns = callContextService.getTurns(session.getId());
        if (turns.isEmpty()) {
            log.warn("[NLP] 坐席辅助分析跳过，上下文为空: sessionId={}", session.getId());
            return;
        }

        String url = callProperties.getGateway().getBaseUrl() + "/api/v1/nlp/agent-assist/analyze";

        JSONObject body = new JSONObject();
        body.put("session_id", session.getId());
        body.put("trigger_turn_id", triggerTurnId);
        body.put("turns", turns);

        JSONObject meta = new JSONObject();
        meta.put("phone", session.getCustomerPhone());
        meta.put("agent_id", session.getAgentId());
        body.put("meta", meta);

        log.info("[NLP] 调用坐席辅助 Gateway: sessionId={}, url={}, turnCount={}", session.getId(), url, turns.size());
        JSONObject result = callGateway(url, body);
        if (result == null) {
            log.warn("[NLP] 坐席辅助 Gateway 返回 null: sessionId={}", session.getId());
            return;
        }

        JSONObject data = result.getJSONObject("data");
        log.info("[NLP] 坐席辅助分析成功: sessionId={}, intent={}, emotion={}, suggestions={}",
                session.getId(),
                data != null ? data.getString("current_intent") : null,
                data != null ? data.getString("emotion") : null,
                data != null && data.getJSONArray("decision_suggestions") != null ? data.getJSONArray("decision_suggestions").size() : 0);
        pushAgentAssistToFrontend(session, data);
    }

    @Override
    public JSONObject generateSessionSummary(CallSession session) {
        log.info("[NLP] 开始生成会话摘要: sessionId={}, agentId={}, customerPhone={}",
                session.getId(), session.getAgentId(), session.getCustomerPhone());
        List<JSONObject> turns = callContextService.getTurns(session.getId());
        if (turns.isEmpty()) {
            log.warn("[NLP] 摘要生成跳过，上下文为空: sessionId={}", session.getId());
            return null;
        }

        String url = callProperties.getGateway().getBaseUrl() + "/api/v1/nlp/conversation-summary";

        JSONObject body = new JSONObject();
        body.put("session_id", session.getId());
        body.put("turns", turns);

        JSONObject meta = new JSONObject();
        meta.put("phone", session.getCustomerPhone());
        meta.put("agent_id", session.getAgentId());
        if (session.getCreateTime() != null) {
            meta.put("started_at", session.getCreateTime().toString());
        }
        body.put("meta", meta);

        log.info("[NLP] 调用摘要 Gateway: sessionId={}, url={}, turnCount={}", session.getId(), url, turns.size());
        JSONObject result = callGateway(url, body);
        if (result == null) {
            log.warn("[NLP] 摘要 Gateway 返回 null: sessionId={}", session.getId());
            return null;
        }

        JSONObject data = result.getJSONObject("data");
        log.info("[NLP] 摘要生成成功: sessionId={}, customerIntent={}, emotion={}",
                session.getId(),
                data != null ? data.getString("customer_intent") : null,
                data != null ? data.getString("emotion") : null);
        return data;
    }

    private void pushAgentAssistToFrontend(CallSession session, JSONObject data) {
        if (session.getAgentId() == null) {
            log.warn("[NLP] 跳过坐席辅助推送，无坐席: sessionId={}", session.getId());
            return;
        }
        AgentProfile agent = agentProfileMapper.selectById(session.getAgentId());
        if (agent == null || agent.getUserId() == null) {
            log.warn("[NLP] 跳过坐席辅助推送，坐席用户不存在: sessionId={}, agentId={}", session.getId(), session.getAgentId());
            return;
        }
        log.info("[NLP] 推送坐席辅助到前端: sessionId={}, agentUserId={}, intent={}",
                session.getId(), agent.getUserId(), data != null ? data.getString("current_intent") : null);
        JSONObject msg = new JSONObject();
        msg.put("type", "agent_assist");
        msg.put("call_session_id", session.getId());
        msg.put("current_intent", data.getString("current_intent"));
        msg.put("intent_confidence", data.getDouble("intent_confidence"));
        msg.put("keywords", data.getJSONArray("keywords"));
        msg.put("entities", data.getJSONObject("entities"));
        msg.put("emotion", data.getString("emotion"));
        msg.put("emotion_trend", data.getString("emotion_trend"));
        msg.put("stage_summary", data.getString("stage_summary"));
        msg.put("missing_slots", data.getJSONArray("missing_slots"));
        msg.put("recommended_questions", data.getJSONArray("recommended_questions"));
        msg.put("decision_suggestions", data.getJSONArray("decision_suggestions"));
        msg.put("suggested_reply", data.getString("suggested_reply"));
        msg.put("suggested_followup_questions", data.getJSONArray("suggested_followup_questions"));
        msg.put("task_suggestion", data.getJSONObject("task_suggestion"));
        msg.put("risk_flags", data.getJSONArray("risk_flags"));
        CallWebSocket.sendMessage(agent.getUserId(), msg.toJSONString());
    }

    private JSONObject callGateway(String url, JSONObject body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
        log.info("[NLP] Gateway 请求: url={}, bodySize={}", url, body.toJSONString().length());

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JSONObject result = JSON.parseObject(response.getBody());
            if (result != null && result.getIntValue("code") == 0) {
                log.info("[NLP] Gateway 成功: url={}, code={}", url, result.getIntValue("code"));
                return result;
            }
            log.warn("[NLP] Gateway 返回异常: url={}, resp={}", url, result);
        } catch (Exception e) {
            log.error("[NLP] Gateway 调用失败: url={}", url, e);
        }
        return null;
    }
}

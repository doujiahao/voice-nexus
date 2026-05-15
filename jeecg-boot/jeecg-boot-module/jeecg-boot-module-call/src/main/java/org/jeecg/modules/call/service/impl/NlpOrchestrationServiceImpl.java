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
        List<JSONObject> turns = callContextService.getTurns(session.getId());
        if (turns.isEmpty()) {
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

        JSONObject result = callGateway(url, body);
        if (result == null) {
            return;
        }

        JSONObject data = result.getJSONObject("data");
        pushAgentAssistToFrontend(session, data);
    }

    @Override
    public JSONObject generateSessionSummary(CallSession session) {
        List<JSONObject> turns = callContextService.getTurns(session.getId());
        if (turns.isEmpty()) {
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

        JSONObject result = callGateway(url, body);
        if (result == null) {
            return null;
        }

        return result.getJSONObject("data");
    }

    private void pushAgentAssistToFrontend(CallSession session, JSONObject data) {
        if (session.getAgentId() == null) {
            return;
        }
        AgentProfile agent = agentProfileMapper.selectById(session.getAgentId());
        if (agent == null || agent.getUserId() == null) {
            log.warn("[NLP] 跳过坐席辅助推送，坐席用户不存在: sessionId={}, agentId={}", session.getId(), session.getAgentId());
            return;
        }
        JSONObject msg = new JSONObject();
        msg.put("type", "agent_assist");
        msg.put("call_session_id", session.getId());
        msg.put("current_intent", data.getString("current_intent"));
        msg.put("emotion", data.getString("emotion"));
        msg.put("stage_summary", data.getString("stage_summary"));
        msg.put("missing_slots", data.getJSONArray("missing_slots"));
        msg.put("recommended_questions", data.getJSONArray("recommended_questions"));
        msg.put("decision_suggestions", data.getJSONArray("decision_suggestions"));
        msg.put("suggested_reply", data.getString("suggested_reply"));
        msg.put("suggested_followup_questions", data.getJSONArray("suggested_followup_questions"));
        msg.put("task_suggestion", data.getJSONObject("task_suggestion"));
        CallWebSocket.sendMessage(agent.getUserId(), msg.toJSONString());
    }

    private JSONObject callGateway(String url, JSONObject body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            JSONObject result = JSON.parseObject(response.getBody());
            if (result != null && result.getIntValue("code") == 0) {
                return result;
            }
            log.warn("[NLP] Gateway 返回异常: url={}, resp={}", url, result);
        } catch (Exception e) {
            log.error("[NLP] Gateway 调用失败: url={}", url, e);
        }
        return null;
    }
}

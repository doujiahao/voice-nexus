package org.jeecg.modules.call.service;

import com.alibaba.fastjson.JSONObject;
import org.jeecg.modules.call.entity.CallSession;

public interface INlpOrchestrationService {

    void analyzeAgentAssist(CallSession session, String triggerTurnId);

    JSONObject generateSessionSummary(CallSession session);
}

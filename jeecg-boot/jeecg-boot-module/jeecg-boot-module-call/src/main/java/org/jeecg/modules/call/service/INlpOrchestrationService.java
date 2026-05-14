package org.jeecg.modules.call.service;

import com.alibaba.fastjson.JSONObject;

import java.util.List;

public interface INlpOrchestrationService {

    /**
     * 坐席实时辅助分析（每次新 turn 后触发）
     */
    void analyzeAgentAssist(String sessionId, String triggerTurnId);

    /**
     * 通话结束后生成会话摘要
     */
    JSONObject generateSessionSummary(String sessionId);
}

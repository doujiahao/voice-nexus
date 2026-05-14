package org.jeecg.modules.call.service;

import com.alibaba.fastjson.JSONObject;

import java.util.List;

public interface ICallContextService {

    /**
     * 追加一条 turn 到活跃 session 上下文
     */
    void appendTurn(String sessionId, JSONObject turn);

    /**
     * 获取活跃 session 的全量 turns
     */
    List<JSONObject> getTurns(String sessionId);

    /**
     * 获取最近 N 条 turns
     */
    List<JSONObject> getRecentTurns(String sessionId, int count);

    /**
     * 清除 session 上下文（通话结束时调用）
     */
    void clearContext(String sessionId);
}

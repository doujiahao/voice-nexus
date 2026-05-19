package org.jeecg.modules.call.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.call.service.ICallContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CallContextServiceImpl implements ICallContextService {

    private static final String KEY_PREFIX = "call:ctx:turns:";
    private static final long TTL_HOURS = 4;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void appendTurn(String sessionId, JSONObject turn) {
        String key = KEY_PREFIX + sessionId;
        stringRedisTemplate.opsForList().rightPush(key, turn.toJSONString());
        stringRedisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
        Long size = stringRedisTemplate.opsForList().size(key);
        log.info("[Context] 追加对话轮次: sessionId={}, turnId={}, speakerRole={}, turnCount={}",
                sessionId, turn.getString("turn_id"), turn.getString("speaker_role"), size);
    }

    @Override
    public List<JSONObject> getTurns(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        List<String> raw = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) {
            log.debug("[Context] 读取全部轮次，上下文为空: sessionId={}", sessionId);
            return Collections.emptyList();
        }
        log.info("[Context] 读取全部轮次: sessionId={}, turnCount={}", sessionId, raw.size());
        return raw.stream().map(JSON::parseObject).collect(Collectors.toList());
    }

    @Override
    public List<JSONObject> getRecentTurns(String sessionId, int count) {
        String key = KEY_PREFIX + sessionId;
        Long size = stringRedisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            log.debug("[Context] 读取最近轮次，上下文为空: sessionId={}, requestCount={}", sessionId, count);
            return Collections.emptyList();
        }
        long start = Math.max(0, size - count);
        List<String> raw = stringRedisTemplate.opsForList().range(key, start, -1);
        if (raw == null) {
            log.warn("[Context] 读取最近轮次返回 null: sessionId={}, totalSize={}, requestCount={}", sessionId, size, count);
            return Collections.emptyList();
        }
        log.info("[Context] 读取最近轮次: sessionId={}, totalSize={}, requestCount={}, actualCount={}",
                sessionId, size, count, raw.size());
        return raw.stream().map(JSON::parseObject).collect(Collectors.toList());
    }

    @Override
    public void clearContext(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        Boolean deleted = stringRedisTemplate.delete(key);
        log.info("[Context] 清理上下文: sessionId={}, deleted={}", sessionId, deleted);
    }
}

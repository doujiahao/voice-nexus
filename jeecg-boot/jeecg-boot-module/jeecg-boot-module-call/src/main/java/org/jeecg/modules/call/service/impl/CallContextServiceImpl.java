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
    }

    @Override
    public List<JSONObject> getTurns(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        List<String> raw = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        return raw.stream().map(JSON::parseObject).collect(Collectors.toList());
    }

    @Override
    public List<JSONObject> getRecentTurns(String sessionId, int count) {
        String key = KEY_PREFIX + sessionId;
        Long size = stringRedisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            return Collections.emptyList();
        }
        long start = Math.max(0, size - count);
        List<String> raw = stringRedisTemplate.opsForList().range(key, start, -1);
        if (raw == null) {
            return Collections.emptyList();
        }
        return raw.stream().map(JSON::parseObject).collect(Collectors.toList());
    }

    @Override
    public void clearContext(String sessionId) {
        stringRedisTemplate.delete(KEY_PREFIX + sessionId);
    }
}

package org.jeecg.modules.call.service.impl;

import org.jeecg.modules.call.service.ICallQueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CallQueueServiceImpl implements ICallQueueService {

    private static final String QUEUE_KEY_PREFIX = "call:queue:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void enqueue(String skillGroupId, String sessionId) {
        redisTemplate.opsForList().rightPush(QUEUE_KEY_PREFIX + skillGroupId, sessionId);
    }

    @Override
    public String dequeue(String skillGroupId) {
        Object val = redisTemplate.opsForList().leftPop(QUEUE_KEY_PREFIX + skillGroupId);
        return val != null ? val.toString() : null;
    }

    @Override
    public int getQueueSize(String skillGroupId) {
        Long size = redisTemplate.opsForList().size(QUEUE_KEY_PREFIX + skillGroupId);
        return size != null ? size.intValue() : 0;
    }

    @Override
    public int getPosition(String skillGroupId, String sessionId) {
        Long size = redisTemplate.opsForList().size(QUEUE_KEY_PREFIX + skillGroupId);
        if (size == null || size == 0) return -1;
        for (int i = 0; i < size; i++) {
            Object val = redisTemplate.opsForList().index(QUEUE_KEY_PREFIX + skillGroupId, i);
            if (sessionId.equals(val)) return i + 1;
        }
        return -1;
    }

    @Override
    public void remove(String skillGroupId, String sessionId) {
        redisTemplate.opsForList().remove(QUEUE_KEY_PREFIX + skillGroupId, 1, sessionId);
    }
}

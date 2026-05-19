package org.jeecg.modules.call.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.call.service.ICallQueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CallQueueServiceImpl implements ICallQueueService {

    private static final String QUEUE_KEY_PREFIX = "call:queue:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void enqueue(String skillGroupId, String sessionId) {
        String key = QUEUE_KEY_PREFIX + skillGroupId;
        redisTemplate.opsForList().rightPush(key, sessionId);
        Long size = redisTemplate.opsForList().size(key);
        log.info("[Queue] 入队: skillGroupId={}, sessionId={}, queueSize={}", skillGroupId, sessionId, size);
    }

    @Override
    public String dequeue(String skillGroupId) {
        String key = QUEUE_KEY_PREFIX + skillGroupId;
        Object val = redisTemplate.opsForList().leftPop(key);
        String sessionId = val != null ? val.toString() : null;
        Long size = redisTemplate.opsForList().size(key);
        log.info("[Queue] 出队: skillGroupId={}, sessionId={}, remainingSize={}", skillGroupId, sessionId, size);
        return sessionId;
    }

    @Override
    public int getQueueSize(String skillGroupId) {
        Long size = redisTemplate.opsForList().size(QUEUE_KEY_PREFIX + skillGroupId);
        int result = size != null ? size.intValue() : 0;
        log.debug("[Queue] 查询队列大小: skillGroupId={}, size={}", skillGroupId, result);
        return result;
    }

    @Override
    public int getPosition(String skillGroupId, String sessionId) {
        String key = QUEUE_KEY_PREFIX + skillGroupId;
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            log.debug("[Queue] 查询排队位置，队列为空: skillGroupId={}, sessionId={}", skillGroupId, sessionId);
            return -1;
        }
        for (int i = 0; i < size; i++) {
            Object val = redisTemplate.opsForList().index(key, i);
            if (sessionId.equals(val)) {
                int position = i + 1;
                log.info("[Queue] 查询排队位置: skillGroupId={}, sessionId={}, position={}, totalSize={}", skillGroupId, sessionId, position, size);
                return position;
            }
        }
        log.warn("[Queue] 查询排队位置，会话不在队列中: skillGroupId={}, sessionId={}, totalSize={}", skillGroupId, sessionId, size);
        return -1;
    }

    @Override
    public void remove(String skillGroupId, String sessionId) {
        String key = QUEUE_KEY_PREFIX + skillGroupId;
        redisTemplate.opsForList().remove(key, 1, sessionId);
        Long size = redisTemplate.opsForList().size(key);
        log.info("[Queue] 移除排队: skillGroupId={}, sessionId={}, remainingSize={}", skillGroupId, sessionId, size);
    }
}

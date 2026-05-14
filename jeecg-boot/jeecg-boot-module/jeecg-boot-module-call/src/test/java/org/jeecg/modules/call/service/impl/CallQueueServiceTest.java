package org.jeecg.modules.call.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallQueueServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ListOperations<String, Object> listOps;

    @InjectMocks
    private CallQueueServiceImpl callQueueService;

    private static final String SKILL_GROUP_ID = "sg-001";
    private static final String SESSION_ID = "sess-001";
    private static final String KEY = "call:queue:sg-001";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForList()).thenReturn(listOps);
    }

    @Test
    void enqueue_shouldRightPushToRedis() {
        callQueueService.enqueue(SKILL_GROUP_ID, SESSION_ID);
        verify(listOps).rightPush(KEY, SESSION_ID);
    }

    @Test
    void dequeue_shouldLeftPopFromRedis() {
        when(listOps.leftPop(KEY)).thenReturn(SESSION_ID);
        String result = callQueueService.dequeue(SKILL_GROUP_ID);
        assertEquals(SESSION_ID, result);
    }

    @Test
    void dequeue_shouldReturnNullWhenEmpty() {
        when(listOps.leftPop(KEY)).thenReturn(null);
        assertNull(callQueueService.dequeue(SKILL_GROUP_ID));
    }

    @Test
    void getQueueSize_shouldReturnSize() {
        when(listOps.size(KEY)).thenReturn(5L);
        assertEquals(5, callQueueService.getQueueSize(SKILL_GROUP_ID));
    }

    @Test
    void getQueueSize_shouldReturnZeroWhenNull() {
        when(listOps.size(KEY)).thenReturn(null);
        assertEquals(0, callQueueService.getQueueSize(SKILL_GROUP_ID));
    }

    @Test
    void getPosition_shouldReturnOneBasedIndex() {
        when(listOps.size(KEY)).thenReturn(3L);
        when(listOps.index(KEY, 0)).thenReturn("other-sess");
        when(listOps.index(KEY, 1)).thenReturn(SESSION_ID);

        assertEquals(2, callQueueService.getPosition(SKILL_GROUP_ID, SESSION_ID));
    }

    @Test
    void getPosition_shouldReturnMinusOneWhenNotFound() {
        when(listOps.size(KEY)).thenReturn(2L);
        when(listOps.index(KEY, 0)).thenReturn("a");
        when(listOps.index(KEY, 1)).thenReturn("b");

        assertEquals(-1, callQueueService.getPosition(SKILL_GROUP_ID, SESSION_ID));
    }

    @Test
    void getPosition_shouldReturnMinusOneWhenQueueEmpty() {
        when(listOps.size(KEY)).thenReturn(0L);
        assertEquals(-1, callQueueService.getPosition(SKILL_GROUP_ID, SESSION_ID));
    }

    @Test
    void remove_shouldCallRedisRemove() {
        callQueueService.remove(SKILL_GROUP_ID, SESSION_ID);
        verify(listOps).remove(KEY, 1, SESSION_ID);
    }
}

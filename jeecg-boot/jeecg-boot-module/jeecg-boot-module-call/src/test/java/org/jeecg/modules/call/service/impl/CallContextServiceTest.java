package org.jeecg.modules.call.service.impl;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallContextServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ListOperations<String, String> listOps;

    @InjectMocks
    private CallContextServiceImpl callContextService;

    private static final String SESSION_ID = "sess-001";
    private static final String KEY = "call:ctx:turns:sess-001";

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForList()).thenReturn(listOps);
    }

    @Test
    void appendTurn_shouldPushAndSetTtl() {
        JSONObject turn = new JSONObject();
        turn.put("speaker_role", "AGENT");
        turn.put("corrected_text", "你好");

        callContextService.appendTurn(SESSION_ID, turn);

        verify(listOps).rightPush(KEY, turn.toJSONString());
        verify(stringRedisTemplate).expire(KEY, 4, TimeUnit.HOURS);
    }

    @Test
    void getTurns_shouldReturnParsedList() {
        JSONObject t1 = new JSONObject();
        t1.put("text", "hello");
        JSONObject t2 = new JSONObject();
        t2.put("text", "world");

        when(listOps.range(KEY, 0, -1)).thenReturn(Arrays.asList(t1.toJSONString(), t2.toJSONString()));

        List<JSONObject> result = callContextService.getTurns(SESSION_ID);
        assertEquals(2, result.size());
        assertEquals("hello", result.get(0).getString("text"));
        assertEquals("world", result.get(1).getString("text"));
    }

    @Test
    void getTurns_shouldReturnEmptyWhenNull() {
        when(listOps.range(KEY, 0, -1)).thenReturn(null);
        assertTrue(callContextService.getTurns(SESSION_ID).isEmpty());
    }

    @Test
    void getRecentTurns_shouldReturnLastNTurns() {
        when(listOps.size(KEY)).thenReturn(10L);
        JSONObject t = new JSONObject();
        t.put("text", "recent");
        when(listOps.range(KEY, 7, -1)).thenReturn(List.of(t.toJSONString(), t.toJSONString(), t.toJSONString()));

        List<JSONObject> result = callContextService.getRecentTurns(SESSION_ID, 3);
        assertEquals(3, result.size());
    }

    @Test
    void getRecentTurns_shouldReturnEmptyWhenNoSize() {
        when(listOps.size(KEY)).thenReturn(0L);
        assertTrue(callContextService.getRecentTurns(SESSION_ID, 5).isEmpty());
    }

    @Test
    void clearContext_shouldDeleteKey() {
        callContextService.clearContext(SESSION_ID);
        verify(stringRedisTemplate).delete(KEY);
    }
}

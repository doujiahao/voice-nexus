package org.jeecg.modules.call.service.impl;

import com.alibaba.fastjson.JSONObject;
import org.jeecg.modules.call.config.CallProperties;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.service.ICallContextService;
import org.jeecg.modules.call.service.ICallSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NlpOrchestrationServiceTest {

    @Mock private CallProperties callProperties;
    @Mock private ICallContextService callContextService;
    @Mock private ICallSessionService callSessionService;
    @Mock private RestTemplate restTemplate;

    @InjectMocks
    private NlpOrchestrationServiceImpl nlpOrchestrationService;

    @BeforeEach
    void setUp() {
        CallProperties.GatewayConfig gw = new CallProperties.GatewayConfig();
        gw.setBaseUrl("http://localhost:30001");
        lenient().when(callProperties.getGateway()).thenReturn(gw);
        ReflectionTestUtils.setField(nlpOrchestrationService, "restTemplate", restTemplate);
    }

    @Test
    void generateSessionSummary_shouldReturnNull_whenNoTurns() {
        when(callContextService.getTurns("sess-001")).thenReturn(Collections.emptyList());
        assertNull(nlpOrchestrationService.generateSessionSummary("sess-001"));
    }

    @Test
    void generateSessionSummary_shouldReturnNull_whenSessionNotFound() {
        JSONObject turn = new JSONObject();
        turn.put("text", "hello");
        when(callContextService.getTurns("sess-001")).thenReturn(List.of(turn));
        when(callSessionService.getById("sess-001")).thenReturn(null);

        assertNull(nlpOrchestrationService.generateSessionSummary("sess-001"));
    }

    @Test
    void generateSessionSummary_shouldReturnData_onSuccess() {
        JSONObject turn = new JSONObject();
        turn.put("text", "我要报修");
        when(callContextService.getTurns("sess-001")).thenReturn(List.of(turn));

        CallSession session = new CallSession();
        session.setId("sess-001");
        session.setCustomerPhone("13800000001");
        session.setAgentId("agent-001");
        when(callSessionService.getById("sess-001")).thenReturn(session);

        JSONObject data = new JSONObject();
        data.put("customer_intent", "报修");
        data.put("emotion", "neutral");
        JSONObject resp = new JSONObject();
        resp.put("code", 0);
        resp.put("data", data);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(resp.toJSONString(), HttpStatus.OK));

        JSONObject result = nlpOrchestrationService.generateSessionSummary("sess-001");
        assertNotNull(result);
        assertEquals("报修", result.getString("customer_intent"));
    }

    @Test
    void generateSessionSummary_shouldReturnNull_onGatewayError() {
        JSONObject turn = new JSONObject();
        turn.put("text", "hello");
        when(callContextService.getTurns("sess-001")).thenReturn(List.of(turn));

        CallSession session = new CallSession();
        session.setId("sess-001");
        session.setCustomerPhone("13800000001");
        when(callSessionService.getById("sess-001")).thenReturn(session);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertNull(nlpOrchestrationService.generateSessionSummary("sess-001"));
    }
}

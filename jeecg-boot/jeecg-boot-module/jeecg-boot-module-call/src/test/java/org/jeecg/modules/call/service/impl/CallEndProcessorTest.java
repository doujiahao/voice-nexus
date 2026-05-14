package org.jeecg.modules.call.service.impl;

import com.alibaba.fastjson.JSONObject;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.entity.CallTag;
import org.jeecg.modules.call.mapper.CallSessionMapper;
import org.jeecg.modules.call.mapper.CallTagMapper;
import org.jeecg.modules.call.service.ICallContextService;
import org.jeecg.modules.call.service.INlpOrchestrationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallEndProcessorTest {

    @Mock private INlpOrchestrationService nlpOrchestrationService;
    @Mock private ICallContextService callContextService;
    @Mock private CallSessionMapper callSessionMapper;
    @Mock private CallTagMapper callTagMapper;

    @InjectMocks
    private CallEndProcessor callEndProcessor;

    @Test
    void processCallEnd_shouldGenerateSummaryAndTags() {
        CallSession session = new CallSession();
        session.setId("sess-001");

        JSONObject summary = new JSONObject();
        summary.put("customer_intent", "报修停电");
        summary.put("emotion", "angry");
        summary.put("keywords", List.of("停电", "报修", "紧急"));
        when(nlpOrchestrationService.generateSessionSummary(session)).thenReturn(summary);
        when(callSessionMapper.updateById(any(CallSession.class))).thenReturn(1);

        callEndProcessor.processCallEnd(session);

        ArgumentCaptor<CallSession> sessionCaptor = ArgumentCaptor.forClass(CallSession.class);
        verify(callSessionMapper).updateById(sessionCaptor.capture());
        assertTrue(sessionCaptor.getValue().getSummary().contains("报修停电"));

        // 3 keywords + 1 intent + 1 emotion = 5 tags
        ArgumentCaptor<CallTag> tagCaptor = ArgumentCaptor.forClass(CallTag.class);
        verify(callTagMapper, times(5)).insert(tagCaptor.capture());

        List<CallTag> tags = tagCaptor.getAllValues();
        assertTrue(tags.stream().anyMatch(t -> "NLP_KEYWORD".equals(t.getSource()) && "停电".equals(t.getTagName())));
        assertTrue(tags.stream().anyMatch(t -> "NLP_INTENT".equals(t.getSource()) && "报修停电".equals(t.getTagName())));
        assertTrue(tags.stream().anyMatch(t -> "NLP_EMOTION".equals(t.getSource()) && "angry".equals(t.getTagName())));

        verify(callContextService).clearContext("sess-001");
    }

    @Test
    void processCallEnd_shouldClearContextWhenSummaryNull() {
        CallSession session = new CallSession();
        session.setId("sess-002");
        when(nlpOrchestrationService.generateSessionSummary(session)).thenReturn(null);

        callEndProcessor.processCallEnd(session);

        verify(callSessionMapper, never()).updateById(any(CallSession.class));
        verify(callTagMapper, never()).insert(any(CallTag.class));
        verify(callContextService).clearContext("sess-002");
    }

    @Test
    void processCallEnd_shouldClearContextEvenOnException() {
        CallSession session = new CallSession();
        session.setId("sess-003");
        when(nlpOrchestrationService.generateSessionSummary(session)).thenThrow(new RuntimeException("gateway down"));

        callEndProcessor.processCallEnd(session);

        verify(callContextService).clearContext("sess-003");
    }
}

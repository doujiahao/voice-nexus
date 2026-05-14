package org.jeecg.modules.call.service.impl;

import com.alibaba.fastjson.JSONObject;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.entity.CallTag;
import org.jeecg.modules.call.mapper.CallTagMapper;
import org.jeecg.modules.call.service.ICallContextService;
import org.jeecg.modules.call.service.ICallSessionService;
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
    @Mock private ICallSessionService callSessionService;
    @Mock private CallTagMapper callTagMapper;

    @InjectMocks
    private CallEndProcessor callEndProcessor;

    @Test
    void processCallEnd_shouldGenerateSummaryAndTags() {
        String sessionId = "sess-001";

        JSONObject summary = new JSONObject();
        summary.put("customer_intent", "报修停电");
        summary.put("emotion", "angry");
        summary.put("keywords", List.of("停电", "报修", "紧急"));

        when(nlpOrchestrationService.generateSessionSummary(sessionId)).thenReturn(summary);

        CallSession session = new CallSession();
        session.setId(sessionId);
        when(callSessionService.getById(sessionId)).thenReturn(session);
        when(callSessionService.updateById(any())).thenReturn(true);

        callEndProcessor.processCallEnd(sessionId);

        verify(callSessionService).updateById(argThat(s -> {
            CallSession cs = (CallSession) s;
            return cs.getSummary() != null && cs.getSummary().contains("报修停电");
        }));

        // 3 keywords + 1 intent + 1 emotion = 5 tags
        ArgumentCaptor<CallTag> tagCaptor = ArgumentCaptor.forClass(CallTag.class);
        verify(callTagMapper, times(5)).insert(tagCaptor.capture());

        List<CallTag> tags = tagCaptor.getAllValues();
        assertTrue(tags.stream().anyMatch(t -> "NLP_KEYWORD".equals(t.getSource()) && "停电".equals(t.getTagName())));
        assertTrue(tags.stream().anyMatch(t -> "NLP_INTENT".equals(t.getSource()) && "报修停电".equals(t.getTagName())));
        assertTrue(tags.stream().anyMatch(t -> "NLP_EMOTION".equals(t.getSource()) && "angry".equals(t.getTagName())));

        verify(callContextService).clearContext(sessionId);
    }

    @Test
    void processCallEnd_shouldClearContextEvenWhenSummaryNull() {
        String sessionId = "sess-002";
        when(nlpOrchestrationService.generateSessionSummary(sessionId)).thenReturn(null);

        callEndProcessor.processCallEnd(sessionId);

        verify(callSessionService, never()).updateById(any());
        verify(callTagMapper, never()).insert(any(CallTag.class));
        verify(callContextService).clearContext(sessionId);
    }

    @Test
    void processCallEnd_shouldClearContextEvenOnException() {
        String sessionId = "sess-003";
        when(nlpOrchestrationService.generateSessionSummary(sessionId)).thenThrow(new RuntimeException("gateway down"));

        callEndProcessor.processCallEnd(sessionId);

        verify(callContextService).clearContext(sessionId);
    }
}

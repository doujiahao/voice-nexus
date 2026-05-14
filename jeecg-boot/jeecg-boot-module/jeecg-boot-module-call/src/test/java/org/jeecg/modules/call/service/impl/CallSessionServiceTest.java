package org.jeecg.modules.call.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.modules.call.dto.CallEventDTO;
import org.jeecg.modules.call.entity.AgentProfile;
import org.jeecg.modules.call.entity.CallEventLog;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.enums.AgentStatusEnum;
import org.jeecg.modules.call.mapper.AgentProfileMapper;
import org.jeecg.modules.call.mapper.CallEventLogMapper;
import org.jeecg.modules.call.mapper.CallSessionMapper;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CallSessionServiceTest {

    @Mock private CallSessionMapper callSessionMapper;
    @Mock private CallEventLogMapper callEventLogMapper;
    @Mock private AgentProfileMapper agentProfileMapper;
    @Mock private IAgentProfileService agentProfileService;
    @Mock private CallEndProcessor callEndProcessor;

    @Spy
    private CallSessionServiceImpl callSessionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(callSessionService, "baseMapper", callSessionMapper);
        ReflectionTestUtils.setField(callSessionService, "callEventLogMapper", callEventLogMapper);
        ReflectionTestUtils.setField(callSessionService, "agentProfileMapper", agentProfileMapper);
        ReflectionTestUtils.setField(callSessionService, "agentProfileService", agentProfileService);
        ReflectionTestUtils.setField(callSessionService, "callEndProcessor", callEndProcessor);
    }

    @Test
    void handleEvent_shouldReturnError_whenSessionNotFound() {
        doReturn(null).when(callSessionService).getByFsCallId("fs-unknown");

        CallEventDTO event = new CallEventDTO();
        event.setEventType("ANSWERED");

        Map<String, Object> result = callSessionService.handleEvent("fs-unknown", event);
        assertFalse((Boolean) result.get("acknowledged"));
        assertNotNull(result.get("error"));
    }

    @Test
    void handleEvent_answered_shouldTransitionToTalking() {
        CallSession session = new CallSession();
        session.setId("sess-001");
        session.setStatus("RINGING");
        session.setAgentId("agent-001");

        AgentProfile agent = new AgentProfile();
        agent.setId("agent-001");
        agent.setUserId("user-001");

        doReturn(session).when(callSessionService).getByFsCallId("fs-001");
        when(agentProfileMapper.selectById("agent-001")).thenReturn(agent);

        CallEventDTO event = new CallEventDTO();
        event.setEventType("ANSWERED");

        Map<String, Object> result = callSessionService.handleEvent("fs-001", event);
        assertTrue((Boolean) result.get("acknowledged"));
        assertEquals("TALKING", result.get("status"));
        assertEquals("TALKING", session.getStatus());
        assertNotNull(session.getAnswerTime());

        verify(agentProfileService).changeStatus("user-001", AgentStatusEnum.TALKING, "通话接通");
        verify(callEventLogMapper).insert(any(CallEventLog.class));
    }

    @Test
    void handleEvent_callEnded_shouldEndSession() {
        CallSession session = new CallSession();
        session.setId("sess-001");
        session.setStatus("TALKING");
        session.setAgentId("agent-001");

        AgentProfile agent = new AgentProfile();
        agent.setId("agent-001");
        agent.setUserId("user-001");

        doReturn(session).when(callSessionService).getByFsCallId("fs-001");
        when(agentProfileMapper.selectById("agent-001")).thenReturn(agent);

        CallEventDTO event = new CallEventDTO();
        event.setEventType("CALL_ENDED");
        event.setEndedBy("CUSTOMER");
        event.setDurationSec(120);
        event.setMetadata(Map.of("hangup_cause", "NORMAL"));

        Map<String, Object> result = callSessionService.handleEvent("fs-001", event);
        assertTrue((Boolean) result.get("acknowledged"));
        assertEquals("ENDING", result.get("status"));
        assertEquals("ENDED", session.getStatus());
        assertEquals("CUSTOMER", session.getEndedBy());
        assertEquals(120, session.getDurationSec());

        verify(agentProfileService).changeStatus("user-001", AgentStatusEnum.WRAP_UP, "通话结束");
        verify(callEndProcessor).processCallEnd(session);
    }

    @Test
    void handleEvent_unknownType_shouldAcknowledgeWithCurrentStatus() {
        CallSession session = new CallSession();
        session.setId("sess-001");
        session.setStatus("TALKING");

        doReturn(session).when(callSessionService).getByFsCallId("fs-001");

        CallEventDTO event = new CallEventDTO();
        event.setEventType("HOLD");

        Map<String, Object> result = callSessionService.handleEvent("fs-001", event);
        assertTrue((Boolean) result.get("acknowledged"));
        assertEquals("TALKING", result.get("status"));
    }
}

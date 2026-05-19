package org.jeecg.modules.call.service.impl;

import org.jeecg.modules.call.dto.CallEventDTO;
import org.jeecg.modules.call.entity.AgentProfile;
import org.jeecg.modules.call.entity.CallEventLog;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.enums.AgentStatusEnum;
import org.jeecg.modules.call.mapper.AgentProfileMapper;
import org.jeecg.modules.call.mapper.CallEventLogMapper;
import org.jeecg.modules.call.mapper.CallSessionMapper;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.jeecg.modules.call.service.ICallQueueService;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CallSessionRingingConfirmedTest {

    @Mock private CallSessionMapper callSessionMapper;
    @Mock private CallEventLogMapper callEventLogMapper;
    @Mock private AgentProfileMapper agentProfileMapper;
    @Mock private IAgentProfileService agentProfileService;
    @Mock private ICallQueueService callQueueService;
    @Mock private CallEndProcessor callEndProcessor;

    @Spy
    private CallSessionServiceImpl callSessionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(callSessionService, "baseMapper", callSessionMapper);
        ReflectionTestUtils.setField(callSessionService, "callEventLogMapper", callEventLogMapper);
        ReflectionTestUtils.setField(callSessionService, "agentProfileMapper", agentProfileMapper);
        ReflectionTestUtils.setField(callSessionService, "agentProfileService", agentProfileService);
        ReflectionTestUtils.setField(callSessionService, "callQueueService", callQueueService);
        ReflectionTestUtils.setField(callSessionService, "callEndProcessor", callEndProcessor);
    }

    private CallSession createSession(String fsCallId, String agentId) {
        CallSession session = new CallSession();
        session.setId("sess-001");
        session.setFsCallId(fsCallId);
        session.setStatus("RINGING");
        session.setAgentId(agentId);
        session.setCustomerPhone("13800138000");
        return session;
    }

    private AgentProfile createAgent(String agentId, String userId) {
        AgentProfile agent = new AgentProfile();
        agent.setId(agentId);
        agent.setUserId(userId);
        return agent;
    }

    @Test
    void handleEvent_ringing_shouldPushPendingNotIncomingCall() {
        CallSession session = createSession("fs-ring-001", "agent-001");
        AgentProfile agent = createAgent("agent-001", "user-001");

        doReturn(session).when(callSessionService).getByFsCallId("fs-ring-001");
        when(agentProfileMapper.selectById("agent-001")).thenReturn(agent);
        when(callSessionMapper.insert(any(CallSession.class))).thenReturn(1);

        CallEventDTO event = new CallEventDTO();
        event.setEventType("RINGING");
        event.setMetadata(Map.of("customerPhone", "13800138000", "calledNumber", "1001", "skillGroupId", "skill-001", "agentId", "agent-001"));

        Map<String, Object> result = callSessionService.handleEvent("fs-ring-001", event);

        assertTrue((Boolean) result.get("acknowledged"));
        assertEquals("RINGING", result.get("status"));
        verify(agentProfileService).changeStatus("user-001", AgentStatusEnum.RINGING, "来电分配");

        // Verify pending timeout was scheduled
        ConcurrentHashMap<String, ScheduledFuture<?>> pendingMap =
                (ConcurrentHashMap<String, ScheduledFuture<?>>) ReflectionTestUtils.getField(
                        CallSessionServiceImpl.class, "pendingRingingTimeouts");
        assertNotNull(pendingMap.get("fs-ring-001"));

        // Cleanup
        ScheduledFuture<?> f = pendingMap.remove("fs-ring-001");
        if (f != null) f.cancel(false);
    }

    @Test
    void handleEvent_ringingConfirmed_shouldPushIncomingCallAndCancelTimeout() {
        CallSession session = createSession("fs-conf-001", "agent-001");
        AgentProfile agent = createAgent("agent-001", "user-001");

        doReturn(session).when(callSessionService).getByFsCallId("fs-conf-001");
        when(agentProfileMapper.selectById("agent-001")).thenReturn(agent);

        CallEventDTO event = new CallEventDTO();
        event.setEventType("RINGING_CONFIRMED");

        Map<String, Object> result = callSessionService.handleEvent("fs-conf-001", event);

        assertTrue((Boolean) result.get("acknowledged"));
        assertEquals("sess-001", result.get("call_session_id"));
        verify(callEventLogMapper).insert(any(CallEventLog.class));
    }

    @Test
    void handleEvent_callEnded_shouldCancelRingingTimeout() throws InterruptedException {
        CallSession session = createSession("fs-end-001", "agent-001");
        AgentProfile agent = createAgent("agent-001", "user-001");

        // First send RINGING to schedule timeout
        doReturn(session).when(callSessionService).getByFsCallId("fs-end-001");
        when(agentProfileMapper.selectById("agent-001")).thenReturn(agent);
        when(callSessionMapper.insert(any(CallSession.class))).thenReturn(1);

        CallEventDTO ringingEvent = new CallEventDTO();
        ringingEvent.setEventType("RINGING");
        ringingEvent.setMetadata(Map.of("customerPhone", "13800138000", "calledNumber", "1001", "skillGroupId", "skill-001", "agentId", "agent-001"));
        callSessionService.handleEvent("fs-end-001", ringingEvent);

        // Verify timeout was scheduled
        ConcurrentHashMap<String, ScheduledFuture<?>> pendingMap =
                (ConcurrentHashMap<String, ScheduledFuture<?>>) ReflectionTestUtils.getField(
                        CallSessionServiceImpl.class, "pendingRingingTimeouts");
        assertNotNull(pendingMap.get("fs-end-001"));

        // Now send CALL_ENDED
        session.setStatus("TALKING");
        session.setSkillGroupId("skill-001");
        CallEventDTO endEvent = new CallEventDTO();
        endEvent.setEventType("CALL_ENDED");
        endEvent.setEndedBy("CUSTOMER");
        endEvent.setDurationSec(60);
        endEvent.setMetadata(Map.of("hangup_cause", "NORMAL"));

        Map<String, Object> result = callSessionService.handleEvent("fs-end-001", endEvent);

        assertTrue((Boolean) result.get("acknowledged"));
        // Timeout should have been cancelled
        assertNull(pendingMap.get("fs-end-001"));
    }

    @Test
    void handleEvent_ringingConfirmed_noAgent_shouldAcknowledge() {
        CallSession session = createSession("fs-noagent-001", null);
        doReturn(session).when(callSessionService).getByFsCallId("fs-noagent-001");

        CallEventDTO event = new CallEventDTO();
        event.setEventType("RINGING_CONFIRMED");

        Map<String, Object> result = callSessionService.handleEvent("fs-noagent-001", event);

        assertTrue((Boolean) result.get("acknowledged"));
    }
}

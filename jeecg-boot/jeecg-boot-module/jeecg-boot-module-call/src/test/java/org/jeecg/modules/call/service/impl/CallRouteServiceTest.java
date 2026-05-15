package org.jeecg.modules.call.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.modules.call.dto.RouteRequestDTO;
import org.jeecg.modules.call.dto.RouteResponseDTO;
import org.jeecg.modules.call.entity.*;
import org.jeecg.modules.call.enums.AgentStatusEnum;
import org.jeecg.modules.call.mapper.*;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.jeecg.modules.call.service.ICallQueueService;
import org.jeecg.modules.call.ws.CallWebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallRouteServiceTest {

    @Mock private SkillGroupMapper skillGroupMapper;
    @Mock private SkillGroupAgentMapper skillGroupAgentMapper;
    @Mock private AgentProfileMapper agentProfileMapper;
    @Mock private CallSessionMapper callSessionMapper;
    @Mock private CustomerMapper customerMapper;
    @Mock private CustomerContactMapper customerContactMapper;
    @Mock private IAgentProfileService agentProfileService;
    @Mock private ICallQueueService callQueueService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    @InjectMocks
    private CallRouteServiceImpl callRouteService;

    private SkillGroup skillGroup;
    private AgentProfile agent;
    private SkillGroupAgent sga;

    @BeforeEach
    void setUp() {
        skillGroup = new SkillGroup();
        skillGroup.setId("sg-001");
        skillGroup.setGroupCode("DEFAULT");
        skillGroup.setEnabled(1);
        skillGroup.setQueueMaxSize(50);
        skillGroup.setQueueTimeoutSec(30);
        skillGroup.setRingTimeoutSec(20);

        agent = new AgentProfile();
        agent.setId("agent-001");
        agent.setUserId("user-001");
        agent.setExtension("8001");
        agent.setStatus(AgentStatusEnum.ONLINE.getCode());

        sga = new SkillGroupAgent();
        sga.setSkillGroupId("sg-001");
        sga.setAgentId("agent-001");
        sga.setSkillLevel(1);
    }
    @Test
    void route_shouldReturnError_whenSkillGroupNotFound() {
        when(skillGroupMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        RouteRequestDTO req = new RouteRequestDTO();
        req.setSkillGroup("NONEXIST");
        req.setCustomerPhone("13800000001");

        RouteResponseDTO resp = callRouteService.route("fs-001", req);
        assertFalse(resp.isSuccess());
        assertEquals("SKILL_GROUP_NOT_FOUND", resp.getErrorCode());
    }

    @Test
    void route_shouldAssignAgent_whenAvailable() {
        when(skillGroupMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skillGroup);
        when(customerContactMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(callSessionMapper.insert(any(CallSession.class))).thenAnswer(invocation -> {
            CallSession session = invocation.getArgument(0);
            session.setId("call-001");
            return 1;
        });
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(skillGroupAgentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sga));
        when(agentProfileMapper.selectById("agent-001")).thenReturn(agent);

        RouteRequestDTO req = new RouteRequestDTO();
        req.setSkillGroup("DEFAULT");
        req.setCustomerPhone("13800000001");

        try (MockedStatic<CallWebSocket> callWebSocket = mockStatic(CallWebSocket.class)) {
            RouteResponseDTO resp = callRouteService.route("fs-001", req);
            assertTrue(resp.isSuccess());
            assertEquals("RING", resp.getRouteAction());
            assertEquals("agent-001", resp.getTargetAgentId());
            assertEquals("8001", resp.getTargetExtension());
            assertEquals(20, resp.getRingTimeoutSec());

            callWebSocket.verify(() -> CallWebSocket.pushIncomingCall(
                    "user-001", "call-001", "13800000001", null, "fs-001"));
        }
        verify(agentProfileService).changeStatus("user-001", AgentStatusEnum.RINGING, "来电分配");
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void route_shouldEnqueue_whenNoAgentAvailable() {
        when(skillGroupMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skillGroup);
        when(customerContactMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(callSessionMapper.insert(any(CallSession.class))).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(skillGroupAgentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(sga));
        AgentProfile busyAgent = new AgentProfile();
        busyAgent.setId("agent-001");
        busyAgent.setStatus(AgentStatusEnum.TALKING.getCode());
        when(agentProfileMapper.selectById("agent-001")).thenReturn(busyAgent);
        when(callQueueService.getQueueSize("sg-001")).thenReturn(5);
        when(callQueueService.getPosition(eq("sg-001"), any())).thenReturn(6);

        RouteRequestDTO req = new RouteRequestDTO();
        req.setSkillGroup("DEFAULT");
        req.setCustomerPhone("13800000001");

        RouteResponseDTO resp = callRouteService.route("fs-001", req);
        assertTrue(resp.isSuccess());
        assertEquals("QUEUE", resp.getRouteAction());
        assertNotNull(resp.getQueuePosition());

        verify(callQueueService).enqueue(eq("sg-001"), any());
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void route_shouldReturnQueueFull_whenMaxSizeReached() {
        skillGroup.setQueueMaxSize(10);
        when(skillGroupMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skillGroup);
        when(customerContactMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(callSessionMapper.insert(any(CallSession.class))).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(skillGroupAgentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(callQueueService.getQueueSize("sg-001")).thenReturn(10);

        RouteRequestDTO req = new RouteRequestDTO();
        req.setSkillGroup("DEFAULT");
        req.setCustomerPhone("13800000001");

        RouteResponseDTO resp = callRouteService.route("fs-001", req);
        assertFalse(resp.isSuccess());
        assertEquals("QUEUE_FULL", resp.getErrorCode());
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void route_shouldEnqueue_whenLockNotAcquired() {
        when(skillGroupMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skillGroup);
        when(customerContactMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(callSessionMapper.insert(any(CallSession.class))).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        when(callQueueService.getPosition(eq("sg-001"), any())).thenReturn(1);

        RouteRequestDTO req = new RouteRequestDTO();
        req.setSkillGroup("DEFAULT");
        req.setCustomerPhone("13800000001");

        RouteResponseDTO resp = callRouteService.route("fs-001", req);
        assertTrue(resp.isSuccess());
        assertEquals("QUEUE", resp.getRouteAction());
    }
}

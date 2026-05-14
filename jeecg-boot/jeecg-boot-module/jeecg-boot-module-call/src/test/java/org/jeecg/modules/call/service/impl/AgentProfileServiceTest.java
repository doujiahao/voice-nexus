package org.jeecg.modules.call.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.call.entity.AgentProfile;
import org.jeecg.modules.call.entity.AgentStatusLog;
import org.jeecg.modules.call.enums.AgentStatusEnum;
import org.jeecg.modules.call.mapper.AgentProfileMapper;
import org.jeecg.modules.call.mapper.AgentStatusLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentProfileServiceTest {

    @Mock private AgentProfileMapper agentProfileMapper;
    @Mock private AgentStatusLogMapper agentStatusLogMapper;
    @Mock private RedisUtil redisUtil;

    @Spy
    private AgentProfileServiceImpl agentProfileService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(agentProfileService, "baseMapper", agentProfileMapper);
        ReflectionTestUtils.setField(agentProfileService, "redisUtil", redisUtil);
        ReflectionTestUtils.setField(agentProfileService, "agentStatusLogMapper", agentStatusLogMapper);
    }

    @Test
    void changeStatus_shouldUpdateDbAndRedis() {
        AgentProfile profile = new AgentProfile();
        profile.setId("agent-001");
        profile.setUserId("user-001");
        profile.setStatus(AgentStatusEnum.ONLINE.getCode());
        profile.setStatusSince(new Date(System.currentTimeMillis() - 60000));

        doReturn(profile).when(agentProfileService).getByUserId("user-001");

        agentProfileService.changeStatus("user-001", AgentStatusEnum.TALKING, "来电接通");

        assertEquals(AgentStatusEnum.TALKING.getCode(), profile.getStatus());
        assertNotNull(profile.getStatusSince());

        verify(agentProfileMapper).updateById(profile);
        verify(redisUtil).set("call:agent:status:user-001", AgentStatusEnum.TALKING.getCode());

        ArgumentCaptor<AgentStatusLog> logCaptor = ArgumentCaptor.forClass(AgentStatusLog.class);
        verify(agentStatusLogMapper).insert(logCaptor.capture());
        AgentStatusLog log = logCaptor.getValue();
        assertEquals("ONLINE", log.getFromStatus());
        assertEquals("TALKING", log.getToStatus());
        assertEquals("来电接通", log.getReason());
        assertTrue(log.getDurationSec() >= 59);
    }

    @Test
    void changeStatus_shouldThrow_whenProfileNotFound() {
        doReturn(null).when(agentProfileService).getByUserId("user-999");

        assertThrows(IllegalArgumentException.class,
                () -> agentProfileService.changeStatus("user-999", AgentStatusEnum.ONLINE, "test"));
    }

    @Test
    void changeStatus_toOnline_shouldSetLastIdleTime() {
        AgentProfile profile = new AgentProfile();
        profile.setId("agent-001");
        profile.setUserId("user-001");
        profile.setStatus(AgentStatusEnum.WRAP_UP.getCode());
        profile.setStatusSince(new Date());

        doReturn(profile).when(agentProfileService).getByUserId("user-001");

        agentProfileService.changeStatus("user-001", AgentStatusEnum.ONLINE, "话后整理完成");

        assertNotNull(profile.getLastIdleTime());
    }

    @Test
    void getCurrentStatus_shouldReturnFromRedisCache() {
        when(redisUtil.get("call:agent:status:user-001")).thenReturn("TALKING");

        AgentStatusEnum status = agentProfileService.getCurrentStatus("user-001");
        assertEquals(AgentStatusEnum.TALKING, status);
        verify(agentProfileMapper, never()).selectOne(any());
    }

    @Test
    void getCurrentStatus_shouldFallbackToDb_whenCacheMiss() {
        when(redisUtil.get("call:agent:status:user-001")).thenReturn(null);

        AgentProfile profile = new AgentProfile();
        profile.setUserId("user-001");
        profile.setStatus("ONLINE");
        doReturn(profile).when(agentProfileService).getByUserId("user-001");

        AgentStatusEnum status = agentProfileService.getCurrentStatus("user-001");
        assertEquals(AgentStatusEnum.ONLINE, status);
        verify(redisUtil).set("call:agent:status:user-001", "ONLINE");
    }

    @Test
    void getCurrentStatus_shouldReturnOffline_whenNoProfile() {
        when(redisUtil.get("call:agent:status:user-001")).thenReturn(null);
        doReturn(null).when(agentProfileService).getByUserId("user-001");

        assertEquals(AgentStatusEnum.OFFLINE, agentProfileService.getCurrentStatus("user-001"));
    }
}

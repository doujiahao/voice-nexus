package org.jeecg.modules.call.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.modules.call.entity.AgentProfile;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.enums.AgentStatusEnum;
import org.jeecg.modules.call.mapper.AgentProfileMapper;
import org.jeecg.modules.call.mapper.CallSessionMapper;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallRecoveryHandlerTest {

    @Mock
    private CallSessionMapper callSessionMapper;
    @Mock
    private AgentProfileMapper agentProfileMapper;
    @Mock
    private IAgentProfileService agentProfileService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private CallRecoveryHandler handler;

    @Test
    void onApplicationReady_recoversStaleSessionsToEnded() {
        CallSession session = new CallSession();
        session.setId("s1");
        session.setStatus("TALKING");
        when(callSessionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(session));
        when(callSessionMapper.updateById(any(CallSession.class))).thenReturn(1);
        when(agentProfileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(stringRedisTemplate.keys("call:ws:conn:*")).thenReturn(new HashSet<>());

        handler.onApplicationReady();

        verify(callSessionMapper).updateById(argThat((CallSession s) -> "ENDED".equals(s.getStatus())));
    }

    @Test
    void onApplicationReady_recoversAgentStatusToOnline() {
        when(callSessionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        AgentProfile agent = new AgentProfile();
        agent.setUserId("u1");
        agent.setStatus("TALKING");
        when(agentProfileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(agent));
        when(stringRedisTemplate.keys("call:ws:conn:*")).thenReturn(new HashSet<>());

        handler.onApplicationReady();

        verify(agentProfileService).changeStatus(eq("u1"), eq(AgentStatusEnum.ONLINE), any());
    }

    @Test
    void onApplicationReady_cleansStaleWsKeys() {
        when(callSessionMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(agentProfileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        HashSet<String> keys = new HashSet<>(Arrays.asList("call:ws:conn:u1", "call:ws:conn:u2"));
        when(stringRedisTemplate.keys("call:ws:conn:*")).thenReturn(keys);

        handler.onApplicationReady();

        verify(stringRedisTemplate).delete(keys);
    }
}

package org.jeecg.modules.call.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.call.entity.AgentProfile;
import org.jeecg.modules.call.enums.AgentStatusEnum;
import org.jeecg.modules.call.mapper.AgentProfileMapper;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAgentControllerTest {

    @Mock
    private AgentProfileMapper agentProfileMapper;
    @Mock
    private IAgentProfileService agentProfileService;

    @InjectMocks
    private AdminAgentController controller;

    @Test
    void list_returnsAgentsWithRealtimeStatus() {
        AgentProfile agent = new AgentProfile();
        agent.setUserId("user-001");
        agent.setStatus("OFFLINE");

        Page<AgentProfile> page = new Page<>(1, 20);
        page.setRecords(Arrays.asList(agent));
        page.setTotal(1);
        when(agentProfileMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(agentProfileService.getCurrentStatus("user-001")).thenReturn(AgentStatusEnum.ONLINE);

        Result<Page<AgentProfile>> result = controller.list(1, 20, null);

        assertTrue(result.isSuccess());
        assertEquals("ONLINE", result.getResult().getRecords().get(0).getStatus());
    }

    @Test
    void stats_returnsBreakdown() {
        AgentProfile a1 = new AgentProfile();
        a1.setUserId("u1");
        a1.setStatus("ONLINE");
        AgentProfile a2 = new AgentProfile();
        a2.setUserId("u2");
        a2.setStatus("OFFLINE");

        when(agentProfileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(a1, a2));
        when(agentProfileService.getCurrentStatus("u1")).thenReturn(AgentStatusEnum.ONLINE);
        when(agentProfileService.getCurrentStatus("u2")).thenReturn(AgentStatusEnum.OFFLINE);

        Result<JSONObject> result = controller.stats();

        assertTrue(result.isSuccess());
        assertEquals(2, result.getResult().getIntValue("total"));
        assertEquals(1, result.getResult().getIntValue("online"));
    }

    @Test
    void forceStatus_invalidStatus_returnsError() {
        JSONObject body = new JSONObject();
        body.put("status", "INVALID");

        Result<?> result = controller.forceStatus("user-001", body);

        assertFalse(result.isSuccess());
    }

    @Test
    void forceStatus_validStatus_changesAndPushes() {
        JSONObject body = new JSONObject();
        body.put("status", "REST");
        body.put("reason", "午休");

        Result<?> result = controller.forceStatus("user-001", body);

        assertTrue(result.isSuccess());
        verify(agentProfileService).changeStatus(eq("user-001"), eq(AgentStatusEnum.REST), contains("管理员强制"));
    }
}

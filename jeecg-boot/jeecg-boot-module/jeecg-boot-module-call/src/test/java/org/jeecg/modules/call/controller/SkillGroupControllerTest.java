package org.jeecg.modules.call.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.call.entity.SkillGroup;
import org.jeecg.modules.call.entity.SkillGroupAgent;
import org.jeecg.modules.call.mapper.SkillGroupAgentMapper;
import org.jeecg.modules.call.mapper.SkillGroupMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillGroupControllerTest {

    @Mock
    private SkillGroupMapper skillGroupMapper;
    @Mock
    private SkillGroupAgentMapper skillGroupAgentMapper;

    @InjectMocks
    private SkillGroupController controller;

    private SkillGroup sampleGroup;

    @BeforeEach
    void setUp() {
        sampleGroup = new SkillGroup();
        sampleGroup.setId("sg-001");
        sampleGroup.setGroupName("电力报修组");
        sampleGroup.setGroupCode("POWER_REPAIR");
        sampleGroup.setRouteStrategy("ROUND_ROBIN");
        sampleGroup.setEnabled(1);
    }

    @Test
    void list_returnsPagedResult() {
        Page<SkillGroup> page = new Page<>(1, 20);
        page.setRecords(Arrays.asList(sampleGroup));
        page.setTotal(1);
        when(skillGroupMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        Result<Page<SkillGroup>> result = controller.list(1, 20);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getResult().getTotal());
    }

    @Test
    void detail_existingGroup_returnsOk() {
        when(skillGroupMapper.selectById("sg-001")).thenReturn(sampleGroup);

        Result<SkillGroup> result = controller.detail("sg-001");

        assertTrue(result.isSuccess());
        assertEquals("电力报修组", result.getResult().getGroupName());
    }

    @Test
    void detail_nonExisting_returnsError() {
        when(skillGroupMapper.selectById("not-exist")).thenReturn(null);

        Result<SkillGroup> result = controller.detail("not-exist");

        assertFalse(result.isSuccess());
    }

    @Test
    void create_setsDefaultEnabled() {
        SkillGroup input = new SkillGroup();
        input.setGroupName("新组");
        when(skillGroupMapper.insert(any(SkillGroup.class))).thenReturn(1);

        Result<SkillGroup> result = controller.create(input);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getResult().getEnabled());
        assertNotNull(result.getResult().getCreateTime());
    }

    @Test
    void assignAgent_setsDefaultSkillLevel() {
        SkillGroupAgent agent = new SkillGroupAgent();
        agent.setAgentId("agent-001");
        when(skillGroupAgentMapper.insert(any(SkillGroupAgent.class))).thenReturn(1);

        Result<SkillGroupAgent> result = controller.assignAgent("sg-001", agent);

        assertTrue(result.isSuccess());
        assertEquals("sg-001", result.getResult().getSkillGroupId());
        assertEquals(1, result.getResult().getSkillLevel());
    }

    @Test
    void listAgents_returnsAgentsForGroup() {
        SkillGroupAgent a1 = new SkillGroupAgent();
        a1.setAgentId("agent-001");
        SkillGroupAgent a2 = new SkillGroupAgent();
        a2.setAgentId("agent-002");
        when(skillGroupAgentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(a1, a2));

        Result<List<SkillGroupAgent>> result = controller.listAgents("sg-001");

        assertTrue(result.isSuccess());
        assertEquals(2, result.getResult().size());
    }

    @Test
    void delete_callsMapper() {
        when(skillGroupMapper.deleteById("sg-001")).thenReturn(1);

        Result<?> result = controller.delete("sg-001");

        assertTrue(result.isSuccess());
        verify(skillGroupMapper).deleteById("sg-001");
    }
}

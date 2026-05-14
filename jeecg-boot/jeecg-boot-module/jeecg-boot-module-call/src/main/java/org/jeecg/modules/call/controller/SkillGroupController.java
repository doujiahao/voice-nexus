package org.jeecg.modules.call.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.call.entity.SkillGroup;
import org.jeecg.modules.call.entity.SkillGroupAgent;
import org.jeecg.modules.call.mapper.SkillGroupAgentMapper;
import org.jeecg.modules.call.mapper.SkillGroupMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@Slf4j
@Tag(name = "技能组管理")
@RestController
@RequestMapping("/api/v1/skill-groups")
public class SkillGroupController {

    @Autowired
    private SkillGroupMapper skillGroupMapper;
    @Autowired
    private SkillGroupAgentMapper skillGroupAgentMapper;

    @Operation(summary = "技能组列表")
    @GetMapping
    public Result<Page<SkillGroup>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
        LambdaQueryWrapper<SkillGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SkillGroup::getCreateTime);
        Page<SkillGroup> result = skillGroupMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return Result.OK(result);
    }

    @Operation(summary = "技能组详情")
    @GetMapping("/{id}")
    public Result<SkillGroup> detail(@PathVariable String id) {
        SkillGroup group = skillGroupMapper.selectById(id);
        if (group == null) {
            return Result.error("技能组不存在");
        }
        return Result.OK(group);
    }

    @Operation(summary = "创建技能组")
    @PostMapping
    public Result<SkillGroup> create(@RequestBody SkillGroup group) {
        group.setCreateTime(new Date());
        if (group.getEnabled() == null) {
            group.setEnabled(1);
        }
        skillGroupMapper.insert(group);
        return Result.OK(group);
    }

    @Operation(summary = "更新技能组")
    @PutMapping("/{id}")
    public Result<SkillGroup> update(@PathVariable String id, @RequestBody SkillGroup group) {
        group.setId(id);
        group.setUpdateTime(new Date());
        skillGroupMapper.updateById(group);
        return Result.OK(group);
    }

    @Operation(summary = "删除技能组")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        skillGroupMapper.deleteById(id);
        return Result.OK("删除成功");
    }

    @Operation(summary = "技能组下的坐席列表")
    @GetMapping("/{groupId}/agents")
    public Result<List<SkillGroupAgent>> listAgents(@PathVariable String groupId) {
        List<SkillGroupAgent> agents = skillGroupAgentMapper.selectList(
                new LambdaQueryWrapper<SkillGroupAgent>().eq(SkillGroupAgent::getSkillGroupId, groupId));
        return Result.OK(agents);
    }

    @Operation(summary = "分配坐席到技能组")
    @PostMapping("/{groupId}/agents")
    public Result<SkillGroupAgent> assignAgent(@PathVariable String groupId, @RequestBody SkillGroupAgent assignment) {
        assignment.setSkillGroupId(groupId);
        assignment.setCreateTime(new Date());
        if (assignment.getSkillLevel() == null) {
            assignment.setSkillLevel(1);
        }
        skillGroupAgentMapper.insert(assignment);
        return Result.OK(assignment);
    }

    @Operation(summary = "从技能组移除坐席")
    @DeleteMapping("/{groupId}/agents/{assignmentId}")
    public Result<?> removeAgent(@PathVariable String groupId, @PathVariable String assignmentId) {
        skillGroupAgentMapper.deleteById(assignmentId);
        return Result.OK("移除成功");
    }

    @Operation(summary = "更新坐席技能等级")
    @PutMapping("/{groupId}/agents/{assignmentId}")
    public Result<SkillGroupAgent> updateSkillLevel(@PathVariable String groupId,
                                                     @PathVariable String assignmentId,
                                                     @RequestBody SkillGroupAgent assignment) {
        assignment.setId(assignmentId);
        assignment.setUpdateTime(new Date());
        skillGroupAgentMapper.updateById(assignment);
        return Result.OK(assignment);
    }
}

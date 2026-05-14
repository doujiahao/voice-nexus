package org.jeecg.modules.call.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.call.entity.AgentProfile;
import org.jeecg.modules.call.enums.AgentStatusEnum;
import org.jeecg.modules.call.mapper.AgentProfileMapper;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.jeecg.modules.call.ws.CallWebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "管理员-坐席管理")
@RestController
@RequestMapping("/api/v1/admin/agents")
public class AdminAgentController {

    @Autowired
    private AgentProfileMapper agentProfileMapper;
    @Autowired
    private IAgentProfileService agentProfileService;

    @Operation(summary = "坐席列表（含实时状态）")
    @GetMapping
    public Result<Page<AgentProfile>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {

        LambdaQueryWrapper<AgentProfile> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(AgentProfile::getStatus, status);
        }
        wrapper.orderByDesc(AgentProfile::getCreateTime);

        Page<AgentProfile> result = agentProfileMapper.selectPage(new Page<>(page, pageSize), wrapper);

        for (AgentProfile agent : result.getRecords()) {
            AgentStatusEnum currentStatus = agentProfileService.getCurrentStatus(agent.getUserId());
            if (currentStatus != null) {
                agent.setStatus(currentStatus.name());
            }
        }

        return Result.OK(result);
    }

    @Operation(summary = "坐席状态统计")
    @GetMapping("/stats")
    public Result<JSONObject> stats() {
        List<AgentProfile> all = agentProfileMapper.selectList(new LambdaQueryWrapper<>());

        Map<String, Integer> statusCount = new HashMap<>();
        int onlineCount = 0;

        for (AgentProfile agent : all) {
            AgentStatusEnum currentStatus = agentProfileService.getCurrentStatus(agent.getUserId());
            String s = currentStatus != null ? currentStatus.name() : agent.getStatus();
            statusCount.merge(s, 1, Integer::sum);
            if (!"OFFLINE".equals(s)) {
                onlineCount++;
            }
        }

        JSONObject data = new JSONObject();
        data.put("total", all.size());
        data.put("online", onlineCount);
        data.put("status_breakdown", statusCount);
        return Result.OK(data);
    }

    @Operation(summary = "强制变更坐席状态")
    @PostMapping("/{userId}/force-status")
    public Result<?> forceStatus(@PathVariable String userId, @RequestBody JSONObject body) {
        String targetStatus = body.getString("status");
        String reason = body.getString("reason");

        AgentStatusEnum statusEnum;
        try {
            statusEnum = AgentStatusEnum.valueOf(targetStatus);
        } catch (IllegalArgumentException e) {
            return Result.error("无效状态: " + targetStatus);
        }

        agentProfileService.changeStatus(userId, statusEnum, "管理员强制: " + (reason != null ? reason : ""));

        String frontendStatus = mapToFrontendStatus(statusEnum);
        CallWebSocket.pushAgentStatus(userId, frontendStatus);

        return Result.OK("状态已变更为 " + targetStatus);
    }

    private String mapToFrontendStatus(AgentStatusEnum status) {
        return switch (status) {
            case ONLINE -> "idle";
            case REST -> "busy";
            case OFFLINE -> "offline";
            case TALKING, RINGING, HOLDING -> "on_call";
            case WRAP_UP -> "busy";
        };
    }
}

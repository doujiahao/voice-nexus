package org.jeecg.modules.call.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.call.entity.AgentProfile;
import org.jeecg.modules.call.enums.AgentStatusEnum;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "坐席状态管理")
@RestController
@RequestMapping("/call/agent")
public class AgentStatusController {

    @Autowired
    private IAgentProfileService agentProfileService;

    @Operation(summary = "获取当前坐席状态")
    @GetMapping("/status")
    public Result<String> getStatus() {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        AgentStatusEnum status = agentProfileService.getCurrentStatus(user.getId());
        return Result.OK(status.getCode());
    }

    @Operation(summary = "变更坐席状态")
    @PostMapping("/status")
    public Result<String> changeStatus(@RequestParam String status,
                                       @RequestParam(required = false) String reason) {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        AgentStatusEnum newStatus = AgentStatusEnum.fromCode(status);
        agentProfileService.changeStatus(user.getId(), newStatus, reason);
        return Result.OK("状态已变更为: " + newStatus.getDesc());
    }

    @Operation(summary = "获取坐席档案")
    @GetMapping("/profile")
    public Result<AgentProfile> getProfile() {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        AgentProfile profile = agentProfileService.getByUserId(user.getId());
        return Result.OK(profile);
    }
}

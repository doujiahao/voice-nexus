package org.jeecg.modules.call.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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
@RequestMapping("/api/v1/agent")
public class AgentStatusController {

    @Autowired
    private IAgentProfileService agentProfileService;

    @Operation(summary = "获取当前坐席状态")
    @GetMapping("/status")
    public Result<String> getStatus() {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        log.info("[AgentAPI] 查询坐席状态: userId={}", user.getId());
        AgentStatusEnum status = agentProfileService.getCurrentStatus(user.getId());
        log.info("[AgentAPI] 坐席状态返回: userId={}, status={}", user.getId(), status.getCode());
        return Result.OK(status.getCode());
    }

    @Operation(summary = "变更坐席状态")
    @PostMapping("/status")
    public Result<String> changeStatus(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) String reason,
                                       @RequestBody(required = false) String body) {
        if (body != null && body.trim().startsWith("{")) {
            JSONObject json = JSON.parseObject(body);
            if (status == null) {
                status = json.getString("status");
            }
            if (reason == null) {
                reason = json.getString("reason");
            }
        }
        if (status == null) {
            log.warn("[AgentAPI] 变更坐席状态失败，缺少 status 参数");
            return Result.error("缺少坐席状态 status");
        }

        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        log.info("[AgentAPI] 变更坐席状态: userId={}, newStatus={}, reason={}", user.getId(), status, reason);
        AgentStatusEnum newStatus = AgentStatusEnum.fromCode(status);
        agentProfileService.changeStatus(user.getId(), newStatus, reason);
        log.info("[AgentAPI] 坐席状态变更完成: userId={}, newStatus={}", user.getId(), newStatus.getCode());
        return Result.OK("状态已变更为: " + newStatus.getDesc());
    }

    @Operation(summary = "获取坐席档案")
    @GetMapping("/profile")
    public Result<AgentProfile> getProfile() {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        log.info("[AgentAPI] 查询坐席档案: userId={}", user.getId());
        AgentProfile profile = agentProfileService.getByUserId(user.getId());
        log.info("[AgentAPI] 坐席档案返回: userId={}, agentId={}, agentNo={}, extension={}",
                user.getId(), profile != null ? profile.getId() : null,
                profile != null ? profile.getAgentNo() : null,
                profile != null ? profile.getExtension() : null);
        return Result.OK(profile);
    }
}

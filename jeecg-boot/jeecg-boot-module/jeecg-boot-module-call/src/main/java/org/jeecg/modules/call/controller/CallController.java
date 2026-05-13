package org.jeecg.modules.call.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.call.entity.*;
import org.jeecg.modules.call.mapper.*;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.jeecg.modules.call.service.ICallSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Tag(name = "话务外部接口")
@RestController
@RequestMapping("/api/v1")
public class CallController {

    @Autowired
    private IAgentProfileService agentProfileService;
    @Autowired
    private ICallSessionService callSessionService;
    @Autowired
    private CallTurnMapper callTurnMapper;
    @Autowired
    private CallTagMapper callTagMapper;
    @Autowired
    private CustomerMapper customerMapper;

    @Operation(summary = "获取坐席信息")
    @GetMapping("/agent/info")
    public Result<JSONObject> getAgentInfo() {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        AgentProfile profile = agentProfileService.getByUserId(user.getId());

        JSONObject data = new JSONObject();
        data.put("id", profile != null ? profile.getAgentNo() : user.getId());
        data.put("name", user.getRealname());
        data.put("role", "agent");
        data.put("avatar_char", user.getRealname() != null && !user.getRealname().isEmpty()
                ? user.getRealname().substring(0, 1) : "A");
        return Result.OK(data);
    }

    @Operation(summary = "通话记录列表")
    @GetMapping("/calls")
    public Result<JSONObject> listCalls(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(name = "start_time", required = false) String startTime,
            @RequestParam(name = "end_time", required = false) String endTime) {

        LambdaQueryWrapper<CallSession> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !"ALL".equals(status)) {
            wrapper.eq(CallSession::getStatus, status);
        }
        wrapper.orderByDesc(CallSession::getCreateTime);

        Page<CallSession> pageResult = callSessionService.page(new Page<>(page, pageSize), wrapper);

        List<JSONObject> items = pageResult.getRecords().stream().map(s -> {
            JSONObject item = new JSONObject();
            item.put("call_session_id", s.getId());
            item.put("started_at", s.getCreateTime());
            item.put("ended_at", s.getEndTime());
            item.put("duration_ms", s.getDurationSec() != null ? s.getDurationSec() * 1000L : null);
            item.put("phone", s.getCustomerPhone());
            item.put("status", s.getStatus());
            item.put("agent_id", s.getAgentId());
            // 查客户名
            if (s.getCustomerId() != null) {
                Customer c = customerMapper.selectById(s.getCustomerId());
                if (c != null) item.put("customer_name", c.getName());
            }
            return item;
        }).collect(Collectors.toList());

        JSONObject result = new JSONObject();
        result.put("items", items);
        result.put("total", pageResult.getTotal());
        return Result.OK(result);
    }

    @Operation(summary = "通话详情")
    @GetMapping("/calls/{callSessionId}")
    public Result<JSONObject> getCallDetail(@PathVariable String callSessionId) {
        CallSession session = callSessionService.getById(callSessionId);
        if (session == null) {
            return Result.error("通话记录不存在");
        }

        JSONObject data = new JSONObject();
        data.put("call_session_id", session.getId());
        data.put("phone", session.getCustomerPhone());
        data.put("fs_call_id", session.getFsCallId());
        data.put("agent_id", session.getAgentId());
        data.put("started_at", session.getCreateTime());
        data.put("ended_at", session.getEndTime());
        data.put("duration_sec", session.getDurationSec());
        data.put("status", session.getStatus());

        if (session.getCustomerId() != null) {
            Customer c = customerMapper.selectById(session.getCustomerId());
            if (c != null) data.put("customer_name", c.getName());
        }

        // 统计 turn 数
        Long turnCount = callTurnMapper.selectCount(
                new LambdaQueryWrapper<CallTurn>().eq(CallTurn::getSessionId, callSessionId));
        data.put("turn_count", turnCount);

        return Result.OK(data);
    }

    @Operation(summary = "通话转写记录")
    @GetMapping("/calls/{callSessionId}/turns")
    public Result<JSONObject> getCallTurns(@PathVariable String callSessionId) {
        List<CallTurn> turns = callTurnMapper.selectList(
                new LambdaQueryWrapper<CallTurn>()
                        .eq(CallTurn::getSessionId, callSessionId)
                        .orderByAsc(CallTurn::getTurnIndex));

        List<JSONObject> items = turns.stream().map(t -> {
            JSONObject item = new JSONObject();
            item.put("turn_id", t.getId());
            item.put("call_session_id", callSessionId);
            item.put("speaker_role", t.getSpeakerRole());
            item.put("speaker_id", t.getSpeakerId());
            item.put("raw_text", t.getText());
            item.put("corrected_text", t.getCorrectedText());
            item.put("duration_ms", t.getDurationMs());
            item.put("audio_url", t.getAudioUrl());
            item.put("intent", t.getIntent());
            item.put("emotion", t.getEmotion());
            item.put("ts", t.getStartTime());
            return item;
        }).collect(Collectors.toList());

        JSONObject result = new JSONObject();
        result.put("items", items);
        return Result.OK(result);
    }
}

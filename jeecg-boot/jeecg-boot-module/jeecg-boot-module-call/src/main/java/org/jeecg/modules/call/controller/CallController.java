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
import org.jeecg.modules.call.dto.CallListVO;
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
    private CallSessionMapper callSessionMapper;
    @Autowired
    private CallTurnMapper callTurnMapper;
    @Autowired
    private CallTagMapper callTagMapper;
    @Autowired
    private CustomerMapper customerMapper;
    @Autowired
    private AgentProfileMapper agentProfileMapper;

    @Operation(summary = "获取坐席信息")
    @GetMapping("/agent/info")
    public Result<JSONObject> getAgentInfo() {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        log.info("[CallAPI] 获取坐席信息: userId={}, username={}", user.getId(), user.getUsername());
        AgentProfile profile = agentProfileService.getByUserId(user.getId());

        JSONObject data = new JSONObject();
        data.put("id", profile != null ? profile.getAgentNo() : user.getId());
        data.put("name", user.getRealname());
        data.put("role", "agent");
        data.put("avatar_char", user.getRealname() != null && !user.getRealname().isEmpty()
                ? user.getRealname().substring(0, 1) : "A");
        log.info("[CallAPI] 坐席信息返回: userId={}, agentNo={}, agentId={}", user.getId(),
                profile != null ? profile.getAgentNo() : null, profile != null ? profile.getId() : null);
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
        log.info("[CallAPI] 查询通话列表: page={}, pageSize={}, status={}, startTime={}, endTime={}", page, pageSize, status, startTime, endTime);

        Page<CallListVO> pageResult = callSessionMapper.selectCallListPage(
                new Page<>(page, pageSize), status, startTime, endTime);

        List<JSONObject> items = pageResult.getRecords().stream().map(vo -> {
            JSONObject item = new JSONObject();
            item.put("call_session_id", vo.getId());
            item.put("started_at", vo.getCreateTime());
            item.put("ended_at", vo.getEndTime());
            item.put("duration_ms", vo.getDurationSec() != null ? vo.getDurationSec() * 1000L : null);
            item.put("phone", vo.getCustomerPhone());
            item.put("status", vo.getStatus());
            item.put("agent_id", vo.getAgentId());
            item.put("direction", vo.getDirection());
            item.put("customer_name", vo.getCustomerName());
            item.put("agent_name", vo.getAgentName());
            item.put("turn_count", vo.getTurnCount());
            if (vo.getSummary() != null && !vo.getSummary().isEmpty()) {
                try {
                    JSONObject summary = com.alibaba.fastjson.JSON.parseObject(vo.getSummary());
                    String intent = summary.getString("customer_intent");
                    item.put("summary_short", intent != null ? intent : "");
                } catch (Exception ignored) {
                    item.put("summary_short", "");
                }
            }
            return item;
        }).collect(Collectors.toList());

        JSONObject result = new JSONObject();
        result.put("items", items);
        result.put("total", pageResult.getTotal());
        log.info("[CallAPI] 通话列表返回: total={}, currentPage={}, itemCount={}", pageResult.getTotal(), page, items.size());
        return Result.OK(result);
    }

    @Operation(summary = "通话详情")
    @GetMapping("/calls/{callSessionId}")
    public Result<JSONObject> getCallDetail(@PathVariable String callSessionId) {
        log.info("[CallAPI] 查询通话详情: callSessionId={}", callSessionId);
        CallSession session = callSessionService.getById(callSessionId);
        if (session == null) {
            log.warn("[CallAPI] 通话记录不存在: callSessionId={}", callSessionId);
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

        // summary（来自 call_session.summary 字段，NLP session-summary 结果存在这里）
        if (session.getSummary() != null && !session.getSummary().isEmpty()) {
            try {
                JSONObject summary = com.alibaba.fastjson.JSON.parseObject(session.getSummary());
                data.put("summary", summary);
            } catch (Exception e) {
                JSONObject summary = new JSONObject();
                summary.put("summary", session.getSummary());
                data.put("summary", summary);
            }
        }

        return Result.OK(data);
    }

    @Operation(summary = "通话转写记录")
    @GetMapping("/calls/{callSessionId}/turns")
    public Result<JSONObject> getCallTurns(@PathVariable String callSessionId) {
        log.info("[CallAPI] 查询通话转写: callSessionId={}", callSessionId);
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
            item.put("entities", t.getEntities() != null ? JSONObject.parseObject(t.getEntities()) : new JSONObject());
            item.put("emotion", t.getEmotion());
            item.put("ts", t.getStartTime());
            return item;
        }).collect(Collectors.toList());

        JSONObject result = new JSONObject();
        result.put("items", items);
        log.info("[CallAPI] 通话转写返回: callSessionId={}, turnCount={}", callSessionId, items.size());
        return Result.OK(result);
    }

    @Operation(summary = "开启通话会话（前端备用）")
    @PostMapping("/calls/open")
    public Result<JSONObject> openCall(@RequestBody JSONObject body) {
        String agentId = body.getString("agent_id");
        String fsCallId = body.getString("fs_call_id");
        String phone = body.getString("phone");
        String customerName = body.getString("customer_name");
        log.info("[CallAPI] 开启通话会话: agentId={}, fsCallId={}, phone={}, customerName={}", agentId, fsCallId, phone, customerName);

        CallSession session = new CallSession();
        session.setFsCallId(fsCallId);
        session.setDirection("INBOUND");
        session.setStatus("TALKING");
        session.setCustomerPhone(phone);
        session.setAnswerTime(new Date());

        // 匹配坐席
        if (agentId != null) {
            AgentProfile profile = agentProfileService.getByUserId(agentId);
            if (profile != null) {
                session.setAgentId(profile.getId());
            }
        }

        callSessionService.save(session);
        log.info("[CallAPI] 通话会话已创建: sessionId={}, fsCallId={}, agentId={}", session.getId(), fsCallId, session.getAgentId());

        JSONObject data = new JSONObject();
        data.put("call_session_id", session.getId());
        return Result.OK(data);
    }

    @Operation(summary = "获取录音预签名URL")
    @GetMapping("/calls/{callSessionId}/audio-url")
    public Result<JSONObject> getAudioUrl(@PathVariable String callSessionId,
                                          @RequestParam(name = "turn_id", required = false) String turnId) {
        log.info("[CallAPI] 获取录音URL: callSessionId={}, turnId={}", callSessionId, turnId);
        JSONObject data = new JSONObject();

        if (turnId != null) {
            CallTurn turn = callTurnMapper.selectById(turnId);
            if (turn != null && turn.getAudioUrl() != null) {
                String url = org.jeecg.common.util.MinioUtil.getObjectUrl(
                        org.jeecg.common.util.MinioUtil.getBucketName(), turn.getAudioUrl(), 3600);
                data.put("url", url);
                data.put("turn_id", turnId);
            }
        } else {
            List<CallTurn> turns = callTurnMapper.selectList(
                    new LambdaQueryWrapper<CallTurn>()
                            .eq(CallTurn::getSessionId, callSessionId)
                            .isNotNull(CallTurn::getAudioUrl)
                            .orderByAsc(CallTurn::getTurnIndex));
            List<JSONObject> urls = turns.stream().map(t -> {
                JSONObject item = new JSONObject();
                item.put("turn_id", t.getId());
                item.put("turn_index", t.getTurnIndex());
                item.put("speaker_role", t.getSpeakerRole());
                item.put("url", org.jeecg.common.util.MinioUtil.getObjectUrl(
                        org.jeecg.common.util.MinioUtil.getBucketName(), t.getAudioUrl(), 3600));
                return item;
            }).collect(Collectors.toList());
            data.put("items", urls);
        }

        return Result.OK(data);
    }

    @Operation(summary = "通话标签列表")
    @GetMapping("/calls/{callSessionId}/tags")
    public Result<JSONObject> getCallTags(@PathVariable String callSessionId) {
        log.info("[CallAPI] 查询通话标签: callSessionId={}", callSessionId);
        List<CallTag> tags = callTagMapper.selectList(
                new LambdaQueryWrapper<CallTag>().eq(CallTag::getSessionId, callSessionId));

        List<JSONObject> items = tags.stream().map(t -> {
            JSONObject item = new JSONObject();
            item.put("id", t.getId());
            item.put("tag_name", t.getTagName());
            item.put("source", t.getSource());
            item.put("confidence", t.getConfidence());
            return item;
        }).collect(Collectors.toList());

        JSONObject result = new JSONObject();
        result.put("items", items);
        return Result.OK(result);
    }

    @Operation(summary = "手动添加通话标签")
    @PostMapping("/calls/{callSessionId}/tags")
    public Result<CallTag> addTag(@PathVariable String callSessionId, @RequestBody JSONObject body) {
        log.info("[CallAPI] 添加通话标签: callSessionId={}, tagName={}", callSessionId, body.getString("tag_name"));
        CallTag tag = new CallTag();
        tag.setSessionId(callSessionId);
        tag.setTagName(body.getString("tag_name"));
        tag.setSource("MANUAL");
        tag.setCreateTime(new Date());
        callTagMapper.insert(tag);
        log.info("[CallAPI] 标签已添加: callSessionId={}, tagId={}, tagName={}", callSessionId, tag.getId(), tag.getTagName());
        return Result.OK(tag);
    }

    @Operation(summary = "删除通话标签")
    @DeleteMapping("/calls/{callSessionId}/tags/{tagId}")
    public Result<?> deleteTag(@PathVariable String callSessionId, @PathVariable String tagId) {
        log.info("[CallAPI] 删除通话标签: callSessionId={}, tagId={}", callSessionId, tagId);
        callTagMapper.deleteById(tagId);
        return Result.OK("删除成功");
    }

    @Operation(summary = "更新通话备注")
    @PutMapping("/calls/{callSessionId}/remark")
    public Result<?> updateRemark(@PathVariable String callSessionId, @RequestBody JSONObject body) {
        String remark = body.getString("remark");
        log.info("[CallAPI] 更新通话备注: callSessionId={}, remarkLength={}", callSessionId, remark != null ? remark.length() : 0);
        CallSession session = callSessionService.getById(callSessionId);
        if (session == null) {
            log.warn("[CallAPI] 更新备注失败，通话记录不存在: callSessionId={}", callSessionId);
            return Result.error("通话记录不存在");
        }
        session.setRemark(remark);
        callSessionService.updateById(session);
        log.info("[CallAPI] 备注已更新: callSessionId={}", callSessionId);
        return Result.OK("备注更新成功");
    }
}

package org.jeecg.modules.call.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.entity.WorkOrder;
import org.jeecg.modules.call.mapper.WorkOrderMapper;
import org.jeecg.modules.call.service.ICallSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@Slf4j
@Tag(name = "AI辅助工单")
@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderAiController {

    @Autowired
    private ICallSessionService callSessionService;
    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Operation(summary = "AI辅助生成工单（从通话摘要提取）")
    @PostMapping("/ai-generate")
    public Result<WorkOrder> aiGenerate(@RequestBody JSONObject body) {
        String sessionId = body.getString("session_id");
        if (sessionId == null) {
            return Result.error("session_id 必填");
        }

        CallSession session = callSessionService.getById(sessionId);
        if (session == null) {
            return Result.error("通话记录不存在");
        }

        JSONObject summary = null;
        if (session.getSummary() != null && !session.getSummary().isEmpty()) {
            try {
                summary = JSON.parseObject(session.getSummary());
            } catch (Exception ignored) {}
        }

        WorkOrder order = new WorkOrder();
        order.setSessionId(sessionId);
        order.setCustomerId(session.getCustomerId());
        order.setAgentId(session.getAgentId());
        order.setSource("AI_EXTRACT");
        order.setStatus("OPEN");
        order.setOrderNo("WO" + System.currentTimeMillis());
        order.setCreateTime(new Date());

        if (summary != null) {
            order.setCustomerIntent(summary.getString("customer_intent"));
            order.setTitle(buildTitle(summary));
            order.setDescription(buildDescription(summary));
            order.setPriority(inferPriority(summary));
            order.setCategory(inferCategory(summary));
            if (summary.containsKey("entities")) {
                order.setKeyInfo(summary.getString("entities"));
            }
        } else {
            order.setTitle("通话工单 - " + (session.getCustomerPhone() != null ? session.getCustomerPhone() : sessionId));
            order.setPriority("MEDIUM");
        }

        workOrderMapper.insert(order);
        return Result.OK(order);
    }

    private String buildTitle(JSONObject summary) {
        String intent = summary.getString("customer_intent");
        if (intent != null && !intent.isEmpty()) {
            return intent.length() > 50 ? intent.substring(0, 50) : intent;
        }
        return "通话工单";
    }

    private String buildDescription(JSONObject summary) {
        StringBuilder sb = new StringBuilder();
        if (summary.containsKey("key_points")) {
            sb.append("关键信息：\n");
            for (Object point : summary.getJSONArray("key_points")) {
                sb.append("- ").append(point).append("\n");
            }
        }
        if (summary.containsKey("pending_actions")) {
            sb.append("\n待处理：\n");
            for (Object action : summary.getJSONArray("pending_actions")) {
                sb.append("- ").append(action).append("\n");
            }
        }
        return sb.toString();
    }

    private String inferPriority(JSONObject summary) {
        String emotion = summary.getString("emotion");
        if ("urgent".equals(emotion) || "angry".equals(emotion)) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private String inferCategory(JSONObject summary) {
        String intent = summary.getString("customer_intent");
        if (intent == null) return null;
        if (intent.contains("报修") || intent.contains("停电")) return "REPAIR";
        if (intent.contains("投诉")) return "COMPLAINT";
        if (intent.contains("咨询")) return "INQUIRY";
        return "OTHER";
    }
}

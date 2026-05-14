package org.jeecg.modules.call.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.call.entity.WorkOrder;
import org.jeecg.modules.call.entity.WorkOrderItem;
import org.jeecg.modules.call.mapper.WorkOrderItemMapper;
import org.jeecg.modules.call.mapper.WorkOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Tag(name = "工单管理")
@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {

    @Autowired
    private WorkOrderMapper workOrderMapper;
    @Autowired
    private WorkOrderItemMapper workOrderItemMapper;

    @Operation(summary = "工单列表")
    @GetMapping
    public Result<Page<WorkOrder>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(name = "customer_id", required = false) String customerId,
            @RequestParam(name = "session_id", required = false) String sessionId) {

        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(WorkOrder::getStatus, status);
        }
        if (customerId != null) {
            wrapper.eq(WorkOrder::getCustomerId, customerId);
        }
        if (sessionId != null) {
            wrapper.eq(WorkOrder::getSessionId, sessionId);
        }
        wrapper.orderByDesc(WorkOrder::getCreateTime);

        Page<WorkOrder> result = workOrderMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return Result.OK(result);
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public Result<WorkOrder> detail(@PathVariable String id) {
        WorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            return Result.error("工单不存在");
        }
        return Result.OK(order);
    }

    @Operation(summary = "创建工单")
    @PostMapping
    public Result<WorkOrder> create(@RequestBody WorkOrder order) {
        order.setOrderNo(generateOrderNo());
        if (order.getStatus() == null) {
            order.setStatus("OPEN");
        }
        if (order.getSource() == null) {
            order.setSource("CALL");
        }
        order.setCreateTime(new Date());
        workOrderMapper.insert(order);

        addFlowItem(order.getId(), null, "OPEN", "CREATE", "工单创建");
        return Result.OK(order);
    }

    @Operation(summary = "更新工单")
    @PutMapping("/{id}")
    public Result<WorkOrder> update(@PathVariable String id, @RequestBody WorkOrder order) {
        order.setId(id);
        order.setUpdateTime(new Date());
        workOrderMapper.updateById(order);
        return Result.OK(order);
    }

    @Operation(summary = "工单状态流转")
    @PostMapping("/{id}/transition")
    @Transactional(rollbackFor = Exception.class)
    public Result<WorkOrder> transition(@PathVariable String id, @RequestBody WorkOrderItem item) {
        WorkOrder order = workOrderMapper.selectById(id);
        if (order == null) {
            return Result.error("工单不存在");
        }

        String fromStatus = order.getStatus();
        String toStatus = item.getToStatus();

        order.setStatus(toStatus);
        order.setUpdateTime(new Date());
        if ("RESOLVED".equals(toStatus)) {
            order.setResolvedTime(new Date());
            if (item.getContent() != null) {
                order.setResolution(item.getContent());
            }
        }
        if ("CLOSED".equals(toStatus)) {
            order.setClosedTime(new Date());
        }
        workOrderMapper.updateById(order);

        addFlowItem(id, fromStatus, toStatus, item.getAction(), item.getContent());
        return Result.OK(order);
    }

    @Operation(summary = "工单流转记录")
    @GetMapping("/{id}/items")
    public Result<List<WorkOrderItem>> listItems(@PathVariable String id) {
        List<WorkOrderItem> items = workOrderItemMapper.selectList(
                new LambdaQueryWrapper<WorkOrderItem>()
                        .eq(WorkOrderItem::getOrderId, id)
                        .orderByAsc(WorkOrderItem::getOperateTime));
        return Result.OK(items);
    }

    @Operation(summary = "删除工单")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        workOrderMapper.deleteById(id);
        return Result.OK("删除成功");
    }

    private void addFlowItem(String orderId, String fromStatus, String toStatus, String action, String content) {
        LoginUser user = null;
        try {
            user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        } catch (Exception ignored) {}

        WorkOrderItem item = new WorkOrderItem();
        item.setOrderId(orderId);
        item.setFromStatus(fromStatus);
        item.setToStatus(toStatus);
        item.setAction(action != null ? action : "TRANSITION");
        item.setContent(content);
        item.setOperateTime(new Date());
        item.setOperatorId(user != null ? user.getId() : "system");
        item.setCreateTime(new Date());
        workOrderItemMapper.insert(item);
    }

    private String generateOrderNo() {
        return "WO" + System.currentTimeMillis();
    }
}

package org.jeecg.modules.call.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.entity.WorkOrder;
import org.jeecg.modules.call.mapper.WorkOrderMapper;
import org.jeecg.modules.call.service.ICallSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.jeecg.common.api.vo.Result;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderAiControllerTest {

    @Mock private ICallSessionService callSessionService;
    @Mock private WorkOrderMapper workOrderMapper;

    @InjectMocks
    private WorkOrderAiController workOrderAiController;

    @Test
    void aiGenerate_shouldReturnError_whenSessionIdMissing() {
        JSONObject body = new JSONObject();
        Result<WorkOrder> result = workOrderAiController.aiGenerate(body);
        assertFalse(result.isSuccess());
    }

    @Test
    void aiGenerate_shouldReturnError_whenSessionNotFound() {
        JSONObject body = new JSONObject();
        body.put("session_id", "sess-999");
        when(callSessionService.getById("sess-999")).thenReturn(null);

        Result<WorkOrder> result = workOrderAiController.aiGenerate(body);
        assertFalse(result.isSuccess());
    }

    @Test
    void aiGenerate_shouldExtractFromSummary() {
        CallSession session = new CallSession();
        session.setId("sess-001");
        session.setCustomerId("cust-001");
        session.setAgentId("agent-001");
        session.setCustomerPhone("13800000001");

        JSONObject summary = new JSONObject();
        summary.put("customer_intent", "报修停电");
        summary.put("emotion", "angry");
        summary.put("key_points", List.of("小区停电2小时", "已报修未处理"));
        summary.put("pending_actions", List.of("派工单给维修队"));
        session.setSummary(summary.toJSONString());

        when(callSessionService.getById("sess-001")).thenReturn(session);

        JSONObject body = new JSONObject();
        body.put("session_id", "sess-001");

        Result<WorkOrder> result = workOrderAiController.aiGenerate(body);
        assertTrue(result.isSuccess());

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).insert(captor.capture());
        WorkOrder order = captor.getValue();

        assertEquals("sess-001", order.getSessionId());
        assertEquals("cust-001", order.getCustomerId());
        assertEquals("agent-001", order.getAgentId());
        assertEquals("AI_EXTRACT", order.getSource());
        assertEquals("OPEN", order.getStatus());
        assertEquals("HIGH", order.getPriority());
        assertEquals("REPAIR", order.getCategory());
        assertEquals("报修停电", order.getCustomerIntent());
        assertTrue(order.getOrderNo().startsWith("WO"));
        assertTrue(order.getDescription().contains("小区停电2小时"));
    }

    @Test
    void aiGenerate_shouldHandleNoSummary() {
        CallSession session = new CallSession();
        session.setId("sess-001");
        session.setCustomerPhone("13800000001");
        session.setSummary(null);

        when(callSessionService.getById("sess-001")).thenReturn(session);

        JSONObject body = new JSONObject();
        body.put("session_id", "sess-001");

        Result<WorkOrder> result = workOrderAiController.aiGenerate(body);
        assertTrue(result.isSuccess());

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).insert(captor.capture());
        WorkOrder order = captor.getValue();

        assertEquals("MEDIUM", order.getPriority());
        assertTrue(order.getTitle().contains("13800000001"));
    }

    @Test
    void aiGenerate_shouldInferComplaintCategory() {
        CallSession session = new CallSession();
        session.setId("sess-001");

        JSONObject summary = new JSONObject();
        summary.put("customer_intent", "投诉服务态度差");
        summary.put("emotion", "neutral");
        session.setSummary(summary.toJSONString());

        when(callSessionService.getById("sess-001")).thenReturn(session);

        JSONObject body = new JSONObject();
        body.put("session_id", "sess-001");

        Result<WorkOrder> result = workOrderAiController.aiGenerate(body);
        assertTrue(result.isSuccess());

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).insert(captor.capture());
        assertEquals("COMPLAINT", captor.getValue().getCategory());
    }

    @Test
    void aiGenerate_shouldInferInquiryCategory() {
        CallSession session = new CallSession();
        session.setId("sess-001");

        JSONObject summary = new JSONObject();
        summary.put("customer_intent", "咨询电费账单");
        summary.put("emotion", "neutral");
        session.setSummary(summary.toJSONString());

        when(callSessionService.getById("sess-001")).thenReturn(session);

        JSONObject body = new JSONObject();
        body.put("session_id", "sess-001");

        Result<WorkOrder> result = workOrderAiController.aiGenerate(body);
        assertTrue(result.isSuccess());

        ArgumentCaptor<WorkOrder> captor = ArgumentCaptor.forClass(WorkOrder.class);
        verify(workOrderMapper).insert(captor.capture());
        assertEquals("INQUIRY", captor.getValue().getCategory());
    }
}

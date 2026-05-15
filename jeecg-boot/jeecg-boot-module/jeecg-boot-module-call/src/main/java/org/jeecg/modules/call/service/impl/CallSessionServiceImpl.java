package org.jeecg.modules.call.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.call.dto.CallEventDTO;
import org.jeecg.modules.call.entity.AgentProfile;
import org.jeecg.modules.call.entity.CallEventLog;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.enums.AgentStatusEnum;
import org.jeecg.modules.call.mapper.AgentProfileMapper;
import org.jeecg.modules.call.mapper.CallEventLogMapper;
import org.jeecg.modules.call.mapper.CallSessionMapper;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.jeecg.modules.call.service.ICallQueueService;
import org.jeecg.modules.call.service.ICallSessionService;
import org.jeecg.modules.call.ws.CallWebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class CallSessionServiceImpl extends ServiceImpl<CallSessionMapper, CallSession> implements ICallSessionService {

    @Autowired
    private CallEventLogMapper callEventLogMapper;
    @Autowired
    private AgentProfileMapper agentProfileMapper;
    @Autowired
    private IAgentProfileService agentProfileService;
    @Autowired
    private ICallQueueService callQueueService;
    @Autowired
    private CallEndProcessor callEndProcessor;

    @Override
    public CallSession getByFsCallId(String fsCallId) {
        return getOne(new LambdaQueryWrapper<CallSession>()
                .eq(CallSession::getFsCallId, fsCallId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handleEvent(String fsCallId, CallEventDTO event) {
        log.info("[CallEvent] 开始处理通话事件: fsCallId={}, eventType={}, endedBy={}, durationSec={}, metadata={}",
                fsCallId, event.getEventType(), event.getEndedBy(), event.getDurationSec(), event.getMetadata());
        CallSession session = getByFsCallId(fsCallId);
        Map<String, Object> result = new HashMap<>();

        if (session == null) {
            log.warn("[Inbound] 通话事件找不到会话: fsCallId={}, eventType={}", fsCallId, event.getEventType());
            result.put("acknowledged", false);
            result.put("error", "Session not found for fs_call_id: " + fsCallId);
            return result;
        }
        log.info("[Inbound] 通话事件匹配会话: fsCallId={}, sessionId={}, currentStatus={}, eventType={}",
                fsCallId, session.getId(), session.getStatus(), event.getEventType());

        // 记录事件日志
        CallEventLog eventLog = new CallEventLog();
        eventLog.setSessionId(session.getId());
        eventLog.setEventType(event.getEventType());
        eventLog.setEventTime(new Date());
        eventLog.setOperatorType("FSS");
        if (event.getMetadata() != null) {
            eventLog.setDetail(JSON.toJSONString(event.getMetadata()));
        }
        callEventLogMapper.insert(eventLog);
        log.info("[CallEvent] 已写入通话事件日志: fsCallId={}, sessionId={}, eventType={}, detail={}",
                fsCallId, session.getId(), event.getEventType(), eventLog.getDetail());

        // 处理不同事件类型
        switch (event.getEventType()) {
            case "CALL_ENDED":
                log.info("[CallEvent] 处理 CALL_ENDED: fsCallId={}, sessionId={}, agentId={}, endedBy={}, durationSec={}, metadata={}",
                        fsCallId, session.getId(), session.getAgentId(), event.getEndedBy(), event.getDurationSec(), event.getMetadata());
                endSession(session, event.getEndedBy(),
                        event.getMetadata() != null ? event.getMetadata().get("hangup_cause") : "NORMAL",
                        event.getDurationSec());
                result.put("status", "ENDING");
                break;
            case "ANSWERED":
                log.info("[CallEvent] 处理 ANSWERED: fsCallId={}, sessionId={}, agentId={}, oldStatus={}",
                        fsCallId, session.getId(), session.getAgentId(), session.getStatus());
                if ("ENDED".equals(session.getStatus())) {
                    log.warn("[CallEvent] 忽略已结束会话的接通事件: fsCallId={}, sessionId={}, agentId={}",
                            fsCallId, session.getId(), session.getAgentId());
                    result.put("status", session.getStatus());
                    break;
                }
                if (session.getAgentId() == null) {
                    log.warn("[CallEvent] 忽略无坐席会话的接通事件: fsCallId={}, sessionId={}, currentStatus={}",
                            fsCallId, session.getId(), session.getStatus());
                    result.put("status", session.getStatus());
                    break;
                }
                String previousStatus = session.getStatus();
                session.setStatus("TALKING");
                session.setAnswerTime(new Date());
                updateById(session);
                log.info("[CallEvent] 已更新会话为 TALKING: fsCallId={}, sessionId={}, agentId={}",
                        fsCallId, session.getId(), session.getAgentId());
                AgentProfile agent = agentProfileMapper.selectById(session.getAgentId());
                if (agent != null) {
                    log.info("[CallEvent] 准备更新接通坐席状态: fsCallId={}, sessionId={}, agentId={}, userId={}, previousStatus={}",
                            fsCallId, session.getId(), agent.getId(), agent.getUserId(), previousStatus);
                    agentProfileService.changeStatus(agent.getUserId(), AgentStatusEnum.TALKING, "通话接通");
                    if ("RINGING".equals(previousStatus)) {
                        CallWebSocket.pushIncomingCallAnswered(agent.getUserId(), session.getId(), session.getFsCallId());
                    }
                    CallWebSocket.pushCallSession(agent.getUserId(), session.getId());
                    CallWebSocket.pushAgentStatus(agent.getUserId(), "on_call");
                    CallWebSocket.pushCallState(agent.getUserId(), "active");
                    log.info("[CallEvent] 已推送话机接听状态: fsCallId={}, sessionId={}, userId={}, previousStatus={}, agentStatus=on_call, callState=active",
                            fsCallId, session.getId(), agent.getUserId(), previousStatus);
                } else {
                    log.warn("[CallEvent] 接通事件找不到坐席档案: fsCallId={}, sessionId={}, agentId={}",
                            fsCallId, session.getId(), session.getAgentId());
                }
                result.put("status", "TALKING");
                break;
            default:
                log.info("[CallEvent] 通话事件无需状态转换: fsCallId={}, sessionId={}, eventType={}, currentStatus={}",
                        fsCallId, session.getId(), event.getEventType(), session.getStatus());
                result.put("status", session.getStatus());
                break;
        }

        result.put("acknowledged", true);
        result.put("call_session_id", session.getId());
        log.info("[CallEvent] 通话事件处理完成: fsCallId={}, sessionId={}, eventType={}, result={}",
                fsCallId, session.getId(), event.getEventType(), result);
        return result;
    }

    @Override
    public void updateStatus(String sessionId, String newStatus) {
        CallSession session = getById(sessionId);
        if (session != null) {
            session.setStatus(newStatus);
            updateById(session);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void endSession(CallSession session, String endedBy, String hangupCause, Integer durationSec) {
        log.info("[CallEvent] 准备结束会话: fsCallId={}, sessionId={}, agentId={}, endedBy={}, hangupCause={}, durationSec={}",
                session.getFsCallId(), session.getId(), session.getAgentId(), endedBy, hangupCause, durationSec);
        String previousStatus = session.getStatus();
        session.setStatus("ENDED");
        session.setEndTime(new Date());
        session.setEndedBy(endedBy);
        session.setHangupCause(hangupCause);
        session.setDurationSec(durationSec);
        updateById(session);
        log.info("[CallEvent] 已更新会话为 ENDED: fsCallId={}, sessionId={}, agentId={}, hangupCause={}",
                session.getFsCallId(), session.getId(), session.getAgentId(), hangupCause);
        removeFromQueueIfNeeded(session);
        pushIncomingCancelledIfRinging(session, previousStatus, hangupCause);

        // 坐席恢复空闲
        if (session.getAgentId() != null) {
            AgentProfile agent = agentProfileMapper.selectById(session.getAgentId());
            if (agent != null) {
                log.info("[CallEvent] 准备更新结束坐席状态: fsCallId={}, sessionId={}, agentId={}, userId={}, previousStatus={}",
                        session.getFsCallId(), session.getId(), agent.getId(), agent.getUserId(), previousStatus);
                if ("RINGING".equals(previousStatus)) {
                    String reason = "来电取消: " + hangupCause;
                    agentProfileService.changeStatus(agent.getUserId(), AgentStatusEnum.ONLINE, reason);
                    CallWebSocket.pushAgentStatus(agent.getUserId(), "idle");
                    CallWebSocket.pushCallState(agent.getUserId(), "idle");
                    log.info("[CallEvent] 已推送振铃取消状态: fsCallId={}, sessionId={}, userId={}, hangupCause={}, agentStatus=idle, callState=idle",
                            session.getFsCallId(), session.getId(), agent.getUserId(), hangupCause);
                } else {
                    agentProfileService.changeStatus(agent.getUserId(), AgentStatusEnum.WRAP_UP, "通话结束");
                    CallWebSocket.pushAgentStatus(agent.getUserId(), "wrap_up");
                    CallWebSocket.pushCallState(agent.getUserId(), "idle");
                    log.info("[CallEvent] 已推送通话结束状态: fsCallId={}, sessionId={}, userId={}, previousStatus={}, agentStatus=wrap_up, callState=idle",
                            session.getFsCallId(), session.getId(), agent.getUserId(), previousStatus);
                }
            } else {
                log.warn("[CallEvent] 结束会话找不到坐席档案: fsCallId={}, sessionId={}, agentId={}",
                        session.getFsCallId(), session.getId(), session.getAgentId());
            }
        } else {
            log.info("[CallEvent] 结束会话无坐席可恢复: fsCallId={}, sessionId={}", session.getFsCallId(), session.getId());
        }

        log.info("[CallEvent] 准备执行通话结束后处理: fsCallId={}, sessionId={}", session.getFsCallId(), session.getId());
        callEndProcessor.processCallEnd(session);
        log.info("[CallEvent] 通话结束后处理完成: fsCallId={}, sessionId={}", session.getFsCallId(), session.getId());
    }

    private void removeFromQueueIfNeeded(CallSession session) {
        if (session.getSkillGroupId() == null) {
            return;
        }
        callQueueService.remove(session.getSkillGroupId(), session.getId());
        log.info("[CallEvent] 已清理排队队列: fsCallId={}, sessionId={}, skillGroupId={}",
                session.getFsCallId(), session.getId(), session.getSkillGroupId());
    }

    private void pushIncomingCancelledIfRinging(CallSession session, String previousStatus, String hangupCause) {
        if (!"RINGING".equals(previousStatus) || session.getAgentId() == null) {
            return;
        }
        AgentProfile agent = agentProfileMapper.selectById(session.getAgentId());
        if (agent == null || agent.getUserId() == null) {
            log.warn("[CallEvent] 来电取消通知跳过，找不到坐席用户: fsCallId={}, sessionId={}, agentId={}",
                    session.getFsCallId(), session.getId(), session.getAgentId());
            return;
        }
        CallWebSocket.pushIncomingCallCancelled(agent.getUserId(), session.getId(), session.getFsCallId(), hangupCause);
        log.info("[CallEvent] 已推送来电弹框取消: fsCallId={}, sessionId={}, userId={}, hangupCause={}",
                session.getFsCallId(), session.getId(), agent.getUserId(), hangupCause);
    }
}

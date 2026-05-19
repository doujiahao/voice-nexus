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
import org.jeecg.modules.call.ws.CallWebSocket;
import org.jeecg.modules.call.mapper.AgentProfileMapper;
import org.jeecg.modules.call.mapper.CallEventLogMapper;
import org.jeecg.modules.call.mapper.CallSessionMapper;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.jeecg.modules.call.service.ICallQueueService;
import org.jeecg.modules.call.service.ICallSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CallSessionServiceImpl extends ServiceImpl<CallSessionMapper, CallSession> implements ICallSessionService {

    private static final long RINGING_CONFIRM_TIMEOUT_SEC = 20;
    private static final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ringing-confirm-timeout");
        t.setDaemon(true);
        return t;
    });
    private static final ConcurrentHashMap<String, ScheduledFuture<?>> pendingRingingTimeouts = new ConcurrentHashMap<>();
    private static final String SESSION_LOCK_PREFIX = "call:session:lock:";
    private static final long SESSION_LOCK_TTL_SEC = 30;

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
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

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

        // RINGING 事件：创建 CallSession（如果不存在）
        if ("RINGING".equals(event.getEventType())) {
            if (session == null) {
                String lockKey = SESSION_LOCK_PREFIX + fsCallId;
                boolean locked = false;
                try {
                    locked = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, "1", java.time.Duration.ofSeconds(SESSION_LOCK_TTL_SEC)));
                } catch (Exception e) {
                    log.warn("[CallEvent] Redis 锁获取异常，fallback 为 selectOne: fsCallId={}", fsCallId, e);
                }
                if (!locked) {
                    // 锁获取失败，等待后重查
                    try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    session = getByFsCallId(fsCallId);
                    if (session != null) {
                        log.info("[CallEvent] RINGING 锁等待后查到已有会话: fsCallId={}, sessionId={}", fsCallId, session.getId());
                    }
                }
                if (session == null) {
                    session = new CallSession();
                    session.setFsCallId(fsCallId);
                    session.setDirection("INBOUND");
                    session.setStatus("RINGING");
                    if (event.getMetadata() != null) {
                        session.setCustomerPhone(event.getMetadata().get("customerPhone"));
                        session.setCalledNumber(event.getMetadata().get("calledNumber"));
                        session.setSkillGroupId(event.getMetadata().get("skillGroupId"));
                        session.setAgentId(event.getMetadata().get("agentId"));
                    }
                    session.setRingTime(new Date());
                    session.setQueueEnterTime(new Date());
                    save(session);
                    log.info("[CallEvent] RINGING 创建呼入会话: fsCallId={}, sessionId={}, agentId={}",
                            fsCallId, session.getId(), session.getAgentId());
                }
                try { redisTemplate.delete(lockKey); } catch (Exception ignored) {}
            }

            // 更新坐席状态为 RINGING
            if (session.getAgentId() != null) {
                AgentProfile agent = agentProfileMapper.selectById(session.getAgentId());
                if (agent != null) {
                    agentProfileService.changeStatus(agent.getUserId(), AgentStatusEnum.RINGING, "来电分配");
                    // 推送来电预告（不弹窗），等待 RINGING_CONFIRMED 后再弹窗
                    CallWebSocket.pushIncomingCallPending(
                            agent.getUserId(),
                            session.getId(),
                            session.getCustomerPhone(),
                            null,
                            fsCallId);
                    log.info("[CallEvent] RINGING 已推送来电预告: fsCallId={}, sessionId={}, agentUserId={}",
                            fsCallId, session.getId(), agent.getUserId());

                    // 20s 超时兜底：未收到 RINGING_CONFIRMED 则自动推送弹窗
                    String agentUserId = agent.getUserId();
                    String sessionId = session.getId();
                    String customerPhone = session.getCustomerPhone();
                    ScheduledFuture<?> timeout = timeoutScheduler.schedule(() -> {
                        if (pendingRingingTimeouts.remove(fsCallId) != null) {
                            log.warn("[CallEvent] RINGING_CONFIRMED 超时，自动推送来电弹窗: fsCallId={}, sessionId={}", fsCallId, sessionId);
                            CallWebSocket.pushIncomingCall(agentUserId, sessionId, customerPhone, null, fsCallId);
                        }
                    }, RINGING_CONFIRM_TIMEOUT_SEC, TimeUnit.SECONDS);
                    pendingRingingTimeouts.put(fsCallId, timeout);
                }
            }

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

            result.put("status", "RINGING");
            result.put("acknowledged", true);
            result.put("call_session_id", session.getId());
            return result;
        }

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

        // RINGING_CONFIRMED 事件：坐席端已振铃，推送来电弹窗
        if ("RINGING_CONFIRMED".equals(event.getEventType())) {
            // 取消超时兜底
            ScheduledFuture<?> timeout = pendingRingingTimeouts.remove(fsCallId);
            if (timeout != null) {
                timeout.cancel(false);
            }
            if (session.getAgentId() != null) {
                AgentProfile agent = agentProfileMapper.selectById(session.getAgentId());
                if (agent != null) {
                    CallWebSocket.pushIncomingCall(
                            agent.getUserId(),
                            session.getId(),
                            session.getCustomerPhone(),
                            null,
                            fsCallId);
                    log.info("[CallEvent] RINGING_CONFIRMED 已推送来电弹窗: fsCallId={}, sessionId={}, agentUserId={}",
                            fsCallId, session.getId(), agent.getUserId());
                }
            }
            result.put("acknowledged", true);
            result.put("call_session_id", session.getId());
            return result;
        }

        // 处理不同事件类型
        switch (event.getEventType()) {
            case "CALL_ENDED":
                log.info("[CallEvent] 处理 CALL_ENDED: fsCallId={}, sessionId={}, agentId={}, endedBy={}, durationSec={}, metadata={}",
                        fsCallId, session.getId(), session.getAgentId(), event.getEndedBy(), event.getDurationSec(), event.getMetadata());
                // 清理 RINGING 超时兜底
                ScheduledFuture<?> endedTimeout = pendingRingingTimeouts.remove(fsCallId);
                if (endedTimeout != null) {
                    endedTimeout.cancel(false);
                }
                // 振铃中坐席挂断 = 拒接：会话放回排队，推送 incoming_call_cancelled
                if ("RINGING".equals(session.getStatus())
                        && event.getEndedBy() != null
                        && !"CUSTOMER".equals(event.getEndedBy())) {
                    log.info("[CallEvent] 振铃中坐席拒接: fsCallId={}, sessionId={}, endedBy={}", fsCallId, session.getId(), event.getEndedBy());
                    session.setStatus("QUEUING");
                    updateById(session);
                    if (session.getAgentId() != null) {
                        AgentProfile rejectAgent = agentProfileMapper.selectById(session.getAgentId());
                        if (rejectAgent != null) {
                            agentProfileService.changeStatus(rejectAgent.getUserId(), AgentStatusEnum.ONLINE, "坐席拒接(Linphone)");
                            CallWebSocket.pushCallState(rejectAgent.getUserId(), "idle");
                            CallWebSocket.pushIncomingCallCancelled(rejectAgent.getUserId(), session.getId(), session.getCustomerPhone(), fsCallId);
                            log.info("[CallEvent] 已推送拒接通知: fsCallId={}, sessionId={}, agentUserId={}",
                                    fsCallId, session.getId(), rejectAgent.getUserId());
                        }
                    }
                    removeFromQueueIfNeeded(session);
                    result.put("status", "QUEUING");
                    break;
                }
                endSession(session, event.getEndedBy(),
                        event.getMetadata() != null ? event.getMetadata().get("hangup_cause") : "NORMAL",
                        event.getDurationSec());
                result.put("status", "ENDING");
                break;
            case "ANSWERED":
                log.info("[CallEvent] 处理 ANSWERED: fsCallId={}, sessionId={}, agentId={}, oldStatus={}",
                        fsCallId, session.getId(), session.getAgentId(), session.getStatus());
                // 取消 RINGING 超时兜底（ANSWERED 先于 RINGING_CONFIRMED 到达时）
                ScheduledFuture<?> answeredTimeout = pendingRingingTimeouts.remove(fsCallId);
                if (answeredTimeout != null) {
                    answeredTimeout.cancel(false);
                    log.info("[CallEvent] ANSWERED 取消 RINGING 超时: fsCallId={}", fsCallId);
                }
                if ("ENDED".equals(session.getStatus())) {
                    log.warn("[CallEvent] 忽略已结束会话的接通事件: fsCallId={}, sessionId={}, agentId={}",
                            fsCallId, session.getId(), session.getAgentId());
                    result.put("status", session.getStatus());
                    break;
                }
                // 幂等：已 TALKING 说明前端已接听，跳过重复处理
                if ("TALKING".equals(session.getStatus())) {
                    log.info("[CallEvent] 会话已接通，跳过重复 ANSWED: fsCallId={}, sessionId={}", fsCallId, session.getId());
                    result.put("status", "TALKING");
                    break;
                }
                if (session.getAgentId() == null) {
                    log.warn("[CallEvent] 忽略无坐席会话的接通事件: fsCallId={}, sessionId={}, currentStatus={}",
                            fsCallId, session.getId(), session.getStatus());
                    result.put("status", session.getStatus());
                    break;
                }
                session.setStatus("TALKING");
                session.setAnswerTime(new Date());
                try {
                    updateById(session);
                } catch (com.baomidou.mybatisplus.core.exceptions.OptimisticLockerException e) {
                    log.warn("[CallEvent] ANSWERED 乐观锁冲突，跳过: fsCallId={}, sessionId={}", fsCallId, session.getId());
                    result.put("status", session.getStatus());
                    break;
                }
                log.info("[CallEvent] 已更新会话为 TALKING: fsCallId={}, sessionId={}, agentId={}",
                        fsCallId, session.getId(), session.getAgentId());
                AgentProfile agent = agentProfileMapper.selectById(session.getAgentId());
                if (agent != null) {
                    log.info("[CallEvent] 准备更新接通坐席状态: fsCallId={}, sessionId={}, agentId={}, userId={}",
                            fsCallId, session.getId(), agent.getId(), agent.getUserId());
                    agentProfileService.changeStatus(agent.getUserId(), AgentStatusEnum.TALKING, "通话接通");
                    // 通知前端通话已接通（Linphone 接听 → 前端同步）
                    CallWebSocket.pushCallState(agent.getUserId(), "active");
                    CallWebSocket.pushCallSession(agent.getUserId(), session.getId());
                    log.info("[CallEvent] ANSWED 已推送 call_state:active + call_session: fsCallId={}, sessionId={}, agentUserId={}",
                            fsCallId, session.getId(), agent.getUserId());
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
        if ("ENDED".equals(session.getStatus())) {
            log.warn("[CallEvent] 会话已结束，跳过重复 endSession: fsCallId={}, sessionId={}", session.getFsCallId(), session.getId());
            return;
        }
        session.setStatus("ENDED");
        session.setEndTime(new Date());
        session.setEndedBy(endedBy);
        session.setHangupCause(hangupCause);
        session.setDurationSec(durationSec);
        try {
            updateById(session);
        } catch (com.baomidou.mybatisplus.core.exceptions.OptimisticLockerException e) {
            log.warn("[CallEvent] endSession 乐观锁冲突: fsCallId={}, sessionId={}, 将重试", session.getFsCallId(), session.getId());
            session = getById(session.getId());
            if (session == null || "ENDED".equals(session.getStatus())) {
                log.warn("[CallEvent] endSession 重查已结束或不存在，跳过: fsCallId={}", session != null ? session.getFsCallId() : "null");
                return;
            }
            session.setStatus("ENDED");
            session.setEndTime(new Date());
            session.setEndedBy(endedBy);
            session.setHangupCause(hangupCause);
            session.setDurationSec(durationSec);
            updateById(session);
        }
        log.info("[CallEvent] 已更新会话为 ENDED: fsCallId={}, sessionId={}, agentId={}, hangupCause={}",
                session.getFsCallId(), session.getId(), session.getAgentId(), hangupCause);
        removeFromQueueIfNeeded(session);

        // 坐席恢复空闲
        if (session.getAgentId() != null) {
            AgentProfile agent = agentProfileMapper.selectById(session.getAgentId());
            if (agent != null) {
                log.info("[CallEvent] 准备更新结束坐席状态: fsCallId={}, sessionId={}, agentId={}, userId={}",
                        session.getFsCallId(), session.getId(), agent.getId(), agent.getUserId());
                agentProfileService.changeStatus(agent.getUserId(), AgentStatusEnum.ONLINE, "通话结束");
                    CallWebSocket.pushCallState(agent.getUserId(), "idle");
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
}

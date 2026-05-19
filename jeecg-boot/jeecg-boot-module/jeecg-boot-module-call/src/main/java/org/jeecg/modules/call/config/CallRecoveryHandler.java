package org.jeecg.modules.call.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.call.entity.AgentProfile;
import org.jeecg.modules.call.entity.CallSession;
import org.jeecg.modules.call.enums.AgentStatusEnum;
import org.jeecg.modules.call.mapper.AgentProfileMapper;
import org.jeecg.modules.call.mapper.CallSessionMapper;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.jeecg.modules.call.service.impl.CallEndProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class CallRecoveryHandler {

    @Autowired
    private CallSessionMapper callSessionMapper;
    @Autowired
    private AgentProfileMapper agentProfileMapper;
    @Autowired
    private IAgentProfileService agentProfileService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CallEndProcessor callEndProcessor;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("[Recovery] 服务启动，开始恢复检查...");
        recoverStaleCallSessions();
        cleanStaleRedisQueues();
        recoverAgentStatus();
        cleanStaleWsConnections();
        log.info("[Recovery] 恢复检查完成");
    }

    private void recoverStaleCallSessions() {
        List<CallSession> activeSessions = callSessionMapper.selectList(
                new LambdaQueryWrapper<CallSession>()
                        .in(CallSession::getStatus, "TALKING", "RINGING", "QUEUING"));
        log.info("[Recovery] 发现 {} 个残留活跃会话", activeSessions.size());

        int recovered = 0;
        for (CallSession session : activeSessions) {
            String oldStatus = session.getStatus();
            log.info("[Recovery] 恢复会话: sessionId={}, fsCallId={}, status={}, agentId={}",
                    session.getId(), session.getFsCallId(), oldStatus, session.getAgentId());
            session.setStatus("ENDED");
            session.setEndTime(new Date());
            session.setEndedBy("SYSTEM_RECOVERY");
            session.setHangupCause("SERVICE_RESTART");
            callSessionMapper.updateById(session);
            // 触发异步摘要生成（TALKING 状态的会话可能有未生成的摘要）
            if ("TALKING".equals(oldStatus) || (session.getAnswerTime() != null && session.getSummary() == null)) {
                try {
                    callEndProcessor.processCallEnd(session);
                    log.info("[Recovery] 已触发异步摘要: sessionId={}", session.getId());
                } catch (Exception e) {
                    log.warn("[Recovery] 异步摘要触发失败: sessionId={}", session.getId(), e);
                }
            }
            recovered++;
        }
        if (recovered > 0) {
            log.warn("[Recovery] 恢复了 {} 个残留活跃会话为 ENDED", recovered);
        }
    }

    private void cleanStaleRedisQueues() {
        Set<String> queueKeys = stringRedisTemplate.keys("call:queue:*");
        if (queueKeys == null || queueKeys.isEmpty()) {
            log.info("[Recovery] 无残留 Redis 排队队列");
            return;
        }
        int cleaned = 0;
        for (String queueKey : queueKeys) {
            Set<String> sessionIds = stringRedisTemplate.opsForSet().members(queueKey);
            if (sessionIds == null) continue;
            for (String sessionId : sessionIds) {
                CallSession session = callSessionMapper.selectById(sessionId);
                if (session == null || "ENDED".equals(session.getStatus())) {
                    stringRedisTemplate.opsForSet().remove(queueKey, sessionId);
                    cleaned++;
                    log.info("[Recovery] 清理队列无效 session: queueKey={}, sessionId={}", queueKey, sessionId);
                }
            }
        }
        if (cleaned > 0) {
            log.warn("[Recovery] 清理了 {} 个 Redis 队列中的无效 session", cleaned);
        }
    }

    private void recoverAgentStatus() {
        List<AgentProfile> talkingAgents = agentProfileMapper.selectList(
                new LambdaQueryWrapper<AgentProfile>()
                        .in(AgentProfile::getStatus, "TALKING", "RINGING", "HOLDING"));
        log.info("[Recovery] 发现 {} 个异常状态坐席", talkingAgents.size());

        for (AgentProfile agent : talkingAgents) {
            log.info("[Recovery] 恢复坐席状态: agentId={}, userId={}, agentNo={}, oldStatus={}",
                    agent.getId(), agent.getUserId(), agent.getAgentNo(), agent.getStatus());
            agentProfileService.changeStatus(agent.getUserId(), AgentStatusEnum.ONLINE, "服务重启恢复");
        }
        if (!talkingAgents.isEmpty()) {
            log.warn("[Recovery] 恢复了 {} 个坐席状态为 ONLINE", talkingAgents.size());
        }
    }

    private void cleanStaleWsConnections() {
        Set<String> keys = stringRedisTemplate.keys("call:ws:conn:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
            log.info("[Recovery] 清理了 {} 个残留 WS 连接标记", keys.size());
        }
    }
}

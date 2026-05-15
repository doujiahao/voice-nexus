package org.jeecg.modules.call.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.call.entity.AgentProfile;
import org.jeecg.modules.call.entity.AgentStatusLog;
import org.jeecg.modules.call.enums.AgentStatusEnum;
import org.jeecg.modules.call.mapper.AgentProfileMapper;
import org.jeecg.modules.call.mapper.AgentStatusLogMapper;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
public class AgentProfileServiceImpl extends ServiceImpl<AgentProfileMapper, AgentProfile> implements IAgentProfileService {

    private static final String REDIS_KEY_PREFIX = "call:agent:status:";

    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private AgentStatusLogMapper agentStatusLogMapper;

    @Override
    public AgentProfile getByUserId(String userId) {
        return getOne(new LambdaQueryWrapper<AgentProfile>()
                .eq(AgentProfile::getUserId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(String userId, AgentStatusEnum newStatus, String reason) {
        log.info("[AgentStatus] 准备变更坐席状态: userId={}, targetStatus={}, reason={}", userId, newStatus.getCode(), reason);
        AgentProfile profile = getByUserId(userId);
        if (profile == null) {
            log.warn("[AgentStatus] 坐席状态变更失败，未找到坐席档案: userId={}, targetStatus={}, reason={}",
                    userId, newStatus.getCode(), reason);
            throw new IllegalArgumentException("Agent profile not found for user: " + userId);
        }

        String oldStatusCode = profile.getStatus();
        Date now = new Date();
        log.info("[AgentStatus] 命中坐席档案: userId={}, agentId={}, agentNo={}, extension={}, oldStatus={}, targetStatus={}, reason={}",
                userId, profile.getId(), profile.getAgentNo(), profile.getExtension(), oldStatusCode, newStatus.getCode(), reason);

        // 记录状态变更日志
        AgentStatusLog statusLog = new AgentStatusLog();
        statusLog.setAgentId(profile.getId());
        statusLog.setUserId(userId);
        statusLog.setFromStatus(oldStatusCode);
        statusLog.setToStatus(newStatus.getCode());
        statusLog.setChangeTime(now);
        statusLog.setReason(reason);
        if (profile.getStatusSince() != null) {
            long durationSec = (now.getTime() - profile.getStatusSince().getTime()) / 1000;
            statusLog.setDurationSec((int) durationSec);
        }
        agentStatusLogMapper.insert(statusLog);
        log.info("[AgentStatus] 已写入坐席状态日志: userId={}, agentId={}, fromStatus={}, toStatus={}, durationSec={}, reason={}",
                userId, profile.getId(), oldStatusCode, newStatus.getCode(), statusLog.getDurationSec(), reason);

        // 更新 DB
        profile.setStatus(newStatus.getCode());
        profile.setStatusSince(now);
        if (newStatus == AgentStatusEnum.ONLINE) {
            profile.setLastIdleTime(now);
        }
        updateById(profile);
        log.info("[AgentStatus] 已更新坐席状态 DB: userId={}, agentId={}, fromStatus={}, toStatus={}, lastIdleTime={}",
                userId, profile.getId(), oldStatusCode, newStatus.getCode(), profile.getLastIdleTime());

        // 同步 Redis
        redisUtil.set(REDIS_KEY_PREFIX + userId, newStatus.getCode());
        log.info("[AgentStatus] 已同步坐席状态 Redis: userId={}, redisKey={}, status={}",
                userId, REDIS_KEY_PREFIX + userId, newStatus.getCode());
    }

    @Override
    public AgentStatusEnum getCurrentStatus(String userId) {
        Object cached = redisUtil.get(REDIS_KEY_PREFIX + userId);
        if (cached != null) {
            return AgentStatusEnum.fromCode(cached.toString());
        }
        AgentProfile profile = getByUserId(userId);
        if (profile == null) {
            return AgentStatusEnum.OFFLINE;
        }
        // 回填 Redis
        redisUtil.set(REDIS_KEY_PREFIX + userId, profile.getStatus());
        return AgentStatusEnum.fromCode(profile.getStatus());
    }
}

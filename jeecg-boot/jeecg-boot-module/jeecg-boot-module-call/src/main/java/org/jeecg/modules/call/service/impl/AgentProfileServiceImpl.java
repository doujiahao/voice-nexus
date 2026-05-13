package org.jeecg.modules.call.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
        AgentProfile profile = getByUserId(userId);
        if (profile == null) {
            throw new IllegalArgumentException("Agent profile not found for user: " + userId);
        }

        String oldStatusCode = profile.getStatus();
        Date now = new Date();

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

        // 更新 DB
        profile.setStatus(newStatus.getCode());
        profile.setStatusSince(now);
        if (newStatus == AgentStatusEnum.ONLINE) {
            profile.setLastIdleTime(now);
        }
        updateById(profile);

        // 同步 Redis
        redisUtil.set(REDIS_KEY_PREFIX + userId, newStatus.getCode());
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

package org.jeecg.modules.call.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.call.dto.RouteRequestDTO;
import org.jeecg.modules.call.dto.RouteResponseDTO;
import org.jeecg.modules.call.entity.*;
import org.jeecg.modules.call.enums.AgentStatusEnum;
import org.jeecg.modules.call.mapper.*;
import org.jeecg.modules.call.service.IAgentProfileService;
import org.jeecg.modules.call.service.ICallQueueService;
import org.jeecg.modules.call.service.ICallRouteService;
import org.jeecg.modules.call.ws.CallWebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CallRouteServiceImpl implements ICallRouteService {

    private static final String LOCK_PREFIX = "call:route:lock:";

    @Autowired
    private SkillGroupMapper skillGroupMapper;
    @Autowired
    private SkillGroupAgentMapper skillGroupAgentMapper;
    @Autowired
    private AgentProfileMapper agentProfileMapper;
    @Autowired
    private CallSessionMapper callSessionMapper;
    @Autowired
    private CustomerMapper customerMapper;
    @Autowired
    private CustomerContactMapper customerContactMapper;
    @Autowired
    private IAgentProfileService agentProfileService;
    @Autowired
    private ICallQueueService callQueueService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RouteResponseDTO route(String fsCallId, RouteRequestDTO request) {
        // 1. 查找技能组
        SkillGroup skillGroup = skillGroupMapper.selectOne(
                new LambdaQueryWrapper<SkillGroup>()
                        .eq(SkillGroup::getGroupCode, request.getSkillGroup())
                        .eq(SkillGroup::getEnabled, 1));
        if (skillGroup == null) {
            return buildError("SKILL_GROUP_NOT_FOUND", "技能组不存在: " + request.getSkillGroup());
        }

        // 2. 匹配客户
        Customer customer = matchCustomer(request.getCustomerPhone());

        // 3. 创建通话会话
        CallSession session = new CallSession();
        session.setFsCallId(fsCallId);
        session.setDirection("INBOUND");
        session.setStatus("QUEUING");
        session.setCustomerPhone(request.getCustomerPhone());
        session.setCalledNumber(request.getCalledNumber());
        session.setSkillGroupId(skillGroup.getId());
        session.setQueueEnterTime(new Date());
        if (customer != null) {
            session.setCustomerId(customer.getId());
        }
        callSessionMapper.insert(session);

        // 4. 分布式锁 — 防止同一技能组并发分配同一坐席
        String lockKey = LOCK_PREFIX + skillGroup.getId();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            return buildQueueResponse(session, skillGroup);
        }
        try {
            // 5. 查找空闲坐席（LONGEST_IDLE 策略）
            AgentProfile agent = findAvailableAgent(skillGroup.getId());
            if (agent == null) {
                // 检查队列是否已满
                int queueSize = callQueueService.getQueueSize(skillGroup.getId());
                if (queueSize >= skillGroup.getQueueMaxSize()) {
                    session.setStatus("ENDED");
                    session.setHangupCause("QUEUE_FULL");
                    callSessionMapper.updateById(session);
                    return buildError("QUEUE_FULL", "技能组无可用坐席且队列已满");
                }
                return buildQueueResponse(session, skillGroup);
            }

            // 6. 分配坐席
            session.setAgentId(agent.getId());
            session.setStatus("RINGING");
            session.setRingTime(new Date());
            callSessionMapper.updateById(session);

            agentProfileService.changeStatus(agent.getUserId(), AgentStatusEnum.RINGING, "来电分配");
            CallWebSocket.pushIncomingCall(
                    agent.getUserId(),
                    session.getId(),
                    request.getCustomerPhone(),
                    customer != null ? customer.getName() : null,
                    fsCallId);

            // 7. 构建响应
            RouteResponseDTO resp = new RouteResponseDTO();
            resp.setSuccess(true);
            resp.setCallSessionId(session.getId());
            resp.setRouteAction("RING");
            resp.setTargetAgentId(agent.getId());
            resp.setTargetExtension(agent.getExtension());
            resp.setRingTimeoutSec(skillGroup.getRingTimeoutSec());
            if (customer != null) {
                RouteResponseDTO.CustomerInfo ci = new RouteResponseDTO.CustomerInfo();
                ci.setId(customer.getId());
                ci.setName(customer.getName());
                ci.setAccountNo(customer.getAccountNo());
                ci.setMeterNo(customer.getMeterNo());
                resp.setCustomer(ci);
            }
            return resp;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private AgentProfile findAvailableAgent(String skillGroupId) {
        List<SkillGroupAgent> agents = skillGroupAgentMapper.selectList(
                new LambdaQueryWrapper<SkillGroupAgent>()
                        .eq(SkillGroupAgent::getSkillGroupId, skillGroupId)
                        .orderByDesc(SkillGroupAgent::getSkillLevel));

        for (SkillGroupAgent sga : agents) {
            AgentProfile profile = agentProfileMapper.selectById(sga.getAgentId());
            if (profile == null || !AgentStatusEnum.ONLINE.getCode().equals(profile.getStatus())) {
                continue;
            }
            if (!CallWebSocket.isOnline(profile.getUserId())) {
                log.info("[Route] 坐席状态为空闲但 WS 不在线，跳过分配: agentId={}, userId={}", profile.getId(), profile.getUserId());
                continue;
            }
            return profile;
        }
        return null;
    }

    private Customer matchCustomer(String phone) {
        if (phone == null || phone.isEmpty()) return null;
        CustomerContact contact = customerContactMapper.selectOne(
                new LambdaQueryWrapper<CustomerContact>()
                        .eq(CustomerContact::getContactValue, phone)
                        .last("LIMIT 1"));
        if (contact == null) return null;
        return customerMapper.selectById(contact.getCustomerId());
    }

    private RouteResponseDTO buildQueueResponse(CallSession session, SkillGroup skillGroup) {
        callQueueService.enqueue(skillGroup.getId(), session.getId());
        int position = callQueueService.getPosition(skillGroup.getId(), session.getId());

        RouteResponseDTO resp = new RouteResponseDTO();
        resp.setSuccess(true);
        resp.setCallSessionId(session.getId());
        resp.setRouteAction("QUEUE");
        resp.setQueuePosition(position);
        resp.setEstimatedWaitSec(position * skillGroup.getQueueTimeoutSec());
        return resp;
    }

    private RouteResponseDTO buildError(String code, String message) {
        RouteResponseDTO resp = new RouteResponseDTO();
        resp.setSuccess(false);
        resp.setErrorCode(code);
        resp.setMessage(message);
        return resp;
    }
}

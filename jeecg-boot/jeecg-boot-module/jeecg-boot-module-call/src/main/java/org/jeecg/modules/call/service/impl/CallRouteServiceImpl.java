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
import java.util.Locale;
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
        log.info("[Route] 开始呼入路由: fsCallId={}, customerPhone={}, calledNumber={}, skillGroup={}",
                fsCallId, request.getCustomerPhone(), request.getCalledNumber(), request.getSkillGroup());
        String skillGroupCode = normalizeSkillGroup(request.getSkillGroup());
        SkillGroup skillGroup = skillGroupMapper.selectOne(
                new LambdaQueryWrapper<SkillGroup>()
                        .eq(SkillGroup::getEnabled, 1)
                        .and(wrapper -> wrapper
                                .eq(SkillGroup::getGroupCode, skillGroupCode)
                                .or()
                                .eq(SkillGroup::getGroupCode, skillGroupCode.toUpperCase(Locale.ROOT))
                                .or()
                                .eq(SkillGroup::getGroupCode, skillGroupCode.toLowerCase(Locale.ROOT))));
        if (skillGroup == null) {
            log.warn("[Route] 技能组不存在或未启用: fsCallId={}, skillGroup={}", fsCallId, skillGroupCode);
            return buildError("SKILL_GROUP_NOT_FOUND", "技能组不存在: " + skillGroupCode);
        }
        log.info("[Route] 命中技能组: fsCallId={}, skillGroupId={}, groupCode={}", fsCallId, skillGroup.getId(), skillGroup.getGroupCode());

        Customer customer = matchCustomer(request.getCustomerPhone());
        if (customer == null) {
            log.info("[Route] 未匹配到客户: fsCallId={}, phone={}", fsCallId, request.getCustomerPhone());
        } else {
            log.info("[Route] 匹配到客户: fsCallId={}, customerId={}, customerName={}", fsCallId, customer.getId(), customer.getName());
        }

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
        log.info("[Route] 已创建呼入会话: fsCallId={}, sessionId={}, status={}, skillGroupId={}",
                fsCallId, session.getId(), session.getStatus(), skillGroup.getId());

        String lockKey = LOCK_PREFIX + skillGroup.getId();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            log.warn("[Route] 技能组路由锁竞争失败，进入排队: fsCallId={}, sessionId={}, skillGroupId={}",
                    fsCallId, session.getId(), skillGroup.getId());
            return buildQueueResponse(session, skillGroup);
        }
        try {
            AgentProfile agent = findAvailableAgent(skillGroup.getId());
            if (agent == null) {
                int queueSize = callQueueService.getQueueSize(skillGroup.getId());
                log.warn("[Route] 无可用在线坐席: fsCallId={}, sessionId={}, skillGroupId={}, queueSize={}, queueMaxSize={}",
                        fsCallId, session.getId(), skillGroup.getId(), queueSize, skillGroup.getQueueMaxSize());
                if (queueSize >= skillGroup.getQueueMaxSize()) {
                    session.setStatus("ENDED");
                    session.setHangupCause("QUEUE_FULL");
                    callSessionMapper.updateById(session);
                    log.warn("[Route] 队列已满，结束会话: fsCallId={}, sessionId={}", fsCallId, session.getId());
                    return buildError("QUEUE_FULL", "技能组无可用坐席且队列已满");
                }
                return buildQueueResponse(session, skillGroup);
            }

            log.info("[Route] 分配坐席: fsCallId={}, sessionId={}, agentId={}, agentUserId={}, extension={}",
                    fsCallId, session.getId(), agent.getId(), agent.getUserId(), agent.getExtension());
            session.setAgentId(agent.getId());
            session.setStatus("RINGING");
            session.setRingTime(new Date());
            callSessionMapper.updateById(session);

            agentProfileService.changeStatus(agent.getUserId(), AgentStatusEnum.RINGING, "来电分配");
            log.info("[Route] 准备推送来电弹窗: fsCallId={}, sessionId={}, agentUserId={}, phone={}",
                    fsCallId, session.getId(), agent.getUserId(), request.getCustomerPhone());
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

    private String normalizeSkillGroup(String skillGroup) {
        if (skillGroup == null || skillGroup.trim().isEmpty()) {
            return "default";
        }
        return skillGroup.trim();
    }

    private AgentProfile findAvailableAgent(String skillGroupId) {
        List<SkillGroupAgent> agents = skillGroupAgentMapper.selectList(
                new LambdaQueryWrapper<SkillGroupAgent>()
                        .eq(SkillGroupAgent::getSkillGroupId, skillGroupId)
                        .orderByDesc(SkillGroupAgent::getSkillLevel));
        log.info("[Route] 查询候选坐席: skillGroupId={}, candidateCount={}", skillGroupId, agents.size());

        for (SkillGroupAgent sga : agents) {
            AgentProfile profile = agentProfileMapper.selectById(sga.getAgentId());
            if (profile == null) {
                log.warn("[Route] 技能组坐席关联无效: skillGroupId={}, skillGroupAgentId={}, agentId={}",
                        skillGroupId, sga.getId(), sga.getAgentId());
                continue;
            }
            boolean online = CallWebSocket.isOnline(profile.getUserId());
            log.info("[Route] 候选坐席: skillGroupId={}, agentId={}, userId={}, extension={}, status={}, wsOnline={}",
                    skillGroupId, profile.getId(), profile.getUserId(), profile.getExtension(), profile.getStatus(), online);
            if (!AgentStatusEnum.ONLINE.getCode().equals(profile.getStatus())) {
                continue;
            }
            if (!online) {
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
        log.info("[Route] 会话进入排队: sessionId={}, fsCallId={}, skillGroupId={}, position={}",
                session.getId(), session.getFsCallId(), skillGroup.getId(), position);

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

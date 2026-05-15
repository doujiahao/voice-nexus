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
        log.info("[Route] 开始呼入路由: fsCallId={}, customerPhone={}, calledNumber={}, rawSkillGroup={}, metadata={}",
                fsCallId, request.getCustomerPhone(), request.getCalledNumber(), request.getSkillGroup(), request.getFsMetadata());
        String skillGroupCode = normalizeSkillGroup(request.getSkillGroup());
        log.info("[Route] 技能组参数归一化: fsCallId={}, rawSkillGroup={}, normalizedSkillGroup={}",
                fsCallId, request.getSkillGroup(), skillGroupCode);
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
        log.info("[Route] 命中技能组: fsCallId={}, skillGroupId={}, groupCode={}, enabled={}, queueMaxSize={}, queueTimeoutSec={}, ringTimeoutSec={}",
                fsCallId, skillGroup.getId(), skillGroup.getGroupCode(), skillGroup.getEnabled(),
                skillGroup.getQueueMaxSize(), skillGroup.getQueueTimeoutSec(), skillGroup.getRingTimeoutSec());

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
        log.info("[Route] 尝试获取技能组路由锁: fsCallId={}, sessionId={}, skillGroupId={}, lockKey={}",
                fsCallId, session.getId(), skillGroup.getId(), lockKey);
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        log.info("[Route] 技能组路由锁结果: fsCallId={}, sessionId={}, skillGroupId={}, locked={}",
                fsCallId, session.getId(), skillGroup.getId(), locked);
        if (locked == null || !locked) {
            log.warn("[Route] 技能组路由锁竞争失败，进入排队: fsCallId={}, sessionId={}, skillGroupId={}",
                    fsCallId, session.getId(), skillGroup.getId());
            return buildQueueResponse(session, skillGroup);
        }
        try {
            log.info("[Route] 开始查找可用坐席: fsCallId={}, sessionId={}, skillGroupId={}",
                    fsCallId, session.getId(), skillGroup.getId());
            AgentProfile agent = findAvailableAgent(skillGroup.getId(), fsCallId, session.getId());
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

            log.info("[Route] 准备更新坐席状态为振铃: fsCallId={}, sessionId={}, agentId={}, agentUserId={}, oldStatus={}",
                    fsCallId, session.getId(), agent.getId(), agent.getUserId(), agent.getStatus());
            agentProfileService.changeStatus(agent.getUserId(), AgentStatusEnum.RINGING, "来电分配");
            log.info("[Route] 坐席状态已更新为振铃: fsCallId={}, sessionId={}, agentId={}, agentUserId={}",
                    fsCallId, session.getId(), agent.getId(), agent.getUserId());
            log.info("[Route] 准备推送来电弹窗: fsCallId={}, sessionId={}, agentId={}, agentUserId={}, extension={}, phone={}",
                    fsCallId, session.getId(), agent.getId(), agent.getUserId(), agent.getExtension(), request.getCustomerPhone());
            CallWebSocket.pushIncomingCall(
                    agent.getUserId(),
                    session.getId(),
                    request.getCustomerPhone(),
                    customer != null ? customer.getName() : null,
                    fsCallId);
            log.info("[Route] 来电弹窗推送调用完成: fsCallId={}, sessionId={}, agentId={}, agentUserId={}",
                    fsCallId, session.getId(), agent.getId(), agent.getUserId());

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
            log.info("[Route] 已释放技能组路由锁: fsCallId={}, sessionId={}, skillGroupId={}, lockKey={}",
                    fsCallId, session.getId(), skillGroup.getId(), lockKey);
        }
    }

    private String normalizeSkillGroup(String skillGroup) {
        if (skillGroup == null || skillGroup.trim().isEmpty()) {
            return "default";
        }
        return skillGroup.trim();
    }

    private AgentProfile findAvailableAgent(String skillGroupId, String fsCallId, String sessionId) {
        List<SkillGroupAgent> agents = skillGroupAgentMapper.selectList(
                new LambdaQueryWrapper<SkillGroupAgent>()
                        .eq(SkillGroupAgent::getSkillGroupId, skillGroupId)
                        .orderByDesc(SkillGroupAgent::getSkillLevel));
        log.info("[Route] 查询候选坐席: fsCallId={}, sessionId={}, skillGroupId={}, candidateCount={}",
                fsCallId, sessionId, skillGroupId, agents.size());

        for (SkillGroupAgent sga : agents) {
            log.info("[Route] 检查技能组坐席关系: fsCallId={}, sessionId={}, skillGroupId={}, skillGroupAgentId={}, agentId={}, skillLevel={}",
                    fsCallId, sessionId, skillGroupId, sga.getId(), sga.getAgentId(), sga.getSkillLevel());
            AgentProfile profile = agentProfileMapper.selectById(sga.getAgentId());
            if (profile == null) {
                log.warn("[Route] 技能组坐席关联无效: fsCallId={}, sessionId={}, skillGroupId={}, skillGroupAgentId={}, agentId={}",
                        fsCallId, sessionId, skillGroupId, sga.getId(), sga.getAgentId());
                continue;
            }
            log.info("[Route] 候选坐席详情: fsCallId={}, sessionId={}, skillGroupId={}, agentId={}, userId={}, agentNo={}, extension={}, status={}",
                    fsCallId, sessionId, skillGroupId, profile.getId(), profile.getUserId(), profile.getAgentNo(),
                    profile.getExtension(), profile.getStatus());
            if (!AgentStatusEnum.ONLINE.getCode().equals(profile.getStatus())) {
                log.info("[Route] 跳过坐席，状态不是 ONLINE: fsCallId={}, sessionId={}, agentId={}, userId={}, status={}",
                        fsCallId, sessionId, profile.getId(), profile.getUserId(), profile.getStatus());
                continue;
            }
            if (profile.getUserId() == null) {
                log.warn("[Route] 跳过坐席，缺少 userId: fsCallId={}, sessionId={}, agentId={}, extension={}",
                        fsCallId, sessionId, profile.getId(), profile.getExtension());
                continue;
            }
            boolean online = CallWebSocket.isOnline(profile.getUserId());
            if (!online) {
                log.info("[Route] 跳过坐席，WS 不在线: fsCallId={}, sessionId={}, agentId={}, userId={}, extension={}",
                        fsCallId, sessionId, profile.getId(), profile.getUserId(), profile.getExtension());
                continue;
            }
            log.info("[Route] 选中可用坐席: fsCallId={}, sessionId={}, agentId={}, userId={}, extension={}",
                    fsCallId, sessionId, profile.getId(), profile.getUserId(), profile.getExtension());
            return profile;
        }
        log.warn("[Route] 未找到可用坐席: fsCallId={}, sessionId={}, skillGroupId={}, candidateCount={}",
                fsCallId, sessionId, skillGroupId, agents.size());
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

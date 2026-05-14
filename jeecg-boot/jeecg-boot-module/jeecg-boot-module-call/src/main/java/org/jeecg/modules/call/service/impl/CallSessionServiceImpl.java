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
import org.jeecg.modules.call.service.ICallSessionService;
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
    private CallEndProcessor callEndProcessor;

    @Override
    public CallSession getByFsCallId(String fsCallId) {
        return getOne(new LambdaQueryWrapper<CallSession>()
                .eq(CallSession::getFsCallId, fsCallId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handleEvent(String fsCallId, CallEventDTO event) {
        CallSession session = getByFsCallId(fsCallId);
        Map<String, Object> result = new HashMap<>();

        if (session == null) {
            result.put("acknowledged", false);
            result.put("error", "Session not found for fs_call_id: " + fsCallId);
            return result;
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

        // 处理不同事件类型
        switch (event.getEventType()) {
            case "CALL_ENDED":
                endSession(session, event.getEndedBy(),
                        event.getMetadata() != null ? event.getMetadata().get("hangup_cause") : "NORMAL",
                        event.getDurationSec());
                result.put("status", "ENDING");
                break;
            case "ANSWERED":
                session.setStatus("TALKING");
                session.setAnswerTime(new Date());
                updateById(session);
                if (session.getAgentId() != null) {
                    AgentProfile agent = agentProfileMapper.selectById(session.getAgentId());
                    if (agent != null) {
                        agentProfileService.changeStatus(agent.getUserId(), AgentStatusEnum.TALKING, "通话接通");
                    }
                }
                result.put("status", "TALKING");
                break;
            default:
                result.put("status", session.getStatus());
                break;
        }

        result.put("acknowledged", true);
        result.put("call_session_id", session.getId());
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
        session.setStatus("ENDED");
        session.setEndTime(new Date());
        session.setEndedBy(endedBy);
        session.setHangupCause(hangupCause);
        session.setDurationSec(durationSec);
        updateById(session);

        // 坐席恢复空闲
        if (session.getAgentId() != null) {
            AgentProfile agent = agentProfileMapper.selectById(session.getAgentId());
            if (agent != null) {
                agentProfileService.changeStatus(agent.getUserId(), AgentStatusEnum.WRAP_UP, "通话结束");
            }
        }

        callEndProcessor.processCallEnd(session.getId());
    }
}

package org.jeecg.modules.call.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.call.dto.CallEventDTO;
import org.jeecg.modules.call.entity.CallSession;

import java.util.Map;

public interface ICallSessionService extends IService<CallSession> {

    CallSession getByFsCallId(String fsCallId);

    Map<String, Object> handleEvent(String fsCallId, CallEventDTO event);

    void updateStatus(String sessionId, String newStatus);

    void endSession(CallSession session, String endedBy, String hangupCause, Integer durationSec);
}

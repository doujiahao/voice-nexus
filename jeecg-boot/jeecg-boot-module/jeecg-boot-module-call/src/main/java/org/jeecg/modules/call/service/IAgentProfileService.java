package org.jeecg.modules.call.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.call.entity.AgentProfile;
import org.jeecg.modules.call.enums.AgentStatusEnum;

public interface IAgentProfileService extends IService<AgentProfile> {

    AgentProfile getByUserId(String userId);

    void changeStatus(String userId, AgentStatusEnum newStatus, String reason);

    AgentStatusEnum getCurrentStatus(String userId);
}

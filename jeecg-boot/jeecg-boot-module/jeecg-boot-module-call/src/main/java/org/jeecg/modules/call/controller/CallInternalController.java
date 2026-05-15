package org.jeecg.modules.call.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.call.dto.CallEventDTO;
import org.jeecg.modules.call.dto.RouteRequestDTO;
import org.jeecg.modules.call.dto.RouteResponseDTO;
import org.jeecg.modules.call.service.ICallRouteService;
import org.jeecg.modules.call.service.ICallSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Tag(name = "内部接口 - freeswichService")
@RestController
@RequestMapping("/api/v1/internal")
public class CallInternalController {

    @Autowired
    private ICallRouteService callRouteService;
    @Autowired
    private ICallSessionService callSessionService;

    @IgnoreAuth
    @Operation(summary = "呼入路由申请")
    @PostMapping("/calls/{fsCallId}/route")
    public RouteResponseDTO route(@PathVariable String fsCallId,
                                  @RequestBody RouteRequestDTO request) {
        log.info("[Inbound] 收到路由申请: fsCallId={}, customerPhone={}, calledNumber={}, skillGroup={}, metadata={}",
                fsCallId, request.getCustomerPhone(), request.getCalledNumber(), request.getSkillGroup(), request.getFsMetadata());
        RouteResponseDTO response = callRouteService.route(fsCallId, request);
        log.info("[Inbound] 路由响应: fsCallId={}, success={}, action={}, sessionId={}, targetAgentId={}, targetExtension={}, errorCode={}, message={}",
                fsCallId, response.isSuccess(), response.getRouteAction(), response.getCallSessionId(),
                response.getTargetAgentId(), response.getTargetExtension(), response.getErrorCode(), response.getMessage());
        return response;
    }

    @IgnoreAuth
    @Operation(summary = "通话事件上报")
    @PostMapping("/calls/{fsCallId}/events")
    public Map<String, Object> reportEvent(@PathVariable String fsCallId,
                                           @RequestBody CallEventDTO event) {
        log.info("[Inbound] 收到通话事件: fsCallId={}, type={}, endedBy={}, durationSec={}, metadata={}",
                fsCallId, event.getEventType(), event.getEndedBy(), event.getDurationSec(), event.getMetadata());
        Map<String, Object> response = callSessionService.handleEvent(fsCallId, event);
        log.info("[Inbound] 通话事件响应: fsCallId={}, response={}", fsCallId, response);
        return response;
    }
}

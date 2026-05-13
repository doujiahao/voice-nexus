package org.jeecg.modules.call.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/call/internal")
public class CallInternalController {

    @Autowired
    private ICallRouteService callRouteService;
    @Autowired
    private ICallSessionService callSessionService;

    @Operation(summary = "呼入路由申请")
    @PostMapping("/calls/{fsCallId}/route")
    public RouteResponseDTO route(@PathVariable String fsCallId,
                                  @RequestBody RouteRequestDTO request) {
        log.info("呼入路由申请: fsCallId={}, phone={}", fsCallId, request.getCustomerPhone());
        return callRouteService.route(fsCallId, request);
    }

    @Operation(summary = "通话事件上报")
    @PostMapping("/calls/{fsCallId}/events")
    public Map<String, Object> reportEvent(@PathVariable String fsCallId,
                                           @RequestBody CallEventDTO event) {
        log.info("通话事件上报: fsCallId={}, type={}", fsCallId, event.getEventType());
        return callSessionService.handleEvent(fsCallId, event);
    }
}

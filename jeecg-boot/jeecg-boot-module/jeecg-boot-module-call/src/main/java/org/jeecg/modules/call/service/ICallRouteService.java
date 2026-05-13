package org.jeecg.modules.call.service;

import org.jeecg.modules.call.dto.RouteRequestDTO;
import org.jeecg.modules.call.dto.RouteResponseDTO;

public interface ICallRouteService {

    RouteResponseDTO route(String fsCallId, RouteRequestDTO request);
}

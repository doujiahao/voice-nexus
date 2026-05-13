package org.jeecg.modules.call.dto;

import lombok.Data;

import java.util.Map;

@Data
public class RouteRequestDTO {
    private String customerPhone;
    private String calledNumber;
    private String skillGroup;
    private Map<String, String> fsMetadata;
}

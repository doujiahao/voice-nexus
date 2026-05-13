package org.jeecg.modules.call.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CallEventDTO {
    private String eventType;
    private String endedBy;
    private Integer durationSec;
    private String timestamp;
    private Map<String, String> metadata;
}

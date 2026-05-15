package org.jeecg.modules.call.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.Map;

@Data
public class CallEventDTO {
    @JsonAlias("event_type")
    private String eventType;
    @JsonAlias("ended_by")
    private String endedBy;
    @JsonAlias("duration_sec")
    private Integer durationSec;
    private String timestamp;
    private Map<String, String> metadata;
}

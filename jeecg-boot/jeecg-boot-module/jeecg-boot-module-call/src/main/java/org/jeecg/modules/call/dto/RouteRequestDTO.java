package org.jeecg.modules.call.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.Map;

@Data
public class RouteRequestDTO {
    @JsonAlias("customer_phone")
    private String customerPhone;
    @JsonAlias("called_number")
    private String calledNumber;
    @JsonAlias("skill_group")
    private String skillGroup;
    @JsonAlias("fs_metadata")
    private Map<String, String> fsMetadata;
}

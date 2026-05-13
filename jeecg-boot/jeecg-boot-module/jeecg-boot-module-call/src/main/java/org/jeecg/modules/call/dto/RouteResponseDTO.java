package org.jeecg.modules.call.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RouteResponseDTO {
    private boolean success;
    private String callSessionId;
    private String routeAction;

    // RING 场景
    private String targetAgentId;
    private String targetExtension;
    private Integer ringTimeoutSec;
    private CustomerInfo customer;

    // QUEUE 场景
    private Integer queuePosition;
    private Integer estimatedWaitSec;

    // 失败场景
    private String errorCode;
    private String message;

    @Data
    public static class CustomerInfo {
        private String id;
        private String name;
        private String accountNo;
        private String meterNo;
    }
}

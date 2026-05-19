package org.jeecg.modules.call.dto;

import lombok.Data;

import java.util.Date;

@Data
public class CallListVO {
    private String id;
    private String fsCallId;
    private String direction;
    private String status;
    private String customerPhone;
    private String customerId;
    private String agentId;
    private String skillGroupId;
    private Date createTime;
    private Date endTime;
    private Integer durationSec;
    private String endedBy;
    private String summary;
    private String remark;
    private String customerName;
    private String agentName;
    private Long turnCount;
}

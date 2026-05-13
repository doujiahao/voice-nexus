package org.jeecg.modules.call.enums;

public enum AgentStatusEnum {

    OFFLINE("OFFLINE", "离线"),
    ONLINE("ONLINE", "在线空闲"),
    REST("REST", "小休"),
    RINGING("RINGING", "振铃中"),
    TALKING("TALKING", "通话中"),
    HOLDING("HOLDING", "保持中"),
    WRAP_UP("WRAP_UP", "话后整理");

    private final String code;
    private final String desc;

    AgentStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static AgentStatusEnum fromCode(String code) {
        for (AgentStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown agent status: " + code);
    }
}

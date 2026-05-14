package org.jeecg.modules.call.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentStatusEnumTest {

    @Test
    void fromCode_shouldReturnCorrectEnum() {
        assertEquals(AgentStatusEnum.ONLINE, AgentStatusEnum.fromCode("ONLINE"));
        assertEquals(AgentStatusEnum.OFFLINE, AgentStatusEnum.fromCode("OFFLINE"));
        assertEquals(AgentStatusEnum.TALKING, AgentStatusEnum.fromCode("TALKING"));
        assertEquals(AgentStatusEnum.RINGING, AgentStatusEnum.fromCode("RINGING"));
        assertEquals(AgentStatusEnum.REST, AgentStatusEnum.fromCode("REST"));
        assertEquals(AgentStatusEnum.HOLDING, AgentStatusEnum.fromCode("HOLDING"));
        assertEquals(AgentStatusEnum.WRAP_UP, AgentStatusEnum.fromCode("WRAP_UP"));
    }

    @Test
    void fromCode_shouldThrow_whenUnknown() {
        assertThrows(IllegalArgumentException.class, () -> AgentStatusEnum.fromCode("INVALID"));
    }

    @Test
    void getCode_shouldReturnCodeString() {
        assertEquals("ONLINE", AgentStatusEnum.ONLINE.getCode());
        assertEquals("WRAP_UP", AgentStatusEnum.WRAP_UP.getCode());
    }

    @Test
    void getDesc_shouldReturnDescription() {
        assertEquals("在线空闲", AgentStatusEnum.ONLINE.getDesc());
        assertEquals("通话中", AgentStatusEnum.TALKING.getDesc());
    }
}

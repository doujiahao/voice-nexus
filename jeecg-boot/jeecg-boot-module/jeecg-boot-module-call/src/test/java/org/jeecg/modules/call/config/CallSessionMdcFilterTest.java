package org.jeecg.modules.call.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class CallSessionMdcFilterTest {

    private final CallSessionMdcFilter filter = new CallSessionMdcFilter();

    @Test
    void extractsSessionIdFromPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/calls/sess-123/turns");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertEquals("sess-123", MDC.get("callSessionId"));
        });

        assertNull(MDC.get("callSessionId"));
    }

    @Test
    void extractsSessionIdFromHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agent/status");
        request.addHeader("X-Call-Session-Id", "header-sess-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertEquals("header-sess-456", MDC.get("callSessionId"));
        });
    }

    @Test
    void noSessionId_mdcEmpty() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/agent/info");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertNull(MDC.get("callSessionId"));
        });
    }

    @Test
    void mdcClearedAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/calls/sess-789/detail");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertNull(MDC.get("callSessionId"));
    }
}

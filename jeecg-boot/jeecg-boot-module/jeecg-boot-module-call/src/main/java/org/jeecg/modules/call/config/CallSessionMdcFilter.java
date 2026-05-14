package org.jeecg.modules.call.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CallSessionMdcFilter implements Filter {

    private static final String MDC_KEY = "callSessionId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest httpReq) {
                String sessionId = extractSessionId(httpReq);
                if (sessionId != null) {
                    MDC.put(MDC_KEY, sessionId);
                }
            }
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String extractSessionId(HttpServletRequest request) {
        String path = request.getRequestURI();
        // /api/v1/calls/{callSessionId}/... 或 /api/v1/internal/calls/{fsCallId}/...
        if (path.contains("/calls/")) {
            String[] parts = path.split("/calls/");
            if (parts.length > 1) {
                String remainder = parts[1];
                int slash = remainder.indexOf('/');
                return slash > 0 ? remainder.substring(0, slash) : remainder;
            }
        }
        // Header 方式（freeswichService 调用时可能携带）
        String header = request.getHeader("X-Call-Session-Id");
        if (header != null && !header.isEmpty()) {
            return header;
        }
        return null;
    }
}

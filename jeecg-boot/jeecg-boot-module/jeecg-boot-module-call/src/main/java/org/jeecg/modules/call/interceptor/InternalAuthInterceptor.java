package org.jeecg.modules.call.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jeecg.modules.call.config.CallProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
public class InternalAuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_INTERNAL_KEY = "X-Internal-Key";

    private final CallProperties callProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String key = request.getHeader(HEADER_INTERNAL_KEY);
        if (callProperties.getInternal().getSecretKey().equals(key)) {
            return true;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"Invalid internal key\"}");
        return false;
    }
}

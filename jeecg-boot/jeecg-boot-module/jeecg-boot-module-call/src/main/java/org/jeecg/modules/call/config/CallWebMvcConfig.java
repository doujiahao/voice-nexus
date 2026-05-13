package org.jeecg.modules.call.config;

import org.jeecg.modules.call.interceptor.InternalAuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;

@Configuration
public class CallWebMvcConfig implements WebMvcConfigurer {

    @Resource
    private CallProperties callProperties;

    @Bean
    public InternalAuthInterceptor internalAuthInterceptor() {
        return new InternalAuthInterceptor(callProperties);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalAuthInterceptor())
                .addPathPatterns("/call/internal/**");
    }
}

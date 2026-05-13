package org.jeecg.modules.call.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jeecg.call")
public class CallProperties {

    private InternalConfig internal = new InternalConfig();
    private GatewayConfig gateway = new GatewayConfig();

    @Data
    public static class InternalConfig {
        private String secretKey = "changeme-internal-key";
    }

    @Data
    public static class GatewayConfig {
        private String baseUrl = "http://192.168.1.21:30001";
    }
}

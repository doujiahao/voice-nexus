package org.jeecg.modules.call.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jeecg.call")
public class CallProperties {

    private InternalConfig internal = new InternalConfig();

    @Data
    public static class InternalConfig {
        private String secretKey = "changeme-internal-key";
    }
}

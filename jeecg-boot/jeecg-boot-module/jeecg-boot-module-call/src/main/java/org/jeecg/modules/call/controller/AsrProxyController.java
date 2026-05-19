package org.jeecg.modules.call.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.call.config.CallProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Map;

@Slf4j
@Tag(name = "AI 代理转发")
@RestController
@RequestMapping("/api/v1/asr")
public class AsrProxyController {

    @Autowired
    private CallProperties callProperties;

    private final RestTemplate restTemplate = new RestTemplate();

    @Operation(summary = "ASR 转写（代理转发到 Gateway）")
    @PostMapping("/transcribe")
    public ResponseEntity<String> transcribe(HttpServletRequest request) {
        String gatewayUrl = callProperties.getGateway().getBaseUrl() + "/api/v1/asr/transcribe";
        log.info("[AsrProxy] 收到 ASR 转写代理请求: gatewayUrl={}", gatewayUrl);

        MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // 转发文件
        MultipartFile file = multipartRequest.getFile("file");
        if (file != null) {
            body.add("file", file.getResource());
            log.info("[AsrProxy] 转发文件: fileName={}, size={}", file.getOriginalFilename(), file.getSize());
        } else {
            log.warn("[AsrProxy] ASR 转写请求无文件");
        }

        // 转发其他表单字段
        Map<String, String[]> params = multipartRequest.getParameterMap();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            if (entry.getValue().length > 0) {
                body.add(entry.getKey(), entry.getValue()[0]);
            }
        }
        log.info("[AsrProxy] 转发参数: params={}", params.keySet());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    gatewayUrl, HttpMethod.POST, entity, String.class);
            log.info("[AsrProxy] Gateway 响应: statusCode={}", response.getStatusCode());
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("[AsrProxy] ASR 代理转发失败: gatewayUrl={}", gatewayUrl, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"code\":-1,\"message\":\"Gateway unreachable: " + e.getMessage() + "\"}");
        }
    }

    @Operation(summary = "坐席实时辅助（代理转发到 Gateway）")
    @PostMapping("/agent-assist/analyze")
    public ResponseEntity<String> agentAssist(@RequestBody String body) {
        String gatewayUrl = callProperties.getGateway().getBaseUrl() + "/api/v1/nlp/agent-assist/analyze";
        log.info("[AsrProxy] 收到坐席辅助代理请求: gatewayUrl={}, bodyLength={}", gatewayUrl, body.length());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    gatewayUrl, HttpMethod.POST, entity, String.class);
            log.info("[AsrProxy] 坐席辅助 Gateway 响应: statusCode={}", response.getStatusCode());
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (Exception e) {
            log.error("[AsrProxy] AgentAssist 代理转发失败: gatewayUrl={}", gatewayUrl, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"code\":-1,\"message\":\"Gateway unreachable: " + e.getMessage() + "\"}");
        }
    }
}

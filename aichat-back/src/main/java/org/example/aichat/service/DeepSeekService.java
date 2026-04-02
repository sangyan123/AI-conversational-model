package org.example.aichat.service;

/**
 * @author 吕鹏宇
 * @since 2026/1/3
 */

import org.example.aichat.dto.AiChatRequest;
import org.example.aichat.dto.AiChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DeepSeekService {

    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.model}")
    private String model;

    private final RestTemplate restTemplate;

    public DeepSeekService() {
        this.restTemplate = new RestTemplate();
    }

    public String chatWithAI(String userMessage) {
        try {
            // 检查API密钥是否配置
            if (apiKey == null || apiKey.equals("your-api-key-here")) {
                return "API密钥未配置，请在环境变量中设置DEEPSEEK_API_KEY。";
            }
            
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // 构建消息列表
            List<AiChatRequest.Message> messages = new ArrayList<>();
            messages.add(new AiChatRequest.Message("system", "You are a helpful assistant."));
            messages.add(new AiChatRequest.Message("user", userMessage));

            // 构建请求体
            AiChatRequest request = new AiChatRequest();
            request.setModel(model);
            request.setMessages(messages);
            request.setTemperature(0.7);
            request.setMax_tokens(2000);

            // 发送请求
            HttpEntity<AiChatRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<AiChatResponse> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    AiChatResponse.class
            );

            if (response.getStatusCode() == HttpStatus.OK &&
                    response.getBody() != null &&
                    !response.getBody().getChoices().isEmpty()) {
                return response.getBody().getChoices().get(0).getMessage().getContent();
            }

            return "抱歉，暂时无法处理您的请求。";

        } catch (Exception e) {
            log.error("调用DeepSeek API失败", e);
            return "服务暂时不可用，请稍后再试。";
        }
    }
}

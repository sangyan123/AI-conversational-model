package org.example.aichat.service;

/**
 * @author 吕鹏宇
 * @since 2026/1/3
 */

import org.example.aichat.dto.AiChatRequest;
import org.example.aichat.dto.AiChatResponse;
import org.example.aichat.dto.ChatRequest;
import org.example.aichat.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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

    @Value("${deepseek.api.temperature:0.7}")
    private double temperature;

    @Value("${deepseek.api.max-tokens:2000}")
    private int maxTokens;

    @Value("${deepseek.api.system-prompt:You are a helpful assistant.}")
    private String systemPrompt;

    private final RestTemplate restTemplate;

    public DeepSeekService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String chatWithAI(String userMessage) {
        return chatWithAI(userMessage, List.of());
    }

    /**
     * 带历史上下文的多轮对话。
     *
     * @param userMessage 本轮用户输入
     * @param history     历史消息（role 为 user/assistant），可为空
     * @return AI 回复文本
     * @throws AiServiceException 上游不可用、鉴权失败或返回异常时
     */
    public String chatWithAI(String userMessage, List<ChatRequest.HistoryMessage> history) {
        // 检查API密钥是否配置
        if (apiKey == null || apiKey.isBlank() || apiKey.equals("your-api-key-here")) {
            throw new AiServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "API 密钥未配置，请设置环境变量 DEEPSEEK_API_KEY 后重启服务。"
            );
        }

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        // 构建消息列表：system + 历史 + 本轮用户输入
        List<AiChatRequest.Message> messages = new ArrayList<>();
        messages.add(new AiChatRequest.Message("system", systemPrompt));

        if (history != null) {
            for (ChatRequest.HistoryMessage item : history) {
                String role = normalizeRole(item.getRole());
                if (role == null || item.getContent() == null || item.getContent().isBlank()) {
                    continue;
                }
                messages.add(new AiChatRequest.Message(role, item.getContent()));
            }
        }

        messages.add(new AiChatRequest.Message("user", userMessage));

        // 构建请求体
        AiChatRequest request = new AiChatRequest();
        request.setModel(model);
        request.setMessages(messages);
        request.setTemperature(temperature);
        request.setMax_tokens(maxTokens);

        // 发送请求
        HttpEntity<AiChatRequest> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<AiChatResponse> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    AiChatResponse.class
            );

            AiChatResponse body = response.getBody();
            if (response.getStatusCode() == HttpStatus.OK
                    && body != null
                    && body.getChoices() != null
                    && !body.getChoices().isEmpty()
                    && body.getChoices().get(0).getMessage() != null
                    && body.getChoices().get(0).getMessage().getContent() != null) {
                return body.getChoices().get(0).getMessage().getContent();
            }

            log.error("DeepSeek 返回结构异常: {}", body);
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI 服务返回了空结果，请稍后重试。");

        } catch (ResourceAccessException e) {
            // 连接超时 / 读取超时 / DNS 失败
            log.error("调用 DeepSeek 超时或网络不可达", e);
            throw new AiServiceException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "AI 服务响应超时，请稍后重试。",
                    e
            );

        } catch (HttpClientErrorException e) {
            // 4xx：密钥错误、参数错误、限流等
            HttpStatusCode statusCode = e.getStatusCode();
            log.error("调用 DeepSeek 客户端错误: {} - {}", statusCode.value(), e.getResponseBodyAsString());

            if (statusCode.value() == 401 || statusCode.value() == 403) {
                throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI 服务鉴权失败，请检查 API 密钥。", e);
            }
            if (statusCode.value() == 429) {
                throw new AiServiceException(HttpStatus.TOO_MANY_REQUESTS, "AI 服务限流，请稍后重试。", e);
            }
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI 服务请求被拒绝。", e);

        } catch (HttpServerErrorException e) {
            log.error("调用 DeepSeek 服务端错误: {}", e.getStatusCode().value(), e);
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI 服务暂时不可用，请稍后重试。", e);

        } catch (AiServiceException e) {
            throw e;

        } catch (Exception e) {
            log.error("调用 DeepSeek API 失败", e);
            throw new AiServiceException(HttpStatus.BAD_GATEWAY, "AI 服务调用失败，请稍后重试。", e);
        }
    }

    /**
     * 把外部传入的角色统一成 DeepSeek 认识的角色，未知角色返回 null 以跳过。
     */
    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        return switch (role.trim().toLowerCase()) {
            case "user" -> "user";
            case "assistant", "ai" -> "assistant";
            case "system" -> "system";
            default -> null;
        };
    }
}

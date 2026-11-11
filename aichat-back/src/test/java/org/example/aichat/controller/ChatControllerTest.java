package org.example.aichat.controller;

import org.example.aichat.exception.AiServiceException;
import org.example.aichat.service.DeepSeekService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ChatController 接口层测试：校验参数检查、状态码与错误体结构。
 */
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeepSeekService deepSeekService;

    @Test
    @DisplayName("健康检查返回 200")
    void health() throws Exception {
        mockMvc.perform(get("/api/chat/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("正常请求：返回 message 与 timestamp")
    void chatOk() throws Exception {
        when(deepSeekService.chatWithAI(anyString(), any())).thenReturn("AI 回复");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("AI 回复"))
                .andExpect(jsonPath("$.timestamp").isNumber());
    }

    @Test
    @DisplayName("空消息：返回 400 而不是 500")
    void blankMessageReturns400() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("缺少 message 字段：返回 400")
    void missingMessageReturns400() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("history 超过 50 条：返回 400")
    void historyTooLongReturns400() throws Exception {
        StringBuilder sb = new StringBuilder("{\"message\":\"x\",\"history\":[");
        for (int i = 0; i < 51; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"role\":\"user\",\"content\":\"m").append(i).append("\"}");
        }
        sb.append("]}");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sb.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("history 正常传入：透传给 service")
    void historyForwarded() throws Exception {
        when(deepSeekService.chatWithAI(eq("新问题"), any())).thenReturn("ok");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"新问题","history":[
                                  {"role":"user","content":"老问题"},
                                  {"role":"assistant","content":"老回答"}
                                ]}
                                """))
                .andExpect(status().isOk());

        verify(deepSeekService).chatWithAI(eq("新问题"), any());
    }

    @Test
    @DisplayName("上游超时：异常映射为 504 且带结构化错误体")
    void upstreamTimeoutMapsTo504() throws Exception {
        when(deepSeekService.chatWithAI(anyString(), any()))
                .thenThrow(new AiServiceException(HttpStatus.GATEWAY_TIMEOUT, "AI 服务响应超时，请稍后重试。"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.message").value("AI 服务响应超时，请稍后重试。"));
    }

    @Test
    @DisplayName("未配置密钥：503")
    void missingKeyMapsTo503() throws Exception {
        when(deepSeekService.chatWithAI(anyString(), any()))
                .thenThrow(new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "API 密钥未配置。"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    @DisplayName("限流：429")
    void rateLimitMapsTo429() throws Exception {
        when(deepSeekService.chatWithAI(anyString(), any()))
                .thenThrow(new AiServiceException(HttpStatus.TOO_MANY_REQUESTS, "限流"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isTooManyRequests());
    }
}

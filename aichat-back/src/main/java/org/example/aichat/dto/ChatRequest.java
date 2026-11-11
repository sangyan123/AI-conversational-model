package org.example.aichat.dto;

/**
 * @author 吕鹏宇
 * @since 2026/1/3
 */

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {
    @NotBlank(message = "消息不能为空")
    private String message;

    /**
     * 可选的历史对话，用于多轮上下文。
     * 不传或为空时，退化为单轮问答。
     */
    @Size(max = 50, message = "历史消息最多 50 条")
    private List<HistoryMessage> history;

    @Data
    public static class HistoryMessage {
        /** "user" 或 "assistant" */
        @NotBlank(message = "历史消息角色不能为空")
        private String role;

        @NotBlank(message = "历史消息内容不能为空")
        private String content;
    }
}

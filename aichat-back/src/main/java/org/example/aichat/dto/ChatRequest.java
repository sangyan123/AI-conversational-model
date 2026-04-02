package org.example.aichat.dto;

/**
 * @author 吕鹏宇
 * @since 2026/1/3
 */

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    @NotBlank(message = "消息不能为空")
    private String message;
}

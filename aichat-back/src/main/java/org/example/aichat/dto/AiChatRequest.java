package org.example.aichat.dto;

/**
 * @author 吕鹏宇
 * @since 2026/1/3
 */
import lombok.Data;
import java.util.List;

@Data
public class AiChatRequest {
    private String model;
    private List<Message> messages;
    private Double temperature = 0.7;
    private Integer max_tokens = 2000;

    @Data
    public static class Message {
        private String role;
        private String content;

        public Message() {}

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
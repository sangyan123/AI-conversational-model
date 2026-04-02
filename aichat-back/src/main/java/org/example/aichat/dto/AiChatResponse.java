package org.example.aichat.dto;

/**
 * @author 吕鹏宇
 * @since 2026/1/3
 */

import lombok.Data;
import java.util.List;

@Data
public class AiChatResponse {
    private String id;
    private List<Choice> choices;

    @Data
    public static class Choice {
        private Message message;
        private Integer index;

        @Data
        public static class Message {
            private String role;
            private String content;
        }
    }
}

package org.example.aichat;

/**
 * @author 吕鹏宇
 * @since 2026/1/3
 */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiChatApplication.class, args);
        System.out.println("AI Chat Backend is running on http://localhost:8080");
    }
}

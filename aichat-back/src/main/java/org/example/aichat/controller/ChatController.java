package org.example.aichat.controller;

/**
 * @author 吕鹏宇
 * @since 2026/1/3
 */

import org.example.aichat.dto.ChatRequest;
import org.example.aichat.dto.ChatResponse;
import org.example.aichat.service.DeepSeekService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final DeepSeekService deepSeekService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String aiResponse = deepSeekService.chatWithAI(request.getMessage(), request.getHistory());

        ChatResponse response = new ChatResponse();
        response.setMessage(aiResponse);
        response.setTimestamp(System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("AI Chat Service is running");
    }
}

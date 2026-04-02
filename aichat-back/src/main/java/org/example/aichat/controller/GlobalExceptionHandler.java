package org.example.aichat.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> handleResourceAccessException(ResourceAccessException e) {
        log.error("API 调用失败：{}", e.getMessage());

        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", "服务暂时不可用，请检查网络连接或稍后再试");
        error.put("error", "Connection Timeout");
        error.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("服务器错误：{}", e.getMessage(), e);

        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", "服务器内部错误");
        error.put("error", "Internal Server Error");
        error.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

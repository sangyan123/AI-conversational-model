package org.example.aichat.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.aichat.exception.AiServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 上游 AI 服务相关错误，使用异常自带的状态码，让前端能区分"超时/限流/鉴权失败"。
     */
    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<Map<String, Object>> handleAiServiceException(AiServiceException e) {
        log.error("AI 服务调用失败：{}", e.getMessage());

        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", e.getMessage());
        error.put("error", e.getStatus().getReasonPhrase());
        error.put("status", e.getStatus().value());
        error.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(e.getStatus()).body(error);
    }

    /**
     * 参数校验失败，返回 400 而不是 500。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("请求参数校验失败：{}", detail);

        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", detail);
        error.put("error", "Bad Request");
        error.put("status", HttpStatus.BAD_REQUEST.value());
        error.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> handleResourceAccessException(ResourceAccessException e) {
        log.error("API 调用失败：{}", e.getMessage());

        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", "服务暂时不可用，请检查网络连接或稍后再试");
        error.put("error", "Connection Timeout");
        error.put("status", HttpStatus.GATEWAY_TIMEOUT.value());
        error.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("服务器错误：{}", e.getMessage(), e);

        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", "服务器内部错误");
        error.put("error", "Internal Server Error");
        error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

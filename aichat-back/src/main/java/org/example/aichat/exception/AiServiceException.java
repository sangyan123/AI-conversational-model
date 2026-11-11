package org.example.aichat.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 调用上游 AI 服务失败时抛出，携带期望返回给客户端的 HTTP 状态。
 *
 * @author 吕鹏宇
 * @since 2026/1/3
 */
@Getter
public class AiServiceException extends RuntimeException {

    private final HttpStatus status;

    public AiServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public AiServiceException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}

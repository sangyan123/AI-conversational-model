package org.example.aichat.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 可选的共享令牌鉴权。
 *
 * <p>未配置 {@code auth.token} 时整体跳过，保持本地开发开箱即用；
 * 配置后要求请求头携带 {@code Authorization: Bearer <token>}。
 *
 * <p>注意这里只解决「谁能调用我的后端」，不是用户体系——
 * 真正的多用户鉴权应交由 Spring Security + 持久化用户表。
 *
 * @author 吕鹏宇
 * @since 2026/1/3
 */
@Component
@Slf4j
public class AuthFilter extends OncePerRequestFilter {

    @Value("${auth.token:}")
    private String expectedToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 未配置令牌 = 不启用鉴权
        if (expectedToken == null || expectedToken.isBlank()) {
            return true;
        }
        // 健康检查始终放行，便于探活
        return "/api/chat/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String provided = header != null && header.startsWith("Bearer ")
                ? header.substring("Bearer ".length()).trim()
                : null;

        if (provided == null || !constantTimeEquals(provided, expectedToken)) {
            log.warn("鉴权失败: uri={} 是否携带令牌={}", request.getRequestURI(), provided != null);

            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"success":false,"message":"未授权：请提供有效的访问令牌。","error":"Unauthorized","status":401}
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** 定长比较，避免通过响应时间侧信道猜出令牌。 */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }
}

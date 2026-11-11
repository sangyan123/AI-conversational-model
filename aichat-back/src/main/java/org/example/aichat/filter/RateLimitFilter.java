package org.example.aichat.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单的按 IP 限流，保护 DeepSeek 额度不被单客户端耗尽。
 *
 * <p>采用固定窗口计数，够用且无需引入额外依赖（如 Bucket4j / Redis）。
 * 多实例部署时这是「每实例限流」，不是全局限流——若需要全局配额，
 * 应换成 Redis 计数器。
 *
 * <p>通过 {@code ratelimit.enabled=false} 可整体关闭。
 *
 * @author 吕鹏宇
 * @since 2026/1/3
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${ratelimit.enabled:true}")
    private boolean enabled;

    /** 每个窗口内允许的请求数 */
    @Value("${ratelimit.requests-per-window:20}")
    private int requestsPerWindow;

    /** 窗口长度（毫秒） */
    @Value("${ratelimit.window-ms:60000}")
    private long windowMs;

    private record Window(long startMs, AtomicInteger count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 只限制真正消耗上游额度的聊天接口；健康检查不限制
        return !enabled || !"/api/chat".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String key = clientKey(request);
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startMs() >= windowMs) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        int used = window.count().get();
        if (used > requestsPerWindow) {
            long retryAfterSec = Math.max(1, (windowMs - (now - window.startMs())) / 1000);
            log.warn("触发限流: key={} 第 {} 次请求，上限 {}/{}ms", key, used, requestsPerWindow, windowMs);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(retryAfterSec));
            response.getWriter().write("""
                    {"success":false,"message":"请求过于频繁，请稍后再试。","error":"Too Many Requests","status":429}
                    """);
            return;
        }

        // 顺手清理过期窗口，避免长期运行内存增长
        if (windows.size() > 1024) {
            windows.entrySet().removeIf(e -> now - e.getValue().startMs() >= windowMs);
        }

        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // 取最左侧的原始客户端地址
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

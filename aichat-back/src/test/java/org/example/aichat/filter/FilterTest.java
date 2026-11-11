package org.example.aichat.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 限流与鉴权过滤器单元测试（不启动 Spring 容器，直接驱动 Filter）。
 */
class FilterTest {

    // ---------- RateLimitFilter ----------

    private RateLimitFilter rateLimitFilter(int requestsPerWindow) {
        RateLimitFilter f = new RateLimitFilter();
        ReflectionTestUtils.setField(f, "enabled", true);
        ReflectionTestUtils.setField(f, "requestsPerWindow", requestsPerWindow);
        ReflectionTestUtils.setField(f, "windowMs", 60000L);
        return f;
    }

    private MockHttpServletRequest chatRequest(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/chat");
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    @DisplayName("限流：超过上限后返回 429 并带 Retry-After")
    void rateLimitExceeded() throws Exception {
        RateLimitFilter filter = rateLimitFilter(2);

        // 前两次放行
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(chatRequest("10.0.0.1"), res, new MockFilterChain());
            assertThat(res.getStatus()).isEqualTo(200);
        }

        // 第三次被拒
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(chatRequest("10.0.0.1"), blocked, new MockFilterChain());

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(blocked.getContentAsString()).contains("请求过于频繁");
    }

    @Test
    @DisplayName("限流：不同 IP 各自独立计数")
    void rateLimitIsPerClient() throws Exception {
        RateLimitFilter filter = rateLimitFilter(1);

        MockHttpServletResponse a1 = new MockHttpServletResponse();
        filter.doFilter(chatRequest("10.0.0.1"), a1, new MockFilterChain());
        assertThat(a1.getStatus()).isEqualTo(200);

        // 另一个 IP 仍然可用
        MockHttpServletResponse b1 = new MockHttpServletResponse();
        filter.doFilter(chatRequest("10.0.0.2"), b1, new MockFilterChain());
        assertThat(b1.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("限流：健康检查不受限制")
    void healthNotLimited() throws Exception {
        RateLimitFilter filter = rateLimitFilter(1);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/chat/health");
            req.setRemoteAddr("10.0.0.9");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("限流：支持 X-Forwarded-For")
    void usesForwardedFor() throws Exception {
        RateLimitFilter filter = rateLimitFilter(1);

        MockHttpServletRequest req = chatRequest("127.0.0.1");
        req.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(200);

        // 同一个真实客户端再来一次 -> 应被限流
        MockHttpServletRequest again = chatRequest("127.0.0.1");
        again.addHeader("X-Forwarded-For", "203.0.113.7");
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(again, blocked, new MockFilterChain());
        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    // ---------- AuthFilter ----------

    private AuthFilter authFilter(String token) {
        AuthFilter f = new AuthFilter();
        ReflectionTestUtils.setField(f, "expectedToken", token);
        return f;
    }

    @Test
    @DisplayName("鉴权：未配置令牌时完全放行")
    void authDisabledWhenNoToken() throws Exception {
        AuthFilter filter = authFilter("");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/chat");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("鉴权：缺少令牌返回 401")
    void authMissingToken() throws Exception {
        AuthFilter filter = authFilter("secret-token");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/chat");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(res.getContentAsString()).contains("未授权");
    }

    @Test
    @DisplayName("鉴权：错误令牌返回 401")
    void authWrongToken() throws Exception {
        AuthFilter filter = authFilter("secret-token");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/chat");
        req.addHeader("Authorization", "Bearer wrong-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("鉴权：正确令牌放行")
    void authValidToken() throws Exception {
        AuthFilter filter = authFilter("secret-token");

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/chat");
        req.addHeader("Authorization", "Bearer secret-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("鉴权：健康检查始终放行")
    void authAllowsHealth() throws Exception {
        AuthFilter filter = authFilter("secret-token");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/chat/health");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());

        assertThat(res.getStatus()).isEqualTo(200);
    }
}

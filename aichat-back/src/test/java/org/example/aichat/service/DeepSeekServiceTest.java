package org.example.aichat.service;

import org.example.aichat.dto.ChatRequest;
import org.example.aichat.exception.AiServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * DeepSeekService 单元测试：重点验证多轮历史组装与上游异常映射。
 */
class DeepSeekServiceTest {

    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String OK_BODY = """
            {"id":"1","choices":[{"index":0,"message":{"role":"assistant","content":"你好"}}]}
            """;

    /** 一次测试所需的被测服务 + 它绑定的 mock 服务器。 */
    private record Ctx(DeepSeekService service, MockRestServiceServer server) {
    }

    /** 构造一个打了桩（已注入配置、绑定 MockRestServiceServer）的被测服务。 */
    private static Ctx newContext(String apiKey) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        DeepSeekService service = new DeepSeekService(restTemplate);

        ReflectionTestUtils.setField(service, "apiUrl", API_URL);
        ReflectionTestUtils.setField(service, "apiKey", apiKey);
        ReflectionTestUtils.setField(service, "model", "deepseek-chat");
        ReflectionTestUtils.setField(service, "temperature", 0.7);
        ReflectionTestUtils.setField(service, "maxTokens", 2000);
        ReflectionTestUtils.setField(service, "systemPrompt", "SYS");

        return new Ctx(service, server);
    }

    private static ChatRequest.HistoryMessage msg(String role, String content) {
        ChatRequest.HistoryMessage m = new ChatRequest.HistoryMessage();
        m.setRole(role);
        m.setContent(content);
        return m;
    }

    @Test
    @DisplayName("单轮：只发送 system + 当前用户消息")
    void singleTurn() {
        Ctx ctx = newContext("sk-test-key");
        ctx.server().expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = request.getBody().toString();
                    assertThat(body).contains("\"role\":\"system\",\"content\":\"SYS\"");
                    assertThat(body).contains("\"role\":\"user\",\"content\":\"你好\"");
                })
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        String reply = ctx.service().chatWithAI("你好");

        ctx.server().verify();
        assertThat(reply).isEqualTo("你好");
    }

    @Test
    @DisplayName("多轮：system → 历史 → 本轮，顺序正确")
    void multiTurnOrder() {
        Ctx ctx = newContext("sk-test-key");
        ctx.server().expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = request.getBody().toString();
                    int sys = body.indexOf("\"content\":\"SYS\"");
                    int h1 = body.indexOf("我叫小明");
                    int h2 = body.indexOf("你好小明");
                    int cur = body.indexOf("第三个问题");

                    assertThat(sys).isGreaterThanOrEqualTo(0);
                    assertThat(sys).isLessThan(h1);
                    assertThat(h1).isLessThan(h2);
                    assertThat(h2).isLessThan(cur);
                })
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        ctx.service().chatWithAI("第三个问题", List.of(
                msg("user", "我叫小明"),
                msg("assistant", "你好小明")
        ));

        ctx.server().verify();
    }

    @Test
    @DisplayName("角色白名单：未知角色丢弃，AI 归一化为 assistant，空内容跳过")
    void roleWhitelist() {
        Ctx ctx = newContext("sk-test-key");
        ctx.server().expect(requestTo(API_URL))
                .andExpect(request -> {
                    String body = request.getBody().toString();
                    assertThat(body).doesNotContain("hacker-payload");
                    assertThat(body).contains("\"role\":\"assistant\",\"content\":\"归一化\"");
                    assertThat(body).doesNotContain("空内容");
                })
                .andRespond(withSuccess(OK_BODY, MediaType.APPLICATION_JSON));

        ctx.service().chatWithAI("hi", List.of(
                msg("hacker", "hacker-payload"),
                msg("AI", "归一化"),
                msg("user", "   ")
        ));

        ctx.server().verify();
    }

    @Test
    @DisplayName("未配置密钥：抛出 503，且不发起任何请求")
    void missingApiKey() {
        Ctx ctx = newContext("your-api-key-here");

        assertThatThrownBy(() -> ctx.service().chatWithAI("hi"))
                .isInstanceOf(AiServiceException.class)
                .satisfies(e -> assertThat(((AiServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        ctx.server().verify();
    }

    @Test
    @DisplayName("上游 401：映射为 502 鉴权失败")
    void upstreamUnauthorized() {
        Ctx ctx = newContext("sk-test-key");
        ctx.server().expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid key\"}"));

        assertThatThrownBy(() -> ctx.service().chatWithAI("hi"))
                .isInstanceOf(AiServiceException.class)
                .satisfies(e -> {
                    AiServiceException ex = (AiServiceException) e;
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getMessage()).contains("鉴权失败");
                });
    }

    @Test
    @DisplayName("上游 429：映射为 429 限流")
    void upstreamRateLimited() {
        Ctx ctx = newContext("sk-test-key");
        ctx.server().expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"rate limit\"}"));

        assertThatThrownBy(() -> ctx.service().chatWithAI("hi"))
                .isInstanceOf(AiServiceException.class)
                .satisfies(e -> assertThat(((AiServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    @DisplayName("上游 5xx：映射为 502")
    void upstreamServerError() {
        Ctx ctx = newContext("sk-test-key");
        ctx.server().expect(requestTo(API_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"boom\"}"));

        assertThatThrownBy(() -> ctx.service().chatWithAI("hi"))
                .isInstanceOf(AiServiceException.class)
                .satisfies(e -> assertThat(((AiServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    @DisplayName("上游返回空 choices：映射为 502")
    void emptyChoices() {
        Ctx ctx = newContext("sk-test-key");
        ctx.server().expect(requestTo(API_URL))
                .andRespond(withSuccess("{\"id\":\"1\",\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> ctx.service().chatWithAI("hi"))
                .isInstanceOf(AiServiceException.class)
                .satisfies(e -> assertThat(((AiServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}

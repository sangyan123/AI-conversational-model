package org.example.aichat.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置，重点是设置真实的连接/读取超时。
 *
 * <p>默认的 {@code new RestTemplate()} 没有任何超时，上游卡住时请求会一直挂起，
 * 直到 Tomcat 线程耗尽。
 *
 * @author 吕鹏宇
 * @since 2026/1/3
 */
@Configuration
public class RestTemplateConfig {

    @Value("${deepseek.api.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${deepseek.api.read-timeout-ms:60000}")
    private int readTimeoutMs;

    @Bean
    public RestTemplate restTemplate() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                .build();

        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        return new RestTemplate(factory);
    }
}

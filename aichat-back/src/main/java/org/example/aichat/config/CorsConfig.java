package org.example.aichat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 白名单。
 *
 * <p>Vite 端口被占用时会自动顺延（5174、5175…），因此这里用正则匹配 5500/517x，
 * 避免开发者换个端口就被浏览器拦下。
 *
 * @author 吕鹏宇
 * @since 2026/1/3
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origin-patterns:http://localhost:[*],http://127.0.0.1:[*]}")
    private String[] allowedOriginPatterns;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

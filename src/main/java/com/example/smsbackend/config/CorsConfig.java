package com.example.smsbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String FRONTEND_ORIGIN = "https://ev12frontend-dbdk.vercel.app";
    private static final String VERCEL_ORIGIN_PATTERN = "https://*.vercel.app";
    private static final String LOCALHOST_HTTP_PATTERN = "http://localhost:*";
    private static final String LOCALHOST_HTTPS_PATTERN = "https://localhost:*";

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                        FRONTEND_ORIGIN,
                        VERCEL_ORIGIN_PATTERN,
                        LOCALHOST_HTTP_PATTERN,
                        LOCALHOST_HTTPS_PATTERN
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

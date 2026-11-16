package com.suivicontrats.front.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = new ArrayList<>(List.of(
                "http://localhost:3000", "http://localhost:5173",
                "http://192.168.*.*:3000", "http://192.168.*.*:5173",
                "http://10.*.*.*:3000", "http://10.*.*.*:5173"));
        for (int secondOctet = 16; secondOctet <= 31; secondOctet++) {
            origins.add("http://172." + secondOctet + ".*.*:3000");
            origins.add("http://172." + secondOctet + ".*.*:5173");
        }

        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}

package com.miguel.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

	static final String ORIGEN_TAURI_WINDOWS = "http://tauri.localhost";
	static final String ORIGEN_VITE_DESARROLLO = "http://localhost:5173";

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(ORIGEN_TAURI_WINDOWS, ORIGEN_VITE_DESARROLLO)
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("Content-Type")
				.allowCredentials(false);
	}
}

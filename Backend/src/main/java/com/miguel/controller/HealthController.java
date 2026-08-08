package com.miguel.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.miguel.dto.HealthResponse;

@RestController
public class HealthController {

	@GetMapping("/api/health")
	public HealthResponse health() {
		return new HealthResponse("UP");
	}
}

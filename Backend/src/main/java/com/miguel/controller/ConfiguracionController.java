package com.miguel.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.miguel.dto.ConfiguracionResponse;
import com.miguel.dto.GuardarRutaRequest;
import com.miguel.service.ConfiguracionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/configuracion")
public class ConfiguracionController {
	private final ConfiguracionService configuracionService;

	public ConfiguracionController(ConfiguracionService configuracionService) {
		this.configuracionService = configuracionService;
	}

	@GetMapping
	public ConfiguracionResponse obtenerConfiguracion() {
		return configuracionService.obtenerConfiguracion();
	}

	@PutMapping("/ruta")
	public ConfiguracionResponse guardarRuta(
			@Valid @RequestBody GuardarRutaRequest request) {

		return configuracionService.guardarRuta(request.getRuta());
	}
}
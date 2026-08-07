package com.miguel.dto;

import jakarta.validation.constraints.NotBlank;

public class GuardarRutaRequest {
	@NotBlank(message = "La ruta de los libros no puede estar vacía")
	private String ruta;

	public GuardarRutaRequest() {
		
	}

	public GuardarRutaRequest(String ruta) {
		this.ruta = ruta;
	}

	public String getRuta() {
		return ruta;
	}

	public void setRuta(String ruta) {
		this.ruta = ruta;
	}
}
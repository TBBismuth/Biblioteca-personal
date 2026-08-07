package com.miguel.dto;

public class ConfiguracionResponse {
	private boolean configurada;
	private String rutaLibros;
	private boolean rutaAccesible;

	public ConfiguracionResponse() {
		
	}

	public ConfiguracionResponse(boolean configurada, String rutaLibros, boolean rutaAccesible) {
		this.configurada = configurada;
		this.rutaLibros = rutaLibros;
		this.rutaAccesible = rutaAccesible;
	}

	public boolean isConfigurada() {
		return configurada;
	}

	public void setConfigurada(boolean configurada) {
		this.configurada = configurada;
	}

	public String getRutaLibros() {
		return rutaLibros;
	}

	public void setRutaLibros(String rutaLibros) {
		this.rutaLibros = rutaLibros;
	}

	public boolean isRutaAccesible() {
		return rutaAccesible;
	}

	public void setRutaAccesible(boolean rutaAccesible) {
		this.rutaAccesible = rutaAccesible;
	}
}

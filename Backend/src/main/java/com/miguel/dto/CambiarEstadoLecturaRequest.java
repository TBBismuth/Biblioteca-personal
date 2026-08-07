package com.miguel.dto;

import jakarta.validation.constraints.NotNull;

public class CambiarEstadoLecturaRequest {
	@NotNull(message = "Debes indicar el estado de lectura")
	private Boolean leido;

	public CambiarEstadoLecturaRequest() {
		
	}
	public CambiarEstadoLecturaRequest(Boolean leido) {
		this.leido = leido;
	}

	public Boolean getLeido() {
		return leido;
	}
	public void setLeido(Boolean leido) {
		this.leido = leido;
	}
}
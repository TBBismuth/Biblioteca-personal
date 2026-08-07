package com.miguel.dto;

public record ErrorEliminacionResponse(
		int status,
		String error,
		String message,
		ResultadoEliminacionResponse resultadoParcial) {
}

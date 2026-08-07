package com.miguel.dto;

import java.util.List;

public record ResultadoEliminacionResponse(
		Long libroId,
		List<CopiaEliminadaResponse> copiasEliminadas,
		int copiasRestantes,
		boolean libroDisponible) {
}

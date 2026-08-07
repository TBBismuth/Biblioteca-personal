package com.miguel.service;

import java.util.Locale;

public enum EstadoLecturaFiltro {
	TODOS,
	LEIDOS,
	PENDIENTES;

	public static EstadoLecturaFiltro desde(String valor) {
		if (valor == null || valor.isBlank()) {
			return TODOS;
		}

		try {
			return valueOf(valor.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(
					"El estado debe ser TODOS, LEIDOS o PENDIENTES"
			);
		}
	}
}

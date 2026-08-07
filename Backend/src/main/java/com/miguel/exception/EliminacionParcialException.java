package com.miguel.exception;

import com.miguel.dto.ResultadoEliminacionResponse;

public class EliminacionParcialException extends RuntimeException {
	private final ResultadoEliminacionResponse resultadoParcial;

	public EliminacionParcialException(
			String mensaje, ResultadoEliminacionResponse resultadoParcial, Throwable causa) {
		super(mensaje, causa);
		this.resultadoParcial = resultadoParcial;
	}

	public ResultadoEliminacionResponse getResultadoParcial() {
		return resultadoParcial;
	}
}

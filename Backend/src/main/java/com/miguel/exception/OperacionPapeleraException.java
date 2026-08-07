package com.miguel.exception;

public class OperacionPapeleraException extends RuntimeException {
	public OperacionPapeleraException(String mensaje) {
		super(mensaje);
	}

	public OperacionPapeleraException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}

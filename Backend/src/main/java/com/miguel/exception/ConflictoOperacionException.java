package com.miguel.exception;

public class ConflictoOperacionException extends RuntimeException {
	public ConflictoOperacionException(String mensaje) {
		super(mensaje);
	}
}

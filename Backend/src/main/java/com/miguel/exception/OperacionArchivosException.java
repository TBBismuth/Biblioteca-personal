package com.miguel.exception;

public class OperacionArchivosException extends RuntimeException {
	public OperacionArchivosException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}

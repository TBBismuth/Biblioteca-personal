package com.miguel.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.miguel.dto.ApiErrorResponse;
import com.miguel.dto.ErrorEliminacionResponse;
import com.miguel.exception.EliminacionParcialException;
import com.miguel.exception.RecursoNoEncontradoException;
import com.miguel.exception.ConflictoOperacionException;
import com.miguel.exception.OperacionArchivosException;

@RestControllerAdvice
public class ApiExceptionHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(RecursoNoEncontradoException.class)
	public ResponseEntity<ApiErrorResponse> manejarNoEncontrado(RecursoNoEncontradoException error) {
		return respuesta(HttpStatus.NOT_FOUND, error.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> manejarPeticionInvalida(IllegalArgumentException error) {
		return respuesta(HttpStatus.BAD_REQUEST, error.getMessage());
	}

	@ExceptionHandler(ConflictoOperacionException.class)
	public ResponseEntity<ApiErrorResponse> manejarConflicto(ConflictoOperacionException error) {
		return respuesta(HttpStatus.CONFLICT, error.getMessage());
	}

	@ExceptionHandler(OperacionArchivosException.class)
	public ResponseEntity<ApiErrorResponse> manejarErrorDeArchivos(OperacionArchivosException error) {
		LOGGER.error("Falló la operación coordinada de renombrado", error);
		return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, error.getMessage());
	}

	@ExceptionHandler(EliminacionParcialException.class)
	public ResponseEntity<ErrorEliminacionResponse> manejarEliminacionParcial(
			EliminacionParcialException error) {
		LOGGER.error("Falló el envío coordinado a la Papelera", error);
		HttpStatus estado = HttpStatus.INTERNAL_SERVER_ERROR;
		return ResponseEntity.status(estado).body(new ErrorEliminacionResponse(
				estado.value(), estado.getReasonPhrase(), error.getMessage(),
				error.getResultadoParcial()));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> manejarTipoInvalido(MethodArgumentTypeMismatchException error) {
		return respuesta(
				HttpStatus.BAD_REQUEST,
				"El parámetro " + error.getName() + " tiene un valor no válido"
		);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> manejarValidacion(MethodArgumentNotValidException error) {
		String mensaje = error.getBindingResult().getFieldErrors().isEmpty()
				? "La petición contiene datos no válidos"
				: error.getBindingResult().getFieldErrors().getFirst().getDefaultMessage();
		return respuesta(HttpStatus.BAD_REQUEST, mensaje);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> manejarErrorInesperado(Exception error) {
		LOGGER.error("Error interno no controlado", error);
		return respuesta(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"Se ha producido un error interno inesperado"
		);
	}

	private ResponseEntity<ApiErrorResponse> respuesta(HttpStatus estado, String mensaje) {
		return ResponseEntity.status(estado).body(
				new ApiErrorResponse(estado.value(), estado.getReasonPhrase(), mensaje));
	}
}

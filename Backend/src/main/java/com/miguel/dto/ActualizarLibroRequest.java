package com.miguel.dto;

import java.util.List;

public class ActualizarLibroRequest {
	private String titulo;
	private List<String> autores;

	public ActualizarLibroRequest() {
	}

	public ActualizarLibroRequest(String titulo, List<String> autores) {
		this.titulo = titulo;
		this.autores = autores;
	}

	public String getTitulo() { return titulo; }
	public void setTitulo(String titulo) { this.titulo = titulo; }
	public List<String> getAutores() { return autores; }
	public void setAutores(List<String> autores) { this.autores = autores; }
}

package com.miguel.dto;

import java.util.List;

public class LibroResponse {
	private Long id;
	private String titulo;
	private boolean leido;
	private List<String> autores;
	private List<String> formatos;
	private int numeroArchivos;

	public LibroResponse() {
		
	}

	public LibroResponse(Long id, String titulo, boolean leido, List<String> autores,
			List<String> formatos, int numeroArchivos) {
		this.id = id;
		this.titulo = titulo;
		this.leido = leido;
		this.autores = autores;
		this.formatos = formatos;
		this.numeroArchivos = numeroArchivos;
	}

	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getTitulo() {
		return titulo;
	}
	public void setTitulo(String titulo) {
		this.titulo = titulo;
	}
	public boolean isLeido() {
		return leido;
	}
	public void setLeido(boolean leido) {
		this.leido = leido;
	}
	public List<String> getAutores() {
		return autores;
	}
	public void setAutores(List<String> autores) {
		this.autores = autores;
	}
	public List<String> getFormatos() {
		return formatos;
	}
	public void setFormatos(List<String> formatos) {
		this.formatos = formatos;
	}
	public int getNumeroArchivos() {
		return numeroArchivos;
	}
	public void setNumeroArchivos(int numeroArchivos) {
		this.numeroArchivos = numeroArchivos;
	}
}
package com.miguel.dto;

import java.util.List;

public class LibroPaginadoResponse {
	private List<LibroResponse> libros;
	private int paginaActual;
	private int tamanoPagina;
	private long totalResultados;
	private int totalPaginas;
	private boolean primeraPagina;
	private boolean ultimaPagina;

	public LibroPaginadoResponse() {
	}

	public LibroPaginadoResponse(
			List<LibroResponse> libros,
			int paginaActual,
			int tamanoPagina,
			long totalResultados,
			int totalPaginas,
			boolean primeraPagina,
			boolean ultimaPagina) {
		this.libros = libros;
		this.paginaActual = paginaActual;
		this.tamanoPagina = tamanoPagina;
		this.totalResultados = totalResultados;
		this.totalPaginas = totalPaginas;
		this.primeraPagina = primeraPagina;
		this.ultimaPagina = ultimaPagina;
	}

	public List<LibroResponse> getLibros() { return libros; }
	public void setLibros(List<LibroResponse> libros) { this.libros = libros; }
	public int getPaginaActual() { return paginaActual; }
	public void setPaginaActual(int paginaActual) { this.paginaActual = paginaActual; }
	public int getTamanoPagina() { return tamanoPagina; }
	public void setTamanoPagina(int tamanoPagina) { this.tamanoPagina = tamanoPagina; }
	public long getTotalResultados() { return totalResultados; }
	public void setTotalResultados(long totalResultados) { this.totalResultados = totalResultados; }
	public int getTotalPaginas() { return totalPaginas; }
	public void setTotalPaginas(int totalPaginas) { this.totalPaginas = totalPaginas; }
	public boolean isPrimeraPagina() { return primeraPagina; }
	public void setPrimeraPagina(boolean primeraPagina) { this.primeraPagina = primeraPagina; }
	public boolean isUltimaPagina() { return ultimaPagina; }
	public void setUltimaPagina(boolean ultimaPagina) { this.ultimaPagina = ultimaPagina; }
}

package com.miguel.dto;

public class ResumenBibliotecaResponse {
	private long totalLibros;
	private long totalLeidos;
	private long totalPendientes;

	public ResumenBibliotecaResponse() {
	}

	public ResumenBibliotecaResponse(long totalLibros, long totalLeidos, long totalPendientes) {
		this.totalLibros = totalLibros;
		this.totalLeidos = totalLeidos;
		this.totalPendientes = totalPendientes;
	}

	public long getTotalLibros() { return totalLibros; }
	public void setTotalLibros(long totalLibros) { this.totalLibros = totalLibros; }
	public long getTotalLeidos() { return totalLeidos; }
	public void setTotalLeidos(long totalLeidos) { this.totalLeidos = totalLeidos; }
	public long getTotalPendientes() { return totalPendientes; }
	public void setTotalPendientes(long totalPendientes) { this.totalPendientes = totalPendientes; }
}

package com.miguel.dto;

import java.util.List;

public class ResultadoEscaneoResponse {
	private int archivosEncontrados;
	private int archivosNuevos;
	private int librosNuevos;
	private int archivosYaRegistrados;
	private int archivosMovidosRenombrados;
	private int copiasIdenticasNuevas;
	private int archivosModificados;
	private int archivosDesaparecidosEliminados;
	private int nombresInvalidos;
	private int erroresLectura;
	private List<String> detallesInvalidos;
	private List<String> detallesErrores;
	private List<DetalleArchivoEscaneoResponse> detallesArchivosNuevos;
	private List<DetalleArchivoEscaneoResponse> detallesArchivosDesaparecidos;

	public ResultadoEscaneoResponse() {
	}

	public ResultadoEscaneoResponse(
			int archivosEncontrados,
			int archivosNuevos,
			int librosNuevos,
			int archivosYaRegistrados,
			int archivosMovidosRenombrados,
			int copiasIdenticasNuevas,
			int archivosModificados,
			int archivosDesaparecidosEliminados,
			int nombresInvalidos,
			int erroresLectura,
			List<String> detallesInvalidos,
			List<String> detallesErrores,
			List<DetalleArchivoEscaneoResponse> detallesArchivosNuevos,
			List<DetalleArchivoEscaneoResponse> detallesArchivosDesaparecidos) {
		this.archivosEncontrados = archivosEncontrados;
		this.archivosNuevos = archivosNuevos;
		this.librosNuevos = librosNuevos;
		this.archivosYaRegistrados = archivosYaRegistrados;
		this.archivosMovidosRenombrados = archivosMovidosRenombrados;
		this.copiasIdenticasNuevas = copiasIdenticasNuevas;
		this.archivosModificados = archivosModificados;
		this.archivosDesaparecidosEliminados = archivosDesaparecidosEliminados;
		this.nombresInvalidos = nombresInvalidos;
		this.erroresLectura = erroresLectura;
		this.detallesInvalidos = detallesInvalidos;
		this.detallesErrores = detallesErrores;
		this.detallesArchivosNuevos = detallesArchivosNuevos;
		this.detallesArchivosDesaparecidos = detallesArchivosDesaparecidos;
	}

	public int getArchivosEncontrados() { return archivosEncontrados; }
	public void setArchivosEncontrados(int valor) { this.archivosEncontrados = valor; }
	public int getArchivosNuevos() { return archivosNuevos; }
	public void setArchivosNuevos(int valor) { this.archivosNuevos = valor; }
	public int getLibrosNuevos() { return librosNuevos; }
	public void setLibrosNuevos(int valor) { this.librosNuevos = valor; }
	public int getArchivosYaRegistrados() { return archivosYaRegistrados; }
	public void setArchivosYaRegistrados(int valor) { this.archivosYaRegistrados = valor; }
	public int getArchivosMovidosRenombrados() { return archivosMovidosRenombrados; }
	public void setArchivosMovidosRenombrados(int valor) { this.archivosMovidosRenombrados = valor; }
	public int getCopiasIdenticasNuevas() { return copiasIdenticasNuevas; }
	public void setCopiasIdenticasNuevas(int valor) { this.copiasIdenticasNuevas = valor; }
	public int getArchivosModificados() { return archivosModificados; }
	public void setArchivosModificados(int valor) { this.archivosModificados = valor; }
	public int getArchivosDesaparecidosEliminados() { return archivosDesaparecidosEliminados; }
	public void setArchivosDesaparecidosEliminados(int valor) { this.archivosDesaparecidosEliminados = valor; }
	public int getNombresInvalidos() { return nombresInvalidos; }
	public void setNombresInvalidos(int valor) { this.nombresInvalidos = valor; }
	public int getErroresLectura() { return erroresLectura; }
	public void setErroresLectura(int valor) { this.erroresLectura = valor; }
	public List<String> getDetallesInvalidos() { return detallesInvalidos; }
	public void setDetallesInvalidos(List<String> valor) { this.detallesInvalidos = valor; }
	public List<String> getDetallesErrores() { return detallesErrores; }
	public void setDetallesErrores(List<String> valor) { this.detallesErrores = valor; }
	public List<DetalleArchivoEscaneoResponse> getDetallesArchivosNuevos() { return detallesArchivosNuevos; }
	public void setDetallesArchivosNuevos(List<DetalleArchivoEscaneoResponse> valor) { this.detallesArchivosNuevos = valor; }
	public List<DetalleArchivoEscaneoResponse> getDetallesArchivosDesaparecidos() { return detallesArchivosDesaparecidos; }
	public void setDetallesArchivosDesaparecidos(List<DetalleArchivoEscaneoResponse> valor) { this.detallesArchivosDesaparecidos = valor; }
}

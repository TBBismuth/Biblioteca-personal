package com.miguel.dto;

import java.util.List;

public class ActualizarLibroResponse {
	private LibroResponse libro;
	private List<ArchivoRenombradoResponse> archivosRenombrados;

	public ActualizarLibroResponse() {
	}

	public ActualizarLibroResponse(LibroResponse libro, List<ArchivoRenombradoResponse> archivosRenombrados) {
		this.libro = libro;
		this.archivosRenombrados = archivosRenombrados;
	}

	public LibroResponse getLibro() { return libro; }
	public void setLibro(LibroResponse valor) { this.libro = valor; }
	public List<ArchivoRenombradoResponse> getArchivosRenombrados() { return archivosRenombrados; }
	public void setArchivosRenombrados(List<ArchivoRenombradoResponse> valor) { this.archivosRenombrados = valor; }
}

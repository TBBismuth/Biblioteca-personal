package com.miguel.dto;

public class DetalleArchivoEscaneoResponse {
	private String nombreArchivo;
	private String ruta;

	public DetalleArchivoEscaneoResponse() {
	}

	public DetalleArchivoEscaneoResponse(String nombreArchivo, String ruta) {
		this.nombreArchivo = nombreArchivo;
		this.ruta = ruta;
	}

	public String getNombreArchivo() {
		return nombreArchivo;
	}

	public void setNombreArchivo(String nombreArchivo) {
		this.nombreArchivo = nombreArchivo;
	}

	public String getRuta() {
		return ruta;
	}

	public void setRuta(String ruta) {
		this.ruta = ruta;
	}
}

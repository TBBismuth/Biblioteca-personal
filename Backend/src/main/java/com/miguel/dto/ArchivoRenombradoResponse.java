package com.miguel.dto;

public class ArchivoRenombradoResponse {
	private Long idArchivo;
	private String nombreAnterior;
	private String nombreNuevo;
	private String rutaAnterior;
	private String rutaNueva;

	public ArchivoRenombradoResponse() {
	}

	public ArchivoRenombradoResponse(Long idArchivo, String nombreAnterior, String nombreNuevo,
			String rutaAnterior, String rutaNueva) {
		this.idArchivo = idArchivo;
		this.nombreAnterior = nombreAnterior;
		this.nombreNuevo = nombreNuevo;
		this.rutaAnterior = rutaAnterior;
		this.rutaNueva = rutaNueva;
	}

	public Long getIdArchivo() { return idArchivo; }
	public void setIdArchivo(Long valor) { this.idArchivo = valor; }
	public String getNombreAnterior() { return nombreAnterior; }
	public void setNombreAnterior(String valor) { this.nombreAnterior = valor; }
	public String getNombreNuevo() { return nombreNuevo; }
	public void setNombreNuevo(String valor) { this.nombreNuevo = valor; }
	public String getRutaAnterior() { return rutaAnterior; }
	public void setRutaAnterior(String valor) { this.rutaAnterior = valor; }
	public String getRutaNueva() { return rutaNueva; }
	public void setRutaNueva(String valor) { this.rutaNueva = valor; }
}

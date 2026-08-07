package com.miguel.dto;

import java.time.Instant;

public class CopiaLibroResponse {
	private Long id;
	private String nombreArchivo;
	private String extension;
	private String ruta;
	private long tamanioBytes;
	private Instant ultimaModificacion;

	public CopiaLibroResponse() {
	}

	public CopiaLibroResponse(
			Long id,
			String nombreArchivo,
			String extension,
			String ruta,
			long tamanioBytes,
			Instant ultimaModificacion) {
		this.id = id;
		this.nombreArchivo = nombreArchivo;
		this.extension = extension;
		this.ruta = ruta;
		this.tamanioBytes = tamanioBytes;
		this.ultimaModificacion = ultimaModificacion;
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getNombreArchivo() { return nombreArchivo; }
	public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }
	public String getExtension() { return extension; }
	public void setExtension(String extension) { this.extension = extension; }
	public String getRuta() { return ruta; }
	public void setRuta(String ruta) { this.ruta = ruta; }
	public long getTamanioBytes() { return tamanioBytes; }
	public void setTamanioBytes(long tamanioBytes) { this.tamanioBytes = tamanioBytes; }
	public Instant getUltimaModificacion() { return ultimaModificacion; }
	public void setUltimaModificacion(Instant ultimaModificacion) { this.ultimaModificacion = ultimaModificacion; }
}

package com.miguel.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
		name = "archivos_libro",
		indexes = @Index(name = "idx_archivos_libro_sha256_tamanio", columnList = "sha256,tamanio_bytes")
)
public class ArchivoLibro {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false, unique = true, length = 1500)
	private String ruta;
	@Column(nullable = false, length = 500)
	private String nombreArchivo;
	@Column(nullable = false, length = 20)
	private String extension;
	@Column(name = "tamanio_bytes", nullable = false)
	private long tamanioBytes;
	@Column(nullable = false)
	private Instant ultimaModificacion;
	@Column(length = 64)
	private String sha256;
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "libro_id", nullable = false)
	private Libro libro;

	public ArchivoLibro() {
		
	}
	public ArchivoLibro(String ruta, String nombreArchivo, String extension, long tamanioBytes, Instant ultimaModificacion, Libro libro) {
		this.ruta = ruta;
		this.nombreArchivo = nombreArchivo;
		this.extension = extension;
		this.tamanioBytes = tamanioBytes;
		this.ultimaModificacion = ultimaModificacion;
		this.libro = libro;
	}

	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getRuta() {
		return ruta;
	}
	public void setRuta(String ruta) {
		this.ruta = ruta;
	}
	public String getNombreArchivo() {
		return nombreArchivo;
	}
	public void setNombreArchivo(String nombreArchivo) {
		this.nombreArchivo = nombreArchivo;
	}
	public String getExtension() {
		return extension;
	}
	public void setExtension(String extension) {
		this.extension = extension;
	}
	public long getTamanioBytes() {
		return tamanioBytes;
	}
	public void setTamanioBytes(long tamanioBytes) {
		this.tamanioBytes = tamanioBytes;
	}
	public Instant getUltimaModificacion() {
		return ultimaModificacion;
	}
	public void setUltimaModificacion(Instant ultimaModificacion) {
		this.ultimaModificacion = ultimaModificacion;
	}
	public String getSha256() {
		return sha256;
	}
	public void setSha256(String sha256) {
		this.sha256 = sha256;
	}
	public Libro getLibro() {
		return libro;
	}
	public void setLibro(Libro libro) {
		this.libro = libro;
	}
}

package com.miguel.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "configuracion")
public class Configuracion {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "ruta_libros", nullable = false, length = 2000)
	private String rutaLibros;

	public Configuracion() {
		
	}

	public Configuracion(String rutaLibros) {
		this.rutaLibros = rutaLibros;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getRutaLibros() {
		return rutaLibros;
	}

	public void setRutaLibros(String rutaLibros) {
		this.rutaLibros = rutaLibros;
	}
}
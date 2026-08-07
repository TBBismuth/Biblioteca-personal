package com.miguel.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "libros")
public class Libro {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false, length = 500)
	private String titulo;
	@Column(nullable = false)
	private boolean leido = false;
	@ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
	@JoinTable(name = "libro_autor", joinColumns = @JoinColumn(name = "libro_id"), inverseJoinColumns = @JoinColumn(name = "autor_id"))
	private Set<Autor> autores = new LinkedHashSet<>();
	@OneToMany(mappedBy = "libro", cascade = CascadeType.ALL)
	private List<ArchivoLibro> archivos = new ArrayList<>();

	public Libro() {
		
	}
	public Libro(String titulo, boolean leido, Set<Autor> autores, List<ArchivoLibro> archivos) {
		this.titulo = titulo;
		this.leido = leido;
		this.autores = autores;
		this.archivos = archivos;
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
	public Set<Autor> getAutores() {
		return autores;
	}
	public void setAutores(Set<Autor> autores) {
		this.autores = autores;
	}
	public List<ArchivoLibro> getArchivos() {
		return archivos;
	}
	public void setArchivos(List<ArchivoLibro> archivos) {
		this.archivos = archivos;
	}
}

package com.miguel.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.miguel.dto.LibroResponse;
import com.miguel.exception.ConflictoOperacionException;
import com.miguel.exception.RecursoNoEncontradoException;
import com.miguel.model.ArchivoLibro;
import com.miguel.model.Autor;
import com.miguel.model.Libro;
import com.miguel.repository.AutorRepository;
import com.miguel.repository.LibroRepository;

import jakarta.persistence.EntityManager;

@Service
public class ActualizacionLibroDatosService {
	private final LibroRepository libroRepository;
	private final AutorRepository autorRepository;
	private final NormalizadorBiblioteca normalizador;
	private final EntityManager entityManager;

	public ActualizacionLibroDatosService(
			LibroRepository libroRepository,
			AutorRepository autorRepository,
			NormalizadorBiblioteca normalizador,
			EntityManager entityManager) {
		this.libroRepository = libroRepository;
		this.autorRepository = autorRepository;
		this.normalizador = normalizador;
		this.entityManager = entityManager;
	}

	@Transactional(readOnly = true)
	public DatosLibro preparar(Long id, String titulo, List<String> autores) {
		Libro libro = libroRepository.findConAutoresById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException(
						"No existe ningún libro con el ID " + id));
		libroRepository.findConArchivosById(id);
		if (libro.getArchivos().isEmpty()) {
			throw new ConflictoOperacionException(
					"El libro no tiene copias físicas registradas y no se puede renombrar.");
		}

		String nuevaClave = normalizador.crearClaveLibro(titulo, autores);
		boolean conflicto = libroRepository.findTodosConAutores().stream()
				.filter(otro -> !otro.getId().equals(id))
				.anyMatch(otro -> normalizador.crearClaveLibro(
						otro.getTitulo(), otro.getAutores().stream().map(Autor::getNombre).toList())
						.equals(nuevaClave));
		if (conflicto) {
			throw new ConflictoOperacionException(
					"Ya existe otro libro con ese título y esos autores.");
		}

		List<CopiaActual> copias = libro.getArchivos().stream()
				.sorted(Comparator.comparing(ArchivoLibro::getRuta, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(ArchivoLibro::getId))
				.map(archivo -> new CopiaActual(
						archivo.getId(), archivo.getNombreArchivo(), archivo.getRuta(),
						archivo.getExtension(), archivo.getSha256()))
				.toList();
		return new DatosLibro(libro.getId(), libro.isLeido(), copias);
	}

	@Transactional
	public LibroResponse aplicar(
			Long id,
			String titulo,
			List<String> nombresAutores,
			List<CambioArchivo> cambios) {
		Libro libro = libroRepository.findConAutoresById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException(
						"No existe ningún libro con el ID " + id));
		libroRepository.findConArchivosById(id);
		Map<Long, ArchivoLibro> archivosPorId = new LinkedHashMap<>();
		libro.getArchivos().forEach(archivo -> archivosPorId.put(archivo.getId(), archivo));

		for (CambioArchivo cambio : cambios) {
			ArchivoLibro archivo = obtenerArchivo(archivosPorId, cambio.idArchivo());
			archivo.setRuta(cambio.rutaTemporal());
			archivo.setNombreArchivo(cambio.nombreTemporal());
		}
		entityManager.flush();

		for (CambioArchivo cambio : cambios) {
			ArchivoLibro archivo = obtenerArchivo(archivosPorId, cambio.idArchivo());
			archivo.setRuta(cambio.rutaNueva());
			archivo.setNombreArchivo(cambio.nombreNuevo());
		}

		Map<String, Autor> autoresExistentes = new LinkedHashMap<>();
		for (Autor autor : autorRepository.findAll()) {
			autoresExistentes.putIfAbsent(normalizador.normalizarTexto(autor.getNombre()), autor);
		}
		LinkedHashSet<Autor> autoresNuevos = new LinkedHashSet<>();
		for (String nombre : nombresAutores) {
			String clave = normalizador.normalizarTexto(nombre);
			Autor autor = autoresExistentes.get(clave);
			if (autor == null) {
				autor = new Autor();
				autor.setNombre(nombre);
				autor.setLibros(new LinkedHashSet<>());
				autoresExistentes.put(clave, autor);
			}
			autoresNuevos.add(autor);
		}

		for (Autor anterior : new LinkedHashSet<>(libro.getAutores())) {
			if (!autoresNuevos.contains(anterior)) {
				anterior.getLibros().remove(libro);
			}
		}
		for (Autor nuevo : autoresNuevos) {
			nuevo.getLibros().add(libro);
		}
		libro.setTitulo(titulo);
		libro.getAutores().clear();
		libro.getAutores().addAll(autoresNuevos);
		libroRepository.saveAndFlush(libro);

		List<String> formatos = libro.getArchivos().stream()
				.map(ArchivoLibro::getExtension)
				.distinct()
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
		return new LibroResponse(
				libro.getId(), libro.getTitulo(), libro.isLeido(),
				List.copyOf(nombresAutores), formatos, libro.getArchivos().size());
	}

	private ArchivoLibro obtenerArchivo(Map<Long, ArchivoLibro> archivos, Long id) {
		ArchivoLibro archivo = archivos.get(id);
		if (archivo == null) {
			throw new IllegalStateException("La copia registrada cambió durante el renombrado");
		}
		return archivo;
	}

	public record DatosLibro(Long id, boolean leido, List<CopiaActual> copias) {
	}

	public record CopiaActual(
			Long id, String nombreArchivo, String ruta, String extension, String sha256) {
	}

	public record CambioArchivo(
			Long idArchivo,
			String nombreAnterior,
			String nombreTemporal,
			String nombreNuevo,
			String rutaAnterior,
			String rutaTemporal,
			String rutaNueva) {
	}
}

package com.miguel.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.miguel.config.Configuracion;
import com.miguel.exception.ConflictoOperacionException;
import com.miguel.exception.RecursoNoEncontradoException;
import com.miguel.model.ArchivoLibro;
import com.miguel.model.Libro;
import com.miguel.repository.ArchivoLibroRepository;
import com.miguel.repository.ConfiguracionRepository;
import com.miguel.repository.LibroRepository;

@Service
public class EliminacionLibroDatosService {
	private final LibroRepository libroRepository;
	private final ArchivoLibroRepository archivoRepository;
	private final ConfiguracionRepository configuracionRepository;

	public EliminacionLibroDatosService(
			LibroRepository libroRepository,
			ArchivoLibroRepository archivoRepository,
			ConfiguracionRepository configuracionRepository) {
		this.libroRepository = libroRepository;
		this.archivoRepository = archivoRepository;
		this.configuracionRepository = configuracionRepository;
	}

	@Transactional(readOnly = true)
	public PreparacionEliminacion prepararUna(Long libroId, Long archivoId) {
		Libro libro = cargarLibro(libroId);
		ArchivoLibro archivo = archivoRepository.findById(archivoId)
				.orElseThrow(() -> new RecursoNoEncontradoException(
						"No existe ninguna copia con el ID " + archivoId));
		if (!archivo.getLibro().getId().equals(libroId)) {
			throw new ConflictoOperacionException(
					"La copia indicada no pertenece al libro solicitado.");
		}
		return preparar(libro, List.of(archivo));
	}

	@Transactional(readOnly = true)
	public PreparacionEliminacion prepararTodas(Long libroId) {
		Libro libro = cargarLibro(libroId);
		if (libro.getArchivos().isEmpty()) {
			throw new ConflictoOperacionException(
					"El libro no tiene copias físicas registradas.");
		}
		List<ArchivoPreparado> copias = libro.getArchivos().stream()
				.sorted(Comparator.comparing(ArchivoLibro::getRuta, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(ArchivoLibro::getId))
				.map(this::convertir)
				.toList();
		return new PreparacionEliminacion(
				libro.getId(), obtenerRutaConfigurada(), copias, libro.getArchivos().size());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int eliminarConfirmado(Long libroId, Long archivoId) {
		Libro libro = cargarLibro(libroId);
		ArchivoLibro archivo = libro.getArchivos().stream()
				.filter(copia -> copia.getId().equals(archivoId))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"La copia enviada a la Papelera ya no está registrada."));
		libro.getArchivos().remove(archivo);
		archivoRepository.delete(archivo);
		archivoRepository.flush();
		return libro.getArchivos().size();
	}

	private Libro cargarLibro(Long libroId) {
		Libro libro = libroRepository.findConAutoresById(libroId)
				.orElseThrow(() -> new RecursoNoEncontradoException(
						"No existe ningún libro con el ID " + libroId));
		libroRepository.findConArchivosById(libroId);
		return libro;
	}

	private PreparacionEliminacion preparar(Libro libro, List<ArchivoLibro> archivos) {
		return new PreparacionEliminacion(
				libro.getId(), obtenerRutaConfigurada(),
				archivos.stream().map(this::convertir).toList(), libro.getArchivos().size());
	}

	private String obtenerRutaConfigurada() {
		return configuracionRepository.findFirstByOrderByIdAsc()
				.map(Configuracion::getRutaLibros)
				.orElseThrow(() -> new ConflictoOperacionException(
						"No hay una carpeta de biblioteca configurada."));
	}

	private ArchivoPreparado convertir(ArchivoLibro archivo) {
		return new ArchivoPreparado(
				archivo.getId(), archivo.getNombreArchivo(), archivo.getRuta());
	}

	public record PreparacionEliminacion(
			Long libroId, String rutaConfigurada, List<ArchivoPreparado> archivos, int copiasIniciales) {
	}

	public record ArchivoPreparado(Long id, String nombreArchivo, String ruta) {
	}
}

package com.miguel.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.miguel.dto.CopiaLibroResponse;
import com.miguel.dto.LibroPaginadoResponse;
import com.miguel.dto.LibroResponse;
import com.miguel.dto.ResumenBibliotecaResponse;
import com.miguel.exception.RecursoNoEncontradoException;
import com.miguel.model.ArchivoLibro;
import com.miguel.model.Autor;
import com.miguel.model.Libro;
import com.miguel.repository.LibroRepository;

@Service
public class LibroService {
	private static final int TAMANO_MAXIMO = 100;

	private final LibroRepository libroRepository;
	private final NormalizadorBiblioteca normalizador;

	public LibroService(
			LibroRepository libroRepository,
			NormalizadorBiblioteca normalizador) {
		this.libroRepository = libroRepository;
		this.normalizador = normalizador;
	}

	@Transactional(readOnly = true)
	public LibroPaginadoResponse listar(
			String busqueda,
			EstadoLecturaFiltro estado,
			int pagina,
			int tamano) {

		validarPaginacion(pagina, tamano);
		List<String> terminosBusqueda = crearTerminosBusqueda(busqueda);

		List<Libro> disponibles = libroRepository.findDisponiblesConAutores();
		if (!disponibles.isEmpty()) {
			libroRepository.findConArchivosByIdIn(
					disponibles.stream().map(Libro::getId).toList());
		}
		List<Libro> resultados = disponibles.stream()
				.filter(libro -> coincideEstado(libro, estado))
				.filter(libro -> coincideBusqueda(libro, terminosBusqueda))
				.sorted(Comparator
						.comparing(Libro::getTitulo, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(Libro::getId))
				.toList();

		long totalResultados = resultados.size();
		int totalPaginas = (int) ((totalResultados + tamano - 1) / tamano);
		long inicioCalculado = (long) pagina * tamano;
		int inicio = (int) Math.min(inicioCalculado, totalResultados);
		int fin = (int) Math.min(inicioCalculado + tamano, totalResultados);
		List<LibroResponse> libros = resultados.subList(inicio, fin).stream()
				.map(this::convertirAResponse)
				.toList();

		return new LibroPaginadoResponse(
				libros,
				pagina,
				tamano,
				totalResultados,
				totalPaginas,
				pagina == 0,
				totalPaginas == 0 || pagina >= totalPaginas - 1
		);
	}

	@Transactional(readOnly = true)
	public ResumenBibliotecaResponse obtenerResumen() {
		long total = libroRepository.contarDisponibles();
		long leidos = libroRepository.contarDisponiblesLeidos();
		return new ResumenBibliotecaResponse(total, leidos, total - leidos);
	}

	@Transactional(readOnly = true)
	public List<CopiaLibroResponse> obtenerCopias(Long id) {
		Libro libro = obtenerLibroConDetalles(id);
		return libro.getArchivos().stream()
				.sorted(Comparator
						.comparing(ArchivoLibro::getRuta, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(ArchivoLibro::getId))
				.map(this::convertirACopiaResponse)
				.toList();
	}

	@Transactional
	public LibroResponse cambiarEstadoLectura(Long id, boolean leido) {
		Libro libro = obtenerLibroConDetalles(id);
		libro.setLeido(leido);
		return convertirAResponse(libroRepository.save(libro));
	}

	private Libro obtenerLibroConDetalles(Long id) {
		Libro libro = libroRepository.findConAutoresById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException(
						"No existe ningún libro con el ID " + id));
		libroRepository.findConArchivosById(id);
		return libro;
	}

	private void validarPaginacion(int pagina, int tamano) {
		if (pagina < 0) {
			throw new IllegalArgumentException("La página no puede ser negativa");
		}
		if (tamano < 1 || tamano > TAMANO_MAXIMO) {
			throw new IllegalArgumentException("El tamaño de página debe estar entre 1 y 100");
		}
	}

	private boolean coincideEstado(Libro libro, EstadoLecturaFiltro estado) {
		return switch (estado) {
			case TODOS -> true;
			case LEIDOS -> libro.isLeido();
			case PENDIENTES -> !libro.isLeido();
		};
	}

	private boolean coincideBusqueda(Libro libro, List<String> terminos) {
		if (terminos.isEmpty()) {
			return true;
		}
		String textoBuscable = normalizador.normalizarTexto(
				libro.getTitulo() + " " + libro.getAutores().stream()
						.map(Autor::getNombre)
						.reduce("", (autores, autor) -> autores + " " + autor));
		return terminos.stream().allMatch(textoBuscable::contains);
	}

	private List<String> crearTerminosBusqueda(String busqueda) {
		if (busqueda == null || busqueda.isBlank()) {
			return List.of();
		}
		return Arrays.stream(normalizador.normalizarTexto(busqueda).split("\\s+"))
				.filter(termino -> !termino.isBlank())
				.distinct()
				.toList();
	}

	private LibroResponse convertirAResponse(Libro libro) {
		List<String> autores = libro.getAutores().stream()
				.map(Autor::getNombre)
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
		List<String> formatos = libro.getArchivos().stream()
				.map(ArchivoLibro::getExtension)
				.distinct()
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();

		return new LibroResponse(
				libro.getId(), libro.getTitulo(), libro.isLeido(),
				autores, formatos, libro.getArchivos().size());
	}

	private CopiaLibroResponse convertirACopiaResponse(ArchivoLibro archivo) {
		return new CopiaLibroResponse(
				archivo.getId(), archivo.getNombreArchivo(), archivo.getExtension(),
				archivo.getRuta(), archivo.getTamanioBytes(), archivo.getUltimaModificacion());
	}

}

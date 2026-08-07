package com.miguel.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.miguel.model.ArchivoLibro;
import com.miguel.model.Autor;
import com.miguel.model.Libro;
import com.miguel.repository.ArchivoLibroRepository;
import com.miguel.repository.AutorRepository;
import com.miguel.repository.LibroRepository;
import com.miguel.service.EscaneoLibrosService.ArchivoDetectado;
import com.miguel.service.EscaneoLibrosService.DetalleArchivoEscaneo;
import com.miguel.service.EscaneoLibrosService.EstadoArchivo;
import com.miguel.service.EscaneoLibrosService.ResultadoEscaneo;

@Service
public class SincronizacionLibrosService {

	private static final Pattern SUFIJO_COPIA = Pattern.compile(
			"\\s*\\[(?:duplicado|versi[oó]n)\\s+\\d+\\]\\s*$",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	private final LibroRepository libroRepository;
	private final AutorRepository autorRepository;
	private final ArchivoLibroRepository archivoLibroRepository;
	private final NormalizadorBiblioteca normalizador;

	public SincronizacionLibrosService(
			LibroRepository libroRepository,
			AutorRepository autorRepository,
			ArchivoLibroRepository archivoLibroRepository,
			NormalizadorBiblioteca normalizador) {
		this.libroRepository = libroRepository;
		this.autorRepository = autorRepository;
		this.archivoLibroRepository = archivoLibroRepository;
		this.normalizador = normalizador;
	}

	@Transactional(readOnly = true)
	public List<EstadoArchivo> cargarEstadoArchivos() {
		return archivoLibroRepository.findAll().stream()
				.map(archivo -> new EstadoArchivo(
						archivo.getId(), archivo.getRuta(), archivo.getTamanioBytes(),
						archivo.getUltimaModificacion(), archivo.getSha256()))
				.toList();
	}

	@Transactional
	public ResultadoEscaneo sincronizar(
			List<ArchivoDetectado> detectados,
			int archivosEncontrados,
			List<String> detallesErrores) {

		List<ArchivoLibro> registrados = archivoLibroRepository.findAll();
		Map<String, ArchivoLibro> porRuta = new HashMap<>();
		Map<ClaveContenido, List<ArchivoLibro>> porContenido = new HashMap<>();

		for (ArchivoLibro archivo : registrados) {
			porRuta.put(EscaneoLibrosService.normalizarRuta(archivo.getRuta()), archivo);
			if (archivo.getSha256() != null) {
				porContenido.computeIfAbsent(
						new ClaveContenido(archivo.getSha256(), archivo.getTamanioBytes()),
						clave -> new ArrayList<>()).add(archivo);
			}
		}

		Map<String, Autor> autoresPorClave = cargarAutores();
		Map<String, Libro> librosPorClave = cargarLibros();
		Set<String> rutasDetectadas = detectados.stream()
				.map(ArchivoDetectado::claveRuta)
				.collect(java.util.stream.Collectors.toSet());
		Set<Long> idsEmparejados = new HashSet<>();
		Contadores contadores = new Contadores();
		contadores.erroresLectura = detallesErrores.size();

		for (ArchivoDetectado detectado : detectados) {
			if (detectado.errorLectura()) {
				continue;
			}

			ArchivoLibro mismaRuta = porRuta.get(detectado.claveRuta());
			if (mismaRuta != null) {
				procesarMismaRuta(
						mismaRuta, detectado, idsEmparejados, porContenido,
						autoresPorClave, librosPorClave, contadores);
				continue;
			}

			procesarRutaNueva(
					detectado, registrados, rutasDetectadas, idsEmparejados, porContenido,
					autoresPorClave, librosPorClave, contadores
			);
		}

		List<ArchivoLibro> desaparecidos = registrados.stream()
				.filter(archivo -> !idsEmparejados.contains(archivo.getId()))
				.filter(archivo -> !rutasDetectadas.contains(
						EscaneoLibrosService.normalizarRuta(archivo.getRuta())))
				.toList();
		desaparecidos.forEach(archivo -> contadores.detallesArchivosDesaparecidos.add(
				new DetalleArchivoEscaneo(archivo.getNombreArchivo(), archivo.getRuta())));

		archivoLibroRepository.deleteAll(desaparecidos);

		return contadores.aResultado(
				archivosEncontrados,
				List.copyOf(detallesErrores)
		);
	}

	private void procesarMismaRuta(
			ArchivoLibro archivo,
			ArchivoDetectado detectado,
			Set<Long> idsEmparejados,
			Map<ClaveContenido, List<ArchivoLibro>> porContenido,
			Map<String, Autor> autoresPorClave,
			Map<String, Libro> librosPorClave,
			Contadores contadores) {

		boolean cambiado = archivo.getTamanioBytes() != detectado.tamanioBytes()
				|| !archivo.getUltimaModificacion().equals(detectado.ultimaModificacion());
		boolean faltabaHash = archivo.getSha256() == null;
		ClaveContenido contenidoAnterior = archivo.getSha256() == null
				? null
				: new ClaveContenido(archivo.getSha256(), archivo.getTamanioBytes());

		if (cambiado && contenidoAnterior != null) {
			List<ArchivoLibro> grupoAnterior = porContenido.get(contenidoAnterior);
			if (grupoAnterior != null) {
				grupoAnterior.remove(archivo);
			}
		}

		if (!archivo.getNombreArchivo().equals(detectado.nombreArchivo())) {
			actualizarAsociacionSegunNombre(
					archivo, detectado, analizarNombre(detectado.nombreArchivo()),
					autoresPorClave, librosPorClave, contadores);
		}

		archivo.setNombreArchivo(detectado.nombreArchivo());
		archivo.setExtension(detectado.extension());
		archivo.setTamanioBytes(detectado.tamanioBytes());
		archivo.setUltimaModificacion(detectado.ultimaModificacion());
		archivo.setSha256(detectado.sha256());
		archivoLibroRepository.save(archivo);
		idsEmparejados.add(archivo.getId());

		if (cambiado) {
			contadores.archivosModificados++;
		} else {
			contadores.archivosYaRegistrados++;
		}

		if (cambiado || faltabaHash) {
			porContenido.computeIfAbsent(
					new ClaveContenido(detectado.sha256(), detectado.tamanioBytes()),
					clave -> new ArrayList<>()).add(archivo);
		}
	}

	private void procesarRutaNueva(
			ArchivoDetectado detectado,
			List<ArchivoLibro> registrados,
			Set<String> rutasDetectadas,
			Set<Long> idsEmparejados,
			Map<ClaveContenido, List<ArchivoLibro>> porContenido,
			Map<String, Autor> autoresPorClave,
			Map<String, Libro> librosPorClave,
			Contadores contadores) {

		ClaveContenido claveContenido = new ClaveContenido(detectado.sha256(), detectado.tamanioBytes());
		List<ArchivoLibro> coincidencias = porContenido.getOrDefault(claveContenido, List.of());
		DatosNombre datosNombre = analizarNombre(detectado.nombreArchivo());
		List<ArchivoLibro> candidatosMovidos = coincidencias.stream()
				.filter(archivo -> !idsEmparejados.contains(archivo.getId()))
				.filter(archivo -> !rutasDetectadas.contains(
						EscaneoLibrosService.normalizarRuta(archivo.getRuta())))
				.toList();
		ArchivoLibro movido = elegirMovido(candidatosMovidos, detectado, datosNombre);

		if (movido != null) {
			actualizarAsociacionSegunNombre(
					movido, detectado, datosNombre,
					autoresPorClave, librosPorClave, contadores);
			actualizarDatosArchivo(movido, detectado);
			archivoLibroRepository.save(movido);
			idsEmparejados.add(movido.getId());
			contadores.archivosMovidosRenombrados++;
			return;
		}

		ArchivoLibro copiaDe;
		if (datosNombre == null) {
			// Compatibilidad: una copia física idéntica ya reconocida puede conservar
			// el libro de la otra ruta aunque su nombre aislado no sea interpretable.
			copiaDe = coincidencias.stream().findFirst().orElse(null);
		} else {
			String claveNombre = crearClaveLibro(datosNombre);
			copiaDe = coincidencias.stream()
					.filter(archivo -> crearClaveLibro(archivo.getLibro()).equals(claveNombre))
					.findFirst().orElse(null);
		}
		if (copiaDe != null) {
			ArchivoLibro copia = crearArchivo(detectado, copiaDe.getLibro());
			archivoLibroRepository.save(copia);
			copiaDe.getLibro().getArchivos().add(copia);
			idsEmparejados.add(copia.getId());
			registrados.add(copia);
			porContenido.computeIfAbsent(claveContenido, clave -> new ArrayList<>()).add(copia);
			contadores.registrarArchivoNuevo(detectado);
			contadores.copiasIdenticasNuevas++;
			return;
		}

		if (datosNombre == null) {
			registrarNombreInvalido(detectado, contadores);
			return;
		}

		Libro libro = obtenerOCrearLibro(
				datosNombre, false, autoresPorClave, librosPorClave, contadores);

		ArchivoLibro nuevo = crearArchivo(detectado, libro);
		libro.getArchivos().add(nuevo);
		libroRepository.save(libro);
		idsEmparejados.add(nuevo.getId());
		registrados.add(nuevo);
		porContenido.computeIfAbsent(claveContenido, clave -> new ArrayList<>()).add(nuevo);
		contadores.registrarArchivoNuevo(detectado);
	}

	private ArchivoLibro elegirMovido(
			List<ArchivoLibro> candidatos,
			ArchivoDetectado detectado,
			DatosNombre datosNombre) {
		if (datosNombre != null) {
			String claveDetectada = crearClaveLibro(datosNombre);
			ArchivoLibro mismaIdentidad = candidatos.stream()
					.filter(archivo -> crearClaveLibro(archivo.getLibro()).equals(claveDetectada))
					.findFirst().orElse(null);
			if (mismaIdentidad != null) {
				return mismaIdentidad;
			}
		}
		return candidatos.stream()
				.filter(archivo -> archivo.getNombreArchivo().equals(detectado.nombreArchivo()))
				.findFirst()
				.orElse(candidatos.isEmpty() ? null : candidatos.getFirst());
	}

	private void actualizarAsociacionSegunNombre(
			ArchivoLibro archivo,
			ArchivoDetectado detectado,
			DatosNombre datosNombre,
			Map<String, Autor> autoresPorClave,
			Map<String, Libro> librosPorClave,
			Contadores contadores) {
		if (datosNombre == null) {
			registrarNombreInvalido(detectado, contadores);
			return;
		}
		if (crearClaveLibro(archivo.getLibro()).equals(crearClaveLibro(datosNombre))) {
			return;
		}

		Libro origen = archivo.getLibro();
		Libro destino = obtenerOCrearLibro(
				datosNombre, origen.isLeido(), autoresPorClave, librosPorClave, contadores);
		archivo.setLibro(destino);
		origen.getArchivos().remove(archivo);
		destino.getArchivos().add(archivo);
	}

	private Libro obtenerOCrearLibro(
			DatosNombre datosNombre,
			boolean leidoInicial,
			Map<String, Autor> autoresPorClave,
			Map<String, Libro> librosPorClave,
			Contadores contadores) {
		String claveLibro = crearClaveLibro(datosNombre);
		Libro existente = librosPorClave.get(claveLibro);
		if (existente != null) {
			return existente;
		}

		Set<Autor> autores = obtenerOCrearAutores(datosNombre.autores(), autoresPorClave);
		Libro nuevo = new Libro();
		nuevo.setTitulo(datosNombre.titulo());
		nuevo.setLeido(leidoInicial);
		nuevo.setAutores(autores);
		nuevo.setArchivos(new ArrayList<>());
		for (Autor autor : autores) {
			autor.getLibros().add(nuevo);
		}
		libroRepository.save(nuevo);
		librosPorClave.put(claveLibro, nuevo);
		contadores.librosNuevos++;
		return nuevo;
	}

	private String crearClaveLibro(DatosNombre datosNombre) {
		return normalizador.crearClaveLibro(datosNombre.titulo(), datosNombre.autores());
	}

	private String crearClaveLibro(Libro libro) {
		return normalizador.crearClaveLibro(
				libro.getTitulo(), libro.getAutores().stream().map(Autor::getNombre).toList());
	}

	private void registrarNombreInvalido(ArchivoDetectado detectado, Contadores contadores) {
		contadores.nombresInvalidos++;
		contadores.detallesInvalidos.add(
				detectado.ruta() + " | No cumple el formato Autor - Título");
	}

	private ArchivoLibro crearArchivo(ArchivoDetectado detectado, Libro libro) {
		ArchivoLibro archivo = new ArchivoLibro();
		actualizarDatosArchivo(archivo, detectado);
		archivo.setLibro(libro);
		return archivo;
	}

	private void actualizarDatosArchivo(ArchivoLibro archivo, ArchivoDetectado detectado) {
		archivo.setRuta(detectado.ruta());
		archivo.setNombreArchivo(detectado.nombreArchivo());
		archivo.setExtension(detectado.extension());
		archivo.setTamanioBytes(detectado.tamanioBytes());
		archivo.setUltimaModificacion(detectado.ultimaModificacion());
		archivo.setSha256(detectado.sha256());
	}

	private DatosNombre analizarNombre(String nombreArchivo) {
		int punto = nombreArchivo.lastIndexOf('.');
		if (punto <= 0) {
			return null;
		}

		String sinExtension = nombreArchivo.substring(0, punto).trim();
		int separador = sinExtension.indexOf(" - ");
		if (separador <= 0) {
			return null;
		}

		String bloqueAutores = sinExtension.substring(0, separador).trim();
		String titulo = SUFIJO_COPIA.matcher(sinExtension.substring(separador + 3).trim())
				.replaceFirst("").trim();
		if (bloqueAutores.isBlank() || titulo.isBlank()) {
			return null;
		}

		String[] separados = bloqueAutores.split(",", -1);
		if (Arrays.stream(separados).anyMatch(String::isBlank)) {
			return null;
		}

		Map<String, String> sinRepetir = new LinkedHashMap<>();
		for (String autor : separados) {
			String limpio = autor.trim();
			sinRepetir.putIfAbsent(normalizador.normalizarTexto(limpio), limpio);
		}
		return new DatosNombre(titulo, new ArrayList<>(sinRepetir.values()));
	}

	private Set<Autor> obtenerOCrearAutores(
			List<String> nombres,
			Map<String, Autor> autoresPorClave) {
		Set<Autor> autores = new LinkedHashSet<>();
		for (String nombre : nombres) {
			String clave = normalizador.normalizarTexto(nombre);
			Autor autor = autoresPorClave.get(clave);
			if (autor == null) {
				autor = new Autor();
				autor.setNombre(nombre);
				autor.setLibros(new LinkedHashSet<>());
				autoresPorClave.put(clave, autor);
			}
			autores.add(autor);
		}
		return autores;
	}

	private Map<String, Autor> cargarAutores() {
		Map<String, Autor> resultado = new LinkedHashMap<>();
		for (Autor autor : autorRepository.findAll()) {
			resultado.putIfAbsent(normalizador.normalizarTexto(autor.getNombre()), autor);
		}
		return resultado;
	}

	private Map<String, Libro> cargarLibros() {
		Map<String, Libro> resultado = new LinkedHashMap<>();
		for (Libro libro : libroRepository.findAll()) {
			resultado.putIfAbsent(normalizador.crearClaveLibro(
					libro.getTitulo(), libro.getAutores().stream().map(Autor::getNombre).toList()), libro);
		}
		return resultado;
	}

	private record DatosNombre(String titulo, List<String> autores) {
	}

	private record ClaveContenido(String sha256, long tamanioBytes) {
	}

	private static class Contadores {
		private int librosNuevos;
		private int archivosYaRegistrados;
		private int archivosMovidosRenombrados;
		private int copiasIdenticasNuevas;
		private int archivosModificados;
		private int nombresInvalidos;
		private int erroresLectura;
		private final List<String> detallesInvalidos = new ArrayList<>();
		private final List<DetalleArchivoEscaneo> detallesArchivosNuevos = new ArrayList<>();
		private final List<DetalleArchivoEscaneo> detallesArchivosDesaparecidos = new ArrayList<>();

		private void registrarArchivoNuevo(ArchivoDetectado detectado) {
			detallesArchivosNuevos.add(
					new DetalleArchivoEscaneo(detectado.nombreArchivo(), detectado.ruta()));
		}

		private ResultadoEscaneo aResultado(int encontrados, List<String> detallesErrores) {
			Comparator<DetalleArchivoEscaneo> ordenDetalles = Comparator
					.comparing(DetalleArchivoEscaneo::ruta, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(DetalleArchivoEscaneo::nombreArchivo, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(DetalleArchivoEscaneo::ruta)
					.thenComparing(DetalleArchivoEscaneo::nombreArchivo);
			detallesArchivosNuevos.sort(ordenDetalles);
			detallesArchivosDesaparecidos.sort(ordenDetalles);

			return new ResultadoEscaneo(
					encontrados, detallesArchivosNuevos.size(), librosNuevos, archivosYaRegistrados,
					archivosMovidosRenombrados, copiasIdenticasNuevas, archivosModificados,
					detallesArchivosDesaparecidos.size(), nombresInvalidos, erroresLectura,
					List.copyOf(detallesInvalidos), detallesErrores,
					List.copyOf(detallesArchivosNuevos),
					List.copyOf(detallesArchivosDesaparecidos)
			);
		}
	}
}

package com.miguel.service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class EscaneoLibrosService {

	private static final Set<String> EXTENSIONES_SOPORTADAS = Set.of(
			"pdf", "epub", "doc", "docx", "rtf", "mobi",
			"azw", "azw3", "fb2", "txt", "odt"
	);

	private final Sha256Service sha256Service;
	private final SincronizacionLibrosService sincronizacionLibrosService;
	private final CoordinadorBiblioteca coordinadorBiblioteca;

	public EscaneoLibrosService(
			Sha256Service sha256Service,
			SincronizacionLibrosService sincronizacionLibrosService,
			CoordinadorBiblioteca coordinadorBiblioteca) {
		this.sha256Service = sha256Service;
		this.sincronizacionLibrosService = sincronizacionLibrosService;
		this.coordinadorBiblioteca = coordinadorBiblioteca;
	}

	public ResultadoEscaneo escanearCarpeta(String rutaCarpeta) {
		return coordinadorBiblioteca.ejecutarExclusivo(() -> escanearSinBloqueo(rutaCarpeta));
	}

	private ResultadoEscaneo escanearSinBloqueo(String rutaCarpeta) {
		Path carpetaRaiz = validarCarpetaRaiz(rutaCarpeta);
		Map<String, EstadoArchivo> estadoPorRuta = new HashMap<>();

		for (EstadoArchivo estado : sincronizacionLibrosService.cargarEstadoArchivos()) {
			estadoPorRuta.put(normalizarRuta(estado.ruta()), estado);
		}

		Recorrido recorrido = recorrerCarpeta(carpetaRaiz, estadoPorRuta);

		return sincronizacionLibrosService.sincronizar(
				recorrido.archivos(),
				recorrido.archivosEncontrados(),
				recorrido.detallesErrores()
		);
	}

	private Path validarCarpetaRaiz(String rutaCarpeta) {
		if (rutaCarpeta == null || rutaCarpeta.isBlank()) {
			throw new IllegalArgumentException("La ruta de la carpeta no puede estar vacía");
		}

		final Path carpetaRaiz;
		try {
			carpetaRaiz = Path.of(rutaCarpeta).toAbsolutePath().normalize();
		} catch (InvalidPathException e) {
			throw new IllegalArgumentException("La ruta configurada no es válida", e);
		}

		if (!Files.exists(carpetaRaiz)) {
			throw new IllegalArgumentException("La carpeta configurada no existe: " + carpetaRaiz);
		}

		if (!Files.isDirectory(carpetaRaiz)) {
			throw new IllegalArgumentException("La ruta configurada no es una carpeta: " + carpetaRaiz);
		}

		if (!Files.isReadable(carpetaRaiz)) {
			throw new IllegalArgumentException("No se puede acceder a la carpeta configurada: " + carpetaRaiz);
		}

		return carpetaRaiz;
	}

	private Recorrido recorrerCarpeta(
			Path carpetaRaiz,
			Map<String, EstadoArchivo> estadoPorRuta) {

		List<ArchivoDetectado> archivos = new ArrayList<>();
		List<String> detallesErrores = new ArrayList<>();
		int[] encontrados = {0};

		try {
			Files.walkFileTree(carpetaRaiz, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path ruta, BasicFileAttributes atributos) {
					if (!atributos.isRegularFile() || !esFormatoSoportado(ruta)) {
						return FileVisitResult.CONTINUE;
					}

					encontrados[0]++;
					archivos.add(inspeccionarArchivo(ruta, atributos, estadoPorRuta, detallesErrores));
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path ruta, IOException error) throws IOException {
					throw new IOException("No se pudo recorrer " + ruta + ": " + error.getMessage(), error);
				}
			});
		} catch (IOException | SecurityException e) {
			throw new IllegalStateException(
					"No se pudo recorrer completamente la carpeta configurada. No se ha modificado la biblioteca.",
					e
			);
		}

		archivos.sort(Comparator.comparing(ArchivoDetectado::claveRuta));
		return new Recorrido(List.copyOf(archivos), encontrados[0], List.copyOf(detallesErrores));
	}

	private ArchivoDetectado inspeccionarArchivo(
			Path ruta,
			BasicFileAttributes atributos,
			Map<String, EstadoArchivo> estadoPorRuta,
			List<String> detallesErrores) {

		String rutaCompleta = ruta.toAbsolutePath().normalize().toString();
		String claveRuta = normalizarRuta(rutaCompleta);
		String nombre = ruta.getFileName().toString();
		String extension = obtenerExtension(nombre);
		long tamanio = atributos.size();
		Instant modificacion = normalizarModificacion(atributos.lastModifiedTime().toInstant());
		EstadoArchivo registrado = estadoPorRuta.get(claveRuta);
		boolean necesitaHash = registrado == null
				|| registrado.sha256() == null
				|| registrado.tamanioBytes() != tamanio
				|| !registrado.ultimaModificacion().equals(modificacion);

		if (!necesitaHash) {
			return new ArchivoDetectado(
					rutaCompleta, claveRuta, nombre, extension, tamanio, modificacion,
					registrado.sha256(), false
			);
		}

		try {
			String sha256 = sha256Service.calcular(ruta);
			BasicFileAttributes despues = Files.readAttributes(ruta, BasicFileAttributes.class);

			if (despues.size() != tamanio
					|| !normalizarModificacion(despues.lastModifiedTime().toInstant()).equals(modificacion)) {
				throw new IOException("El archivo cambió mientras se estaba leyendo");
			}

			return new ArchivoDetectado(
					rutaCompleta, claveRuta, nombre, extension, tamanio, modificacion,
					sha256, false
			);
		} catch (IOException | SecurityException e) {
			detallesErrores.add(rutaCompleta + " | " + mensajeSeguro(e));
			return new ArchivoDetectado(
					rutaCompleta, claveRuta, nombre, extension, tamanio, modificacion,
					null, true
			);
		}
	}

	private String mensajeSeguro(Exception error) {
		return error.getMessage() == null || error.getMessage().isBlank()
				? "No se pudo leer el archivo"
				: error.getMessage();
	}

	private boolean esFormatoSoportado(Path ruta) {
		return EXTENSIONES_SOPORTADAS.contains(obtenerExtension(ruta.getFileName().toString()));
	}

	private String obtenerExtension(String nombreArchivo) {
		int punto = nombreArchivo.lastIndexOf('.');
		if (punto < 0 || punto == nombreArchivo.length() - 1) {
			return "";
		}
		return nombreArchivo.substring(punto + 1).toLowerCase(Locale.ROOT);
	}

	private Instant normalizarModificacion(Instant modificacion) {
		return modificacion.truncatedTo(ChronoUnit.MICROS);
	}

	static String normalizarRuta(String ruta) {
		return Path.of(ruta).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
	}

	public record EstadoArchivo(
			Long id,
			String ruta,
			long tamanioBytes,
			Instant ultimaModificacion,
			String sha256) {
	}

	public record ArchivoDetectado(
			String ruta,
			String claveRuta,
			String nombreArchivo,
			String extension,
			long tamanioBytes,
			Instant ultimaModificacion,
			String sha256,
			boolean errorLectura) {
	}

	private record Recorrido(
			List<ArchivoDetectado> archivos,
			int archivosEncontrados,
			List<String> detallesErrores) {
	}

	public record ResultadoEscaneo(
			int archivosEncontrados,
			int archivosNuevos,
			int librosNuevos,
			int archivosYaRegistrados,
			int archivosMovidosRenombrados,
			int copiasIdenticasNuevas,
			int archivosModificados,
			int archivosDesaparecidosEliminados,
			int nombresInvalidos,
			int erroresLectura,
			List<String> detallesInvalidos,
			List<String> detallesErrores,
			List<DetalleArchivoEscaneo> detallesArchivosNuevos,
			List<DetalleArchivoEscaneo> detallesArchivosDesaparecidos) {
	}

	public record DetalleArchivoEscaneo(String nombreArchivo, String ruta) {
	}
}

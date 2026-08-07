package com.miguel.service;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.miguel.dto.ActualizarLibroRequest;
import com.miguel.dto.ActualizarLibroResponse;
import com.miguel.dto.ArchivoRenombradoResponse;
import com.miguel.dto.LibroResponse;
import com.miguel.exception.ConflictoOperacionException;
import com.miguel.exception.OperacionArchivosException;
import com.miguel.service.ActualizacionLibroDatosService.CambioArchivo;
import com.miguel.service.ActualizacionLibroDatosService.CopiaActual;
import com.miguel.service.ActualizacionLibroDatosService.DatosLibro;

@Service
public class RenombradoLibroService {
	private static final Logger LOGGER = LoggerFactory.getLogger(RenombradoLibroService.class);
	private static final int LONGITUD_MAXIMA_COMPONENTE = 255;
	private static final Pattern CARACTER_INVALIDO = Pattern.compile("[\\x00-\\x1f<>:\"/\\\\|?*]");
	private static final Pattern NOMBRE_RESERVADO = Pattern.compile(
			"(?i)^(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?$");
	private static final Pattern SUFIJO_COPIA = Pattern.compile(
			"\\s*(\\[(?:duplicado|versi[oó]n)\\s+\\d+\\])\\s*$",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

	private final ActualizacionLibroDatosService datosService;
	private final NormalizadorBiblioteca normalizador;
	private final OperacionesArchivos archivos;
	private final CoordinadorBiblioteca coordinador;

	public RenombradoLibroService(
			ActualizacionLibroDatosService datosService,
			NormalizadorBiblioteca normalizador,
			OperacionesArchivos archivos,
			CoordinadorBiblioteca coordinador) {
		this.datosService = datosService;
		this.normalizador = normalizador;
		this.archivos = archivos;
		this.coordinador = coordinador;
	}

	public ActualizarLibroResponse actualizar(Long id, ActualizarLibroRequest request) {
		DatosEntrada entrada = validarEntrada(request);
		return coordinador.ejecutarExclusivo(() -> actualizarSinBloqueo(id, entrada));
	}

	private ActualizarLibroResponse actualizarSinBloqueo(Long id, DatosEntrada entrada) {
		DatosLibro libro = datosService.preparar(id, entrada.titulo(), entrada.autores());
		List<CambioArchivo> cambios = planificar(libro.copias(), entrada);
		List<Movimiento> movimientos = cambios.stream().map(Movimiento::new).toList();

		try {
			ejecutarRenombrado(movimientos);
			LibroResponse libroActualizado = datosService.aplicar(
					id, entrada.titulo(), entrada.autores(), cambios);
			List<ArchivoRenombradoResponse> respuestas = cambios.stream()
					.map(cambio -> new ArchivoRenombradoResponse(
							cambio.idArchivo(), cambio.nombreAnterior(), cambio.nombreNuevo(),
							cambio.rutaAnterior(), cambio.rutaNueva()))
					.toList();
			return new ActualizarLibroResponse(libroActualizado, respuestas);
		} catch (RuntimeException error) {
			boolean restaurado = restaurar(movimientos);
			if (!restaurado) {
				throw new OperacionArchivosException(
						"No se pudo completar el renombrado y algún archivo puede requerir revisión manual.",
						error);
			}
			throw new OperacionArchivosException(
					"No se pudo completar el renombrado. Los nombres originales se han restaurado.", error);
		}
	}

	private DatosEntrada validarEntrada(ActualizarLibroRequest request) {
		if (request == null || request.getTitulo() == null || request.getTitulo().trim().isEmpty()) {
			throw new IllegalArgumentException("El título es obligatorio");
		}
		String titulo = request.getTitulo().trim();
		validarComponenteWindows(titulo, "El título");
		if (request.getAutores() == null || request.getAutores().isEmpty()) {
			throw new IllegalArgumentException("Debe indicarse al menos un autor");
		}

		Map<String, String> autoresUnicos = new LinkedHashMap<>();
		for (String valor : request.getAutores()) {
			if (valor == null || valor.trim().isEmpty()) {
				throw new IllegalArgumentException("Los autores no pueden estar vacíos");
			}
			String autor = valor.trim();
			validarComponenteWindows(autor, "El autor " + autor);
			autoresUnicos.putIfAbsent(normalizador.normalizarTexto(autor), autor);
		}
		if (autoresUnicos.isEmpty()) {
			throw new IllegalArgumentException("Debe indicarse al menos un autor");
		}
		return new DatosEntrada(titulo, List.copyOf(autoresUnicos.values()));
	}

	private void validarComponenteWindows(String valor, String etiqueta) {
		if (CARACTER_INVALIDO.matcher(valor).find()) {
			throw new IllegalArgumentException(etiqueta + " contiene caracteres no válidos para Windows");
		}
		if (valor.endsWith(".") || valor.endsWith(" ")) {
			throw new IllegalArgumentException(etiqueta + " no puede terminar en punto ni espacio");
		}
		if (NOMBRE_RESERVADO.matcher(valor).matches()) {
			throw new IllegalArgumentException(etiqueta + " utiliza un nombre reservado de Windows");
		}
	}

	private List<CambioArchivo> planificar(List<CopiaActual> copias, DatosEntrada entrada) {
		String nombreBase = String.join(", ", entrada.autores()) + " - " + entrada.titulo();
		Set<String> rutasOrigen = new HashSet<>();
		for (CopiaActual copia : copias) {
			Path origen = convertirRuta(copia.ruta());
			if (!archivos.existe(origen) || !archivos.esArchivoRegular(origen)) {
				throw new ConflictoOperacionException(
						"No se encuentra el archivo de origen registrado: " + copia.ruta());
			}
			Path padre = origen.getParent();
			if (padre == null || !archivos.esDirectorio(padre)) {
				throw new ConflictoOperacionException(
						"No se encuentra la carpeta del archivo registrado: " + copia.ruta());
			}
			rutasOrigen.add(claveRuta(origen));
		}

		Set<String> destinosReservados = new HashSet<>();
		List<DestinoPlaneado> destinos = new ArrayList<>();
		for (CopiaActual copia : copias) {
			Path origen = convertirRuta(copia.ruta());
			String extension = extraerExtension(copia.nombreArchivo());
			String sufijo = extraerSufijo(copia.nombreArchivo());
			String nombreFinal = construirNombre(nombreBase, sufijo, extension);
			Path destino = origen.resolveSibling(nombreFinal);

			if (!destinosReservados.add(claveRuta(destino))) {
				int numero = 2;
				do {
					nombreFinal = construirNombre(nombreBase, "[duplicado " + numero++ + "]", extension);
					destino = origen.resolveSibling(nombreFinal);
				} while (!destinosReservados.add(claveRuta(destino)));
			}
			validarLongitud(nombreFinal);
			destinos.add(new DestinoPlaneado(copia, origen, destino, nombreFinal));
		}

		for (DestinoPlaneado destino : destinos) {
			if (archivos.existe(destino.rutaNueva())
					&& !rutasOrigen.contains(claveRuta(destino.rutaNueva()))) {
				throw new ConflictoOperacionException(
						"Ya existe un archivo en el destino " + destino.rutaNueva());
			}
		}

		List<CambioArchivo> cambios = new ArrayList<>();
		Set<String> temporales = new HashSet<>(destinosReservados);
		for (DestinoPlaneado destino : destinos) {
			if (destino.rutaAnterior().toString().equals(destino.rutaNueva().toString())) {
				continue;
			}
			Path temporal;
			do {
				temporal = destino.rutaAnterior().resolveSibling(
						".biblioteca-personal-renombrado-" + UUID.randomUUID() + ".tmp");
			} while (archivos.existe(temporal) || !temporales.add(claveRuta(temporal)));
			cambios.add(new CambioArchivo(
					destino.copia().id(), destino.copia().nombreArchivo(),
					temporal.getFileName().toString(), destino.nombreNuevo(),
					destino.rutaAnterior().toString(), temporal.toString(), destino.rutaNueva().toString()));
		}
		return List.copyOf(cambios);
	}

	private void ejecutarRenombrado(List<Movimiento> movimientos) {
		try {
			for (Movimiento movimiento : movimientos) {
				archivos.mover(movimiento.origen, movimiento.temporal);
				movimiento.estado = EstadoMovimiento.TEMPORAL;
			}
			for (Movimiento movimiento : movimientos) {
				archivos.mover(movimiento.temporal, movimiento.destino);
				movimiento.estado = EstadoMovimiento.FINAL;
			}
		} catch (IOException | SecurityException error) {
			throw new OperacionArchivosException("Falló un movimiento de archivo", error);
		}
	}

	private boolean restaurar(List<Movimiento> movimientos) {
		boolean correcto = true;
		for (int indice = movimientos.size() - 1; indice >= 0; indice--) {
			Movimiento movimiento = movimientos.get(indice);
			if (movimiento.estado != EstadoMovimiento.FINAL) continue;
			try {
				archivos.mover(movimiento.destino, movimiento.temporal);
				movimiento.estado = EstadoMovimiento.TEMPORAL;
			} catch (Exception error) {
				correcto = false;
				LOGGER.error("No se pudo evacuar {} a {} durante la restauración",
						movimiento.destino, movimiento.temporal, error);
			}
		}
		for (int indice = movimientos.size() - 1; indice >= 0; indice--) {
			Movimiento movimiento = movimientos.get(indice);
			if (movimiento.estado != EstadoMovimiento.TEMPORAL) continue;
			try {
				archivos.mover(movimiento.temporal, movimiento.origen);
				movimiento.estado = EstadoMovimiento.ORIGINAL;
			} catch (Exception error) {
				correcto = false;
				LOGGER.error("No se pudo restaurar {} a {}",
						movimiento.temporal, movimiento.origen, error);
			}
		}
		return correcto;
	}

	private String construirNombre(String base, String sufijo, String extension) {
		return base + (sufijo.isEmpty() ? "" : " " + sufijo) + extension;
	}

	private String extraerSufijo(String nombre) {
		int punto = nombre.lastIndexOf('.');
		String base = punto > 0 ? nombre.substring(0, punto) : nombre;
		Matcher matcher = SUFIJO_COPIA.matcher(base);
		return matcher.find() ? matcher.group(1) : "";
	}

	private String extraerExtension(String nombre) {
		int punto = nombre.lastIndexOf('.');
		if (punto < 0 || punto == nombre.length() - 1) {
			throw new ConflictoOperacionException(
					"El archivo registrado no tiene una extensión válida: " + nombre);
		}
		return nombre.substring(punto);
	}

	private void validarLongitud(String nombre) {
		if (nombre.length() > LONGITUD_MAXIMA_COMPONENTE) {
			throw new IllegalArgumentException(
					"El nombre de archivo resultante supera el límite de 255 caracteres de Windows");
		}
	}

	private Path convertirRuta(String ruta) {
		try {
			return Path.of(ruta).toAbsolutePath().normalize();
		} catch (InvalidPathException error) {
			throw new ConflictoOperacionException("La ruta registrada no es válida: " + ruta);
		}
	}

	private String claveRuta(Path ruta) {
		return ruta.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
	}

	private record DatosEntrada(String titulo, List<String> autores) {
	}

	private record DestinoPlaneado(
			CopiaActual copia, Path rutaAnterior, Path rutaNueva, String nombreNuevo) {
	}

	private enum EstadoMovimiento { ORIGINAL, TEMPORAL, FINAL }

	private static class Movimiento {
		private final Path origen;
		private final Path temporal;
		private final Path destino;
		private EstadoMovimiento estado = EstadoMovimiento.ORIGINAL;

		private Movimiento(CambioArchivo cambio) {
			origen = Path.of(cambio.rutaAnterior());
			temporal = Path.of(cambio.rutaTemporal());
			destino = Path.of(cambio.rutaNueva());
		}
	}
}

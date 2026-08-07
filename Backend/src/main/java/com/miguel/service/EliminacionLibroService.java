package com.miguel.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.miguel.dto.CopiaEliminadaResponse;
import com.miguel.dto.ResultadoEliminacionResponse;
import com.miguel.exception.ConflictoOperacionException;
import com.miguel.exception.EliminacionParcialException;
import com.miguel.exception.OperacionPapeleraException;
import com.miguel.service.EliminacionLibroDatosService.ArchivoPreparado;
import com.miguel.service.EliminacionLibroDatosService.PreparacionEliminacion;

@Service
public class EliminacionLibroService {
	private final EliminacionLibroDatosService datosService;
	private final PapeleraService papeleraService;
	private final CoordinadorBiblioteca coordinador;

	public EliminacionLibroService(
			EliminacionLibroDatosService datosService,
			PapeleraService papeleraService,
			CoordinadorBiblioteca coordinador) {
		this.datosService = datosService;
		this.papeleraService = papeleraService;
		this.coordinador = coordinador;
	}

	public ResultadoEliminacionResponse eliminarCopia(Long libroId, Long archivoId) {
		return coordinador.ejecutarExclusivo(() -> ejecutar(
				datosService.prepararUna(libroId, archivoId)));
	}

	public ResultadoEliminacionResponse eliminarTodas(Long libroId) {
		return coordinador.ejecutarExclusivo(() -> ejecutar(
				datosService.prepararTodas(libroId)));
	}

	private ResultadoEliminacionResponse ejecutar(PreparacionEliminacion preparacion) {
		List<ArchivoValidado> validados = validarTodos(preparacion);
		List<CopiaEliminadaResponse> eliminadas = new ArrayList<>();
		int restantes = preparacion.copiasIniciales();

		for (ArchivoValidado validado : validados) {
			try {
				papeleraService.enviar(validado.rutaReal());
				if (Files.exists(validado.rutaReal(), LinkOption.NOFOLLOW_LINKS)) {
					throw new OperacionPapeleraException(
							"Windows no confirmó el envío del archivo a la Papelera de reciclaje.");
				}
				restantes = datosService.eliminarConfirmado(
						preparacion.libroId(), validado.archivo().id());
				eliminadas.add(new CopiaEliminadaResponse(
						validado.archivo().id(), validado.archivo().nombreArchivo(),
						validado.archivo().ruta()));
			} catch (RuntimeException error) {
				ResultadoEliminacionResponse parcial = resultado(
						preparacion.libroId(), eliminadas, restantes);
				throw new EliminacionParcialException(
						"No se pudieron enviar todas las copias a la Papelera de reciclaje. "
						+ "Las copias confirmadas antes del fallo sí se eliminaron.",
						parcial, error);
			}
		}
		return resultado(preparacion.libroId(), eliminadas, restantes);
	}

	private ResultadoEliminacionResponse resultado(
			Long libroId, List<CopiaEliminadaResponse> eliminadas, int restantes) {
		return new ResultadoEliminacionResponse(
				libroId, List.copyOf(eliminadas), restantes, restantes > 0);
	}

	private List<ArchivoValidado> validarTodos(PreparacionEliminacion preparacion) {
		Path raiz = convertirRutaAbsoluta(
				preparacion.rutaConfigurada(), "La ruta configurada de la biblioteca no es válida.");
		final Path raizReal;
		try {
			raizReal = raiz.toRealPath();
		} catch (IOException | SecurityException error) {
			throw new ConflictoOperacionException(
					"No se puede acceder a la carpeta configurada de la biblioteca.");
		}

		List<ArchivoValidado> resultado = new ArrayList<>();
		for (ArchivoPreparado archivo : preparacion.archivos()) {
			Path registrada = convertirRutaAbsoluta(
					archivo.ruta(), "La ruta registrada de la copia no es válida.");
			if (!registrada.equals(registrada.normalize())) {
				throw new ConflictoOperacionException(
						"La ruta registrada contiene segmentos no permitidos.");
			}
			registrada = registrada.normalize();
			if (!registrada.startsWith(raiz)) {
				throw new ConflictoOperacionException(
						"La copia está fuera de la carpeta configurada de la biblioteca.");
			}
			Path nombre = registrada.getFileName();
			if (nombre == null || !nombre.toString().equalsIgnoreCase(archivo.nombreArchivo())) {
				throw new ConflictoOperacionException(
						"La ruta registrada no corresponde al nombre de la copia.");
			}
			if (!Files.exists(registrada, LinkOption.NOFOLLOW_LINKS)) {
				throw new ConflictoOperacionException(
						"El archivo ya no se encuentra en su ubicación registrada. "
						+ "Actualiza la biblioteca para sincronizar los cambios.");
			}
			if (Files.isSymbolicLink(registrada)
					|| !Files.isRegularFile(registrada, LinkOption.NOFOLLOW_LINKS)) {
				throw new ConflictoOperacionException(
						"La ruta registrada no corresponde a un archivo regular seguro.");
			}
			try {
				Path real = registrada.toRealPath();
				if (!real.startsWith(raizReal)) {
					throw new ConflictoOperacionException(
							"La copia resuelve fuera de la carpeta configurada de la biblioteca.");
				}
				resultado.add(new ArchivoValidado(archivo, real));
			} catch (IOException | SecurityException error) {
				throw new ConflictoOperacionException(
						"No se pudo validar de forma segura la ruta registrada.");
			}
		}
		return List.copyOf(resultado);
	}

	private Path convertirRutaAbsoluta(String valor, String mensaje) {
		try {
			Path ruta = Path.of(valor);
			if (!ruta.isAbsolute()) {
				throw new ConflictoOperacionException(mensaje);
			}
			return ruta;
		} catch (InvalidPathException | NullPointerException error) {
			throw new ConflictoOperacionException(mensaje);
		}
	}

	private record ArchivoValidado(ArchivoPreparado archivo, Path rutaReal) {
	}
}

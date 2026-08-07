package com.miguel.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.miguel.dto.ConfiguracionResponse;
import com.miguel.config.Configuracion;
import com.miguel.repository.ConfiguracionRepository;

@Service
public class ConfiguracionService {
	private final ConfiguracionRepository configuracionRepository;

	public ConfiguracionService(ConfiguracionRepository configuracionRepository) {
		this.configuracionRepository = configuracionRepository;
	}

	@Transactional(readOnly = true)
	public ConfiguracionResponse obtenerConfiguracion() {
		return configuracionRepository.findFirstByOrderByIdAsc()
				.map(configuracion -> new ConfiguracionResponse(
						true,
						configuracion.getRutaLibros(),
						esRutaAccesible(configuracion.getRutaLibros())
				))
				.orElseGet(() -> new ConfiguracionResponse(false, null, false));
	}

	@Transactional(readOnly = true)
	public String obtenerRutaLibros() {
		return configuracionRepository.findFirstByOrderByIdAsc()
				.map(Configuracion::getRutaLibros)
				.orElseThrow(() -> new IllegalStateException(
						"Todavía no se ha configurado la carpeta de libros"
				));
	}

	@Transactional
	public ConfiguracionResponse guardarRuta(String ruta) {
		Path carpeta;
		try {
			carpeta = Path.of(ruta).toAbsolutePath().normalize();
		} catch (InvalidPathException | NullPointerException e) {
			throw new IllegalArgumentException("La ruta indicada no es válida", e);
		}

		if (!Files.exists(carpeta)) {
			throw new IllegalArgumentException(
					"La carpeta indicada no existe: " + carpeta
			);
		}

		if (!Files.isDirectory(carpeta)) {
			throw new IllegalArgumentException(
					"La ruta indicada no corresponde a una carpeta"
			);
		}

		if (!esRutaAccesible(carpeta)) {
			throw new IllegalArgumentException(
					"No se puede acceder a la carpeta indicada: " + carpeta
			);
		}

		Configuracion configuracion = configuracionRepository
				.findFirstByOrderByIdAsc()
				.orElseGet(Configuracion::new);

		configuracion.setRutaLibros(carpeta.toString());

		Configuracion guardada =
				configuracionRepository.save(configuracion);

		return new ConfiguracionResponse(
				true,
				guardada.getRutaLibros(),
				true
		);
	}

	private boolean esRutaAccesible(String ruta) {
		try {
			return esRutaAccesible(Path.of(ruta));
		} catch (InvalidPathException | NullPointerException e) {
			return false;
		}
	}

	private boolean esRutaAccesible(Path carpeta) {
		try {
			if (!Files.exists(carpeta) || !Files.isDirectory(carpeta) || !Files.isReadable(carpeta)) {
				return false;
			}
		}
		catch (SecurityException e) {
			return false;
		}

		try (DirectoryStream<Path> ignorado = Files.newDirectoryStream(carpeta)) {
			return true;
		} catch (IOException | SecurityException e) {
			return false;
		}
	}
}

package com.miguel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.miguel.dto.ConfiguracionResponse;
import com.miguel.repository.ConfiguracionRepository;

@SpringBootTest
class ConfiguracionServiceTests {

	@Autowired
	private ConfiguracionService configuracionService;
	@Autowired
	private ConfiguracionRepository configuracionRepository;

	@TempDir
	Path temporal;

	@BeforeEach
	void limpiarConfiguracion() {
		configuracionRepository.deleteAll();
	}

	@Test
	void distingueConfiguracionAusenteAccesibleEInaccesible() throws Exception {
		ConfiguracionResponse ausente = configuracionService.obtenerConfiguracion();
		assertThat(ausente.isConfigurada()).isFalse();
		assertThat(ausente.isRutaAccesible()).isFalse();

		Path carpeta = Files.createDirectory(temporal.resolve("libros"));
		configuracionService.guardarRuta(carpeta.toString());
		assertThat(configuracionService.obtenerConfiguracion().isRutaAccesible()).isTrue();

		Files.delete(carpeta);
		ConfiguracionResponse inaccesible = configuracionService.obtenerConfiguracion();
		assertThat(inaccesible.isConfigurada()).isTrue();
		assertThat(inaccesible.isRutaAccesible()).isFalse();
	}

	@Test
	void rutaNuevaInvalidaNoSustituyeLaAnterior() {
		configuracionService.guardarRuta(temporal.toString());
		String anterior = configuracionService.obtenerRutaLibros();

		assertThatThrownBy(() -> configuracionService.guardarRuta(temporal.resolve("ausente").toString()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(configuracionService.obtenerRutaLibros()).isEqualTo(anterior);
	}
}

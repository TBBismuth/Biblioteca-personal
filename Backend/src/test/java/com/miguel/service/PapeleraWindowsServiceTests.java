package com.miguel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class PapeleraWindowsServiceTests {
	@Test
	void transmiteRutaUnicodeFueraDelComandoYSolicitaPapeleraSinPerfil() {
		AtomicReference<List<String>> comandoRecibido = new AtomicReference<>();
		AtomicReference<String> variableRecibida = new AtomicReference<>();
		AtomicReference<String> rutaRecibida = new AtomicReference<>();
		PapeleraWindowsService servicio = new PapeleraWindowsService(
				(comando, variable, valor, tiempo) -> {
					comandoRecibido.set(comando);
					variableRecibida.set(variable);
					rutaRecibida.set(valor);
					assertThat(tiempo).isEqualTo(Duration.ofSeconds(30));
					return 0;
				});
		Path ruta = Path.of("C:\\Biblioteca con espacios\\Áutora - 日本語 'obra'.epub");

		servicio.enviar(ruta);

		assertThat(comandoRecibido.get()).contains("-NoProfile", "-NonInteractive", "-Command");
		assertThat(String.join(" ", comandoRecibido.get())).contains("SendToRecycleBin")
				.doesNotContain(ruta.toString());
		assertThat(variableRecibida.get()).isEqualTo(PapeleraWindowsService.VARIABLE_RUTA);
		assertThat(rutaRecibida.get()).isEqualTo(ruta.toString());
		assertThat(PapeleraWindowsService.SCRIPT).doesNotContain("Files.delete", "File.delete");
	}
}

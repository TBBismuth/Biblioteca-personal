package com.miguel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.miguel.dto.ActualizarLibroRequest;
import com.miguel.exception.OperacionArchivosException;
import com.miguel.service.ActualizacionLibroDatosService.CopiaActual;
import com.miguel.service.ActualizacionLibroDatosService.DatosLibro;

class RenombradoLibroRecuperacionTests {
	@TempDir Path temporal;

	@Test
	void restauraSiFallaLaPrimeraFase() throws IOException {
		probarFalloMovimiento(2);
	}

	@Test
	void restauraSiFallaLaSegundaFase() throws IOException {
		probarFalloMovimiento(4);
	}

	@Test
	void restauraSiFallaLaActualizacionDeBase() throws IOException {
		Path uno = Files.writeString(temporal.resolve("Viejo - Uno.pdf"), "uno");
		ActualizacionLibroDatosService datos = prepararDatos(uno);
		when(datos.aplicar(eq(1L), eq("Nuevo"), eq(List.of("Autor")), anyList()))
				.thenThrow(new IllegalStateException("fallo de base simulado"));
		RenombradoLibroService servicio = crearServicio(datos, new OperacionesArchivos());

		assertThatThrownBy(() -> servicio.actualizar(
				1L, new ActualizarLibroRequest("Nuevo", List.of("Autor"))))
				.isInstanceOf(OperacionArchivosException.class)
				.hasMessageContaining("restaurado");

		assertThat(uno).exists();
		assertThat(temporal.resolve("Autor - Nuevo.pdf")).doesNotExist();
		assertThat(temporales()).isEmpty();
	}

	private void probarFalloMovimiento(int llamadaConFallo) throws IOException {
		Path uno = Files.writeString(temporal.resolve("Viejo - Uno.pdf"), "uno");
		Path dos = Files.writeString(temporal.resolve("Viejo - Dos.pdf"), "dos");
		ActualizacionLibroDatosService datos = mock(ActualizacionLibroDatosService.class);
		when(datos.preparar(eq(1L), eq("Nuevo"), eq(List.of("Autor"))))
				.thenReturn(new DatosLibro(1L, false, List.of(
						new CopiaActual(1L, uno.getFileName().toString(), uno.toString(), "pdf", "a"),
						new CopiaActual(2L, dos.getFileName().toString(), dos.toString(), "pdf", "b"))));
		RenombradoLibroService servicio = crearServicio(datos, new OperacionesConFallo(llamadaConFallo));

		assertThatThrownBy(() -> servicio.actualizar(
				1L, new ActualizarLibroRequest("Nuevo", List.of("Autor"))))
				.isInstanceOf(OperacionArchivosException.class)
				.hasMessageContaining("restaurado");

		assertThat(uno).exists();
		assertThat(dos).exists();
		assertThat(temporales()).isEmpty();
	}

	private ActualizacionLibroDatosService prepararDatos(Path archivo) {
		ActualizacionLibroDatosService datos = mock(ActualizacionLibroDatosService.class);
		when(datos.preparar(eq(1L), eq("Nuevo"), eq(List.of("Autor"))))
				.thenReturn(new DatosLibro(1L, false, List.of(
						new CopiaActual(1L, archivo.getFileName().toString(),
								archivo.toString(), "pdf", "hash"))));
		return datos;
	}

	private RenombradoLibroService crearServicio(
			ActualizacionLibroDatosService datos, OperacionesArchivos archivos) {
		return new RenombradoLibroService(
				datos, new NormalizadorBiblioteca(), archivos, new CoordinadorBiblioteca());
	}

	private List<Path> temporales() throws IOException {
		try (var rutas = Files.list(temporal)) {
			return rutas.filter(ruta -> ruta.getFileName().toString()
					.startsWith(".biblioteca-personal-renombrado-")).toList();
		}
	}

	private static class OperacionesConFallo extends OperacionesArchivos {
		private final int llamadaConFallo;
		private int llamadas;
		private boolean falloConsumido;

		private OperacionesConFallo(int llamadaConFallo) {
			this.llamadaConFallo = llamadaConFallo;
		}

		@Override
		public void mover(Path origen, Path destino) throws IOException {
			llamadas++;
			if (!falloConsumido && llamadas == llamadaConFallo) {
				falloConsumido = true;
				throw new IOException("fallo de movimiento simulado");
			}
			super.mover(origen, destino);
		}
	}
}

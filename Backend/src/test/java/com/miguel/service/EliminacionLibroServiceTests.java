package com.miguel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.miguel.config.Configuracion;
import com.miguel.dto.ResultadoEliminacionResponse;
import com.miguel.exception.ConflictoOperacionException;
import com.miguel.exception.EliminacionParcialException;
import com.miguel.model.ArchivoLibro;
import com.miguel.model.Autor;
import com.miguel.model.Libro;
import com.miguel.repository.ArchivoLibroRepository;
import com.miguel.repository.AutorRepository;
import com.miguel.repository.ConfiguracionRepository;
import com.miguel.repository.LibroRepository;

@SpringBootTest
@AutoConfigureMockMvc
class EliminacionLibroServiceTests {
	@Autowired private EliminacionLibroService eliminacionService;
	@Autowired private EscaneoLibrosService escaneoService;
	@Autowired private RenombradoLibroService renombradoService;
	@Autowired private LibroService libroService;
	@Autowired private ArchivoLibroRepository archivoRepository;
	@Autowired private LibroRepository libroRepository;
	@Autowired private AutorRepository autorRepository;
	@Autowired private ConfiguracionRepository configuracionRepository;
	@Autowired private MockMvc mockMvc;
	@MockitoBean private PapeleraService papeleraService;

	@TempDir Path temporal;
	private Path biblioteca;
	private Path papeleraSimulada;

	@BeforeEach
	void preparar() throws IOException {
		archivoRepository.deleteAll();
		libroRepository.deleteAll();
		autorRepository.deleteAll();
		configuracionRepository.deleteAll();
		reset(papeleraService);
		biblioteca = Files.createDirectory(temporal.resolve("biblioteca"));
		papeleraSimulada = Files.createDirectory(temporal.resolve("papelera-simulada"));
		configuracionRepository.save(new Configuracion(biblioteca.toString()));
		doAnswer(invocacion -> {
			Path origen = invocacion.getArgument(0);
			Files.move(origen, papeleraSimulada.resolve(origen.getFileName()));
			return null;
		}).when(papeleraService).enviar(any(Path.class));
	}

	@Test
	void eliminaUnaCopiaEntreDosYActualizaConsultasSinAlterarLibro() throws IOException {
		Path epub = crear("Autora - Obra.epub", "epub");
		Path pdf = crear("Autora - Obra.pdf", "pdf");
		escaneoService.escanearCarpeta(biblioteca.toString());
		Libro libro = libroRepository.findTodosConAutores().getFirst();
		Long libroId = libro.getId();
		Long autorId = libro.getAutores().iterator().next().getId();
		Long epubId = archivoRepository.findByRuta(epub.toString()).orElseThrow().getId();
		libroService.cambiarEstadoLectura(libroId, true);

		ResultadoEliminacionResponse resultado = eliminacionService.eliminarCopia(libroId, epubId);

		assertThat(resultado.copiasEliminadas()).singleElement().satisfies(copia -> {
			assertThat(copia.idArchivo()).isEqualTo(epubId);
			assertThat(copia.nombreArchivo()).isEqualTo("Autora - Obra.epub");
			assertThat(copia.rutaAnterior()).isEqualTo(epub.toString());
		});
		assertThat(resultado.copiasRestantes()).isEqualTo(1);
		assertThat(resultado.libroDisponible()).isTrue();
		assertThat(epub).doesNotExist();
		assertThat(pdf).exists();
		assertThat(archivoRepository.findById(epubId)).isEmpty();
		assertThat(libroRepository.findById(libroId)).get()
				.extracting(Libro::isLeido).isEqualTo(true);
		assertThat(autorRepository.findById(autorId)).isPresent();
		assertThat(libroService.listar("obra autora", EstadoLecturaFiltro.LEIDOS, 0, 50).getLibros())
				.singleElement().satisfies(respuesta -> {
					assertThat(respuesta.getNumeroArchivos()).isEqualTo(1);
					assertThat(respuesta.getFormatos()).containsExactly("pdf");
				});
	}

	@Test
	void eliminaUltimaCopiaYConservaHistorialLeidoYAutoresOculto() throws IOException {
		Path ruta = crear("Autor histórico - Obra histórica.epub", "contenido");
		escaneoService.escanearCarpeta(biblioteca.toString());
		Libro libro = libroRepository.findTodosConAutores().getFirst();
		Long libroId = libro.getId();
		Long autorId = libro.getAutores().iterator().next().getId();
		Long archivoId = archivoRepository.findByRuta(ruta.toString()).orElseThrow().getId();
		libroService.cambiarEstadoLectura(libroId, true);

		ResultadoEliminacionResponse resultado = eliminacionService.eliminarCopia(libroId, archivoId);

		assertThat(resultado.copiasRestantes()).isZero();
		assertThat(resultado.libroDisponible()).isFalse();
		assertThat(libroRepository.findById(libroId)).get().satisfies(historial -> {
			assertThat(historial.getTitulo()).isEqualTo("Obra histórica");
			assertThat(historial.isLeido()).isTrue();
		});
		assertThat(autorRepository.findById(autorId)).isPresent();
		assertThat(libroService.listar(null, EstadoLecturaFiltro.TODOS, 0, 50).getLibros()).isEmpty();
		assertThat(libroService.listar("histórica", EstadoLecturaFiltro.LEIDOS, 0, 50).getLibros()).isEmpty();
		assertThat(libroService.obtenerResumen().getTotalLibros()).isZero();

		Files.writeString(ruta, "contenido reaparecido");
		escaneoService.escanearCarpeta(biblioteca.toString());
		assertThat(libroRepository.count()).isEqualTo(1);
		assertThat(archivoRepository.findAll()).singleElement()
				.extracting(copia -> copia.getLibro().getId()).isEqualTo(libroId);
		assertThat(libroRepository.findById(libroId)).get()
				.extracting(Libro::isLeido).isEqualTo(true);
	}

	@Test
	void eliminaTodasLasCopiasYReescaneoNoLasCuentaComoDesaparecidas() throws IOException {
		crear("Autor - Colección.epub", "uno");
		crear("Autor - Colección.pdf", "dos");
		escaneoService.escanearCarpeta(biblioteca.toString());
		Long libroId = libroRepository.findAll().getFirst().getId();

		ResultadoEliminacionResponse resultado = eliminacionService.eliminarTodas(libroId);
		EscaneoLibrosService.ResultadoEscaneo reescaneo =
				escaneoService.escanearCarpeta(biblioteca.toString());

		assertThat(resultado.copiasEliminadas()).hasSize(2);
		assertThat(resultado.copiasRestantes()).isZero();
		assertThat(archivoRepository.findAll()).isEmpty();
		assertThat(reescaneo.archivosDesaparecidosEliminados()).isZero();
		assertThat(reescaneo.archivosNuevos()).isZero();
		assertThat(libroRepository.findById(libroId)).isPresent();
	}

	@Test
	void endpointsDevuelvenContratoYErrores404Y409() throws Exception, IOException {
		Path ruta = crear("Autor - Endpoint.pdf", "contenido");
		escaneoService.escanearCarpeta(biblioteca.toString());
		ArchivoLibro archivo = archivoRepository.findByRuta(ruta.toString()).orElseThrow();

		mockMvc.perform(delete("/api/libros/{libro}/copias/{archivo}",
				archivo.getLibro().getId(), archivo.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.libroId").value(archivo.getLibro().getId()))
				.andExpect(jsonPath("$.copiasEliminadas[0].idArchivo").value(archivo.getId()))
				.andExpect(jsonPath("$.copiasRestantes").value(0))
				.andExpect(jsonPath("$.libroDisponible").value(false));

		mockMvc.perform(delete("/api/libros/{libro}/copias", Long.MAX_VALUE))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/libros/{libro}/copias", archivo.getLibro().getId()))
				.andExpect(status().isConflict());
	}

	@Test
	void rechazaCopiaInexistenteYOtroLibro() throws Exception, IOException {
		crear("Autor uno - Uno.pdf", "uno");
		crear("Autor dos - Dos.pdf", "dos");
		escaneoService.escanearCarpeta(biblioteca.toString());
		List<ArchivoLibro> archivos = archivoRepository.findAll();
		ArchivoLibro primero = archivos.getFirst();
		ArchivoLibro segundo = archivos.get(1);

		mockMvc.perform(delete("/api/libros/{libro}/copias/{archivo}",
				primero.getLibro().getId(), Long.MAX_VALUE))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/libros/{libro}/copias/{archivo}",
				primero.getLibro().getId(), segundo.getId()))
				.andExpect(status().isConflict());
		verifyNoInteractions(papeleraService);
	}

	@Test
	void archivoAusenteNoSeConsideraEliminado() throws Exception, IOException {
		Path ruta = crear("Autor - Ausente.pdf", "contenido");
		escaneoService.escanearCarpeta(biblioteca.toString());
		ArchivoLibro archivo = archivoRepository.findByRuta(ruta.toString()).orElseThrow();
		Files.delete(ruta);

		mockMvc.perform(delete("/api/libros/{libro}/copias/{archivo}",
				archivo.getLibro().getId(), archivo.getId()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(
						org.hamcrest.Matchers.containsString("Actualiza la biblioteca")));
		assertThat(archivoRepository.findById(archivo.getId())).isPresent();
		verifyNoInteractions(papeleraService);
	}

	@Test
	void validaTodasLasRutasAntesDeEnviarLaPrimera() throws IOException {
		Path valida = crear("Autor - Válida.epub", "uno");
		crear("Autor - Válida.pdf", "dos");
		escaneoService.escanearCarpeta(biblioteca.toString());
		Long libroId = libroRepository.findAll().getFirst().getId();
		ArchivoLibro corrupta = archivoRepository.findAll().stream()
				.filter(copia -> !copia.getRuta().equals(valida.toString())).findFirst().orElseThrow();
		corrupta.setRuta(temporal.resolve("fuera.pdf").toString());
		archivoRepository.saveAndFlush(corrupta);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> eliminacionService.eliminarTodas(libroId))
				.isInstanceOf(ConflictoOperacionException.class).hasMessageContaining("fuera");
		assertThat(valida).exists();
		assertThat(archivoRepository.count()).isEqualTo(2);
		verifyNoInteractions(papeleraService);
	}

	@Test
	void rechazaRutaRelativaConPuntosYEnlaceSimbolicoSinTocarPapelera() throws IOException {
		Path ruta = crear("Autor - Segura.pdf", "contenido");
		escaneoService.escanearCarpeta(biblioteca.toString());
		ArchivoLibro archivo = archivoRepository.findByRuta(ruta.toString()).orElseThrow();
		archivo.setRuta(biblioteca.resolve("sub").resolve("..").resolve(ruta.getFileName()).toString());
		archivoRepository.saveAndFlush(archivo);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> eliminacionService.eliminarCopia(
				archivo.getLibro().getId(), archivo.getId()))
				.isInstanceOf(ConflictoOperacionException.class).hasMessageContaining("segmentos");
		verifyNoInteractions(papeleraService);
	}

	@Test
	void falloDePapeleraNoBorraArchivoRegistroLibroNiAutores() throws IOException {
		Path ruta = crear("Áutora Unicode - Obra con espacios 日本語.epub", "contenido");
		escaneoService.escanearCarpeta(biblioteca.toString());
		ArchivoLibro archivo = archivoRepository.findByRuta(ruta.toString()).orElseThrow();
		Long libroId = archivo.getLibro().getId();
		long autores = autorRepository.count();
		doThrow(new RuntimeException("fallo simulado")).when(papeleraService).enviar(any(Path.class));

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> eliminacionService.eliminarCopia(
				libroId, archivo.getId())).isInstanceOf(EliminacionParcialException.class);

		assertThat(ruta).exists();
		assertThat(archivoRepository.findById(archivo.getId())).isPresent();
		assertThat(libroRepository.findById(libroId)).isPresent();
		assertThat(autorRepository.count()).isEqualTo(autores);
	}

	@Test
	void falloParcialConservaSoloCopiasNoEnviadasYLoExponeEnJson() throws Exception, IOException {
		Path primera = crear("Autor - Parcial.epub", "uno");
		Path segunda = crear("Autor - Parcial.pdf", "dos");
		escaneoService.escanearCarpeta(biblioteca.toString());
		Long libroId = libroRepository.findAll().getFirst().getId();
		reset(papeleraService);
		doAnswer(invocacion -> {
			Path origen = invocacion.getArgument(0);
			Files.move(origen, papeleraSimulada.resolve(origen.getFileName()));
			return null;
		}).doThrow(new RuntimeException("segundo fallo")).when(papeleraService).enviar(any(Path.class));

		mockMvc.perform(delete("/api/libros/{libro}/copias", libroId))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.resultadoParcial.copiasEliminadas.length()").value(1))
				.andExpect(jsonPath("$.resultadoParcial.copiasRestantes").value(1))
				.andExpect(jsonPath("$.resultadoParcial.libroDisponible").value(true));

		assertThat(List.of(primera, segunda)).filteredOn(Files::exists).hasSize(1);
		assertThat(archivoRepository.findAll()).hasSize(1);
		assertThat(libroRepository.findById(libroId)).isPresent();
	}

	@Test
	void eliminacionExcluyeEscaneoMientrasOpera() throws Exception {
		ArchivoLibro archivo = prepararOperacionBloqueada();
		CountDownLatch dentro = new CountDownLatch(1);
		CountDownLatch continuar = new CountDownLatch(1);
		bloquearPapelera(dentro, continuar);
		CompletableFuture<ResultadoEliminacionResponse> eliminacion = CompletableFuture.supplyAsync(
				() -> eliminacionService.eliminarCopia(archivo.getLibro().getId(), archivo.getId()));
		assertThat(dentro.await(2, TimeUnit.SECONDS)).isTrue();
		CompletableFuture<?> escaneo = CompletableFuture.supplyAsync(
				() -> escaneoService.escanearCarpeta(biblioteca.toString()));
		Thread.sleep(100);
		assertThat(escaneo).isNotDone();
		continuar.countDown();
		eliminacion.get(3, TimeUnit.SECONDS);
		escaneo.get(3, TimeUnit.SECONDS);
	}

	@Test
	void eliminacionExcluyeRenombradoMientrasOpera() throws Exception {
		ArchivoLibro archivo = prepararOperacionBloqueada();
		Path otra = crear("Otra autora - Otra obra.pdf", "otro");
		escaneoService.escanearCarpeta(biblioteca.toString());
		Long otroLibro = archivoRepository.findByRuta(otra.toString()).orElseThrow().getLibro().getId();
		CountDownLatch dentro = new CountDownLatch(1);
		CountDownLatch continuar = new CountDownLatch(1);
		bloquearPapelera(dentro, continuar);
		CompletableFuture<ResultadoEliminacionResponse> eliminacion = CompletableFuture.supplyAsync(
				() -> eliminacionService.eliminarCopia(archivo.getLibro().getId(), archivo.getId()));
		assertThat(dentro.await(2, TimeUnit.SECONDS)).isTrue();
		CompletableFuture<?> renombrado = CompletableFuture.supplyAsync(() -> renombradoService.actualizar(
				otroLibro, new com.miguel.dto.ActualizarLibroRequest("Renombrada", List.of("Autora"))));
		Thread.sleep(100);
		assertThat(renombrado).isNotDone();
		continuar.countDown();
		eliminacion.get(3, TimeUnit.SECONDS);
		renombrado.get(3, TimeUnit.SECONDS);
	}

	private ArchivoLibro prepararOperacionBloqueada() throws IOException {
		Path ruta = crear("Autor - Bloqueo.epub", "contenido");
		escaneoService.escanearCarpeta(biblioteca.toString());
		return archivoRepository.findByRuta(ruta.toString()).orElseThrow();
	}

	private void bloquearPapelera(CountDownLatch dentro, CountDownLatch continuar) {
		reset(papeleraService);
		doAnswer(invocacion -> {
			dentro.countDown();
			if (!continuar.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
			Path origen = invocacion.getArgument(0);
			Files.move(origen, papeleraSimulada.resolve(origen.getFileName()));
			return null;
		}).when(papeleraService).enviar(any(Path.class));
	}

	private Path crear(String nombre, String contenido) throws IOException {
		return Files.writeString(biblioteca.resolve(nombre), contenido);
	}
}

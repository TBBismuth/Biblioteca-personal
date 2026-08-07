package com.miguel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.miguel.dto.ActualizarLibroRequest;
import com.miguel.dto.ActualizarLibroResponse;
import com.miguel.exception.ConflictoOperacionException;
import com.miguel.exception.RecursoNoEncontradoException;
import com.miguel.model.ArchivoLibro;
import com.miguel.model.Libro;
import com.miguel.repository.ArchivoLibroRepository;
import com.miguel.repository.AutorRepository;
import com.miguel.repository.ConfiguracionRepository;
import com.miguel.repository.LibroRepository;

@SpringBootTest
@AutoConfigureMockMvc
class RenombradoLibroServiceTests {
	@Autowired private RenombradoLibroService renombradoService;
	@Autowired private EscaneoLibrosService escaneoService;
	@Autowired private LibroService libroService;
	@Autowired private ArchivoLibroRepository archivoRepository;
	@Autowired private LibroRepository libroRepository;
	@Autowired private AutorRepository autorRepository;
	@Autowired private ConfiguracionRepository configuracionRepository;
	@Autowired private MockMvc mockMvc;

	@TempDir Path temporal;

	@BeforeEach
	void limpiarBaseDePruebas() {
		archivoRepository.deleteAll();
		libroRepository.deleteAll();
		autorRepository.deleteAll();
		configuracionRepository.deleteAll();
	}

	@Test
	void renombraUnaCopiaYConservaIdentidadEstadoYHash() throws IOException {
		Path anterior = crear("Autora Vieja - Título viejo.epub", "contenido");
		escaneoService.escanearCarpeta(temporal.toString());
		ArchivoLibro archivoAntes = archivoRepository.findAll().getFirst();
		Long archivoId = archivoAntes.getId();
		Long libroId = archivoAntes.getLibro().getId();
		String hash = archivoAntes.getSha256();
		libroService.cambiarEstadoLectura(libroId, true);

		ActualizarLibroResponse respuesta = renombradoService.actualizar(
				libroId, peticion("Título nuevo", "Autor Uno", "Autor Dos"));

		Path nueva = temporal.resolve("Autor Uno, Autor Dos - Título nuevo.epub");
		assertThat(anterior).doesNotExist();
		assertThat(nueva).exists();
		assertThat(respuesta.getLibro().getTitulo()).isEqualTo("Título nuevo");
		assertThat(respuesta.getLibro().getAutores()).containsExactly("Autor Uno", "Autor Dos");
		assertThat(respuesta.getLibro().getNumeroArchivos()).isEqualTo(1);
		assertThat(libroService.obtenerCopias(libroId)).hasSize(1);
		assertThat(respuesta.getLibro().isLeido()).isTrue();
		assertThat(respuesta.getArchivosRenombrados()).singleElement().satisfies(detalle -> {
			assertThat(detalle.getIdArchivo()).isEqualTo(archivoId);
			assertThat(detalle.getRutaAnterior()).isEqualTo(anterior.toString());
			assertThat(detalle.getRutaNueva()).isEqualTo(nueva.toString());
		});
		ArchivoLibro archivoDespues = archivoRepository.findById(archivoId).orElseThrow();
		assertThat(archivoDespues.getLibro().getId()).isEqualTo(libroId);
		assertThat(archivoDespues.getSha256()).isEqualTo(hash);
		assertThat(archivoDespues.getExtension()).isEqualTo("epub");
		assertThat(listarTemporales()).isEmpty();
	}

	@Test
	void eliminaUnAutorSinEliminarloGlobalmenteNiAlterarLaCopia() throws IOException {
		Path objetivo = crear(
				"Autor Compartido, Autor Retirado - Objetivo.epub", "contenido objetivo");
		crear("Autor Retirado - Otra obra.pdf", "otro contenido");
		escaneoService.escanearCarpeta(temporal.toString());
		Libro libro = libroRepository.findTodosConAutores().stream()
				.filter(candidato -> candidato.getTitulo().equals("Objetivo"))
				.findFirst().orElseThrow();
		Long libroId = libro.getId();
		ArchivoLibro copiaAntes = archivoRepository.findByRuta(objetivo.toString()).orElseThrow();
		Long copiaId = copiaAntes.getId();
		String hash = copiaAntes.getSha256();
		long numeroCopiasAntes = archivoRepository.count();

		ActualizarLibroResponse respuesta = renombradoService.actualizar(
				libroId, peticion("Objetivo", "Autor Compartido"));

		Path nuevaRuta = temporal.resolve("Autor Compartido - Objetivo.epub");
		assertThat(respuesta.getLibro().getAutores()).containsExactly("Autor Compartido");
		assertThat(respuesta.getLibro().getNumeroArchivos()).isEqualTo(1);
		assertThat(objetivo).doesNotExist();
		assertThat(nuevaRuta).exists();
		assertThat(archivoRepository.count()).isEqualTo(numeroCopiasAntes);
		ArchivoLibro copiaDespues = archivoRepository.findById(copiaId).orElseThrow();
		assertThat(copiaDespues.getRuta()).isEqualTo(nuevaRuta.toString());
		assertThat(copiaDespues.getSha256()).isEqualTo(hash);
		assertThat(copiaDespues.getLibro().getId()).isEqualTo(libroId);
		assertThat(libroRepository.findConAutoresById(libroId).orElseThrow().getAutores())
				.extracting(autor -> autor.getNombre())
				.containsExactly("Autor Compartido");
		assertThat(autorRepository.findByNombreIgnoreCase("Autor Retirado")).isPresent();
		assertThat(libroRepository.findTodosConAutores().stream()
				.filter(candidato -> candidato.getTitulo().equals("Otra obra"))
				.findFirst().orElseThrow().getAutores())
				.extracting(autor -> autor.getNombre())
				.containsExactly("Autor Retirado");
		assertThat(libroService.obtenerCopias(libroId)).singleElement().satisfies(copia -> {
			assertThat(copia.getId()).isEqualTo(copiaId);
			assertThat(copia.getRuta()).isEqualTo(nuevaRuta.toString());
		});
		assertThat(listarTemporales()).isEmpty();
	}

	@Test
	void renombraCopiasEnCarpetasDistintasConservandoExtensionesYSufijos() throws IOException {
		Path carpetaA = Files.createDirectory(temporal.resolve("a"));
		Path carpetaB = Files.createDirectory(temporal.resolve("b"));
		Path carpetaC = Files.createDirectory(temporal.resolve("c"));
		crear(carpetaA, "Viejo - Obra.pdf", "igual");
		crear(carpetaB, "Viejo - Obra [duplicado 7].EPUB", "igual");
		crear(carpetaC, "Viejo - Obra [Versión 3].mobi", "igual");
		escaneoService.escanearCarpeta(temporal.toString());
		Long id = libroRepository.findAll().getFirst().getId();

		ActualizarLibroResponse respuesta = renombradoService.actualizar(id, peticion("Nueva", "Autor"));

		assertThat(carpetaA.resolve("Autor - Nueva.pdf")).exists();
		assertThat(carpetaB.resolve("Autor - Nueva [duplicado 7].EPUB")).exists();
		assertThat(carpetaC.resolve("Autor - Nueva [Versión 3].mobi")).exists();
		assertThat(respuesta.getArchivosRenombrados()).hasSize(3);
		assertThat(libroService.obtenerCopias(id))
				.extracting(copia -> copia.getRuta())
				.containsExactlyInAnyOrder(
						carpetaA.resolve("Autor - Nueva.pdf").toString(),
						carpetaB.resolve("Autor - Nueva [duplicado 7].EPUB").toString(),
						carpetaC.resolve("Autor - Nueva [Versión 3].mobi").toString());
	}

	@Test
	void resuelveColisionPropiaConSufijoDeterminista() throws IOException {
		crear("A - Obra.pdf", "igual");
		crear("Z nombre libre.pdf", "igual");
		escaneoService.escanearCarpeta(temporal.toString());
		Long id = libroRepository.findAll().getFirst().getId();

		renombradoService.actualizar(id, peticion("Nueva", "Autor"));

		assertThat(temporal.resolve("Autor - Nueva.pdf")).exists();
		assertThat(temporal.resolve("Autor - Nueva [duplicado 2].pdf")).exists();
		assertThat(archivoRepository.findAll()).hasSize(2)
				.extracting(ArchivoLibro::getNombreArchivo)
				.containsExactlyInAnyOrder("Autor - Nueva.pdf", "Autor - Nueva [duplicado 2].pdf");
	}

	@Test
	void reutilizaAutorExistenteYEliminaDuplicadosDeLaPeticion() throws IOException {
		crear("Existente - Otra.pdf", "otro");
		crear("Antiguo - Objetivo.epub", "objetivo");
		escaneoService.escanearCarpeta(temporal.toString());
		Libro objetivo = libroRepository.findAll().stream()
				.filter(libro -> libro.getTitulo().equals("Objetivo")).findFirst().orElseThrow();
		long autoresAntes = autorRepository.count();

		ActualizarLibroResponse respuesta = renombradoService.actualizar(
				objetivo.getId(), peticion("Objetivo nuevo", "Existente", "existénte", "Nuevo"));

		assertThat(respuesta.getLibro().getAutores()).containsExactly("Existente", "Nuevo");
		assertThat(autorRepository.count()).isEqualTo(autoresAntes + 1);
	}

	@Test
	void reescaneoPosteriorNoCreaNiEliminaArchivos() throws IOException {
		crear("Autor - Antes.azw3", "contenido");
		escaneoService.escanearCarpeta(temporal.toString());
		Long id = libroRepository.findAll().getFirst().getId();
		renombradoService.actualizar(id, peticion("Después", "Autor"));

		EscaneoLibrosService.ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(resultado.archivosYaRegistrados()).isEqualTo(1);
		assertThat(resultado.archivosNuevos()).isZero();
		assertThat(resultado.archivosDesaparecidosEliminados()).isZero();
	}

	@Test
	void peticionIdenticaNoMueveArchivos() throws IOException {
		Path ruta = crear("Autor - Título.pdf", "contenido");
		escaneoService.escanearCarpeta(temporal.toString());
		Long id = libroRepository.findAll().getFirst().getId();

		ActualizarLibroResponse respuesta = renombradoService.actualizar(id, peticion("Título", "Autor"));

		assertThat(respuesta.getArchivosRenombrados()).isEmpty();
		assertThat(ruta).exists();
		assertThat(listarTemporales()).isEmpty();
	}

	@Test
	void permiteCambioSoloDePresentacion() throws IOException {
		Path anterior = crear("autor - titulo.pdf", "contenido");
		escaneoService.escanearCarpeta(temporal.toString());
		Long id = libroRepository.findAll().getFirst().getId();

		renombradoService.actualizar(id, peticion("TÍTULO", "AUTOR"));

		assertThat(temporal.resolve("AUTOR - TÍTULO.pdf")).exists();
		assertThat(archivoRepository.findAll()).singleElement()
				.extracting(ArchivoLibro::getNombreArchivo).isEqualTo("AUTOR - TÍTULO.pdf");
	}

	@Test
	void rechazaDatosInvalidosDeWindowsYLongitud() throws IOException {
		crear("Autor - Título.pdf", "contenido");
		escaneoService.escanearCarpeta(temporal.toString());
		Long id = libroRepository.findAll().getFirst().getId();

		assertThatThrownBy(() -> renombradoService.actualizar(id, peticion(" ", "Autor")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> renombradoService.actualizar(id, new ActualizarLibroRequest("Título", List.of())))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> renombradoService.actualizar(id, peticion("Título", " ")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> renombradoService.actualizar(id, peticion("Mal: título", "Autor")))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Windows");
		assertThatThrownBy(() -> renombradoService.actualizar(id, peticion("CON", "Autor")))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reservado");
		assertThatThrownBy(() -> renombradoService.actualizar(id, peticion("x".repeat(250), "Autor")))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("255");
	}

	@Test
	void rechazaLibroInexistenteHistoricoYOtraIdentidad() throws IOException {
		assertThatThrownBy(() -> renombradoService.actualizar(999999L, peticion("Título", "Autor")))
				.isInstanceOf(RecursoNoEncontradoException.class);

		Path historico = crear("Histórico - Obra.pdf", "histórico");
		crear("Otro - Existente.pdf", "existente");
		escaneoService.escanearCarpeta(temporal.toString());
		Libro libroHistorico = libroRepository.findAll().stream()
				.filter(libro -> libro.getTitulo().equals("Obra")).findFirst().orElseThrow();
		Libro existente = libroRepository.findAll().stream()
				.filter(libro -> libro.getTitulo().equals("Existente")).findFirst().orElseThrow();
		Files.delete(historico);
		escaneoService.escanearCarpeta(temporal.toString());

		assertThatThrownBy(() -> renombradoService.actualizar(
				libroHistorico.getId(), peticion("Nueva", "Autor")))
				.isInstanceOf(ConflictoOperacionException.class).hasMessageContaining("copias físicas");
		assertThatThrownBy(() -> renombradoService.actualizar(
				existente.getId(), peticion("Obra", "Histórico")))
				.isInstanceOf(ConflictoOperacionException.class).hasMessageContaining("Ya existe otro libro");
	}

	@Test
	void rechazaOrigenAusenteYDestinoExternoSinMoverNada() throws IOException {
		Path origen = crear("Autor - Antes.pdf", "contenido");
		escaneoService.escanearCarpeta(temporal.toString());
		Long id = libroRepository.findAll().getFirst().getId();
		Files.delete(origen);
		assertThatThrownBy(() -> renombradoService.actualizar(id, peticion("Nuevo", "Autor")))
				.isInstanceOf(ConflictoOperacionException.class).hasMessageContaining("origen");

		Files.writeString(origen, "contenido");
		Path externo = crear("Autor - Nuevo.pdf", "externo");
		assertThatThrownBy(() -> renombradoService.actualizar(id, peticion("Nuevo", "Autor")))
				.isInstanceOf(ConflictoOperacionException.class).hasMessageContaining("destino");
		assertThat(origen).exists();
		assertThat(externo).exists();
	}

	@Test
	void endpointDevuelveContratoYEstadosHttpComprensibles() throws Exception {
		crear("Autor - Antes.pdf", "contenido");
		escaneoService.escanearCarpeta(temporal.toString());
		Long id = libroRepository.findAll().getFirst().getId();

		mockMvc.perform(put("/api/libros/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"titulo":"Después","autores":["Autora"]}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.libro.id").value(id))
				.andExpect(jsonPath("$.libro.titulo").value("Después"))
				.andExpect(jsonPath("$.archivosRenombrados[0].nombreAnterior")
						.value("Autor - Antes.pdf"))
				.andExpect(jsonPath("$.archivosRenombrados[0].nombreNuevo")
						.value("Autora - Después.pdf"));

		mockMvc.perform(put("/api/libros/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"\",\"autores\":[\"Autor\"]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El título es obligatorio"));

		mockMvc.perform(put("/api/libros/{id}", 999999)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Título\",\"autores\":[\"Autor\"]}"))
				.andExpect(status().isNotFound());

		Files.delete(temporal.resolve("Autora - Después.pdf"));
		mockMvc.perform(put("/api/libros/{id}", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"titulo\":\"Otro\",\"autores\":[\"Autor\"]}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(
						org.hamcrest.Matchers.containsString("origen")));
	}

	private ActualizarLibroRequest peticion(String titulo, String... autores) {
		return new ActualizarLibroRequest(titulo, List.of(autores));
	}

	private Path crear(String nombre, String contenido) throws IOException {
		return crear(temporal, nombre, contenido);
	}

	private Path crear(Path carpeta, String nombre, String contenido) throws IOException {
		return Files.writeString(carpeta.resolve(nombre), contenido);
	}

	private List<Path> listarTemporales() throws IOException {
		try (var rutas = Files.walk(temporal)) {
			return rutas.filter(ruta -> ruta.getFileName().toString()
					.startsWith(".biblioteca-personal-renombrado-")).toList();
		}
	}
}

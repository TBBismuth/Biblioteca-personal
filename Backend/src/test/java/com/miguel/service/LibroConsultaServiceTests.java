package com.miguel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.miguel.dto.CopiaLibroResponse;
import com.miguel.dto.LibroPaginadoResponse;
import com.miguel.dto.ResumenBibliotecaResponse;
import com.miguel.exception.RecursoNoEncontradoException;
import com.miguel.model.ArchivoLibro;
import com.miguel.model.Autor;
import com.miguel.model.Libro;
import com.miguel.repository.ArchivoLibroRepository;
import com.miguel.repository.AutorRepository;
import com.miguel.repository.LibroRepository;

@SpringBootTest
@AutoConfigureMockMvc
class LibroConsultaServiceTests {

	@Autowired
	private LibroService libroService;
	@Autowired
	private LibroRepository libroRepository;
	@Autowired
	private ArchivoLibroRepository archivoRepository;
	@Autowired
	private AutorRepository autorRepository;
	@Autowired
	private MockMvc mockMvc;

	private final AtomicInteger secuenciaRuta = new AtomicInteger();

	@BeforeEach
	void limpiarBaseDePruebas() {
		archivoRepository.deleteAll();
		libroRepository.deleteAll();
		autorRepository.deleteAll();
		secuenciaRuta.set(0);
	}

	@Test
	void listadoPaginadoMantieneOrdenEstable() {
		guardarLibro("beta", false, List.of("Autor B"), 1);
		Libro alphaPrimero = guardarLibro("Alpha", false, List.of("Autor A"), 1);
		Libro alphaSegundo = guardarLibro("alpha", true, List.of("Autor C"), 1);
		guardarLibro("Gamma", false, List.of("Autor D"), 1);

		LibroPaginadoResponse primera = libroService.listar(null, EstadoLecturaFiltro.TODOS, 0, 2);
		LibroPaginadoResponse segunda = libroService.listar(null, EstadoLecturaFiltro.TODOS, 1, 2);

		assertThat(primera.getLibros()).extracting("id")
				.containsExactly(alphaPrimero.getId(), alphaSegundo.getId());
		assertThat(segunda.getLibros()).extracting("titulo").containsExactly("beta", "Gamma");
		assertThat(primera.getTotalResultados()).isEqualTo(4);
		assertThat(primera.getTotalPaginas()).isEqualTo(2);
		assertThat(primera.isPrimeraPagina()).isTrue();
		assertThat(segunda.isUltimaPagina()).isTrue();
	}

	@Test
	void buscaPorTituloYAutorIgnorandoMayusculasYTildes() {
		guardarLibro("Cien años de soledad", false, List.of("Gabriel García Márquez"), 1);
		guardarLibro("El resplandor", false, List.of("Stephen King"), 1);
		guardarLibro("Otro libro", false, List.of("Otra autora"), 1);

		assertThat(libroService.listar("AÑOS", EstadoLecturaFiltro.TODOS, 0, 50).getLibros())
				.extracting("titulo").containsExactly("Cien años de soledad");
		assertThat(libroService.listar("garcia marquez", EstadoLecturaFiltro.TODOS, 0, 50).getLibros())
				.extracting("titulo").containsExactly("Cien años de soledad");
		assertThat(libroService.listar("STEPHEN KING", EstadoLecturaFiltro.TODOS, 0, 50).getLibros())
				.extracting("titulo").containsExactly("El resplandor");
		assertThat(libroService.listar("   ", EstadoLecturaFiltro.TODOS, 0, 50).getTotalResultados())
				.isEqualTo(3);
	}

	@Test
	void buscaTerminosDeAutorEnCualquierOrdenYPorFragmentos() {
		guardarLibro("Manual", false, List.of("Miguel Guerrero"), 1);

		for (String busqueda : List.of(
				"miguel guerrero", "guerrero miguel", "MIGUEL GUERRERO",
				"guer mig", "mig guer")) {
			assertThat(libroService.listar(
					busqueda, EstadoLecturaFiltro.TODOS, 0, 50).getLibros())
					.extracting("titulo").containsExactly("Manual");
		}
	}

	@Test
	void combinaTerminosDeTituloYAutoresIgnorandoOrdenTildesYEspacios() {
		guardarLibro("1984", false, List.of("George Orwell"), 1);
		guardarLibro("Cien años de soledad", false, List.of("Gabriel García Márquez"), 1);

		for (String busqueda : List.of("orwell 1984", "1984 orwell", "george 1984", "198 orw")) {
			assertThat(libroService.listar(
					busqueda, EstadoLecturaFiltro.TODOS, 0, 50).getLibros())
					.extracting("titulo").containsExactly("1984");
		}
		for (String busqueda : List.of(
				"garcia soledad", "soledad marquez", "cien gabriel",
				"MARQUEZ   cien   soledad", "cien cien gabriel")) {
			assertThat(libroService.listar(
					busqueda, EstadoLecturaFiltro.TODOS, 0, 50).getLibros())
					.extracting("titulo").containsExactly("Cien años de soledad");
		}
		assertThat(libroService.listar(
				"orwell soledad", EstadoLecturaFiltro.TODOS, 0, 50).getLibros()).isEmpty();
	}

	@Test
	void busquedaPorTerminosMantieneAndFiltrosPaginacionYLibrosUnicos() {
		Libro leido = guardarLibro(
				"Crónica norte", true, List.of("Miguel Guerrero", "Ana Pérez"), 1);
		Libro pendiente = guardarLibro(
				"Crónica sur", false, List.of("Miguel Guerrero", "Luis Ruiz"), 1);
		guardarLibro("Otra obra", false, List.of("Miguel Guerrero"), 1);

		LibroPaginadoResponse primera = libroService.listar(
				"guerrero cronica", EstadoLecturaFiltro.TODOS, 0, 1);
		LibroPaginadoResponse segunda = libroService.listar(
				"cronica guerrero", EstadoLecturaFiltro.TODOS, 1, 1);

		assertThat(primera.getTotalResultados()).isEqualTo(2);
		assertThat(primera.getTotalPaginas()).isEqualTo(2);
		assertThat(primera.getLibros()).extracting("id").containsExactly(leido.getId());
		assertThat(segunda.getLibros()).extracting("id").containsExactly(pendiente.getId());
		assertThat(libroService.listar(
				"guerrero cronica", EstadoLecturaFiltro.LEIDOS, 0, 50).getLibros())
				.extracting("id").containsExactly(leido.getId());
		assertThat(libroService.listar(
				"guerrero cronica", EstadoLecturaFiltro.PENDIENTES, 0, 50).getLibros())
				.extracting("id").containsExactly(pendiente.getId());
		assertThat(libroService.listar(
				"guerrero inexistente", EstadoLecturaFiltro.TODOS, 0, 50).getLibros()).isEmpty();
	}

	@Test
	void combinaBusquedaConFiltrosDeLectura() {
		guardarLibro("Saga norte", true, List.of("Autora"), 1);
		guardarLibro("Saga sur", false, List.of("Autora"), 1);
		guardarLibro("Independiente", true, List.of("Autor"), 1);

		assertThat(libroService.listar(null, EstadoLecturaFiltro.LEIDOS, 0, 50).getTotalResultados())
				.isEqualTo(2);
		assertThat(libroService.listar(null, EstadoLecturaFiltro.PENDIENTES, 0, 50).getTotalResultados())
				.isEqualTo(1);
		assertThat(libroService.listar("saga", EstadoLecturaFiltro.LEIDOS, 0, 50).getLibros())
				.extracting("titulo").containsExactly("Saga norte");
		assertThat(libroService.listar("saga", EstadoLecturaFiltro.PENDIENTES, 0, 50).getLibros())
				.extracting("titulo").containsExactly("Saga sur");
	}

	@Test
	void excluyeHistorialDeListadoBusquedaYContadores() {
		guardarLibro("Disponible leído", true, List.of("Autor"), 1);
		guardarLibro("Disponible pendiente", false, List.of("Autor"), 1);
		guardarLibro("Solo historial", true, List.of("Autor"), 0);

		ResumenBibliotecaResponse resumen = libroService.obtenerResumen();

		assertThat(libroService.listar(null, EstadoLecturaFiltro.TODOS, 0, 50).getTotalResultados())
				.isEqualTo(2);
		assertThat(libroService.listar("historial", EstadoLecturaFiltro.TODOS, 0, 50).getLibros())
				.isEmpty();
		assertThat(resumen.getTotalLibros()).isEqualTo(2);
		assertThat(resumen.getTotalLeidos()).isEqualTo(1);
		assertThat(resumen.getTotalPendientes()).isEqualTo(1);
		assertThat(resumen.getTotalLibros())
				.isEqualTo(resumen.getTotalLeidos() + resumen.getTotalPendientes());
	}

	@Test
	void totalResultadosCorrespondeAlConjuntoFiltradoNoALaPagina() {
		guardarLibro("Coincidencia uno", false, List.of("Autor"), 1);
		guardarLibro("Coincidencia dos", false, List.of("Autor"), 1);
		guardarLibro("Coincidencia tres", false, List.of("Autor"), 1);

		LibroPaginadoResponse respuesta = libroService.listar(
				"coincidencia", EstadoLecturaFiltro.PENDIENTES, 0, 2);

		assertThat(respuesta.getLibros()).hasSize(2);
		assertThat(respuesta.getTotalResultados()).isEqualTo(3);
		assertThat(respuesta.getTotalPaginas()).isEqualTo(2);
	}

	@Test
	void devuelveDetalleDeUnaYVariasCopiasSinHash() {
		Libro unaCopia = guardarLibro("Una copia", false, List.of("Autor"), 1);
		Libro variasCopias = guardarLibro("Varias copias", false, List.of("Autor"), 3);

		List<CopiaLibroResponse> una = libroService.obtenerCopias(unaCopia.getId());
		List<CopiaLibroResponse> varias = libroService.obtenerCopias(variasCopias.getId());

		assertThat(una).singleElement().satisfies(copia -> {
			assertThat(copia.getNombreArchivo()).isEqualTo("Una copia-1.epub");
			assertThat(copia.getExtension()).isEqualTo("epub");
			assertThat(copia.getTamanioBytes()).isEqualTo(1001);
			assertThat(copia.getUltimaModificacion()).isNotNull();
		});
		assertThat(varias).hasSize(3);
		assertThat(varias).extracting(CopiaLibroResponse::getRuta).isSortedAccordingTo(
				String.CASE_INSENSITIVE_ORDER);
	}

	@Test
	void dosAutoresYUnaCopiaNoMultiplicanArchivosNiFormatos() {
		Libro libro = guardarLibro(
				"Obra compartida", false, List.of("Autora Uno", "Autor Dos"), 1);

		LibroPaginadoResponse listado = libroService.listar(
				"autora uno", EstadoLecturaFiltro.TODOS, 0, 50);

		assertThat(listado.getTotalResultados()).isEqualTo(1);
		assertThat(listado.getLibros()).singleElement().satisfies(resultado -> {
			assertThat(resultado.getId()).isEqualTo(libro.getId());
			assertThat(resultado.getNumeroArchivos()).isEqualTo(1);
			assertThat(resultado.getFormatos()).containsExactly("epub");
		});
		assertThat(libroService.obtenerCopias(libro.getId()))
				.singleElement()
				.extracting(CopiaLibroResponse::getRuta)
				.isEqualTo(libro.getArchivos().getFirst().getRuta());
	}

	@Test
	void tresAutoresYDosCopiasNoDuplicanLibroNiRutasEnListadoBusquedaOFiltros() {
		Libro libro = guardarLibro(
				"Obra plural", true, List.of("Autor Uno", "Autor Dos", "Autor Tres"), 2);

		for (EstadoLecturaFiltro filtro : List.of(EstadoLecturaFiltro.TODOS, EstadoLecturaFiltro.LEIDOS)) {
			LibroPaginadoResponse listado = libroService.listar("autor", filtro, 0, 1);
			assertThat(listado.getTotalResultados()).isEqualTo(1);
			assertThat(listado.getLibros()).singleElement().satisfies(resultado -> {
				assertThat(resultado.getId()).isEqualTo(libro.getId());
				assertThat(resultado.getNumeroArchivos()).isEqualTo(2);
				assertThat(resultado.getFormatos()).containsExactly("epub");
			});
		}

		List<CopiaLibroResponse> copias = libroService.obtenerCopias(libro.getId());
		assertThat(copias).hasSize(2);
		assertThat(copias).extracting(CopiaLibroResponse::getId).doesNotHaveDuplicates();
		assertThat(copias).extracting(CopiaLibroResponse::getRuta).doesNotHaveDuplicates();
	}

	@Test
	void historialDevuelveCopiasVaciasYLibroInexistenteProduce404() throws Exception {
		Libro historial = guardarLibro("Historial", true, List.of("Autor"), 0);

		assertThat(libroService.obtenerCopias(historial.getId())).isEmpty();
		assertThatThrownBy(() -> libroService.obtenerCopias(Long.MAX_VALUE))
				.isInstanceOf(RecursoNoEncontradoException.class);
		mockMvc.perform(get("/api/libros/{id}/copias", Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("No existe ningún libro con el ID " + Long.MAX_VALUE));
	}

	@Test
	void parametrosInvalidosProducen400Comprensible() throws Exception {
		mockMvc.perform(get("/api/libros").param("pagina", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("La página no puede ser negativa"));
		mockMvc.perform(get("/api/libros").param("tamano", "0"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/libros").param("tamano", "101"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/libros").param("estado", "DESCONOCIDO"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El estado debe ser TODOS, LEIDOS o PENDIENTES"));
	}

	@Test
	void cambioDeEstadoPersisteYUsa404ParaIdInexistente() throws Exception {
		Libro libro = guardarLibro("Cambio", false, List.of("Autor"), 1);

		mockMvc.perform(patch("/api/libros/{id}/leido", libro.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"leido\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.leido").value(true));
		assertThat(libroRepository.findById(libro.getId()).orElseThrow().isLeido()).isTrue();

		mockMvc.perform(patch("/api/libros/{id}/leido", Long.MAX_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"leido\":true}"))
				.andExpect(status().isNotFound());
	}

	private Libro guardarLibro(
			String titulo,
			boolean leido,
			List<String> nombresAutores,
			int numeroCopias) {
		Libro libro = new Libro();
		libro.setTitulo(titulo);
		libro.setLeido(leido);
		libro.setAutores(new LinkedHashSet<>());
		libro.setArchivos(new ArrayList<>());

		for (String nombreAutor : nombresAutores) {
			Autor autor = new Autor();
			autor.setNombre(nombreAutor);
			autor.setLibros(new LinkedHashSet<>());
			autor.getLibros().add(libro);
			libro.getAutores().add(autor);
		}

		for (int numero = 1; numero <= numeroCopias; numero++) {
			int secuencia = secuenciaRuta.incrementAndGet();
			ArchivoLibro archivo = new ArchivoLibro();
			archivo.setNombreArchivo(titulo + "-" + numero + ".epub");
			archivo.setExtension("epub");
			archivo.setRuta(String.format("C:\\biblioteca\\%04d-%s.epub", secuencia, titulo));
			archivo.setTamanioBytes(1000L + numero);
			archivo.setUltimaModificacion(Instant.parse("2026-01-01T10:00:00Z").plusSeconds(numero));
			archivo.setSha256(String.format("%064x", secuencia));
			archivo.setLibro(libro);
			libro.getArchivos().add(archivo);
		}

		return libroRepository.saveAndFlush(libro);
	}
}

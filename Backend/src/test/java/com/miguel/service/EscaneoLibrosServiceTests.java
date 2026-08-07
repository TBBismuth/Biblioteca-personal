package com.miguel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.FileVisitor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.miguel.model.ArchivoLibro;
import com.miguel.model.Libro;
import com.miguel.repository.ArchivoLibroRepository;
import com.miguel.repository.AutorRepository;
import com.miguel.repository.ConfiguracionRepository;
import com.miguel.repository.LibroRepository;
import com.miguel.service.EscaneoLibrosService.ResultadoEscaneo;

@SpringBootTest
class EscaneoLibrosServiceTests {

	@Autowired
	private EscaneoLibrosService escaneoService;
	@Autowired
	private LibroService libroService;
	@Autowired
	private ArchivoLibroRepository archivoRepository;
	@Autowired
	private LibroRepository libroRepository;
	@Autowired
	private AutorRepository autorRepository;
	@Autowired
	private ConfiguracionRepository configuracionRepository;

	@TempDir
	Path temporal;

	@BeforeEach
	void limpiarBaseDePruebas() {
		archivoRepository.deleteAll();
		libroRepository.deleteAll();
		autorRepository.deleteAll();
		configuracionRepository.deleteAll();
	}

	@Test
	void escaneoInicialCalculaHashYRegistraArchivo() throws IOException {
		crearLibro(temporal, "Autora - Libro.epub", "contenido inicial");

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(resultado.archivosEncontrados()).isEqualTo(1);
		assertThat(resultado.archivosNuevos()).isEqualTo(1);
		assertThat(resultado.librosNuevos()).isEqualTo(1);
		assertThat(resultado.detallesArchivosNuevos()).singleElement().satisfies(detalle -> {
			assertThat(detalle.nombreArchivo()).isEqualTo("Autora - Libro.epub");
			assertThat(detalle.ruta()).isEqualTo(
					temporal.resolve("Autora - Libro.epub").toAbsolutePath().normalize().toString());
		});
		assertThat(resultado.detallesArchivosNuevos()).hasSize(resultado.archivosNuevos());
		assertThat(resultado.detallesArchivosDesaparecidos()).isEmpty();
		ArchivoLibro archivo = archivoRepository.findAll().getFirst();
		assertThat(archivo.getSha256()).hasSize(64);
	}

	@Test
	void reescaneoSinCambiosConservaElRegistro() throws IOException {
		crearLibro(temporal, "Autor - Libro.pdf", "sin cambios");
		escaneoService.escanearCarpeta(temporal.toString());
		Long archivoId = archivoRepository.findAll().getFirst().getId();

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(resultado.archivosYaRegistrados()).isEqualTo(1);
		assertThat(resultado.archivosNuevos()).isZero();
		assertThat(resultado.detallesArchivosNuevos()).isEmpty();
		assertThat(resultado.detallesArchivosDesaparecidos()).isEmpty();
		assertThat(archivoRepository.findAll().getFirst().getId()).isEqualTo(archivoId);
	}

	@Test
	void reescaneoCompletaHashFaltanteDeUnRegistroAntiguo() throws IOException {
		crearLibro(temporal, "Autor - Antiguo.pdf", "registro anterior");
		escaneoService.escanearCarpeta(temporal.toString());
		ArchivoLibro antiguo = archivoRepository.findAll().getFirst();
		Long id = antiguo.getId();
		antiguo.setSha256(null);
		archivoRepository.save(antiguo);

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(resultado.archivosYaRegistrados()).isEqualTo(1);
		assertThat(archivoRepository.findById(id).orElseThrow().getSha256()).hasSize(64);
	}

	@Test
	void archivoModificadoEnLaMismaRutaActualizaHashYConservaLibro() throws Exception {
		Path archivo = crearLibro(temporal, "Autor - Modificado.pdf", "contenido uno");
		escaneoService.escanearCarpeta(temporal.toString());
		ArchivoLibro antes = archivoRepository.findAll().getFirst();
		Long archivoId = antes.getId();
		Long libroId = antes.getLibro().getId();
		String hashAnterior = antes.getSha256();
		Thread.sleep(5);
		Files.writeString(archivo, "contenido completamente diferente");

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		ArchivoLibro despues = archivoRepository.findAll().getFirst();
		assertThat(resultado.archivosModificados()).isEqualTo(1);
		assertThat(despues.getId()).isEqualTo(archivoId);
		assertThat(despues.getLibro().getId()).isEqualTo(libroId);
		assertThat(despues.getSha256()).isNotEqualTo(hashAnterior);
	}

	@Test
	void moverArchivoConservaArchivoLibroYEstadoLeido() throws IOException {
		Path original = crearLibro(temporal, "Autor - Libro.pdf", "contenido movido");
		escaneoService.escanearCarpeta(temporal.toString());
		ArchivoLibro antes = archivoRepository.findAll().getFirst();
		Long archivoId = antes.getId();
		Long libroId = antes.getLibro().getId();
		libroService.cambiarEstadoLectura(libroId, true);
		Path subcarpeta = Files.createDirectory(temporal.resolve("subcarpeta"));
		Files.move(original, subcarpeta.resolve(original.getFileName()));

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		ArchivoLibro despues = archivoRepository.findAll().getFirst();
		assertThat(resultado.archivosMovidosRenombrados()).isEqualTo(1);
		assertThat(despues.getId()).isEqualTo(archivoId);
		assertThat(despues.getLibro().getId()).isEqualTo(libroId);
		assertThat(libroRepository.findById(libroId).orElseThrow().isLeido()).isTrue();
	}

	@Test
	void renombrarArchivoConNombreInvalidoSeSiguePorHashYSeInforma() throws IOException {
		Path original = crearLibro(temporal, "Autor - Título.pdf", "contenido renombrado");
		escaneoService.escanearCarpeta(temporal.toString());
		Long archivoId = archivoRepository.findAll().getFirst().getId();
		Files.move(original, temporal.resolve("Nombre que ya no sigue el formato.pdf"));

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(resultado.archivosMovidosRenombrados()).isEqualTo(1);
		assertThat(resultado.nombresInvalidos()).isEqualTo(1);
		assertThat(resultado.detallesInvalidos()).singleElement()
				.asString().contains("Nombre que ya no sigue el formato.pdf");
		assertThat(archivoRepository.findAll().getFirst()).satisfies(archivo -> {
			assertThat(archivo.getId()).isEqualTo(archivoId);
			assertThat(archivo.getNombreArchivo()).isEqualTo("Nombre que ya no sigue el formato.pdf");
		});
		assertThat(libroRepository.findAll()).singleElement()
				.extracting(Libro::getTitulo).isEqualTo("Título");
	}

	@Test
	void renombradoManualReasociaLaCopiaConservandoIdHashLecturaEHistorial() throws IOException {
		Path original = crearLibro(temporal, "Título antiguo - Autor antiguo.epub", "contenido");
		escaneoService.escanearCarpeta(temporal.toString());
		ArchivoLibro antes = archivoRepository.findAll().getFirst();
		Long archivoId = antes.getId();
		Long libroAnteriorId = antes.getLibro().getId();
		String hash = antes.getSha256();
		libroService.cambiarEstadoLectura(libroAnteriorId, true);
		Files.move(original, temporal.resolve("Autor correcto - Título correcto.epub"));

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		ArchivoLibro despues = archivoRepository.findAll().getFirst();
		assertThat(resultado.archivosMovidosRenombrados()).isEqualTo(1);
		assertThat(resultado.archivosNuevos()).isZero();
		assertThat(resultado.archivosDesaparecidosEliminados()).isZero();
		assertThat(resultado.librosNuevos()).isEqualTo(1);
		assertThat(despues.getId()).isEqualTo(archivoId);
		assertThat(despues.getSha256()).isEqualTo(hash);
		Libro libroNuevo = libroRepository.findAll().stream()
				.filter(libro -> libro.getTitulo().equals("Título correcto")).findFirst().orElseThrow();
		assertThat(despues.getLibro().getId()).isEqualTo(libroNuevo.getId());
		assertThat(libroNuevo.isLeido()).isTrue();
		assertThat(libroRepository.findById(libroAnteriorId)).get().satisfies(anterior -> {
			assertThat(anterior.getTitulo()).isEqualTo("Autor antiguo");
			assertThat(anterior.isLeido()).isTrue();
		});
		assertThat(libroService.listar("correcto autor", EstadoLecturaFiltro.TODOS, 0, 50)
				.getLibros()).singleElement().satisfies(libro -> {
			assertThat(libro.getNumeroArchivos()).isEqualTo(1);
			assertThat(libro.getFormatos()).containsExactly("epub");
		});
	}

	@Test
	void renombrarSoloUnaDeDosCopiasReasociaUnicamenteEsaCopia() throws IOException {
		Path primera = crearLibro(temporal, "Autor - Original.epub", "igual");
		Path segunda = temporal.resolve("Autor - Original [duplicado 2].epub");
		Files.copy(primera, segunda);
		escaneoService.escanearCarpeta(temporal.toString());
		Long libroAnterior = libroRepository.findAll().getFirst().getId();
		Long idSegunda = archivoRepository.findByRuta(segunda.toAbsolutePath().toString()).orElseThrow().getId();
		Files.move(segunda, temporal.resolve("Otra autora - Nueva obra.epub"));

		escaneoService.escanearCarpeta(temporal.toString());

		Long libroNuevo = libroRepository.findAll().stream()
				.filter(libro -> libro.getTitulo().equals("Nueva obra"))
				.map(Libro::getId).findFirst().orElseThrow();
		assertThat(archivoRepository.findById(idSegunda)).get()
				.extracting(archivo -> archivo.getLibro().getId()).isEqualTo(libroNuevo);
		assertThat(archivoRepository.findByRuta(primera.toAbsolutePath().toString())).get()
				.extracting(archivo -> archivo.getLibro().getId()).isEqualTo(libroAnterior);
		assertThat(libroService.obtenerCopias(libroAnterior)).hasSize(1);
	}

	@Test
	void renombrarTodasLasCopiasReutilizaUnSoloLibroNuevo() throws IOException {
		Path primera = crearLibro(temporal, "Autor - Original.pdf", "igual");
		Path segunda = temporal.resolve("Autor - Original [duplicado 2].pdf");
		Files.copy(primera, segunda);
		escaneoService.escanearCarpeta(temporal.toString());
		Files.move(primera, temporal.resolve("Nueva autora - Destino.pdf"));
		Files.move(segunda, temporal.resolve("Nueva autora - Destino [versión 2].pdf"));

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(resultado.archivosMovidosRenombrados()).isEqualTo(2);
		assertThat(resultado.librosNuevos()).isEqualTo(1);
		assertThat(archivoRepository.findAll()).hasSize(2)
				.extracting(archivo -> archivo.getLibro().getId())
				.containsOnly(archivoRepository.findAll().getFirst().getLibro().getId());
	}

	@Test
	void renombradoReutilizaLibroDestinoSinAlterarSusCopiasNiLectura() throws IOException {
		Path origen = crearLibro(temporal, "Autor origen - Obra origen.epub", "origen");
		Path destinoExistente = crearLibro(temporal, "Autor destino - Obra destino.pdf", "destino");
		escaneoService.escanearCarpeta(temporal.toString());
		Libro destino = archivoRepository.findByRuta(destinoExistente.toAbsolutePath().toString())
				.orElseThrow().getLibro();
		libroService.cambiarEstadoLectura(destino.getId(), false);
		Files.move(origen, temporal.resolve("Autor destino - Obra destino.epub"));

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(resultado.librosNuevos()).isZero();
		assertThat(libroService.obtenerCopias(destino.getId())).hasSize(2)
				.extracting("ruta").contains(destinoExistente.toAbsolutePath().toString());
		assertThat(libroRepository.findById(destino.getId())).get()
				.extracting(Libro::isLeido).isEqualTo(false);
	}

	@Test
	void cambiarSoloSufijoMantieneLaIdentidadLogica() throws IOException {
		Path original = crearLibro(temporal, "Autor - Obra [duplicado 2].pdf", "contenido");
		escaneoService.escanearCarpeta(temporal.toString());
		ArchivoLibro antes = archivoRepository.findAll().getFirst();
		Long libroId = antes.getLibro().getId();
		Long archivoId = antes.getId();
		Files.move(original, temporal.resolve("Autor - Obra [versión 7].pdf"));

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(resultado.librosNuevos()).isZero();
		assertThat(archivoRepository.findAll()).singleElement().satisfies(archivo -> {
			assertThat(archivo.getId()).isEqualTo(archivoId);
			assertThat(archivo.getLibro().getId()).isEqualTo(libroId);
		});
	}

	@Test
	void hashIgualConIdentidadesValidasDistintasNoFuerzaElMismoLibro() throws IOException {
		crearLibro(temporal, "Autora uno - Obra uno.txt", "");
		crearLibro(temporal, "Autor dos - Obra dos.txt", "");

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(resultado.librosNuevos()).isEqualTo(2);
		assertThat(resultado.copiasIdenticasNuevas()).isZero();
		assertThat(libroRepository.findAll()).extracting(Libro::getTitulo)
				.containsExactlyInAnyOrder("Obra uno", "Obra dos");
		assertThat(archivoRepository.findAll()).extracting(archivo -> archivo.getLibro().getId())
				.doesNotHaveDuplicates();
	}

	@Test
	void renombradoQueIntercambiaTituloYAutorActualizaDatosYSegundoEscaneoEsEstable() throws IOException {
		Path original = crearLibro(temporal, "Título real - Autor real.pdf", "contenido");
		escaneoService.escanearCarpeta(temporal.toString());
		Files.move(original, temporal.resolve("Autor real - Título real.pdf"));
		escaneoService.escanearCarpeta(temporal.toString());

		ResultadoEscaneo segundo = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(libroService.listar("titulo autor", EstadoLecturaFiltro.TODOS, 0, 50).getLibros())
				.singleElement().satisfies(libro -> {
					assertThat(libro.getTitulo()).isEqualTo("Título real");
					assertThat(libro.getAutores()).containsExactly("Autor real");
				});
		assertThat(segundo.archivosNuevos()).isZero();
		assertThat(segundo.archivosDesaparecidosEliminados()).isZero();
		assertThat(segundo.archivosMovidosRenombrados()).isZero();
		assertThat(segundo.archivosYaRegistrados()).isEqualTo(1);
	}

	@Test
	void copiaIdenticaCreaSegundoArchivoDelMismoLibro() throws IOException {
		Path original = crearLibro(temporal, "Autor - Libro.epub", "contenido duplicado");
		escaneoService.escanearCarpeta(temporal.toString());
		Long libroId = archivoRepository.findAll().getFirst().getLibro().getId();
		Files.copy(original, temporal.resolve("Autor - Libro [duplicado 2].epub"));

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(resultado.copiasIdenticasNuevas()).isEqualTo(1);
		assertThat(resultado.archivosNuevos()).isEqualTo(1);
		assertThat(archivoRepository.findAll()).hasSize(2)
				.allMatch(archivo -> archivo.getLibro().getId().equals(libroId));
		String hash = archivoRepository.findAll().getFirst().getSha256();
		assertThat(archivoRepository.findAll())
				.allMatch(archivo -> archivo.getSha256().equals(hash));
	}

	@Test
	void admiteVariasCopiasIdenticasSinMultiplicarRegistros() throws IOException {
		Path original = crearLibro(temporal, "Autor - Varias.fb2", "tres copias");
		escaneoService.escanearCarpeta(temporal.toString());
		Files.copy(original, temporal.resolve("Autor - Varias [duplicado 2].fb2"));
		Files.copy(original, temporal.resolve("Autor - Varias [duplicado 3].fb2"));

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());
		ResultadoEscaneo reescaneo = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(resultado.copiasIdenticasNuevas()).isEqualTo(2);
		assertThat(resultado.detallesArchivosNuevos())
				.extracting(detalle -> Path.of(detalle.ruta()).getFileName().toString())
				.containsExactly(
						"Autor - Varias [duplicado 2].fb2",
						"Autor - Varias [duplicado 3].fb2");
		assertThat(resultado.detallesArchivosNuevos())
				.allSatisfy(detalle -> assertThat(detalle.ruta()).startsWith(temporal.toString()));
		assertThat(resultado.detallesArchivosNuevos()).hasSize(resultado.archivosNuevos());
		assertThat(archivoRepository.count()).isEqualTo(3);
		assertThat(reescaneo.archivosYaRegistrados()).isEqualTo(3);
		assertThat(reescaneo.archivosNuevos()).isZero();
	}

	@Test
	void eliminarArchivoConservaLibroPeroLoOcultaDelListado() throws IOException {
		Path archivo = crearLibro(temporal, "Autor - Historia.pdf", "historial");
		escaneoService.escanearCarpeta(temporal.toString());
		Long libroId = libroRepository.findAll().getFirst().getId();
		libroService.cambiarEstadoLectura(libroId, true);
		Files.delete(archivo);

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(resultado.archivosDesaparecidosEliminados()).isEqualTo(1);
		assertThat(resultado.detallesArchivosDesaparecidos()).singleElement().satisfies(detalle -> {
			assertThat(detalle.nombreArchivo()).isEqualTo("Autor - Historia.pdf");
			assertThat(detalle.ruta()).isEqualTo(archivo.toAbsolutePath().normalize().toString());
		});
		assertThat(resultado.detallesArchivosDesaparecidos())
				.hasSize(resultado.archivosDesaparecidosEliminados());
		assertThat(archivoRepository.findAll()).isEmpty();
		assertThat(libroRepository.findById(libroId)).get().extracting(Libro::isLeido).isEqualTo(true);
		assertThat(libroService.listar(null, EstadoLecturaFiltro.TODOS, 0, 50).getLibros()).isEmpty();
		assertThat(libroService.listar("Historia", EstadoLecturaFiltro.TODOS, 0, 50).getLibros()).isEmpty();
	}

	@Test
	void desaparicionDeUnaCopiaIdenticaDevuelveSuRutaCorrecta() throws IOException {
		Path original = crearLibro(temporal, "Autor - Copias.pdf", "contenido compartido");
		Path copia = temporal.resolve("Autor - Copias [duplicado 2].pdf");
		Files.copy(original, copia);
		escaneoService.escanearCarpeta(temporal.toString());
		Files.delete(original);

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(resultado.archivosDesaparecidosEliminados()).isEqualTo(1);
		assertThat(resultado.detallesArchivosDesaparecidos()).singleElement().satisfies(detalle -> {
			assertThat(detalle.nombreArchivo()).isEqualTo("Autor - Copias.pdf");
			assertThat(detalle.ruta()).isEqualTo(original.toAbsolutePath().normalize().toString());
		});
		assertThat(archivoRepository.findAll()).singleElement()
				.extracting(ArchivoLibro::getRuta)
				.isEqualTo(copia.toAbsolutePath().normalize().toString());
	}

	@Test
	void archivoQueReapareceRecuperaElEstadoLeido() throws IOException {
		Path archivo = crearLibro(temporal, "Autor - Regreso.mobi", "regresa");
		escaneoService.escanearCarpeta(temporal.toString());
		Long libroId = libroRepository.findAll().getFirst().getId();
		libroService.cambiarEstadoLectura(libroId, true);
		Files.delete(archivo);
		escaneoService.escanearCarpeta(temporal.toString());
		crearLibro(temporal, "Autor - Regreso.mobi", "regresa");

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(temporal.toString());

		assertThat(resultado.librosNuevos()).isZero();
		assertThat(libroRepository.count()).isEqualTo(1);
		assertThat(libroRepository.findById(libroId).orElseThrow().isLeido()).isTrue();
	}

	@Test
	void rutaInexistenteNoEliminaRegistros() throws IOException {
		crearLibro(temporal, "Autor - Seguro.txt", "no borrar");
		escaneoService.escanearCarpeta(temporal.toString());
		Long archivoId = archivoRepository.findAll().getFirst().getId();

		assertThatThrownBy(() -> escaneoService.escanearCarpeta(temporal.resolve("no-existe").toString()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("no existe");
		assertThat(archivoRepository.findAll()).singleElement()
				.extracting(ArchivoLibro::getId).isEqualTo(archivoId);
	}

	@Test
	void recorridoIncompletoNoEjecutaLaSincronizacion() throws IOException {
		SincronizacionLibrosService sincronizacion = mock(SincronizacionLibrosService.class);
		Sha256Service sha256 = mock(Sha256Service.class);
		EscaneoLibrosService servicioAislado = new EscaneoLibrosService(
				sha256, sincronizacion, new CoordinadorBiblioteca());
		Path raiz = temporal.toAbsolutePath().normalize();
		when(sincronizacion.cargarEstadoArchivos()).thenReturn(List.of());

		try (MockedStatic<Files> archivos = mockStatic(Files.class, CALLS_REAL_METHODS)) {
			archivos.when(() -> Files.walkFileTree(
					eq(raiz), org.mockito.ArgumentMatchers.<FileVisitor<Path>>any()))
					.thenThrow(new IOException("fallo de recorrido simulado"));

			assertThatThrownBy(() -> servicioAislado.escanearCarpeta(raiz.toString()))
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("No se pudo recorrer completamente");
		}

		verify(sincronizacion, never()).sincronizar(anyList(), anyInt(), anyList());
	}

	@Test
	void cambiarCarpetaCompletaConservaIdsYLectura() throws IOException {
		Path antigua = Files.createDirectory(temporal.resolve("antigua"));
		Path nueva = Files.createDirectory(temporal.resolve("nueva"));
		crearLibro(antigua, "Autor - Mudanza.azw3", "misma biblioteca");
		escaneoService.escanearCarpeta(antigua.toString());
		ArchivoLibro antes = archivoRepository.findAll().getFirst();
		Long archivoId = antes.getId();
		Long libroId = antes.getLibro().getId();
		libroService.cambiarEstadoLectura(libroId, true);
		crearLibro(nueva, "Autor - Mudanza.azw3", "misma biblioteca");

		ResultadoEscaneo resultado = escaneoService.escanearCarpeta(nueva.toString());

		assertThat(resultado.archivosMovidosRenombrados()).isEqualTo(1);
		assertThat(resultado.archivosDesaparecidosEliminados()).isZero();
		assertThat(archivoRepository.findAll()).singleElement().satisfies(archivo -> {
			assertThat(archivo.getId()).isEqualTo(archivoId);
			assertThat(archivo.getRuta()).startsWith(nueva.toAbsolutePath().toString());
		});
		assertThat(libroRepository.findById(libroId).orElseThrow().isLeido()).isTrue();
	}

	private Path crearLibro(Path carpeta, String nombre, String contenido) throws IOException {
		return Files.writeString(carpeta.resolve(nombre), contenido);
	}
}

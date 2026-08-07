package com.miguel.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import com.miguel.dto.CambiarEstadoLecturaRequest;
import com.miguel.dto.CopiaLibroResponse;
import com.miguel.dto.LibroPaginadoResponse;
import com.miguel.dto.LibroResponse;
import com.miguel.dto.ResumenBibliotecaResponse;
import com.miguel.dto.ActualizarLibroRequest;
import com.miguel.dto.ActualizarLibroResponse;
import com.miguel.dto.ResultadoEliminacionResponse;
import com.miguel.service.EliminacionLibroService;
import com.miguel.service.EstadoLecturaFiltro;
import com.miguel.service.LibroService;
import com.miguel.service.RenombradoLibroService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/libros")
public class LibroController {
	private final LibroService libroService;
	private final RenombradoLibroService renombradoLibroService;
	private final EliminacionLibroService eliminacionLibroService;

	public LibroController(
			LibroService libroService,
			RenombradoLibroService renombradoLibroService,
			EliminacionLibroService eliminacionLibroService) {
		this.libroService = libroService;
		this.renombradoLibroService = renombradoLibroService;
		this.eliminacionLibroService = eliminacionLibroService;
	}

	@GetMapping
	public LibroPaginadoResponse listar(
			@RequestParam(required = false) String busqueda,
			@RequestParam(defaultValue = "TODOS") String estado,
			@RequestParam(defaultValue = "0") int pagina,
			@RequestParam(defaultValue = "50") int tamano) {
		return libroService.listar(
				busqueda,
				EstadoLecturaFiltro.desde(estado),
				pagina,
				tamano
		);
	}

	@GetMapping("/resumen")
	public ResumenBibliotecaResponse obtenerResumen() {
		return libroService.obtenerResumen();
	}

	@GetMapping("/{id}/copias")
	public List<CopiaLibroResponse> obtenerCopias(@PathVariable Long id) {
		return libroService.obtenerCopias(id);
	}

	@PatchMapping("/{id}/leido")
	public LibroResponse cambiarEstadoLectura(
			@PathVariable Long id,
			@Valid @RequestBody CambiarEstadoLecturaRequest request) {
		return libroService.cambiarEstadoLectura(id, request.getLeido());
	}

	@PutMapping("/{id}")
	public ActualizarLibroResponse actualizarLibro(
			@PathVariable Long id,
			@RequestBody ActualizarLibroRequest request) {
		return renombradoLibroService.actualizar(id, request);
	}

	@DeleteMapping("/{idLibro}/copias/{idArchivo}")
	public ResultadoEliminacionResponse eliminarCopia(
			@PathVariable Long idLibro, @PathVariable Long idArchivo) {
		return eliminacionLibroService.eliminarCopia(idLibro, idArchivo);
	}

	@DeleteMapping("/{idLibro}/copias")
	public ResultadoEliminacionResponse eliminarTodasLasCopias(@PathVariable Long idLibro) {
		return eliminacionLibroService.eliminarTodas(idLibro);
	}
}

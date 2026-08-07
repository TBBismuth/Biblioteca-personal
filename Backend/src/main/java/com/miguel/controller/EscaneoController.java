package com.miguel.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.miguel.dto.ResultadoEscaneoResponse;
import com.miguel.dto.DetalleArchivoEscaneoResponse;
import com.miguel.service.ConfiguracionService;
import com.miguel.service.EscaneoLibrosService;
import com.miguel.service.EscaneoLibrosService.ResultadoEscaneo;

@RestController
@RequestMapping("/api/escaneo")
public class EscaneoController {
	private final EscaneoLibrosService escaneoLibrosService;
	private final ConfiguracionService configuracionService;

	public EscaneoController(EscaneoLibrosService escaneoLibrosService, ConfiguracionService configuracionService) {
		this.escaneoLibrosService = escaneoLibrosService;
		this.configuracionService = configuracionService;
	}

	@PostMapping
	public ResultadoEscaneoResponse escanearCarpeta() {
		String ruta = configuracionService.obtenerRutaLibros();
		
		ResultadoEscaneo resultado = escaneoLibrosService.escanearCarpeta(ruta);

		return new ResultadoEscaneoResponse(
				resultado.archivosEncontrados(),
				resultado.archivosNuevos(),
				resultado.librosNuevos(),
				resultado.archivosYaRegistrados(),
				resultado.archivosMovidosRenombrados(),
				resultado.copiasIdenticasNuevas(),
				resultado.archivosModificados(),
				resultado.archivosDesaparecidosEliminados(),
				resultado.nombresInvalidos(),
				resultado.erroresLectura(),
				resultado.detallesInvalidos(),
				resultado.detallesErrores(),
				resultado.detallesArchivosNuevos().stream()
						.map(detalle -> new DetalleArchivoEscaneoResponse(
								detalle.nombreArchivo(), detalle.ruta()))
						.toList(),
				resultado.detallesArchivosDesaparecidos().stream()
						.map(detalle -> new DetalleArchivoEscaneoResponse(
								detalle.nombreArchivo(), detalle.ruta()))
						.toList()
		);
	}
}

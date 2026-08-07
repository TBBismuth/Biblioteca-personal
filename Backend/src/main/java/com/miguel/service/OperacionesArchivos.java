package com.miguel.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.stereotype.Component;

@Component
public class OperacionesArchivos {
	public boolean existe(Path ruta) {
		return Files.exists(ruta);
	}

	public boolean esArchivoRegular(Path ruta) {
		return Files.isRegularFile(ruta);
	}

	public boolean esDirectorio(Path ruta) {
		return Files.isDirectory(ruta);
	}

	public void mover(Path origen, Path destino) throws IOException {
		Files.move(origen, destino);
	}
}

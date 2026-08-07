package com.miguel.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

@Service
public class Sha256Service {

	public String calcular(Path archivo) throws IOException {
		MessageDigest digest = crearDigest();

		try (InputStream entrada = Files.newInputStream(archivo);
				DigestInputStream entradaConHash = new DigestInputStream(entrada, digest)) {
			entradaConHash.transferTo(OutputStreamNulo.INSTANCE);
		}

		return HexFormat.of().formatHex(digest.digest());
	}

	private MessageDigest crearDigest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 no está disponible en esta instalación de Java", e);
		}
	}

	private static final class OutputStreamNulo extends java.io.OutputStream {
		private static final OutputStreamNulo INSTANCE = new OutputStreamNulo();

		@Override
		public void write(int dato) {
			// Descarta los datos: DigestInputStream ya actualiza el hash.
		}

		@Override
		public void write(byte[] datos, int desplazamiento, int longitud) {
			// Descarta los datos: DigestInputStream ya actualiza el hash.
		}
	}
}

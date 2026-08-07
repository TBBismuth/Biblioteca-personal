package com.miguel.service;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class NormalizadorBiblioteca {
	public String normalizarTexto(String texto) {
		String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "");
		return sinTildes.toLowerCase(Locale.ROOT)
				.replaceAll("[^\\p{L}\\p{N}]+", " ")
				.trim().replaceAll("\\s+", " ");
	}

	public String crearClaveLibro(String titulo, Collection<String> autores) {
		String autoresNormalizados = autores.stream()
				.map(this::normalizarTexto)
				.sorted()
				.reduce("", (resultado, autor) -> resultado.isEmpty() ? autor : resultado + "|" + autor);
		return normalizarTexto(titulo) + "||" + autoresNormalizados;
	}
}

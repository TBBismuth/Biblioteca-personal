package com.miguel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miguel.model.ArchivoLibro;

public interface ArchivoLibroRepository extends JpaRepository<ArchivoLibro, Long> {

	Optional<ArchivoLibro> findByRuta(String ruta);

	boolean existsByRuta(String ruta);
}
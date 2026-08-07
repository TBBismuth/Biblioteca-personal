package com.miguel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miguel.model.Autor;

public interface AutorRepository extends JpaRepository<Autor, Long> {

	Optional<Autor> findByNombreIgnoreCase(String nombre);
}
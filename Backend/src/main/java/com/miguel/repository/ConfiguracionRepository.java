package com.miguel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.miguel.config.Configuracion;

public interface ConfiguracionRepository extends JpaRepository<Configuracion, Long> {
	Optional<Configuracion> findFirstByOrderByIdAsc();
}
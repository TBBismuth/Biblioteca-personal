package com.miguel.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.miguel.model.Libro;

public interface LibroRepository extends JpaRepository<Libro, Long> {

	@Query("""
			select distinct libro
			from Libro libro
			left join fetch libro.autores
			where libro.archivos is not empty
			""")
	List<Libro> findDisponiblesConAutores();

	@Query("""
			select distinct libro
			from Libro libro
			left join fetch libro.autores
			where libro.id = :id
			""")
	Optional<Libro> findConAutoresById(@Param("id") Long id);

	@Query("""
			select distinct libro
			from Libro libro
			left join fetch libro.archivos
			where libro.id = :id
			""")
	Optional<Libro> findConArchivosById(@Param("id") Long id);

	@Query("""
			select distinct libro
			from Libro libro
			left join fetch libro.archivos
			where libro.id in :ids
			""")
	List<Libro> findConArchivosByIdIn(@Param("ids") List<Long> ids);

	@Query("select distinct libro from Libro libro left join fetch libro.autores")
	List<Libro> findTodosConAutores();

	@Query("select count(libro) from Libro libro where libro.archivos is not empty")
	long contarDisponibles();

	@Query("select count(libro) from Libro libro where libro.archivos is not empty and libro.leido = true")
	long contarDisponiblesLeidos();
}

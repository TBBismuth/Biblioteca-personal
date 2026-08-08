package com.miguel;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BibliotecaPersonalApplicationTests {
	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoads() {
		assertThat(dataSource).isNotNull();
	}

	@Test
	void utilizaExclusivamenteH2EnMemoriaDuranteLasPruebas() throws Exception {
		try (var conexion = dataSource.getConnection()) {
			assertThat(conexion.getMetaData().getURL())
					.startsWith("jdbc:h2:mem:biblioteca_test");
		}
	}

}

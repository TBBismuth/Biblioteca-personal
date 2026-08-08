package com.miguel.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigTests {
	@Autowired
	private MockMvc mockMvc;

	@Test
	void permiteElOrigenTauriDeWindows() throws Exception {
		comprobarOrigenPermitido(CorsConfig.ORIGEN_TAURI_WINDOWS);
	}

	@Test
	void permiteElOrigenViteDelProyecto() throws Exception {
		comprobarOrigenPermitido(CorsConfig.ORIGEN_VITE_DESARROLLO);
	}

	@Test
	void rechazaUnOrigenExternoArbitrario() throws Exception {
		mockMvc.perform(preflight("https://example.com", "DELETE"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

	private void comprobarOrigenPermitido(String origen) throws Exception {
		mockMvc.perform(preflight(origen, "DELETE"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origen))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
						"GET,POST,PUT,PATCH,DELETE,OPTIONS"));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder preflight(
			String origen, String metodo) {
		return options("/api/libros")
				.header(HttpHeaders.ORIGIN, origen)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, metodo)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type");
	}
}

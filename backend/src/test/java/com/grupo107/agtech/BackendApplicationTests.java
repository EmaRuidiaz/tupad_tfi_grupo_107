package com.grupo107.agtech;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BackendApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void pingRespondeUp() throws Exception {
		mockMvc.perform(get("/api/ping"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void healthCheckEsPublico() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void corsPermiteAlFrontendDeVite() throws Exception {
		mockMvc.perform(options("/api/ping")
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void esquemaYDatosSemillaSeCargaronEnH2() {
		Integer tablas = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_schema) = 'public'", Integer.class);
		Integer lotes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lote", Integer.class);
		assertEquals(14, tablas);
		assertEquals(3, lotes);
	}

}

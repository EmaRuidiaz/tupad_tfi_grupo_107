package com.grupo107.agtech;

import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criterios de aceptación de HU-14 (Autenticación y Roles con JWT).
 * Usa los usuarios semilla de database/data.sql (contraseña de desarrollo: agtech123).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthSecurityTests.EndpointsDePrueba.class)
class AuthSecurityTests {

	private static final String PASSWORD = "agtech123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${app.jwt.secret}")
	private String jwtSecret;

	/** Endpoints sólo para tests: ejercitan @PreAuthorize según la matriz de permisos. */
	@RestController
	@RequestMapping("/api/test-auth")
	static class EndpointsDePrueba {

		@GetMapping("/autenticado")
		Map<String, String> autenticado() {
			return Map.of("ok", "true");
		}

		@GetMapping("/solo-admin")
		@PreAuthorize("hasRole('ADMIN')")
		Map<String, String> soloAdmin() {
			return Map.of("ok", "true");
		}

		@GetMapping("/admin-o-agronomo")
		@PreAuthorize("hasAnyRole('ADMIN', 'AGRONOMO')")
		Map<String, String> adminOAgronomo() {
			return Map.of("ok", "true");
		}
	}

	private ResultActions login(String username, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
	}

	private String token(String username) throws Exception {
		String body = login(username, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	private String bearer(String username) throws Exception {
		return "Bearer " + token(username);
	}

	private String tokenFirmado(String subject, String secret, Date expiracion) {
		return Jwts.builder()
				.setSubject(subject)
				.setIssuedAt(new Date())
				.setExpiration(expiracion)
				.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
				.compact();
	}

	// --- Criterio 1: login y token ---

	@Test
	void loginValidoDevuelveTokenConRolYExpiracionDe8Horas() throws Exception {
		String body = login("admin", PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.type").value("Bearer"))
				.andExpect(jsonPath("$.username").value("admin"))
				.andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"))
				.andReturn().getResponse().getContentAsString();

		Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
		Duration vigencia = Duration.between(Instant.now(), expiresAt);
		assertTrue(vigencia.compareTo(Duration.ofHours(8)) <= 0 && vigencia.compareTo(Duration.ofMinutes(479)) > 0,
				"El token debe vencer en 8 horas, pero vence en " + vigencia);
	}

	@Test
	void cadaUsuarioSemillaRecibeSuRol() throws Exception {
		login("agronomo1", PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ROLE_AGRONOMO"));
		login("operario1", PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ROLE_OPERARIO"));
	}

	@Test
	void loginConPasswordIncorrectoDevuelve401() throws Exception {
		login("admin", "incorrecta")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos"));
	}

	@Test
	void loginConUsuarioInexistenteDevuelveElMismoMensajeQueConPasswordIncorrecto() throws Exception {
		login("nadie", PASSWORD)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos"));
	}

	@Test
	void loginSinCamposObligatoriosDevuelve400() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void loginSoloAceptaPost() throws Exception {
		mockMvc.perform(get("/api/auth/login")).andExpect(status().isUnauthorized());
	}

	// --- Criterio 2: BCrypt ---

	@Test
	void contraseniasSemillaEstanAlmacenadasConBcryptFactor10() {
		Integer sinBcrypt = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM usuario WHERE password_hash NOT LIKE '$2a$10$%'", Integer.class);
		assertEquals(0, sinBcrypt);
	}

	// --- Criterio 3: 401 sin token / token inválido / vencido ---

	@Test
	void sinTokenDevuelve401() throws Exception {
		mockMvc.perform(get("/api/test-auth/autenticado"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void conTokenValidoSePuedeAccederAEndpointProtegido() throws Exception {
		mockMvc.perform(get("/api/test-auth/autenticado").header("Authorization", bearer("operario1")))
				.andExpect(status().isOk());
	}

	@Test
	void conTokenInventadoDevuelve401() throws Exception {
		mockMvc.perform(get("/api/test-auth/autenticado").header("Authorization", "Bearer abc.def.ghi"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void conTokenVencidoDevuelve401() throws Exception {
		String vencido = tokenFirmado("admin", jwtSecret, new Date(System.currentTimeMillis() - 60_000));
		mockMvc.perform(get("/api/test-auth/autenticado").header("Authorization", "Bearer " + vencido))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void conTokenFirmadoConOtraClaveDevuelve401() throws Exception {
		String ajeno = tokenFirmado("admin", "otra-clave-secreta-distinta-de-al-menos-32-bytes",
				new Date(System.currentTimeMillis() + 60_000));
		mockMvc.perform(get("/api/test-auth/autenticado").header("Authorization", "Bearer " + ajeno))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void conTokenManipuladoDevuelve401() throws Exception {
		String token = token("admin");
		int idx = token.lastIndexOf('.') + 5;
		char original = token.charAt(idx);
		char distinto = original == 'A' ? 'B' : 'A';
		String manipulado = token.substring(0, idx) + distinto + token.substring(idx + 1);
		mockMvc.perform(get("/api/test-auth/autenticado").header("Authorization", "Bearer " + manipulado))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void conTokenDeUsuarioInexistenteDevuelve401() throws Exception {
		String fantasma = tokenFirmado("usuario-borrado", jwtSecret, new Date(System.currentTimeMillis() + 60_000));
		mockMvc.perform(get("/api/test-auth/autenticado").header("Authorization", "Bearer " + fantasma))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void endpointsPublicosNoRequierenToken() throws Exception {
		mockMvc.perform(get("/api/ping")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void otrosEndpointsDeActuatorRequierenAutenticacion() throws Exception {
		mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
	}

	// --- Criterio 4: 403 según rol (@PreAuthorize) ---

	@Test
	void adminAccedeAEndpointSoloAdmin() throws Exception {
		mockMvc.perform(get("/api/test-auth/solo-admin").header("Authorization", bearer("admin")))
				.andExpect(status().isOk());
	}

	@Test
	void agronomoYOperarioRecibe403EnEndpointSoloAdmin() throws Exception {
		mockMvc.perform(get("/api/test-auth/solo-admin").header("Authorization", bearer("agronomo1")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.status").value(403));
		mockMvc.perform(get("/api/test-auth/solo-admin").header("Authorization", bearer("operario1")))
				.andExpect(status().isForbidden());
	}

	@Test
	void endpointParaVariosRolesPermiteSoloAEsosRoles() throws Exception {
		mockMvc.perform(get("/api/test-auth/admin-o-agronomo").header("Authorization", bearer("admin"))).andExpect(status().isOk());
		mockMvc.perform(get("/api/test-auth/admin-o-agronomo").header("Authorization", bearer("agronomo1"))).andExpect(status().isOk());
		mockMvc.perform(get("/api/test-auth/admin-o-agronomo").header("Authorization", bearer("operario1"))).andExpect(status().isForbidden());
	}

	// --- CORS en el login (el frontend corre en otro origen) ---

	@Test
	void corsPermiteLoginDesdeElFrontend() throws Exception {
		mockMvc.perform(options("/api/auth/login")
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers", "content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void corsNoPermiteOrigenesAjenos() throws Exception {
		mockMvc.perform(options("/api/auth/login")
						.header("Origin", "http://sitio-malicioso.example")
						.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isForbidden());
	}
}

package com.grupo107.agtech.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Respuestas de error de seguridad en JSON:
 * <ul>
 *   <li>{@code 401 Unauthorized}: falta el token, es inválido o está vencido.</li>
 *   <li>{@code 403 Forbidden}: el usuario está autenticado pero su rol no permite la operación.</li>
 * </ul>
 * Los mensajes son constantes (sin datos del request) para no exponer información ni requerir escape.
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String MSG_NO_AUTENTICADO = "Autenticación requerida: token ausente, inválido o vencido";
    static final String MSG_SIN_PERMISOS = "No tenés permisos para realizar esta operación";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, MSG_NO_AUTENTICADO);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, MSG_SIN_PERMISOS);
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + message + "\"}";
        response.getWriter().write(body);
    }
}

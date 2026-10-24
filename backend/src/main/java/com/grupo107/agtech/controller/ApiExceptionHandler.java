package com.grupo107.agtech.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

/**
 * Traduce errores de la capa web a respuestas JSON con el mismo formato que los errores
 * de seguridad ({@code status}, {@code error}, {@code message}).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(int status, String error, String message) {
    }

    /** Usuario inexistente o contraseña incorrecta: mismo mensaje para no revelar cuál de los dos falló. */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> credencialesInvalidas(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacion(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, detalle);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> formatoJsonInvalido(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Formato de petición o valor enumerado inválido");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> estadoRespuesta(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return build(status, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integridadDatos(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "Operación rechazada por restricción de integridad de datos");
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), status.getReasonPhrase(), message));
    }
}

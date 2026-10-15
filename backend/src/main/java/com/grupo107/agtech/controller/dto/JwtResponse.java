package com.grupo107.agtech.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
public class JwtResponse {
    private String token;
    private String type = "Bearer";
    private String username;
    private List<String> roles;
    /** Instante (UTC) en que vence el token. */
    private Instant expiresAt;

    public JwtResponse(String token, String username, List<String> roles, Instant expiresAt) {
        this.token = token;
        this.username = username;
        this.roles = roles;
        this.expiresAt = expiresAt;
    }
}

package com.grupo107.agtech.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Permite usar @PreAuthorize en los controllers
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final JsonSecurityErrorHandler errorHandler;
    private final boolean h2ConsoleEnabled;
    private final String h2ConsolePath;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService,
                          JwtAuthenticationFilter jwtAuthFilter,
                          JsonSecurityErrorHandler errorHandler,
                          @Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled,
                          @Value("${spring.h2.console.path:/h2-console}") String h2ConsolePath) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthFilter = jwtAuthFilter;
        this.errorHandler = errorHandler;
        this.h2ConsoleEnabled = h2ConsoleEnabled;
        this.h2ConsolePath = h2ConsolePath;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Factor de costo 10 (HU-14, criterio 2).
        return new BCryptPasswordEncoder(10);
    }

    /**
     * El filtro JWT es un @Component, por lo que Spring Boot lo registraría también como filtro
     * de servlet. Se deshabilita ese registro: sólo debe ejecutarse dentro de la cadena de seguridad.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandler)   // 401: sin token / token inválido o vencido
                        .accessDeniedHandler(errorHandler))       // 403: rol sin permiso
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/ping").permitAll()
                        .requestMatchers("/actuator/health").permitAll();
                    // La consola H2 existe sólo en dev (spring.h2.console.enabled=true).
                    if (h2ConsoleEnabled) {
                        auth.requestMatchers(h2ConsolePath + "/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                });

        if (h2ConsoleEnabled) {
            // La consola H2 usa frames del mismo origen.
            http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        }

        http.authenticationProvider(authenticationProvider());
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

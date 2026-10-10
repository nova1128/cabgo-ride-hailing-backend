package com.example.cab.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import com.example.cab.model.Role;
import com.example.cab.model.Trip;

/**
 * CONCEPT: stateless JWT authentication + role-based authorisation (RBAC).
 *
 * FLOW: login returns a signed token -> client sends "Authorization: Bearer <token>" on every call ->
 *       Spring verifies the signature (no database lookup, no server session) -> we read role/profileId
 *       from the token's claims.
 * ALTERNATIVES: server-side sessions (hard to scale across instances), OAuth2/Keycloak (more setup),
 *       RS256 asymmetric keys (better when many services verify tokens; HS256 is fine for one app).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protects cookie-based sessions; we use bearer tokens, so it is not needed.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // The frontend files and monitoring endpoints are public.
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus",
                                "/actuator/info").permitAll()
                        // Login/registration is public.
                        .requestMatchers("/api/v1/auth/**", "/api/v1/config").permitAll()
                        // Role rules: drivers only touch /drivers, riders only /riders.
                        .requestMatchers("/api/v1/drivers/**").hasRole("DRIVER")
                        .requestMatchers("/api/v1/riders/**").hasRole("RIDER")
                        // Trip actions: who may do what.
                        .requestMatchers(HttpMethod.POST, "/api/v1/trips", "/api/v1/trips/quote").hasRole("RIDER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/trips/*/start", "/api/v1/trips/*/complete")
                        .hasRole("DRIVER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/trips/*/rating", "/api/v1/trips/*/payment")
                        .hasRole("RIDER")
                        // Everything else just needs a valid token (services then check ownership).
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    // Turns the token's "role" claim (e.g. DRIVER) into the authority ROLE_DRIVER that hasRole("DRIVER") checks.
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // The shared secret used to sign and verify tokens. Must be at least 32 characters.
    @Bean
    public SecretKey jwtSecretKey(@Value("${cab.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("cab.jwt.secret must be at least 32 characters");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}

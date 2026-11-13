package com.diseconstech.ordenes.seguridad;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AppProperties.class)
public class SeguridadConfig {

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, RespuestasSeguridad respuestas,
                                JwtAuthenticationConverter jwtConverter) throws Exception {
        http
                // JWT viaja en el header Authorization, no en cookies: CSRF no aplica
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // el resto es el frontend estático (Angular) servido por la misma app/proxy
                        .anyRequest().permitAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(respuestas.noAutenticado())
                        .accessDeniedHandler(respuestas.accesoDenegado()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(respuestas.noAutenticado())
                        .accessDeniedHandler(respuestas.accesoDenegado()));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // HS256 con secreto compartido: hay un solo servicio que emite y valida, no hace falta par de llaves.
    @Bean
    SecretKey claveJwt(AppProperties props) {
        return new SecretKeySpec(props.jwt().secreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey clave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey clave) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        // Además de firma y expiración, exige que lo hayamos emitido nosotros
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TokenService.EMISOR));
        return decoder;
    }

    /** Claim "roles": ["ADMIN","APROBADOR"] a autoridades ROLE_ADMIN, ROLE_APROBADOR (para hasRole). */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName(TokenService.CLAIM_ROLES);
        autoridades.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(autoridades);
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(props.cors().origenes());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}

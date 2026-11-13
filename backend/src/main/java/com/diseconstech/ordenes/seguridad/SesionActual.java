package com.diseconstech.ordenes.seguridad;

import com.diseconstech.ordenes.error.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Punto único para saber quién hace la petición. Los servicios nunca confían en ids enviados por el cliente. */
@Component
public class SesionActual {

    public Optional<UsuarioActual> opcional() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            List<String> roles = jwt.getClaimAsStringList(TokenService.CLAIM_ROLES);
            return Optional.of(new UsuarioActual(
                    ((Number) jwt.getClaim(TokenService.CLAIM_UID)).longValue(),
                    jwt.getSubject(),
                    jwt.getClaimAsString(TokenService.CLAIM_NOMBRE),
                    new HashSet<>(roles == null ? List.of() : roles)));
        }
        return Optional.empty();
    }

    public UsuarioActual actual() {
        return opcional().orElseThrow(() -> ApiException.noAutenticado("Sesión no válida"));
    }
}

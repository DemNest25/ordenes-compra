package com.diseconstech.ordenes.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** Propiedades "app.*" de application.yml, tipadas y validadas al arrancar. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Ldap ldap, Cors cors) {

    public record Jwt(String secreto, Duration expiracion) {
        public Jwt {
            if (secreto == null || secreto.length() < 32) {
                throw new IllegalStateException("app.jwt.secreto debe tener al menos 32 caracteres (HS256)");
            }
        }
    }

    public record Ldap(String busquedaBase, String filtroUsuario) {
    }

    public record Cors(List<String> origenes) {
    }
}

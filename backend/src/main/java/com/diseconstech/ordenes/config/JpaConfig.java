package com.diseconstech.ordenes.config;

import com.diseconstech.ordenes.seguridad.SesionActual;
import com.diseconstech.ordenes.seguridad.UsuarioActual;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Activa @CreatedDate/@CreatedBy/... en los maestros.
 * modifyOnCreate=false: al crear, actualizado_en/por quedan en null (solo se llenan al modificar).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditor", dateTimeProviderRef = "ahora", modifyOnCreate = false)
public class JpaConfig {

    /** Id del usuario del JWT de la petición; vacío fuera de una petición autenticada (ej. login). */
    @Bean
    AuditorAware<Long> auditor(SesionActual sesion) {
        return () -> sesion.opcional().map(UsuarioActual::id);
    }

    // Por defecto Spring Data usa LocalDateTime; las columnas son TIMESTAMPTZ a OffsetDateTime.
    @Bean
    DateTimeProvider ahora() {
        return () -> Optional.of(OffsetDateTime.now());
    }
}

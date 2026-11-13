package com.diseconstech.ordenes.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Columnas de auditoría comunes a los maestros (departamentos, proveedores, usuarios).
 * Spring Data las llena solo; el "quién" sale del AuditorAware (usuario autenticado).
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class EntidadAuditable {

    @Column(nullable = false)
    private boolean eliminado = false;

    @CreatedDate
    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @CreatedBy
    @Column(name = "creado_por", updatable = false)
    private Long creadoPor;

    @LastModifiedDate
    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    @LastModifiedBy
    @Column(name = "actualizado_por")
    private Long actualizadoPor;
}

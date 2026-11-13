package com.diseconstech.ordenes.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Auditoría append-only. Los valores anterior/nuevo se guardan como JSON en texto:
 * el servicio de bitácora los serializa, así no dependemos del mapeo JSON de Hibernate.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "bitacora")
public class Bitacora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private OffsetDateTime fecha;

    // Solo el id (sin FK mapeada): la bitácora no debe cargar ni depender del usuario
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 100)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccionBitacora accion;

    @Column(nullable = false, length = 50)
    private String entidad;

    @Column(name = "entidad_id")
    private Long entidadId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valores_anteriores", columnDefinition = "jsonb")
    private String valoresAnteriores;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valores_nuevos", columnDefinition = "jsonb")
    private String valoresNuevos;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @PrePersist
    void alCrear() {
        if (fecha == null) fecha = OffsetDateTime.now();
    }
}

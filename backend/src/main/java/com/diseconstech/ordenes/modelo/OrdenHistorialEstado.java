package com.diseconstech.ordenes.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

/** Append-only: @Immutable hace que Hibernate nunca emita UPDATE (la BD además lo bloquea). */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "orden_historial_estado")
public class OrdenHistorialEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id", nullable = false)
    private Orden orden;

    /** null = creación de la orden. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20)
    private EstadoOrden estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private EstadoOrden estadoNuevo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private OffsetDateTime fecha;

    @Column(length = 500)
    private String comentario;

    @PrePersist
    void alCrear() {
        if (fecha == null) fecha = OffsetDateTime.now();
    }
}

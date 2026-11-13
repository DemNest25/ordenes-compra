package com.diseconstech.ordenes.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "orden_detalle")
public class OrdenDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id", nullable = false, updatable = false)
    private Orden orden;

    @Column(nullable = false)
    private Short linea;

    @Column(nullable = false, length = 300)
    private String descripcion;

    @Column(name = "unidad_medida", nullable = false, length = 30)
    private String unidadMedida = "UNIDAD";

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 18, scale = 2)
    private BigDecimal precioUnitario;

    /** Columna GENERATED de la BD: solo lectura, Hibernate la relee tras INSERT/UPDATE. */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(precision = 18, scale = 2, insertable = false, updatable = false)
    private BigDecimal subtotal;

    @Column(nullable = false)
    private boolean eliminado = false;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = OffsetDateTime.now();
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = OffsetDateTime.now();
    }

    /** Para calcular el total antes de guardar (subtotal aún es null). */
    public BigDecimal calcularSubtotal() {
        return cantidad.multiply(precioUnitario).setScale(2, RoundingMode.HALF_UP);
    }
}

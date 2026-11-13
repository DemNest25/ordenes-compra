package com.diseconstech.ordenes.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "ordenes")
public class Orden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** OC-AAAA-000001: lo genera la BD (DEFAULT + secuencia). Hibernate lo relee tras el INSERT. */
    @Generated
    @Column(nullable = false, length = 20, unique = true, insertable = false, updatable = false)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creador_id", nullable = false, updatable = false)
    private Usuario creador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departamento_id", nullable = false)
    private Departamento departamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOrden estado = EstadoOrden.BORRADOR;

    @Column(nullable = false, length = 500)
    private String descripcion;

    @Column(columnDefinition = "text")
    private String observaciones;

    // CHAR(3) en la BD: sin esto, validate espera VARCHAR y la app no arranca.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String moneda = "GTQ";

    @Column(name = "monto_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal montoTotal = BigDecimal.ZERO;

    @Column(name = "requiere_segunda_aprobacion", nullable = false)
    private boolean requiereSegundaAprobacion = false;

    @Column(name = "fecha_envio")
    private OffsetDateTime fechaEnvio;

    @Column(name = "fecha_resolucion")
    private OffsetDateTime fechaResolucion;

    @Column(name = "fecha_cierre")
    private OffsetDateTime fechaCierre;

    @Column(name = "fecha_cancelacion")
    private OffsetDateTime fechaCancelacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motivo_cancelacion_id")
    private Catalogo motivoCancelacion;

    @Column(name = "comentario_cancelacion", length = 500)
    private String comentarioCancelacion;

    /** Bloqueo optimista: si dos personas editan a la vez, la segunda recibe conflicto. */
    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    private boolean eliminado = false;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    @Column(name = "actualizado_por")
    private Long actualizadoPor;

    // Sin orphanRemoval: quitar una línea es borrado lógico, nunca DELETE.
    // SQLRestriction oculta las líneas eliminadas al cargar la colección.
    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL)
    @SQLRestriction("eliminado = false")
    @OrderBy("linea")
    private List<OrdenDetalle> detalles = new ArrayList<>();

    @PrePersist
    void alCrear() {
        creadoEn = OffsetDateTime.now();
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = OffsetDateTime.now();
    }

    public void agregarDetalle(OrdenDetalle detalle) {
        detalle.setOrden(this);
        detalles.add(detalle);
    }

    /** Suma cantidad × precio de las líneas vivas (mismo redondeo que la columna generada). */
    public BigDecimal calcularTotal() {
        return detalles.stream()
                .filter(d -> !d.isEliminado())
                .map(OrdenDetalle::calcularSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

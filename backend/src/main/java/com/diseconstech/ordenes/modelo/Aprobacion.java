package com.diseconstech.ordenes.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "aprobaciones")
public class Aprobacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_id", nullable = false, updatable = false)
    private Orden orden;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aprobador_id", nullable = false, updatable = false)
    private Usuario aprobador;

    @Column(nullable = false, updatable = false)
    private Short nivel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private DecisionAprobacion decision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motivo_rechazo_id", updatable = false)
    private Catalogo motivoRechazo;

    @Column(length = 500, updatable = false)
    private String comentario;

    // Foto del momento de la decisión
    @Column(name = "monto_orden", nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal montoOrden;

    @Column(name = "limite_aprobador", nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal limiteAprobador;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime fecha;

    // Lo único que cambia después: se anula cuando la orden vuelve a Borrador
    @Column(nullable = false)
    private boolean vigente = true;

    @Column(name = "anulada_en")
    private OffsetDateTime anuladaEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulada_por")
    private Usuario anuladaPor;

    @PrePersist
    void alCrear() {
        if (fecha == null) fecha = OffsetDateTime.now();
    }

    public void anular(Usuario quien) {
        vigente = false;
        anuladaEn = OffsetDateTime.now();
        anuladaPor = quien;
    }
}

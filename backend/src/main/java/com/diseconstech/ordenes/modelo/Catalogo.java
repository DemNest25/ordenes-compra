package com.diseconstech.ordenes.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.time.OffsetDateTime;

/** Listas simples: motivos de rechazo/cancelación, unidades de medida. */
@Getter
@Setter
@Entity
@Table(name = "catalogo")
public class Catalogo {

    public static final String MOTIVO_RECHAZO = "MOTIVO_RECHAZO";
    public static final String MOTIVO_CANCELACION = "MOTIVO_CANCELACION";
    public static final String UNIDAD_MEDIDA = "UNIDAD_MEDIDA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(nullable = false, length = 50)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String descripcion;

    @Column(nullable = false)
    private Short orden = 0;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false)
    private boolean eliminado = false;

    @Generated
    @Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime creadoEn;
}

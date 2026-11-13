package com.diseconstech.ordenes.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Valores configurables (plazo de aprobación, intentos de login...). */
@Getter
@Setter
@Entity
@Table(name = "parametros")
public class Parametro {

    public static final String PLAZO_APROBACION_DIAS = "PLAZO_APROBACION_DIAS";
    public static final String MAX_INTENTOS_LOGIN = "MAX_INTENTOS_LOGIN";
    public static final String MINUTOS_BLOQUEO = "MINUTOS_BLOQUEO";
    public static final String MONEDA = "MONEDA";

    @Id
    @Column(length = 60)
    private String clave;

    @Column(nullable = false, length = 200)
    private String valor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_dato", nullable = false, length = 20)
    private TipoDato tipoDato;

    @Column(nullable = false, length = 300)
    private String descripcion;

    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    @Column(name = "actualizado_por")
    private Long actualizadoPor;

    public int comoEntero() {
        return Integer.parseInt(valor.trim());
    }
}

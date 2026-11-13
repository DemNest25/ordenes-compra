package com.diseconstech.ordenes.reporte;

import com.diseconstech.ordenes.seguridad.UsuarioActual;

import java.util.Arrays;
import java.util.Optional;

/** Catálogo de reportes y quién puede verlos. */
public enum TipoReporte {
    ORDENES_POR_ESTADO("ordenes-por-estado", "Órdenes por estado", Acceso.TODOS),
    RECHAZADAS("rechazadas", "Órdenes rechazadas", Acceso.TODOS),
    TIEMPO_APROBACION("tiempo-aprobacion", "Tiempo de aprobación", Acceso.TODOS),
    PENDIENTES_VENCIDAS("pendientes-vencidas", "Pendientes vencidas", Acceso.TODOS),
    GASTO_DEPARTAMENTO("gasto-departamento", "Gasto por departamento", Acceso.INTERNOS),
    GASTO_PROVEEDOR("gasto-proveedor", "Gasto por proveedor", Acceso.INTERNOS),
    HISTORIAL_PROPIO("historial-propio", "Historial de mis órdenes", Acceso.EXTERNOS);

    enum Acceso { TODOS, INTERNOS, EXTERNOS }

    private final String codigo;
    private final String titulo;
    private final Acceso acceso;

    TipoReporte(String codigo, String titulo, Acceso acceso) {
        this.codigo = codigo;
        this.titulo = titulo;
        this.acceso = acceso;
    }

    public String codigo() {
        return codigo;
    }

    public String titulo() {
        return titulo;
    }

    public boolean permitidoPara(UsuarioActual u) {
        return switch (acceso) {
            case TODOS -> true;
            case INTERNOS -> u.veTodo();
            case EXTERNOS -> !u.veTodo();
        };
    }

    public static Optional<TipoReporte> porCodigo(String codigo) {
        return Arrays.stream(values()).filter(t -> t.codigo.equals(codigo)).findFirst();
    }
}

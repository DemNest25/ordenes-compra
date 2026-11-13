package com.diseconstech.ordenes.servicio;

import com.diseconstech.ordenes.modelo.EstadoOrden;

import java.util.EnumSet;
import java.util.Set;

import static com.diseconstech.ordenes.modelo.EstadoOrden.*;

/**
 * Lo que un usuario puede hacer sobre una orden y desde qué estados.
 * Es la tabla de transiciones del CLAUDE.md en código: el servidor valida con esto
 * y el frontend recibe la lista de acciones permitidas para mostrar solo esos botones.
 */
public enum AccionOrden {
    EDITAR("modificar", true, BORRADOR),
    ELIMINAR("eliminar", true, BORRADOR),
    ENVIAR("enviar a aprobación", true, BORRADOR),
    RETIRAR("retirar", true, PENDIENTE),
    REABRIR("regresar a borrador", true, RECHAZADA, APROBADA),
    CERRAR("cerrar", true, APROBADA),
    CANCELAR("cancelar", true, BORRADOR, PENDIENTE, APROBADA),
    APROBAR("aprobar", false, PENDIENTE),
    RECHAZAR("rechazar", false, PENDIENTE);

    private final String verbo;
    private final boolean soloCreador;
    private final Set<EstadoOrden> estados;

    AccionOrden(String verbo, boolean soloCreador, EstadoOrden primero, EstadoOrden... resto) {
        this.verbo = verbo;
        this.soloCreador = soloCreador;
        this.estados = EnumSet.of(primero, resto);
    }

    public String verbo() {
        return verbo;
    }

    /** Acciones del asesor sobre lo suyo; las demás (aprobar/rechazar) son del aprobador. */
    public boolean soloCreador() {
        return soloCreador;
    }

    public boolean permiteEstado(EstadoOrden estado) {
        return estados.contains(estado);
    }
}

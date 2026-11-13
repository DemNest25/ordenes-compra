package com.diseconstech.ordenes.seguridad;

import com.diseconstech.ordenes.modelo.Rol;

import java.util.Set;

/** Lo que el servidor sabe del usuario de la petición (sale del JWT ya verificado). */
public record UsuarioActual(Long id, String username, String nombre, Set<String> roles) {

    public boolean esAdmin() {
        return roles.contains(Rol.ADMIN);
    }

    public boolean esAprobador() {
        return roles.contains(Rol.APROBADOR);
    }

    /** Internos (ADMIN/APROBADOR) ven todo; externos solo lo propio. */
    public boolean veTodo() {
        return esAdmin() || esAprobador();
    }
}

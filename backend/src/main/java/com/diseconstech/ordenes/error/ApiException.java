package com.diseconstech.ordenes.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Error de negocio con su código HTTP y, si aplica, el campo que lo causó.
 * Una sola clase con fábricas estáticas en vez de una jerarquía de excepciones.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String campo;

    public ApiException(HttpStatus status, String campo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.campo = campo;
    }

    public static ApiException noEncontrado(String que, Object id) {
        return new ApiException(HttpStatus.NOT_FOUND, null, que + " " + id + " no existe");
    }

    /** Regla de negocio incumplida (422). */
    public static ApiException regla(String mensaje) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, null, mensaje);
    }

    /** Regla de negocio incumplida atribuible a un campo concreto (422). */
    public static ApiException regla(String campo, String mensaje) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, campo, mensaje);
    }

    public static ApiException conflicto(String mensaje) {
        return new ApiException(HttpStatus.CONFLICT, null, mensaje);
    }

    public static ApiException prohibido(String mensaje) {
        return new ApiException(HttpStatus.FORBIDDEN, null, mensaje);
    }

    public static ApiException noAutenticado(String mensaje) {
        return new ApiException(HttpStatus.UNAUTHORIZED, null, mensaje);
    }

    public static ApiException bloqueado(String mensaje) {
        return new ApiException(HttpStatus.LOCKED, null, mensaje);
    }

    public static ApiException noDisponible(String mensaje) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, null, mensaje);
    }
}

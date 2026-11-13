package com.diseconstech.ordenes.error;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Formato único de error para toda la API (validación, negocio, seguridad).
 * "errores" trae el detalle por campo para que el frontend lo pinte junto al input.
 */
public record ErrorApi(
        int status,
        String error,
        String mensaje,
        String ruta,
        OffsetDateTime fecha,
        List<ErrorCampo> errores) {

    public record ErrorCampo(String campo, String mensaje) {
    }

    public static ErrorApi de(int status, String error, String mensaje, String ruta, List<ErrorCampo> errores) {
        return new ErrorApi(status, error, mensaje, ruta, OffsetDateTime.now(), errores == null ? List.of() : errores);
    }
}

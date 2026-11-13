package com.diseconstech.ordenes.reporte;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Estructura común a todos los reportes. El tipo de cada columna le dice al PDF y al frontend
 * cómo formatear (ej. MONEDA → "Q 1,234.56"); los valores viajan crudos (BigDecimal, fecha ISO).
 */
public record Reporte(
        String codigo,
        String titulo,
        String alcance,
        String generadoPor,
        OffsetDateTime generadoEn,
        List<Columna> columnas,
        List<List<Object>> filas,
        List<Object> totales) {

    public enum Tipo { TEXTO, ENTERO, DECIMAL, MONEDA, FECHA_HORA }

    public record Columna(String titulo, Tipo tipo) {
    }
}

package com.diseconstech.ordenes.reporte;

import com.diseconstech.ordenes.error.ApiException;
import com.diseconstech.ordenes.reporte.Reporte.Columna;
import com.diseconstech.ordenes.reporte.Reporte.Tipo;
import com.diseconstech.ordenes.seguridad.SesionActual;
import com.diseconstech.ordenes.seguridad.UsuarioActual;
import com.diseconstech.ordenes.servicio.ParametroService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reportes con SQL directo (JdbcTemplate): son agregaciones de solo lectura y en SQL se leen mejor
 * que en JPQL. Se consultan EN VIVO en cada petición: no hay caché, ni de Hibernate ni HTTP.
 * El alcance por perfil (todas vs. solo las propias) se aplica aquí, en el WHERE.
 */
@Service
@Transactional(readOnly = true)
public class ReporteService {

    private static final String ESTADOS_GASTO = "('APROBADA','CERRADA')";

    private final NamedParameterJdbcTemplate jdbc;
    private final SesionActual sesion;
    private final ParametroService parametros;

    public ReporteService(NamedParameterJdbcTemplate jdbc, SesionActual sesion, ParametroService parametros) {
        this.jdbc = jdbc;
        this.sesion = sesion;
        this.parametros = parametros;
    }

    public List<TipoReporte> disponibles() {
        UsuarioActual u = sesion.actual();
        return Arrays.stream(TipoReporte.values()).filter(t -> t.permitidoPara(u)).toList();
    }

    public Reporte generar(String codigo, LocalDate desde, LocalDate hasta) {
        TipoReporte tipo = TipoReporte.porCodigo(codigo)
                .orElseThrow(() -> ApiException.noEncontrado("El reporte", codigo));
        UsuarioActual u = sesion.actual();
        if (!tipo.permitidoPara(u)) {
            throw ApiException.prohibido("No tienes acceso al reporte \"" + tipo.titulo() + "\"");
        }
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw ApiException.regla("hasta", "La fecha final no puede ser anterior a la inicial");
        }

        Consulta q = new Consulta(u, desde, hasta);
        return switch (tipo) {
            case ORDENES_POR_ESTADO -> porEstado(tipo, q);
            case RECHAZADAS -> rechazadas(tipo, q);
            case TIEMPO_APROBACION -> tiempoAprobacion(tipo, q);
            case PENDIENTES_VENCIDAS -> pendientesVencidas(tipo, q);
            case GASTO_DEPARTAMENTO -> gastoPor(tipo, q, "d.nombre", "Departamento",
                    "JOIN oc.departamentos d ON d.id = o.departamento_id");
            case GASTO_PROVEEDOR -> gastoPor(tipo, q, "p.razon_social || ' (NIT ' || p.nit || ')'", "Proveedor",
                    "JOIN oc.proveedores p ON p.id = o.proveedor_id");
            case HISTORIAL_PROPIO -> historialPropio(tipo, q);
        };
    }

    // ================================================================== reportes

    private Reporte porEstado(TipoReporte t, Consulta q) {
        // LEFT JOIN desde la lista de estados: aparecen todos, aunque tengan 0 órdenes
        String sql = """
                SELECT e.estado, COUNT(o.id), COALESCE(SUM(o.monto_total), 0)
                  FROM (VALUES ('BORRADOR',1),('PENDIENTE',2),('APROBADA',3),
                               ('RECHAZADA',4),('CERRADA',5),('CANCELADA',6)) AS e(estado, pos)
                  LEFT JOIN oc.ordenes o ON o.estado = e.estado %s
                 GROUP BY e.estado, e.pos
                 ORDER BY e.pos""".formatted(q.filtro("o"));
        List<Columna> cols = List.of(col("Estado", Tipo.TEXTO), col("Cantidad", Tipo.ENTERO), col("Monto total", Tipo.MONEDA));
        List<List<Object>> filas = consultar(sql, q, cols);
        return reporte(t, q, cols, filas, List.of("Total", sumar(filas, 1), sumar(filas, 2)));
    }

    /** Cada rechazo es un evento: si la orden se corrigió y reenvió, el rechazo sigue apareciendo. */
    private Reporte rechazadas(TipoReporte t, Consulta q) {
        String sql = """
                SELECT o.numero, o.descripcion, cr.nombre_completo, p.razon_social, o.monto_total,
                       a.fecha, ap.nombre_completo, a.nivel, c.descripcion, a.comentario, o.estado
                  FROM oc.ordenes o
                  JOIN oc.aprobaciones a  ON a.orden_id = o.id AND a.decision = 'RECHAZADA'
                  JOIN oc.usuarios cr     ON cr.id = o.creador_id
                  JOIN oc.usuarios ap     ON ap.id = a.aprobador_id
                  JOIN oc.proveedores p   ON p.id = o.proveedor_id
                  LEFT JOIN oc.catalogo c ON c.id = a.motivo_rechazo_id
                 WHERE TRUE %s
                 ORDER BY a.fecha DESC""".formatted(q.filtro("o"));
        List<Columna> cols = List.of(col("Número", Tipo.TEXTO), col("Descripción", Tipo.TEXTO),
                col("Creador", Tipo.TEXTO), col("Proveedor", Tipo.TEXTO), col("Monto", Tipo.MONEDA),
                col("Fecha rechazo", Tipo.FECHA_HORA), col("Rechazó", Tipo.TEXTO), col("Nivel", Tipo.ENTERO),
                col("Motivo", Tipo.TEXTO), col("Comentario", Tipo.TEXTO), col("Estado actual", Tipo.TEXTO));
        List<List<Object>> filas = consultar(sql, q, cols);
        return reporte(t, q, cols, filas, null);
    }

    /** Desde el último envío hasta la aprobación final, en horas. */
    private Reporte tiempoAprobacion(TipoReporte t, Consulta q) {
        String sql = """
                SELECT o.numero, p.razon_social, o.monto_total, o.fecha_envio, o.fecha_resolucion,
                       ROUND(EXTRACT(EPOCH FROM (o.fecha_resolucion - o.fecha_envio)) / 3600, 2),
                       CASE WHEN o.requiere_segunda_aprobacion THEN 2 ELSE 1 END
                  FROM oc.ordenes o
                  JOIN oc.proveedores p ON p.id = o.proveedor_id
                 WHERE o.estado IN ('APROBADA','CERRADA')
                   AND o.fecha_envio IS NOT NULL AND o.fecha_resolucion IS NOT NULL %s
                 ORDER BY o.fecha_resolucion DESC""".formatted(q.filtro("o"));
        List<Columna> cols = List.of(col("Número", Tipo.TEXTO), col("Proveedor", Tipo.TEXTO),
                col("Monto", Tipo.MONEDA), col("Enviada", Tipo.FECHA_HORA), col("Aprobada", Tipo.FECHA_HORA),
                col("Horas", Tipo.DECIMAL), col("Niveles", Tipo.ENTERO));
        List<List<Object>> filas = consultar(sql, q, cols);
        BigDecimal promedio = filas.isEmpty() ? BigDecimal.ZERO
                : sumar(filas, 5).divide(BigDecimal.valueOf(filas.size()), 2, RoundingMode.HALF_UP);
        List<Object> totales = Arrays.asList("Promedio (" + filas.size() + " órdenes)", null, null, null, null, promedio, null);
        return reporte(t, q, cols, filas, totales);
    }

    private Reporte pendientesVencidas(TipoReporte t, Consulta q) {
        q.params.addValue("plazo", parametros.plazoAprobacionDias());
        String sql = """
                SELECT o.numero, o.descripcion, cr.nombre_completo, p.razon_social, o.monto_total, o.fecha_envio,
                       ROUND(EXTRACT(EPOCH FROM (now() - o.fecha_envio)) / 86400, 1),
                       CASE WHEN o.requiere_segunda_aprobacion THEN 'Nivel 2' ELSE 'Nivel 1' END
                  FROM oc.ordenes o
                  JOIN oc.usuarios cr   ON cr.id = o.creador_id
                  JOIN oc.proveedores p ON p.id = o.proveedor_id
                 WHERE o.estado = 'PENDIENTE'
                   AND o.fecha_envio < now() - make_interval(days => :plazo) %s
                 ORDER BY o.fecha_envio""".formatted(q.filtro("o"));
        List<Columna> cols = List.of(col("Número", Tipo.TEXTO), col("Descripción", Tipo.TEXTO),
                col("Creador", Tipo.TEXTO), col("Proveedor", Tipo.TEXTO), col("Monto", Tipo.MONEDA),
                col("Enviada", Tipo.FECHA_HORA), col("Días en espera", Tipo.DECIMAL), col("Falta", Tipo.TEXTO));
        List<List<Object>> filas = consultar(sql, q, cols);
        return reporte(t, q, cols, filas,
                Arrays.asList("Total (" + filas.size() + ")", null, null, null, sumar(filas, 4), null, null, null));
    }

    /** Gasto comprometido = órdenes Aprobadas o Cerradas. */
    private Reporte gastoPor(TipoReporte t, Consulta q, String agrupador, String titulo, String join) {
        String sql = """
                SELECT %s, COUNT(*), SUM(o.monto_total)
                  FROM oc.ordenes o
                  %s
                 WHERE o.estado IN %s %s
                 GROUP BY 1
                 ORDER BY 3 DESC""".formatted(agrupador, join, ESTADOS_GASTO, q.filtro("o"));
        List<Columna> cols = List.of(col(titulo, Tipo.TEXTO), col("Órdenes", Tipo.ENTERO), col("Monto", Tipo.MONEDA));
        List<List<Object>> filas = consultar(sql, q, cols);
        return reporte(t, q, cols, filas, List.of("Total", sumar(filas, 1), sumar(filas, 2)));
    }

    private Reporte historialPropio(TipoReporte t, Consulta q) {
        String sql = """
                SELECT o.numero, o.creado_en, o.descripcion, p.razon_social, o.monto_total, o.estado,
                       COALESCE(o.actualizado_en, o.creado_en)
                  FROM oc.ordenes o
                  JOIN oc.proveedores p ON p.id = o.proveedor_id
                 WHERE TRUE %s
                 ORDER BY o.creado_en DESC""".formatted(q.filtro("o"));
        List<Columna> cols = List.of(col("Número", Tipo.TEXTO), col("Creada", Tipo.FECHA_HORA),
                col("Descripción", Tipo.TEXTO), col("Proveedor", Tipo.TEXTO), col("Monto", Tipo.MONEDA),
                col("Estado", Tipo.TEXTO), col("Última actualización", Tipo.FECHA_HORA));
        List<List<Object>> filas = consultar(sql, q, cols);
        return reporte(t, q, cols, filas,
                Arrays.asList("Total (" + filas.size() + ")", null, null, null, sumar(filas, 4), null, null));
    }

    // ================================================================== soporte

    /** Filtros comunes: no eliminadas, alcance por perfil y rango de fechas (sobre la fecha de creación). */
    private static final class Consulta {
        final UsuarioActual usuario;
        final LocalDate desde;
        final LocalDate hasta;
        final MapSqlParameterSource params = new MapSqlParameterSource();

        Consulta(UsuarioActual usuario, LocalDate desde, LocalDate hasta) {
            this.usuario = usuario;
            this.desde = desde;
            this.hasta = hasta;
        }

        String filtro(String a) {
            StringBuilder sb = new StringBuilder(" AND NOT ").append(a).append(".eliminado");
            if (!usuario.veTodo()) {
                sb.append(" AND ").append(a).append(".creador_id = :uid");
                params.addValue("uid", usuario.id());
            }
            if (desde != null) {
                sb.append(" AND ").append(a).append(".creado_en >= :desde");
                params.addValue("desde", desde.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime());
            }
            if (hasta != null) {
                sb.append(" AND ").append(a).append(".creado_en < :hasta");
                params.addValue("hasta", hasta.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime());
            }
            return sb.toString();
        }

        String alcance() {
            String quien = usuario.veTodo() ? "Todas las órdenes" : "Solo mis órdenes";
            if (desde == null && hasta == null) return quien;
            return quien + " - creadas " + (desde != null ? "desde " + desde : "")
                    + (desde != null && hasta != null ? " " : "") + (hasta != null ? "hasta " + hasta : "");
        }
    }

    /** Lee cada columna según su tipo declarado: el SQL solo debe respetar el orden. */
    private List<List<Object>> consultar(String sql, Consulta q, List<Columna> cols) {
        return jdbc.query(sql, q.params, (rs, n) -> {
            List<Object> fila = new ArrayList<>(cols.size());
            for (int i = 0; i < cols.size(); i++) {
                fila.add(leer(rs, i + 1, cols.get(i).tipo()));
            }
            return fila;
        });
    }

    private static Object leer(ResultSet rs, int i, Tipo tipo) throws SQLException {
        Object v = switch (tipo) {
            case TEXTO -> rs.getString(i);
            case ENTERO -> rs.getLong(i);
            case DECIMAL, MONEDA -> rs.getBigDecimal(i);
            case FECHA_HORA -> rs.getObject(i, OffsetDateTime.class);
        };
        return rs.wasNull() ? null : v;
    }

    private static BigDecimal sumar(List<List<Object>> filas, int col) {
        return filas.stream().map(f -> f.get(col)).filter(v -> v != null)
                .map(v -> v instanceof BigDecimal b ? b : BigDecimal.valueOf(((Number) v).longValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Reporte reporte(TipoReporte t, Consulta q, List<Columna> cols, List<List<Object>> filas, List<Object> totales) {
        return new Reporte(t.codigo(), t.titulo(), q.alcance(), q.usuario.nombre(), OffsetDateTime.now(), cols, filas, totales);
    }

    private static Columna col(String titulo, Tipo tipo) {
        return new Columna(titulo, tipo);
    }
}

package com.diseconstech.ordenes.dto;

import com.diseconstech.ordenes.modelo.*;
import com.diseconstech.ordenes.servicio.AccionOrden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public final class OrdenDtos {

    private OrdenDtos() {
    }

    // ------------------------------------------------------------------ entrada

    public record DetalleRequest(
            @NotBlank(message = "La descripción del producto es obligatoria")
            @Size(max = 300, message = "Máximo 300 caracteres") String descripcion,
            @NotBlank(message = "La unidad de medida es obligatoria")
            @Size(max = 30, message = "Máximo 30 caracteres") String unidadMedida,
            @NotNull(message = "La cantidad es obligatoria")
            @Positive(message = "La cantidad debe ser mayor que cero")
            @Digits(integer = 10, fraction = 2, message = "La cantidad admite hasta 10 enteros y 2 decimales") BigDecimal cantidad,
            @NotNull(message = "El precio unitario es obligatorio")
            @PositiveOrZero(message = "El precio unitario no puede ser negativo")
            @Digits(integer = 16, fraction = 2, message = "El precio admite hasta 16 enteros y 2 decimales") BigDecimal precioUnitario) {
    }

    /** El monto total NO viene del cliente: el servidor lo calcula de las líneas. */
    public record OrdenRequest(
            @NotNull(message = "Selecciona el departamento") Long departamentoId,
            @NotNull(message = "Selecciona el proveedor") Long proveedorId,
            @NotBlank(message = "La descripción es obligatoria")
            @Size(max = 500, message = "Máximo 500 caracteres") String descripcion,
            @Size(max = 4000, message = "Máximo 4000 caracteres") String observaciones,
            @NotEmpty(message = "Agrega al menos una línea de detalle")
            @Size(max = 100, message = "Máximo 100 líneas por orden") List<@Valid DetalleRequest> detalles,
            /** Obligatoria al modificar (bloqueo optimista); se ignora al crear. */
            Integer version) {
    }

    /** Cuerpo común de las transiciones. motivoId: obligatorio al rechazar y al cancelar una aprobada. */
    public record AccionRequest(
            @NotNull(message = "Falta la versión de la orden (recarga la página)") Integer version,
            Long motivoId,
            @Size(max = 500, message = "Máximo 500 caracteres") String comentario) {
    }

    // ------------------------------------------------------------------ salida

    public record Ref(Long id, String nombre) {
        public static Ref de(Usuario u) {
            return u == null ? null : new Ref(u.getId(), u.getNombreCompleto());
        }

        public static Ref de(Departamento d) {
            return d == null ? null : new Ref(d.getId(), d.getNombre());
        }

        public static Ref de(Proveedor p) {
            return p == null ? null : new Ref(p.getId(), p.getRazonSocial());
        }

        public static Ref de(Catalogo c) {
            return c == null ? null : new Ref(c.getId(), c.getDescripcion());
        }
    }

    public record DetalleDto(Long id, Short linea, String descripcion, String unidadMedida,
                             BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
        public static DetalleDto de(OrdenDetalle d) {
            return new DetalleDto(d.getId(), d.getLinea(), d.getDescripcion(), d.getUnidadMedida(),
                    d.getCantidad(), d.getPrecioUnitario(),
                    d.getSubtotal() != null ? d.getSubtotal() : d.calcularSubtotal());
        }
    }

    public record AprobacionDto(Long id, Short nivel, DecisionAprobacion decision, Ref aprobador, Ref motivoRechazo,
                                String comentario, BigDecimal montoOrden, BigDecimal limiteAprobador,
                                OffsetDateTime fecha, boolean vigente) {
        public static AprobacionDto de(Aprobacion a) {
            return new AprobacionDto(a.getId(), a.getNivel(), a.getDecision(), Ref.de(a.getAprobador()),
                    Ref.de(a.getMotivoRechazo()), a.getComentario(), a.getMontoOrden(), a.getLimiteAprobador(),
                    a.getFecha(), a.isVigente());
        }
    }

    public record HistorialDto(EstadoOrden estadoAnterior, EstadoOrden estadoNuevo, Ref usuario,
                               OffsetDateTime fecha, String comentario) {
        public static HistorialDto de(OrdenHistorialEstado h) {
            return new HistorialDto(h.getEstadoAnterior(), h.getEstadoNuevo(), Ref.de(h.getUsuario()),
                    h.getFecha(), h.getComentario());
        }
    }

    public record OrdenResumen(Long id, String numero, EstadoOrden estado, String descripcion, Ref creador,
                               Ref departamento, Ref proveedor, String moneda, BigDecimal montoTotal,
                               boolean requiereSegundaAprobacion, OffsetDateTime creadoEn,
                               OffsetDateTime fechaEnvio, boolean vencida) {
        public static OrdenResumen de(Orden o, boolean vencida) {
            return new OrdenResumen(o.getId(), o.getNumero(), o.getEstado(), o.getDescripcion(),
                    Ref.de(o.getCreador()), Ref.de(o.getDepartamento()), Ref.de(o.getProveedor()), o.getMoneda(),
                    o.getMontoTotal(), o.isRequiereSegundaAprobacion(), o.getCreadoEn(), o.getFechaEnvio(), vencida);
        }
    }

    public record OrdenDto(Long id, String numero, EstadoOrden estado, String descripcion, String observaciones,
                           Ref creador, Ref departamento, Ref proveedor, String moneda, BigDecimal montoTotal,
                           boolean requiereSegundaAprobacion, boolean vencida,
                           OffsetDateTime creadoEn, OffsetDateTime actualizadoEn, OffsetDateTime fechaEnvio,
                           OffsetDateTime fechaResolucion, OffsetDateTime fechaCierre, OffsetDateTime fechaCancelacion,
                           Ref motivoCancelacion, String comentarioCancelacion, Integer version,
                           List<DetalleDto> detalles, List<AprobacionDto> aprobaciones, List<HistorialDto> historial,
                           Set<AccionOrden> acciones) {
    }

    public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {
    }
}

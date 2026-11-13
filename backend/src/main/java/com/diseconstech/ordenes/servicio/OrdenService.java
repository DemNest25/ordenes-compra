package com.diseconstech.ordenes.servicio;

import com.diseconstech.ordenes.dto.OrdenDtos.*;
import com.diseconstech.ordenes.error.ApiException;
import com.diseconstech.ordenes.modelo.*;
import com.diseconstech.ordenes.repositorio.*;
import com.diseconstech.ordenes.seguridad.SesionActual;
import com.diseconstech.ordenes.seguridad.UsuarioActual;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Órdenes: CRUD, máquina de estados y aprobaciones.
 * Toda regla de "quién puede qué" vive en impedimento(); la BD (triggers) es la segunda línea de defensa.
 */
@Service
@Transactional
public class OrdenService {

    private static final String ENTIDAD = "ordenes";
    private static final BigDecimal MONTO_MAXIMO = new BigDecimal("9999999999999999.99"); // NUMERIC(18,2)
    private static final String CODIGO_OTRO = "OTRO";

    private final OrdenRepository ordenes;
    private final AprobacionRepository aprobaciones;
    private final OrdenHistorialEstadoRepository historial;
    private final UsuarioRepository usuarios;
    private final DepartamentoRepository departamentos;
    private final ProveedorRepository proveedores;
    private final CatalogoRepository catalogo;
    private final BitacoraService bitacora;
    private final ParametroService parametros;
    private final SesionActual sesion;

    public OrdenService(OrdenRepository ordenes, AprobacionRepository aprobaciones,
                        OrdenHistorialEstadoRepository historial, UsuarioRepository usuarios,
                        DepartamentoRepository departamentos, ProveedorRepository proveedores,
                        CatalogoRepository catalogo, BitacoraService bitacora, ParametroService parametros,
                        SesionActual sesion) {
        this.ordenes = ordenes;
        this.aprobaciones = aprobaciones;
        this.historial = historial;
        this.usuarios = usuarios;
        this.departamentos = departamentos;
        this.proveedores = proveedores;
        this.catalogo = catalogo;
        this.bitacora = bitacora;
        this.parametros = parametros;
        this.sesion = sesion;
    }

    /** Todo lo que necesita una acción, cargado y validado una sola vez. */
    private record Contexto(Orden orden, Usuario yo, Aprobacion nivel1, UsuarioActual ua) {
    }

    public record Filtro(EstadoOrden estado, String texto, Long proveedorId, Long departamentoId,
                         LocalDate desde, LocalDate hasta, boolean porAprobar) {
    }

    // ================================================================== consultas

    @Transactional(readOnly = true)
    public Pagina<OrdenResumen> listar(Filtro f, int pagina, int tamano) {
        UsuarioActual ua = sesion.actual();
        Specification<Orden> spec = (r, q, cb) -> cb.isFalse(r.get("eliminado"));
        // Filtro por dueño EN EL SERVIDOR: un externo nunca recibe órdenes ajenas, mande lo que mande
        if (!ua.veTodo()) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("creador").get("id"), ua.id()));
        }
        if (f.porAprobar()) {
            spec = spec.and((r, q, cb) -> cb.and(
                    cb.equal(r.get("estado"), EstadoOrden.PENDIENTE),
                    cb.notEqual(r.get("creador").get("id"), ua.id())));
        }
        if (f.estado() != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("estado"), f.estado()));
        }
        if (f.texto() != null && !f.texto().isBlank()) {
            String patron = "%" + f.texto().trim().toLowerCase() + "%";
            spec = spec.and((r, q, cb) -> cb.or(
                    cb.like(cb.lower(r.get("numero")), patron),
                    cb.like(cb.lower(r.get("descripcion")), patron)));
        }
        if (f.proveedorId() != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("proveedor").get("id"), f.proveedorId()));
        }
        if (f.departamentoId() != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("departamento").get("id"), f.departamentoId()));
        }
        if (f.desde() != null) {
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.<OffsetDateTime>get("creadoEn"), inicioDia(f.desde())));
        }
        if (f.hasta() != null) {
            spec = spec.and((r, q, cb) -> cb.lessThan(r.<OffsetDateTime>get("creadoEn"), inicioDia(f.hasta().plusDays(1))));
        }

        Page<Orden> page = ordenes.findAll(spec, PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "creadoEn")));
        OffsetDateTime limite = limiteVencimiento();
        return new Pagina<>(page.map(o -> OrdenResumen.de(o, vencida(o, limite))).getContent(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public OrdenDto obtener(Long id) {
        UsuarioActual ua = sesion.actual();
        return armarDto(cargarVisible(id, ua), ua);
    }

    // ================================================================== CRUD

    public OrdenDto crear(OrdenRequest req) {
        UsuarioActual ua = sesion.actual();
        Orden o = new Orden();
        o.setCreador(usuarios.getReferenceById(ua.id()));
        aplicarDatos(o, req);
        ordenes.saveAndFlush(o);
        registrarHistorial(o, null, EstadoOrden.BORRADOR, ua, "Orden creada");
        bitacora.registrar(AccionBitacora.CREAR, ENTIDAD, o.getId(), null, snapshot(o));
        return armarDto(o, ua);
    }

    public OrdenDto actualizar(Long id, OrdenRequest req) {
        Contexto c = cargarParaAccion(id, req.version(), AccionOrden.EDITAR);
        Orden o = c.orden();
        Map<String, Object> antes = snapshot(o);
        aplicarDatos(o, req);
        tocar(o, c.ua());
        ordenes.saveAndFlush(o);
        bitacora.registrar(AccionBitacora.MODIFICAR, ENTIDAD, o.getId(), antes, snapshot(o));
        return armarDto(o, c.ua());
    }

    /** Borrado lógico: la orden deja de verse pero sigue en la BD con su bitácora. */
    public void eliminar(Long id, Integer version) {
        Contexto c = cargarParaAccion(id, version, AccionOrden.ELIMINAR);
        Orden o = c.orden();
        Map<String, Object> antes = snapshot(o);
        o.setEliminado(true);
        tocar(o, c.ua());
        ordenes.saveAndFlush(o);
        bitacora.registrar(AccionBitacora.ELIMINAR, ENTIDAD, o.getId(), antes, Map.of("eliminado", true));
    }

    // ================================================================== transiciones del asesor

    public OrdenDto enviar(Long id, AccionRequest req) {
        Contexto c = cargarParaAccion(id, req.version(), AccionOrden.ENVIAR);
        Orden o = c.orden();
        if (detallesVivos(o).isEmpty() || o.getMontoTotal().signum() <= 0) {
            throw ApiException.regla("detalles",
                    "Para enviar a aprobación la orden debe tener al menos una línea y un monto mayor que cero");
        }
        o.setFechaEnvio(OffsetDateTime.now());
        o.setRequiereSegundaAprobacion(false);
        cambiarEstado(o, EstadoOrden.PENDIENTE, c.ua(), req.comentario(), AccionBitacora.CAMBIO_ESTADO, Map.of());
        return armarDto(o, c.ua());
    }

    /** Pendiente → Borrador para corregir. Si ya había un primer nivel aprobado, se anula. */
    public OrdenDto retirar(Long id, AccionRequest req) {
        Contexto c = cargarParaAccion(id, req.version(), AccionOrden.RETIRAR);
        Orden o = c.orden();
        anularAprobacionesVigentes(o, c.yo());
        o.setRequiereSegundaAprobacion(false);
        cambiarEstado(o, EstadoOrden.BORRADOR, c.ua(), req.comentario(), AccionBitacora.CAMBIO_ESTADO, Map.of());
        return armarDto(o, c.ua());
    }

    /** Rechazada/Aprobada → Borrador. Las aprobaciones previas quedan anuladas: hay que reaprobar. */
    public OrdenDto reabrir(Long id, AccionRequest req) {
        Contexto c = cargarParaAccion(id, req.version(), AccionOrden.REABRIR);
        Orden o = c.orden();
        int anuladas = anularAprobacionesVigentes(o, c.yo());
        o.setRequiereSegundaAprobacion(false);
        o.setFechaResolucion(null);
        cambiarEstado(o, EstadoOrden.BORRADOR, c.ua(), req.comentario(), AccionBitacora.CAMBIO_ESTADO,
                Map.of("aprobacionesAnuladas", anuladas));
        return armarDto(o, c.ua());
    }

    public OrdenDto cerrar(Long id, AccionRequest req) {
        Contexto c = cargarParaAccion(id, req.version(), AccionOrden.CERRAR);
        Orden o = c.orden();
        o.setFechaCierre(OffsetDateTime.now());
        cambiarEstado(o, EstadoOrden.CERRADA, c.ua(), req.comentario(), AccionBitacora.CAMBIO_ESTADO, Map.of());
        return armarDto(o, c.ua());
    }

    public OrdenDto cancelar(Long id, AccionRequest req) {
        Contexto c = cargarParaAccion(id, req.version(), AccionOrden.CANCELAR);
        Orden o = c.orden();
        Catalogo motivo = null;
        if (req.motivoId() != null) {
            motivo = motivo(req.motivoId(), Catalogo.MOTIVO_CANCELACION);
            exigirComentarioSiEsOtro(motivo, req.comentario());
        } else if (o.getEstado() == EstadoOrden.APROBADA) {
            throw ApiException.regla("motivoId", "Para cancelar una orden aprobada debes indicar el motivo");
        }
        o.setMotivoCancelacion(motivo);
        o.setComentarioCancelacion(vacioANull(req.comentario()));
        o.setFechaCancelacion(OffsetDateTime.now());
        Map<String, Object> extra = new LinkedHashMap<>();
        if (motivo != null) extra.put("motivo", motivo.getDescripcion());
        cambiarEstado(o, EstadoOrden.CANCELADA, c.ua(), req.comentario(), AccionBitacora.CAMBIO_ESTADO, extra);
        return armarDto(o, c.ua());
    }

    // ================================================================== aprobación

    /**
     * Nivel 1: si el monto cabe en el límite del aprobador, queda Aprobada; si no, sigue Pendiente
     * marcada como "requiere segunda aprobación".
     * Nivel 2: otra persona (ni el creador ni el del nivel 1) con límite ≥ monto la deja Aprobada.
     */
    public OrdenDto aprobar(Long id, AccionRequest req) {
        Contexto c = cargarParaAccion(id, req.version(), AccionOrden.APROBAR);
        Orden o = c.orden();
        BigDecimal monto = o.getMontoTotal();
        BigDecimal limite = c.yo().getLimiteAprobacion();
        short nivel = c.nivel1() == null ? (short) 1 : (short) 2;

        registrarDecision(o, c.yo(), nivel, DecisionAprobacion.APROBADA, null, req.comentario(), limite);

        boolean definitiva = nivel == 2 || monto.compareTo(limite) <= 0;
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("nivel", nivel);
        extra.put("montoOrden", monto);
        extra.put("limiteAprobador", limite);
        if (definitiva) {
            o.setFechaResolucion(OffsetDateTime.now());
            cambiarEstado(o, EstadoOrden.APROBADA, c.ua(), req.comentario(), AccionBitacora.APROBAR, extra);
        } else {
            // Aprobación parcial: el estado no cambia, por eso no va al historial de estados
            o.setRequiereSegundaAprobacion(true);
            tocar(o, c.ua());
            ordenes.saveAndFlush(o);
            extra.put("resultado", "Monto supera el límite: requiere segundo nivel");
            bitacora.registrar(AccionBitacora.APROBAR, ENTIDAD, o.getId(), Map.of("estado", o.getEstado()), extra);
        }
        return armarDto(o, c.ua());
    }

    public OrdenDto rechazar(Long id, AccionRequest req) {
        Contexto c = cargarParaAccion(id, req.version(), AccionOrden.RECHAZAR);
        Orden o = c.orden();
        if (req.motivoId() == null) {
            throw ApiException.regla("motivoId", "Selecciona el motivo del rechazo");
        }
        Catalogo motivo = motivo(req.motivoId(), Catalogo.MOTIVO_RECHAZO);
        exigirComentarioSiEsOtro(motivo, req.comentario());
        short nivel = c.nivel1() == null ? (short) 1 : (short) 2;
        BigDecimal limite = Objects.requireNonNullElse(c.yo().getLimiteAprobacion(), BigDecimal.ZERO);

        registrarDecision(o, c.yo(), nivel, DecisionAprobacion.RECHAZADA, motivo, req.comentario(), limite);
        o.setFechaResolucion(OffsetDateTime.now());
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("nivel", nivel);
        extra.put("motivo", motivo.getDescripcion());
        cambiarEstado(o, EstadoOrden.RECHAZADA, c.ua(), req.comentario(), AccionBitacora.RECHAZAR, extra);
        return armarDto(o, c.ua());
    }

    // ================================================================== reglas

    /**
     * ¿Por qué este usuario NO puede hacer esta acción? null = sí puede.
     * Se usa para validar (lanzando el error) y para calcular los botones visibles: misma lógica, cero divergencia.
     */
    private ApiException impedimento(AccionOrden a, Orden o, Usuario u, Aprobacion nivel1) {
        boolean esCreador = o.getCreador().getId().equals(u.getId());
        if (a.soloCreador() && !esCreador) {
            return ApiException.prohibido("Solo quien creó la orden puede " + a.verbo() + " esta orden");
        }
        if (!a.permiteEstado(o.getEstado())) {
            return ApiException.regla("No se puede " + a.verbo() + " una orden en estado " + o.getEstado());
        }
        if (!a.soloCreador()) { // aprobar / rechazar
            if (!u.tieneRol(Rol.APROBADOR)) {
                return ApiException.prohibido("Solo los aprobadores pueden " + a.verbo() + " órdenes");
            }
            if (esCreador) {
                return ApiException.prohibido("No puedes " + a.verbo() + " una orden que tú creaste");
            }
            if (nivel1 != null && nivel1.getAprobador().getId().equals(u.getId())) {
                return ApiException.regla("Ya aprobaste el primer nivel; el segundo nivel debe darlo otra persona");
            }
            if (a == AccionOrden.APROBAR) {
                if (u.getLimiteAprobacion() == null) {
                    return ApiException.prohibido("No tienes un límite de aprobación asignado");
                }
                if (nivel1 != null && u.getLimiteAprobacion().compareTo(o.getMontoTotal()) < 0) {
                    return ApiException.regla("Tu límite de aprobación (" + Moneda.formatear(u.getLimiteAprobacion())
                            + ") no cubre el monto de la orden (" + Moneda.formatear(o.getMontoTotal())
                            + "), necesario para el segundo nivel");
                }
            }
        }
        return null;
    }

    private Set<AccionOrden> accionesPermitidas(Orden o, Usuario u, Aprobacion nivel1) {
        if (o.isEliminado()) return Set.of();
        return Arrays.stream(AccionOrden.values())
                .filter(a -> impedimento(a, o, u, nivel1) == null)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AccionOrden.class)));
    }

    // ================================================================== soporte

    /** Si no existe o (para externos) no es suya: 404. No revelamos que la orden existe. */
    private Orden cargarVisible(Long id, UsuarioActual ua) {
        return ordenes.findDetalleById(id)
                .filter(o -> ua.veTodo() || o.getCreador().getId().equals(ua.id()))
                .orElseThrow(() -> ApiException.noEncontrado("La orden", id));
    }

    private Contexto cargarParaAccion(Long id, Integer version, AccionOrden accion) {
        UsuarioActual ua = sesion.actual();
        Orden o = cargarVisible(id, ua);
        if (version == null) {
            throw ApiException.regla("version", "Falta la versión de la orden (recarga la página)");
        }
        // El cliente actúa sobre lo que VIO; si cambió desde entonces, que recargue antes de decidir
        if (!version.equals(o.getVersion())) {
            throw ApiException.conflicto("La orden " + o.getNumero()
                    + " fue modificada por otro usuario. Recarga para ver los cambios.");
        }
        // Roles y límite se leen de la BD, no del token: un cambio de permisos aplica de inmediato
        Usuario yo = usuarios.findById(ua.id()).orElseThrow(() -> ApiException.noAutenticado("Sesión no válida"));
        Aprobacion nivel1 = nivel1Vigente(o);
        ApiException imp = impedimento(accion, o, yo, nivel1);
        if (imp != null) throw imp;
        return new Contexto(o, yo, nivel1, ua);
    }

    private Aprobacion nivel1Vigente(Orden o) {
        return aprobaciones.findByOrdenIdAndNivelAndVigenteTrue(o.getId(), (short) 1).orElse(null);
    }

    private void aplicarDatos(Orden o, OrdenRequest req) {
        Departamento d = departamentos.findByIdAndEliminadoFalse(req.departamentoId())
                .filter(Departamento::isActivo)
                .orElseThrow(() -> ApiException.regla("departamentoId", "El departamento no existe o está inactivo"));
        Proveedor p = proveedores.findByIdAndEliminadoFalse(req.proveedorId())
                .filter(Proveedor::isActivo)
                .orElseThrow(() -> ApiException.regla("proveedorId", "El proveedor no existe o está inactivo"));
        Set<String> unidades = catalogo.findByTipoAndActivoTrueAndEliminadoFalseOrderByOrden(Catalogo.UNIDAD_MEDIDA)
                .stream().map(Catalogo::getCodigo).collect(Collectors.toSet());
        for (int i = 0; i < req.detalles().size(); i++) {
            if (!unidades.contains(req.detalles().get(i).unidadMedida())) {
                throw ApiException.regla("detalles[" + i + "].unidadMedida", "Unidad de medida no válida");
            }
        }

        o.setDepartamento(d);
        o.setProveedor(p);
        o.setDescripcion(req.descripcion().trim());
        o.setObservaciones(vacioANull(req.observaciones()));
        sincronizarDetalles(o, req.detalles());

        BigDecimal total = o.calcularTotal();
        if (total.compareTo(MONTO_MAXIMO) > 0) {
            throw ApiException.regla("detalles", "El monto total excede el máximo permitido");
        }
        o.setMontoTotal(total);
    }

    /**
     * Reutiliza las líneas existentes por posición: actualiza las que coinciden, agrega las nuevas
     * y marca como eliminadas las sobrantes. Así nunca hay dos líneas vivas con el mismo número
     * (índice único parcial) y nunca hay DELETE físico.
     */
    private void sincronizarDetalles(Orden o, List<DetalleRequest> nuevos) {
        List<OrdenDetalle> vivos = detallesVivos(o);
        for (int i = 0; i < Math.max(vivos.size(), nuevos.size()); i++) {
            if (i >= nuevos.size()) {
                vivos.get(i).setEliminado(true);
                continue;
            }
            OrdenDetalle d = i < vivos.size() ? vivos.get(i) : new OrdenDetalle();
            DetalleRequest r = nuevos.get(i);
            d.setLinea((short) (i + 1));
            d.setDescripcion(r.descripcion().trim());
            d.setUnidadMedida(r.unidadMedida());
            d.setCantidad(r.cantidad());
            d.setPrecioUnitario(r.precioUnitario());
            if (d.getId() == null) o.agregarDetalle(d);
        }
    }

    private static List<OrdenDetalle> detallesVivos(Orden o) {
        return o.getDetalles().stream().filter(d -> !d.isEliminado())
                .sorted(Comparator.comparing(OrdenDetalle::getLinea)).toList();
    }

    private void cambiarEstado(Orden o, EstadoOrden nuevo, UsuarioActual ua, String comentario,
                               AccionBitacora accion, Map<String, Object> extra) {
        EstadoOrden anterior = o.getEstado();
        o.setEstado(nuevo);
        tocar(o, ua);
        ordenes.saveAndFlush(o); // aquí el trigger de la BD vuelve a validar la transición
        registrarHistorial(o, anterior, nuevo, ua, comentario);
        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("estado", nuevo);
        despues.putAll(extra);
        if (comentario != null && !comentario.isBlank()) despues.put("comentario", comentario.trim());
        bitacora.registrar(accion, ENTIDAD, o.getId(), Map.of("estado", anterior), despues);
    }

    private void registrarHistorial(Orden o, EstadoOrden anterior, EstadoOrden nuevo, UsuarioActual ua, String comentario) {
        OrdenHistorialEstado h = new OrdenHistorialEstado();
        h.setOrden(o);
        h.setEstadoAnterior(anterior);
        h.setEstadoNuevo(nuevo);
        h.setUsuario(usuarios.getReferenceById(ua.id()));
        h.setComentario(vacioANull(comentario));
        historial.save(h);
    }

    private void registrarDecision(Orden o, Usuario aprobador, short nivel, DecisionAprobacion decision,
                                   Catalogo motivo, String comentario, BigDecimal limite) {
        Aprobacion a = new Aprobacion();
        a.setOrden(o);
        a.setAprobador(aprobador);
        a.setNivel(nivel);
        a.setDecision(decision);
        a.setMotivoRechazo(motivo);
        a.setComentario(vacioANull(comentario));
        a.setMontoOrden(o.getMontoTotal());
        a.setLimiteAprobador(limite);
        aprobaciones.save(a); // el trigger revalida creador ≠ aprobador y nivel 2 ≠ nivel 1
    }

    private int anularAprobacionesVigentes(Orden o, Usuario quien) {
        List<Aprobacion> vigentes = aprobaciones.findByOrdenIdAndVigenteTrue(o.getId());
        vigentes.forEach(a -> a.anular(quien));
        return vigentes.size();
    }

    private Catalogo motivo(Long id, String tipo) {
        return catalogo.findByIdAndTipoAndActivoTrueAndEliminadoFalse(id, tipo)
                .orElseThrow(() -> ApiException.regla("motivoId", "El motivo seleccionado no es válido"));
    }

    private static void exigirComentarioSiEsOtro(Catalogo motivo, String comentario) {
        if (CODIGO_OTRO.equals(motivo.getCodigo()) && (comentario == null || comentario.isBlank())) {
            throw ApiException.regla("comentario", "Describe el motivo cuando eliges «Otro»");
        }
    }

    /** Marca la orden como modificada; además garantiza que @Version suba aunque solo cambien líneas. */
    private static void tocar(Orden o, UsuarioActual ua) {
        o.setActualizadoPor(ua.id());
        o.setActualizadoEn(OffsetDateTime.now());
    }

    private OrdenDto armarDto(Orden o, UsuarioActual ua) {
        Usuario yo = usuarios.findById(ua.id()).orElseThrow();
        Aprobacion nivel1 = nivel1Vigente(o);
        return new OrdenDto(o.getId(), o.getNumero(), o.getEstado(), o.getDescripcion(), o.getObservaciones(),
                Ref.de(o.getCreador()), Ref.de(o.getDepartamento()), Ref.de(o.getProveedor()), o.getMoneda(),
                o.getMontoTotal(), o.isRequiereSegundaAprobacion(), vencida(o, limiteVencimiento()),
                o.getCreadoEn(), o.getActualizadoEn(), o.getFechaEnvio(), o.getFechaResolucion(),
                o.getFechaCierre(), o.getFechaCancelacion(), Ref.de(o.getMotivoCancelacion()),
                o.getComentarioCancelacion(), o.getVersion(),
                detallesVivos(o).stream().map(DetalleDto::de).toList(),
                aprobaciones.findByOrdenIdOrderByFecha(o.getId()).stream().map(AprobacionDto::de).toList(),
                historial.findByOrdenIdOrderByFecha(o.getId()).stream().map(HistorialDto::de).toList(),
                accionesPermitidas(o, yo, nivel1));
    }

    /** Foto de la orden para la bitácora (valores anterior/nuevo). */
    private static Map<String, Object> snapshot(Orden o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numero", o.getNumero());
        m.put("estado", o.getEstado());
        m.put("descripcion", o.getDescripcion());
        m.put("observaciones", o.getObservaciones());
        m.put("departamentoId", o.getDepartamento().getId());
        m.put("proveedorId", o.getProveedor().getId());
        m.put("montoTotal", o.getMontoTotal());
        m.put("detalles", detallesVivos(o).stream().map(d -> {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("linea", d.getLinea());
            l.put("descripcion", d.getDescripcion());
            l.put("unidadMedida", d.getUnidadMedida());
            l.put("cantidad", d.getCantidad());
            l.put("precioUnitario", d.getPrecioUnitario());
            return l;
        }).toList());
        return m;
    }

    private OffsetDateTime limiteVencimiento() {
        return OffsetDateTime.now().minusDays(parametros.plazoAprobacionDias());
    }

    private static boolean vencida(Orden o, OffsetDateTime limite) {
        return o.getEstado() == EstadoOrden.PENDIENTE && o.getFechaEnvio() != null && o.getFechaEnvio().isBefore(limite);
    }

    private static OffsetDateTime inicioDia(LocalDate fecha) {
        return fecha.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

package com.diseconstech.ordenes.servicio;

import com.diseconstech.ordenes.modelo.AccionBitacora;
import com.diseconstech.ordenes.modelo.Bitacora;
import com.diseconstech.ordenes.repositorio.BitacoraRepository;
import com.diseconstech.ordenes.seguridad.SesionActual;
import com.diseconstech.ordenes.seguridad.UsuarioActual;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registra en la bitácora quién hizo qué, cuándo, desde dónde y con qué valores antes/después.
 * Corre dentro de la transacción de la operación: si la operación se revierte, su registro también.
 */
@Service
public class BitacoraService {

    private final BitacoraRepository repo;
    private final SesionActual sesion;
    private final JsonMapper json;

    public BitacoraService(BitacoraRepository repo, SesionActual sesion, JsonMapper json) {
        this.repo = repo;
        this.sesion = sesion;
        this.json = json;
    }

    /** Operación del usuario autenticado. */
    public void registrar(AccionBitacora accion, String entidad, Long entidadId, Object antes, Object despues) {
        UsuarioActual u = sesion.actual();
        registrarComo(u.id(), u.username(), accion, entidad, entidadId, antes, despues);
    }

    /** Para eventos sin sesión todavía (login, intentos fallidos). */
    public void registrarComo(Long usuarioId, String username, AccionBitacora accion,
                              String entidad, Long entidadId, Object antes, Object despues) {
        Bitacora b = new Bitacora();
        b.setUsuarioId(usuarioId);
        b.setUsername(recortar(username, 100));
        b.setAccion(accion);
        b.setEntidad(entidad);
        b.setEntidadId(entidadId);
        b.setValoresAnteriores(antes == null ? null : json.writeValueAsString(antes));
        b.setValoresNuevos(despues == null ? null : json.writeValueAsString(despues));
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            b.setIp(req.getRemoteAddr());
            b.setUserAgent(recortar(req.getHeader("User-Agent"), 300));
        }
        repo.save(b);
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}

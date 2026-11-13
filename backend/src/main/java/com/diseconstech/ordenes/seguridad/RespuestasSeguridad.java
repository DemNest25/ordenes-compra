package com.diseconstech.ordenes.seguridad;

import com.diseconstech.ordenes.error.ErrorApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * 401/403 generados por los filtros de seguridad (antes de llegar al controlador),
 * con el mismo formato JSON que el resto de errores de la API.
 */
@Component
public class RespuestasSeguridad {

    private final JsonMapper json;

    public RespuestasSeguridad(JsonMapper json) {
        this.json = json;
    }

    public AuthenticationEntryPoint noAutenticado() {
        return (req, res, ex) -> escribir(req, res, HttpStatus.UNAUTHORIZED,
                "Debes iniciar sesión o tu sesión expiró");
    }

    public AccessDeniedHandler accesoDenegado() {
        return (req, res, ex) -> escribir(req, res, HttpStatus.FORBIDDEN,
                "No tienes permiso para esta operación");
    }

    private void escribir(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String mensaje)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getOutputStream(),
                ErrorApi.de(status.value(), status.getReasonPhrase(), mensaje, req.getRequestURI(), null));
    }
}

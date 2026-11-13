package com.diseconstech.ordenes.error;

import com.diseconstech.ordenes.error.ErrorApi.ErrorCampo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduce cualquier excepción a ErrorApi. Regla: el cliente siempre sabe QUÉ campo falló y POR QUÉ;
 * los detalles técnicos (SQL, stack trace) solo van al log.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    /** Constraints UNIQUE de la BD → campo y mensaje entendible. */
    private static final Map<String, ErrorCampo> UNICOS = Map.of(
            "proveedores_nit_key", new ErrorCampo("nit", "Ya existe un proveedor con ese NIT"),
            "departamentos_codigo_key", new ErrorCampo("codigo", "Ya existe un departamento con ese código"),
            "usuarios_username_key", new ErrorCampo("username", "Ya existe un usuario con ese nombre de usuario"),
            "usuarios_email_key", new ErrorCampo("email", "Ya existe un usuario con ese correo"),
            "ux_aprob_nivel_vigente", new ErrorCampo(null, "Ese nivel de aprobación ya fue resuelto por otra persona"));

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorApi> api(ApiException ex, HttpServletRequest req) {
        List<ErrorCampo> errores = ex.getCampo() == null ? List.of() : List.of(new ErrorCampo(ex.getCampo(), ex.getMessage()));
        return respuesta(ex.getStatus(), ex.getMessage(), req, errores);
    }

    /** @Valid en el body: un error por cada campo inválido. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorApi> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErrorCampo> errores = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errores.add(new ErrorCampo(fe.getField(), fe.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errores.add(new ErrorCampo(null, ge.getDefaultMessage())));
        return respuesta(HttpStatus.BAD_REQUEST, "Hay datos inválidos en el formulario", req, errores);
    }

    /** Validaciones en @RequestParam / @PathVariable. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorApi> validacionParametros(HandlerMethodValidationException ex, HttpServletRequest req) {
        List<ErrorCampo> errores = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new ErrorCampo(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, "Hay parámetros inválidos", req, errores);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorApi> violacion(ConstraintViolationException ex, HttpServletRequest req) {
        List<ErrorCampo> errores = ex.getConstraintViolations().stream()
                .map(v -> new ErrorCampo(ultimoNodo(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, "Hay datos inválidos", req, errores);
    }

    /** JSON mal formado o tipo incorrecto (ej. "abc" en un monto). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorApi> jsonInvalido(HttpMessageNotReadableException ex, HttpServletRequest req) {
        String campo = null;
        if (ex.getCause() instanceof JacksonException je && !je.getPath().isEmpty()) {
            campo = je.getPath().stream()
                    .map(r -> r.getPropertyName() != null ? r.getPropertyName() : "[" + r.getIndex() + "]")
                    .collect(Collectors.joining(".")).replace(".[", "[");
        }
        List<ErrorCampo> errores = campo == null ? List.of()
                : List.of(new ErrorCampo(campo, "Valor con formato inválido"));
        return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud es inválido o está mal formado", req, errores);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorApi> tipoParametro(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST, "Parámetro inválido", req,
                List.of(new ErrorCampo(ex.getName(), "Valor inválido: " + ex.getValue())));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorApi> faltaParametro(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST, "Falta un parámetro obligatorio", req,
                List.of(new ErrorCampo(ex.getParameterName(), "Es obligatorio")));
    }

    /** @Version: otro usuario guardó la misma orden mientras tanto. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorApi> concurrencia(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.CONFLICT,
                "El registro fue modificado por otro usuario. Recarga para ver los cambios.", req, List.of());
    }

    /** @PreAuthorize denegado dentro del controlador. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorApi> denegado(AccessDeniedException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación", req, List.of());
    }

    /**
     * Errores que vienen de la BD (segunda línea de defensa). Los triggers lanzan mensajes
     * en español pensados para el usuario (SQLSTATE P0001), así que esos se muestran tal cual.
     */
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorApi> baseDatos(DataAccessException ex, HttpServletRequest req) {
        PSQLException pg = buscarCausa(ex, PSQLException.class);
        if (pg != null && pg.getServerErrorMessage() != null) {
            var sem = pg.getServerErrorMessage();
            String estado = pg.getSQLState();
            switch (estado == null ? "" : estado) {
                case "P0001" -> {
                    return respuesta(HttpStatus.UNPROCESSABLE_CONTENT, sem.getMessage(), req, List.of());
                }
                case "23505" -> {
                    ErrorCampo ec = UNICOS.getOrDefault(sem.getConstraint(),
                            new ErrorCampo(null, "Ya existe un registro con esos datos"));
                    return respuesta(HttpStatus.CONFLICT, ec.mensaje(), req,
                            ec.campo() == null ? List.of() : List.of(ec));
                }
                case "23503" -> {
                    return respuesta(HttpStatus.CONFLICT, "Un registro relacionado no existe o no es válido", req, List.of());
                }
                case "23514", "23502" -> {
                    String campo = sem.getColumn();
                    return respuesta(HttpStatus.BAD_REQUEST, "Un dato no cumple las reglas de la base de datos", req,
                            campo == null ? List.of() : List.of(new ErrorCampo(campo, "Valor no permitido")));
                }
                default -> { }
            }
        }
        log.error("Error de base de datos en {}", req.getRequestURI(), ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno al acceder a los datos", req, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorApi> noExiste(NoResourceFoundException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.NOT_FOUND, "Recurso no encontrado", req, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorApi> metodo(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "Método " + ex.getMethod() + " no permitido", req, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorApi> inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado", req, List.of());
    }

    private static ResponseEntity<ErrorApi> respuesta(HttpStatus status, String mensaje, HttpServletRequest req,
                                                      List<ErrorCampo> errores) {
        return ResponseEntity.status(status)
                .body(ErrorApi.de(status.value(), status.getReasonPhrase(), mensaje, req.getRequestURI(), errores));
    }

    private static String ultimoNodo(String ruta) {
        int i = ruta.lastIndexOf('.');
        return i < 0 ? ruta : ruta.substring(i + 1);
    }

    private static <T extends Throwable> T buscarCausa(Throwable t, Class<T> tipo) {
        while (t != null) {
            if (tipo.isInstance(t)) return tipo.cast(t);
            t = t.getCause();
        }
        return null;
    }
}

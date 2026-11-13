package com.diseconstech.ordenes.web;

import com.diseconstech.ordenes.dto.MaestroDtos.*;
import com.diseconstech.ordenes.dto.OrdenDtos.Pagina;
import com.diseconstech.ordenes.seguridad.SesionActual;
import com.diseconstech.ordenes.servicio.MaestroService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Lectura para cualquier usuario autenticado (llenar combos); escritura solo ADMIN. */
@RestController
@RequestMapping("/api")
public class MaestroController {

    private final MaestroService servicio;
    private final SesionActual sesion;

    public MaestroController(MaestroService servicio, SesionActual sesion) {
        this.servicio = servicio;
        this.sesion = sesion;
    }

    /** tipo: MOTIVO_RECHAZO, MOTIVO_CANCELACION, UNIDAD_MEDIDA */
    @GetMapping("/catalogos/{tipo}")
    public List<CatalogoDto> catalogo(@PathVariable String tipo) {
        return servicio.catalogo(tipo.toUpperCase());
    }

    // ------------------------------------------------------------------ departamentos

    @GetMapping("/departamentos")
    public List<DepartamentoDto> departamentos(@RequestParam(defaultValue = "false") boolean todos) {
        return servicio.departamentos(todos && sesion.actual().esAdmin());
    }

    @PostMapping("/departamentos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public DepartamentoDto crearDepartamento(@Valid @RequestBody DepartamentoRequest req) {
        return servicio.guardarDepartamento(null, req);
    }

    @PutMapping("/departamentos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DepartamentoDto actualizarDepartamento(@PathVariable Long id, @Valid @RequestBody DepartamentoRequest req) {
        return servicio.guardarDepartamento(id, req);
    }

    @DeleteMapping("/departamentos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void eliminarDepartamento(@PathVariable Long id) {
        servicio.eliminarDepartamento(id);
    }

    // ------------------------------------------------------------------ proveedores

    @GetMapping("/proveedores")
    public List<ProveedorDto> proveedores(@RequestParam(defaultValue = "false") boolean todos) {
        return servicio.proveedores(todos && sesion.actual().esAdmin());
    }

    @PostMapping("/proveedores")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProveedorDto crearProveedor(@Valid @RequestBody ProveedorRequest req) {
        return servicio.guardarProveedor(null, req);
    }

    @PutMapping("/proveedores/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProveedorDto actualizarProveedor(@PathVariable Long id, @Valid @RequestBody ProveedorRequest req) {
        return servicio.guardarProveedor(id, req);
    }

    @DeleteMapping("/proveedores/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void eliminarProveedor(@PathVariable Long id) {
        servicio.eliminarProveedor(id);
    }

    // ------------------------------------------------------------------ bitácora

    @GetMapping("/bitacora")
    @PreAuthorize("hasRole('ADMIN')")
    public Pagina<BitacoraDto> bitacora(@RequestParam(required = false) String entidad,
                                        @RequestParam(required = false) Long entidadId,
                                        @RequestParam(defaultValue = "0") @Min(0) int pagina,
                                        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int tamano) {
        return servicio.bitacora(entidad, entidadId, pagina, tamano);
    }
}

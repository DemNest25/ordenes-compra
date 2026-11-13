package com.diseconstech.ordenes.web;

import com.diseconstech.ordenes.dto.OrdenDtos.*;
import com.diseconstech.ordenes.modelo.EstadoOrden;
import com.diseconstech.ordenes.servicio.OrdenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Endpoints delgados: validan forma (@Valid) y delegan. Ninguno recibe el id del usuario:
 * quién actúa siempre sale del JWT dentro del servicio.
 */
@RestController
@RequestMapping("/api/ordenes")
public class OrdenController {

    private final OrdenService servicio;

    public OrdenController(OrdenService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public Pagina<OrdenResumen> listar(
            @RequestParam(required = false) EstadoOrden estado,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) Long departamentoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "false") boolean porAprobar,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página no puede ser negativa") int pagina,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Mínimo 1")
            @Max(value = 100, message = "Máximo 100 por página") int tamano) {
        return servicio.listar(new OrdenService.Filtro(estado, texto, proveedorId, departamentoId, desde, hasta, porAprobar),
                pagina, tamano);
    }

    @GetMapping("/{id}")
    public OrdenDto obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenDto crear(@Valid @RequestBody OrdenRequest req) {
        return servicio.crear(req);
    }

    @PutMapping("/{id}")
    public OrdenDto actualizar(@PathVariable Long id, @Valid @RequestBody OrdenRequest req) {
        return servicio.actualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id, @RequestParam(required = false) Integer version) {
        servicio.eliminar(id, version);
    }

    @PostMapping("/{id}/enviar")
    public OrdenDto enviar(@PathVariable Long id, @Valid @RequestBody AccionRequest req) {
        return servicio.enviar(id, req);
    }

    @PostMapping("/{id}/retirar")
    public OrdenDto retirar(@PathVariable Long id, @Valid @RequestBody AccionRequest req) {
        return servicio.retirar(id, req);
    }

    @PostMapping("/{id}/aprobar")
    public OrdenDto aprobar(@PathVariable Long id, @Valid @RequestBody AccionRequest req) {
        return servicio.aprobar(id, req);
    }

    @PostMapping("/{id}/rechazar")
    public OrdenDto rechazar(@PathVariable Long id, @Valid @RequestBody AccionRequest req) {
        return servicio.rechazar(id, req);
    }

    @PostMapping("/{id}/reabrir")
    public OrdenDto reabrir(@PathVariable Long id, @Valid @RequestBody AccionRequest req) {
        return servicio.reabrir(id, req);
    }

    @PostMapping("/{id}/cerrar")
    public OrdenDto cerrar(@PathVariable Long id, @Valid @RequestBody AccionRequest req) {
        return servicio.cerrar(id, req);
    }

    @PostMapping("/{id}/cancelar")
    public OrdenDto cancelar(@PathVariable Long id, @Valid @RequestBody AccionRequest req) {
        return servicio.cancelar(id, req);
    }
}

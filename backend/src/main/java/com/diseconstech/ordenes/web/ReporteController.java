package com.diseconstech.ordenes.web;

import com.diseconstech.ordenes.reporte.PdfReporte;
import com.diseconstech.ordenes.reporte.Reporte;
import com.diseconstech.ordenes.reporte.ReporteService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Reportes en JSON (para pantalla) y PDF (para exportar). "no-store" en ambos:
 * ni el navegador ni un proxy pueden guardar una copia, siempre se ve el estado actual.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final ReporteService servicio;
    private final PdfReporte pdf;

    public ReporteController(ReporteService servicio, PdfReporte pdf) {
        this.servicio = servicio;
        this.pdf = pdf;
    }

    public record ReporteDisponible(String codigo, String titulo) {
    }

    /** Solo los reportes que el perfil del usuario puede ver (para armar el menú). */
    @GetMapping
    public List<ReporteDisponible> disponibles() {
        return servicio.disponibles().stream().map(t -> new ReporteDisponible(t.codigo(), t.titulo())).toList();
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<Reporte> ver(@PathVariable String codigo,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.generar(codigo, desde, hasta));
    }

    @GetMapping("/{codigo}/pdf")
    public ResponseEntity<byte[]> exportarPdf(@PathVariable String codigo,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Reporte r = servicio.generar(codigo, desde, hasta);
        String archivo = "reporte-" + codigo + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".pdf";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.generar(r));
    }
}

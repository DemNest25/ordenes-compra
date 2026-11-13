package com.diseconstech.ordenes.reporte;

import com.diseconstech.ordenes.reporte.Reporte.Columna;
import com.diseconstech.ordenes.reporte.Reporte.Tipo;
import com.diseconstech.ordenes.servicio.Moneda;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Convierte cualquier Reporte en un PDF tabular. Un solo renderer para todos los reportes. */
@Component
public class PdfReporte {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color GRIS_ENCABEZADO = new Color(230, 233, 238);
    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Font INFO = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
    private static final Font CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
    private static final Font CELDA = FontFactory.getFont(FontFactory.HELVETICA, 8);
    private static final Font TOTAL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);

    public byte[] generar(Reporte r) {
        // Horizontal si hay muchas columnas
        Rectangle pagina = r.columnas().size() > 6 ? PageSize.LETTER.rotate() : PageSize.LETTER;
        Document doc = new Document(pagina, 30, 30, 30, 40);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new PiePagina());
        doc.addTitle(r.titulo());
        doc.open();

        doc.add(new Paragraph(r.titulo(), TITULO));
        doc.add(new Paragraph(r.alcance(), INFO));
        doc.add(new Paragraph("Generado el " + fecha(r.generadoEn()) + " por " + r.generadoPor()
                + " - datos al momento de la consulta", INFO));
        doc.add(Chunk.NEWLINE);

        PdfPTable tabla = new PdfPTable(r.columnas().size());
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1); // la cabecera se repite en cada página
        for (Columna c : r.columnas()) {
            PdfPCell celda = new PdfPCell(new Phrase(c.titulo(), CABECERA));
            celda.setBackgroundColor(GRIS_ENCABEZADO);
            celda.setHorizontalAlignment(alineacion(c.tipo()));
            celda.setPadding(4);
            tabla.addCell(celda);
        }
        if (r.filas().isEmpty()) {
            PdfPCell vacio = new PdfPCell(new Phrase("Sin datos para los filtros seleccionados", CELDA));
            vacio.setColspan(r.columnas().size());
            vacio.setPadding(6);
            vacio.setHorizontalAlignment(Element.ALIGN_CENTER);
            tabla.addCell(vacio);
        }
        for (List<Object> fila : r.filas()) {
            agregarFila(tabla, r.columnas(), fila, CELDA, null);
        }
        if (r.totales() != null && !r.filas().isEmpty()) {
            agregarFila(tabla, r.columnas(), r.totales(), TOTAL, GRIS_ENCABEZADO);
        }
        doc.add(tabla);
        doc.close();
        return out.toByteArray();
    }

    private static void agregarFila(PdfPTable tabla, List<Columna> cols, List<Object> valores, Font font, Color fondo) {
        for (int i = 0; i < cols.size(); i++) {
            Object v = valores.get(i);
            // en la fila de totales la primera celda es una etiqueta de texto
            Tipo tipo = v instanceof String ? Tipo.TEXTO : cols.get(i).tipo();
            PdfPCell celda = new PdfPCell(new Phrase(formatear(v, tipo), font));
            celda.setHorizontalAlignment(alineacion(cols.get(i).tipo()));
            celda.setPadding(3);
            if (fondo != null) celda.setBackgroundColor(fondo);
            tabla.addCell(celda);
        }
    }

    static String formatear(Object v, Tipo tipo) {
        if (v == null) return "";
        return switch (tipo) {
            case MONEDA -> Moneda.formatear((BigDecimal) v);
            case DECIMAL -> new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(v);
            case FECHA_HORA -> fecha((OffsetDateTime) v);
            case ENTERO, TEXTO -> v.toString();
        };
    }

    private static String fecha(OffsetDateTime f) {
        return f.atZoneSameInstant(ZoneId.systemDefault()).format(FECHA);
    }

    private static int alineacion(Tipo tipo) {
        return switch (tipo) {
            case MONEDA, DECIMAL, ENTERO -> Element.ALIGN_RIGHT;
            default -> Element.ALIGN_LEFT;
        };
    }

    private static final class PiePagina extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase("Página " + writer.getPageNumber(), INFO), doc.right(), doc.bottom() - 20, 0);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                    new Phrase("Órdenes de Compra - diseconstech", INFO), doc.left(), doc.bottom() - 20, 0);
        }
    }
}

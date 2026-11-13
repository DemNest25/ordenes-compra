package com.diseconstech.ordenes.web;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReporteTest extends ApiTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test
    void cadaPerfilVeSoloSusReportes() throws Exception {
        mvc.perform(como("cramirez", get("/api/reportes")))
                .andExpect(jsonPath("$[*].codigo", hasItem("historial-propio")))
                .andExpect(jsonPath("$[*].codigo", not(hasItems("gasto-departamento", "gasto-proveedor"))));
        mvc.perform(como("jmorales", get("/api/reportes")))
                .andExpect(jsonPath("$[*].codigo", hasItems("gasto-departamento", "gasto-proveedor")))
                .andExpect(jsonPath("$[*].codigo", not(hasItem("historial-propio"))));
        // el bloqueo es en el servidor, no solo en el menú
        mvc.perform(como("cramirez", get("/api/reportes/gasto-proveedor"))).andExpect(status().isForbidden());
        mvc.perform(como("cramirez", get("/api/reportes/no-existe"))).andExpect(status().isNotFound());
    }

    @Test
    void externoSoloVeOrdenesPorEstadoEHistorialPropio() throws Exception {
        mvc.perform(como("cramirez", get("/api/reportes")))
                .andExpect(jsonPath("$[*].codigo", containsInAnyOrder("ordenes-por-estado", "historial-propio")));
        // Reportes de internos: 403 aunque se pidan directo por URL (JSON y PDF)
        for (String codigo : new String[] {"rechazadas", "tiempo-aprobacion", "pendientes-vencidas"}) {
            mvc.perform(como("cramirez", get("/api/reportes/" + codigo))).andExpect(status().isForbidden());
            mvc.perform(como("cramirez", get("/api/reportes/" + codigo + "/pdf"))).andExpect(status().isForbidden());
            mvc.perform(como("lgarcia", get("/api/reportes/" + codigo))).andExpect(status().isOk());
        }
    }

    @Test
    void porEstadoEsEnVivoYRespetaElAlcance() throws Exception {
        int antesCramirez = borradores("cramirez");
        int antesAsandoval = borradores("asandoval");
        int antesAdmin = borradores("jmorales");

        crear("cramirez", "1", "100.00");

        // sin caché: la nueva orden aparece de inmediato; y solo en el alcance de quien debe verla
        assertEquals(antesCramirez + 1, borradores("cramirez"));
        assertEquals(antesAsandoval, borradores("asandoval"));
        assertEquals(antesAdmin + 1, borradores("jmorales"));

        mvc.perform(como("jmorales", get("/api/reportes/ordenes-por-estado")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.filas", hasSize(6)))
                .andExpect(jsonPath("$.alcance").value("Todas las órdenes"))
                .andExpect(jsonPath("$.columnas[2].tipo").value("MONEDA"));
    }

    @Test
    void gastoPorProveedorSumaAprobadas() throws Exception {
        BigDecimal antes = totalGasto();
        int id = crear("cramirez", "4", "250.50"); // Q 1,002.00
        accion("cramirez", id, "enviar");
        assertEquals(0, antes.compareTo(totalGasto()), "pendiente aún no es gasto");
        accion("lgarcia", id, "aprobar");
        assertEquals(0, antes.add(new BigDecimal("1002.00")).compareTo(totalGasto()));
    }

    @Test
    void pendientesVencidasSegunPlazo() throws Exception {
        int id = crear("cramirez", "1", "100.00");
        accion("cramirez", id, "enviar");
        String numero = JsonPath.read(cuerpo(mvc.perform(como("cramirez", get("/api/ordenes/" + id)))), "$.numero");
        mvc.perform(como("lgarcia", get("/api/reportes/pendientes-vencidas")))
                .andExpect(jsonPath("$.filas[*][0]", not(hasItem(numero))));

        // simulamos que se envió hace 3 días (plazo = 2)
        jdbc.update("UPDATE oc.ordenes SET fecha_envio = now() - interval '3 days' WHERE id = ?", id);
        em.clear(); // el test comparte el contexto JPA entre "peticiones"; que relea la orden de la BD
        mvc.perform(como("lgarcia", get("/api/reportes/pendientes-vencidas")))
                .andExpect(jsonPath("$.filas[*][0]", hasItem(numero)));
        mvc.perform(como("cramirez", get("/api/ordenes/" + id))).andExpect(jsonPath("$.vencida").value(true));
    }

    @Test
    void exportaPdfSinCache() throws Exception {
        crear("cramirez", "1", "1234.56");
        byte[] pdf = mvc.perform(como("cramirez", get("/api/reportes/historial-propio/pdf")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Disposition", startsWith("attachment; filename=\"reporte-historial-propio-")))
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(new String(pdf, 0, 5).startsWith("%PDF"), "debe ser un PDF");
        assertTrue(pdf.length > 1000);
    }

    @Test
    void rangoDeFechasInvalido() throws Exception {
        mvc.perform(como("jmorales", get("/api/reportes/ordenes-por-estado?desde=2026-11-10&hasta=2026-11-01")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errores[0].campo").value("hasta"));
    }

    private int borradores(String usuario) throws Exception {
        // fila 0 = BORRADOR, columna 1 = cantidad
        return JsonPath.read(cuerpo(mvc.perform(como(usuario, get("/api/reportes/ordenes-por-estado")))), "$.filas[0][1]");
    }

    private BigDecimal totalGasto() throws Exception {
        Object total = JsonPath.read(cuerpo(mvc.perform(como("jmorales", get("/api/reportes/gasto-proveedor")))), "$.totales[2]");
        return new BigDecimal(total.toString());
    }
}

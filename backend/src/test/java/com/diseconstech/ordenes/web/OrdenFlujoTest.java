package com.diseconstech.ordenes.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujo completo de órdenes por HTTP: reglas de negocio, permisos y triggers.
 */
class OrdenFlujoTest extends ApiTestBase {

    @Test
    void dobleAprobacionCuandoElMontoSuperaElLimite() throws Exception {
        int id = crear("cramirez", "3", "10000.00"); // Q 30,000
        accion("cramirez", id, "enviar").andExpect(jsonPath("$.estado").value("PENDIENTE"));

        // lgarcia (límite 10,000): aprueba nivel 1, pero sigue pendiente
        accion("lgarcia", id, "aprobar")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.requiereSegundaAprobacion").value(true));

        // ella misma no puede dar el segundo nivel
        accion("lgarcia", id, "aprobar").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje", containsString("segundo nivel debe darlo otra persona")));

        // mperez (límite 50,000) da el nivel 2
        accion("mperez", id, "aprobar")
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.aprobaciones", hasSize(2)))
                .andExpect(jsonPath("$.aprobaciones[1].nivel").value(2))
                .andExpect(jsonPath("$.historial[*].estadoNuevo", contains("BORRADOR", "PENDIENTE", "APROBADA")));
    }

    @Test
    void aprobacionDirectaDentroDelLimite() throws Exception {
        int id = crear("cramirez", "5", "1000.00"); // Q 5,000
        accion("cramirez", id, "enviar");
        accion("lgarcia", id, "aprobar").andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.requiereSegundaAprobacion").value(false));
        accion("cramirez", id, "cerrar").andExpect(jsonPath("$.estado").value("CERRADA"))
                .andExpect(jsonPath("$.acciones", empty()));
    }

    @Test
    void segundoNivelExigeLimiteSuficiente() throws Exception {
        int id = crear("cramirez", "1", "80000.00"); // Q 80,000
        accion("cramirez", id, "enviar");
        accion("lgarcia", id, "aprobar").andExpect(jsonPath("$.requiereSegundaAprobacion").value(true));
        accion("mperez", id, "aprobar").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje", containsString("Q 50,000.00")))
                .andExpect(jsonPath("$.mensaje", containsString("Q 80,000.00")));
        accion("rlopez", id, "aprobar").andExpect(jsonPath("$.estado").value("APROBADA"));
    }

    @Test
    void elCreadorNuncaApruebaLoSuyo() throws Exception {
        int id = crear("rlopez", "1", "100.00"); // rlopez es ADMIN + APROBADOR
        accion("rlopez", id, "enviar")
                .andExpect(jsonPath("$.acciones", not(hasItem("APROBAR"))));
        accion("rlopez", id, "aprobar").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No puedes aprobar una orden que tú creaste"));
    }

    @Test
    void externoSoloVeLoSuyo() throws Exception {
        int id = crear("cramirez", "1", "100.00");
        mvc.perform(como("asandoval", get("/api/ordenes/" + id))).andExpect(status().isNotFound());
        mvc.perform(como("asandoval", get("/api/ordenes"))).andExpect(jsonPath("$.contenido[*].id", not(hasItem(id))));
        accion("asandoval", id, "cancelar").andExpect(status().isNotFound());
        // los internos ven todo
        mvc.perform(como("jmorales", get("/api/ordenes/" + id))).andExpect(status().isOk());
        mvc.perform(como("cramirez", get("/api/ordenes"))).andExpect(jsonPath("$.contenido[*].id", hasItem(id)));
    }

    @Test
    void rechazoConMotivoYReaperturaAnulaAprobaciones() throws Exception {
        int motivo = JsonPath.read(cuerpo(mvc.perform(como("lgarcia", get("/api/catalogos/MOTIVO_RECHAZO")))), "$[0].id");
        int id = crear("cramirez", "2", "10000.00"); // Q 20,000
        accion("cramirez", id, "enviar");
        accion("lgarcia", id, "aprobar"); // nivel 1 parcial

        accion("mperez", id, "rechazar").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errores[0].campo").value("motivoId"));
        accion("mperez", id, "rechazar", motivo, "Falta cotización")
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.aprobaciones[1].decision").value("RECHAZADA"));

        accion("cramirez", id, "reabrir")
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.aprobaciones[*].vigente", everyItem(is(false))))
                .andExpect(jsonPath("$.acciones", hasItems("EDITAR", "ENVIAR")));
    }

    @Test
    void cancelarAprobadaExigeMotivo() throws Exception {
        int id = crear("cramirez", "1", "100.00");
        accion("cramirez", id, "enviar");
        accion("lgarcia", id, "aprobar");
        accion("cramirez", id, "cancelar").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errores[0].campo").value("motivoId"));
    }

    @Test
    void versionObsoletaDaConflicto() throws Exception {
        int id = crear("cramirez", "1", "100.00");
        accion("cramirez", id, "enviar"); // versión pasa de 0 a 1
        mvc.perform(como("lgarcia", post("/api/ordenes/" + id + "/aprobar"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isConflict());
    }

    @Test
    void transicionInvalidaDa422() throws Exception {
        int id = crear("cramirez", "1", "100.00");
        accion("cramirez", id, "cerrar").andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.mensaje").value("No se puede cerrar una orden en estado BORRADOR"));
    }

    @Test
    void erroresDeValidacionIndicanCampoYMotivo() throws Exception {
        String json = """
                {"departamentoId": %d, "descripcion": "",
                 "detalles": [{"descripcion": "X", "unidadMedida": "CAJA", "cantidad": -1, "precioUnitario": 10.123}]}
                """.formatted(departamentoId);
        mvc.perform(como("cramirez", post("/api/ordenes")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", hasItems(
                        "proveedorId", "descripcion", "detalles[0].cantidad", "detalles[0].precioUnitario")))
                .andExpect(jsonPath("$.errores[?(@.campo == 'detalles[0].cantidad')].mensaje",
                        hasItem("La cantidad debe ser mayor que cero")));
    }

    @Test
    void editarRecalculaTotalYSubeVersion() throws Exception {
        int id = crear("cramirez", "2", "50.00");
        String json = """
                {"departamentoId": %d, "proveedorId": %d, "descripcion": "Editada", "version": 0,
                 "detalles": [{"descripcion": "A", "unidadMedida": "CAJA", "cantidad": 1, "precioUnitario": 10.50},
                              {"descripcion": "B", "unidadMedida": "FRASCO", "cantidad": 3, "precioUnitario": 2.25}]}
                """.formatted(departamentoId, proveedorId);
        mvc.perform(como("cramirez", put("/api/ordenes/" + id)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.montoTotal").value(17.25))
                .andExpect(jsonPath("$.detalles", hasSize(2)))
                .andExpect(jsonPath("$.detalles[1].subtotal").value(6.75))
                .andExpect(jsonPath("$.version").value(1));
        // un aprobador no puede editar órdenes ajenas
        mvc.perform(como("lgarcia", put("/api/ordenes/" + id)).contentType(MediaType.APPLICATION_JSON)
                .content(json.replace("\"version\": 0", "\"version\": 1"))).andExpect(status().isForbidden());
    }

    @Test
    void soloAdminModificaMaestros() throws Exception {
        String prov = "{\"nit\":\"999999-9\",\"razonSocial\":\"Prueba, S.A.\"}";
        mvc.perform(como("cramirez", post("/api/proveedores")).contentType(MediaType.APPLICATION_JSON).content(prov))
                .andExpect(status().isForbidden());
        mvc.perform(como("jmorales", post("/api/proveedores")).contentType(MediaType.APPLICATION_JSON).content(prov))
                .andExpect(status().isCreated());
        // NIT duplicado: el UNIQUE de la BD se traduce a error en el campo
        mvc.perform(como("jmorales", post("/api/proveedores")).contentType(MediaType.APPLICATION_JSON).content(prov))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errores[0].campo").value("nit"));
    }
}

package com.diseconstech.ordenes.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base para tests por HTTP con los usuarios demo (V3): login real y JWT.
 * @Transactional: todo lo que hace cada test se revierte al final.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class ApiTestBase {

    @Autowired MockMvc mvc;

    final Map<String, String> tokens = new HashMap<>();
    int departamentoId;
    int proveedorId;

    @BeforeEach
    void catalogos() throws Exception {
        departamentoId = JsonPath.read(cuerpo(mvc.perform(como("cramirez", get("/api/departamentos")))), "$[0].id");
        proveedorId = JsonPath.read(cuerpo(mvc.perform(como("cramirez", get("/api/proveedores")))), "$[0].id");
    }

    MockHttpServletRequestBuilder como(String usuario, MockHttpServletRequestBuilder req) throws Exception {
        return req.header("Authorization", "Bearer " + token(usuario));
    }

    String token(String usuario) throws Exception {
        if (!tokens.containsKey(usuario)) {
            String body = cuerpo(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"%s\",\"password\":\"Demo2026!\"}".formatted(usuario))));
            tokens.put(usuario, JsonPath.read(body, "$.token"));
        }
        return tokens.get(usuario);
    }

    int crear(String usuario, String cantidad, String precio) throws Exception {
        String json = """
                {"departamentoId": %d, "proveedorId": %d, "descripcion": "Orden de prueba",
                 "detalles": [{"descripcion": "Producto", "unidadMedida": "CAJA", "cantidad": %s, "precioUnitario": %s}]}
                """.formatted(departamentoId, proveedorId, cantidad, precio);
        String body = cuerpo(mvc.perform(como(usuario, post("/api/ordenes"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero", startsWith("OC-")))
                .andExpect(jsonPath("$.estado").value("BORRADOR")));
        return JsonPath.read(body, "$.id");
    }

    ResultActions accion(String usuario, int id, String accion) throws Exception {
        return accion(usuario, id, accion, null, null);
    }

    /** Lee la versión actual (como haría el frontend) y ejecuta la acción. */
    ResultActions accion(String usuario, int id, String accion, Integer motivoId, String comentario) throws Exception {
        String actual = mvc.perform(como("jmorales", get("/api/ordenes/" + id))).andReturn().getResponse().getContentAsString();
        int version = JsonPath.read(actual, "$.version");
        String json = "{\"version\":%d,\"motivoId\":%s,\"comentario\":%s}".formatted(version,
                motivoId == null ? "null" : motivoId.toString(), comentario == null ? "null" : "\"" + comentario + "\"");
        return mvc.perform(como(usuario, post("/api/ordenes/" + id + "/" + accion))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    static String cuerpo(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }
}

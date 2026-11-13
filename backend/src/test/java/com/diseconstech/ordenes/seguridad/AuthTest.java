package com.diseconstech.ordenes.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login contra el LDAP embebido y la BD reales. @Transactional: los intentos fallidos
 * y bloqueos se revierten al terminar, así no quedan cuentas demo bloqueadas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthTest {

    static final String PASSWORD = "Demo2026!";

    @Autowired MockMvc mvc;

    ResultActions login(String usuario, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(usuario, password)));
    }

    @Test
    void loginInternoPorLdap() throws Exception {
        login("rlopez", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.usuario.interno").value(true))
                .andExpect(jsonPath("$.usuario.roles", contains("ADMIN", "APROBADOR")))
                .andExpect(jsonPath("$.usuario.limiteAprobacion").value(100000.00));
    }

    @Test
    void loginExternoPorBcrypt() throws Exception {
        login("CRamirez", PASSWORD) // sin distinguir mayúsculas
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.username").value("cramirez"))
                .andExpect(jsonPath("$.usuario.interno").value(false))
                .andExpect(jsonPath("$.usuario.roles", contains("EXTERNO")))
                .andExpect(jsonPath("$.usuario.passwordHash").doesNotExist());
    }

    @Test
    void passwordIncorrecto() throws Exception {
        login("lgarcia", "malo").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Usuario o contraseña incorrectos"));
        login("cramirez", "malo").andExpect(status().isUnauthorized());
        // usuario inexistente: mismo mensaje, no se revela si existe
        login("noexiste", "x").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void camposVaciosDevuelvenErrorPorCampo() throws Exception {
        login("", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[*].campo", containsInAnyOrder("username", "password")));
    }

    @Test
    void bloqueoTrasCincoIntentos() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("asandoval", "malo").andExpect(status().isUnauthorized());
        }
        // aunque ahora la contraseña sea correcta, la cuenta está bloqueada
        login("asandoval", PASSWORD).andExpect(status().isLocked())
                .andExpect(jsonPath("$.mensaje", startsWith("Cuenta bloqueada")));
    }

    @Test
    void endpointProtegidoSinTokenDa401() throws Exception {
        mvc.perform(get("/api/auth/yo")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer token.falso.xyz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegidoConToken() throws Exception {
        String body = login("mperez", PASSWORD).andReturn().getResponse().getContentAsString();
        String token = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("mperez"))
                .andExpect(jsonPath("$.departamento").value("Finanzas"));
    }
}

package com.diseconstech.ordenes.web;

import com.diseconstech.ordenes.dto.AuthDtos.LoginRequest;
import com.diseconstech.ordenes.dto.AuthDtos.LoginResponse;
import com.diseconstech.ordenes.dto.AuthDtos.UsuarioDto;
import com.diseconstech.ordenes.seguridad.AuthService;
import com.diseconstech.ordenes.seguridad.SesionActual;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final SesionActual sesion;

    public AuthController(AuthService auth, SesionActual sesion) {
        this.auth = auth;
        this.sesion = sesion;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.username(), req.password());
    }

    /** Datos frescos del usuario de la sesión (el frontend lo usa al recargar la página). */
    @GetMapping("/yo")
    public UsuarioDto yo() {
        return auth.yo(sesion.actual().id());
    }
}

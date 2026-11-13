package com.diseconstech.ordenes.dto;

import com.diseconstech.ordenes.modelo.Rol;
import com.diseconstech.ordenes.modelo.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Ingresa tu usuario") @Size(max = 100, message = "Máximo 100 caracteres") String username,
            @NotBlank(message = "Ingresa tu contraseña") @Size(max = 200, message = "Máximo 200 caracteres") String password) {
    }

    public record LoginResponse(String token, String tipo, Instant expira, UsuarioDto usuario) {
    }

    /** Datos del usuario para el frontend. Jamás incluye password_hash ni contadores de bloqueo. */
    public record UsuarioDto(Long id, String username, String nombreCompleto, String email, boolean interno,
                             List<String> roles, BigDecimal limiteAprobacion, Long departamentoId, String departamento) {

        public static UsuarioDto de(Usuario u) {
            return new UsuarioDto(u.getId(), u.getUsername(), u.getNombreCompleto(), u.getEmail(), u.esInterno(),
                    u.getRoles().stream().map(Rol::getCodigo).sorted().toList(), u.getLimiteAprobacion(),
                    u.getDepartamento() == null ? null : u.getDepartamento().getId(),
                    u.getDepartamento() == null ? null : u.getDepartamento().getNombre());
        }
    }
}

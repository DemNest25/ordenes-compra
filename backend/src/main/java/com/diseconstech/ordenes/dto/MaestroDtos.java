package com.diseconstech.ordenes.dto;

import com.diseconstech.ordenes.modelo.AccionBitacora;
import com.diseconstech.ordenes.modelo.Bitacora;
import com.diseconstech.ordenes.modelo.Catalogo;
import com.diseconstech.ordenes.modelo.Departamento;
import com.diseconstech.ordenes.modelo.Proveedor;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class MaestroDtos {

    private MaestroDtos() {
    }

    public record CatalogoDto(Long id, String codigo, String descripcion) {
        public static CatalogoDto de(Catalogo c) {
            return new CatalogoDto(c.getId(), c.getCodigo(), c.getDescripcion());
        }
    }

    public record DepartamentoRequest(
            @NotBlank(message = "El código es obligatorio") @Size(max = 20, message = "Máximo 20 caracteres")
            @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Solo letras, números, guion y guion bajo") String codigo,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 100, message = "Máximo 100 caracteres") String nombre,
            Boolean activo) {
    }

    public record DepartamentoDto(Long id, String codigo, String nombre, boolean activo) {
        public static DepartamentoDto de(Departamento d) {
            return new DepartamentoDto(d.getId(), d.getCodigo(), d.getNombre(), d.isActivo());
        }
    }

    public record ProveedorRequest(
            @NotBlank(message = "El NIT es obligatorio") @Size(max = 20, message = "Máximo 20 caracteres")
            @Pattern(regexp = "^[0-9]+-?[0-9Kk]$", message = "NIT inválido (ejemplo: 1234567-8)") String nit,
            @NotBlank(message = "La razón social es obligatoria") @Size(max = 200, message = "Máximo 200 caracteres") String razonSocial,
            @Size(max = 200, message = "Máximo 200 caracteres") String nombreComercial,
            @Size(max = 20, message = "Máximo 20 caracteres")
            @Pattern(regexp = "^[0-9+\\- ]*$", message = "Solo números, espacios, + y -") String telefono,
            @Size(max = 150, message = "Máximo 150 caracteres") @Email(message = "Correo inválido") String email,
            @Size(max = 300, message = "Máximo 300 caracteres") String direccion,
            Boolean activo) {
    }

    public record ProveedorDto(Long id, String nit, String razonSocial, String nombreComercial, String telefono,
                               String email, String direccion, boolean activo) {
        public static ProveedorDto de(Proveedor p) {
            return new ProveedorDto(p.getId(), p.getNit(), p.getRazonSocial(), p.getNombreComercial(),
                    p.getTelefono(), p.getEmail(), p.getDireccion(), p.isActivo());
        }
    }

    /** valoresAnteriores/Nuevos van como texto JSON tal cual se guardaron. */
    public record BitacoraDto(Long id, OffsetDateTime fecha, String username, AccionBitacora accion, String entidad,
                              Long entidadId, String valoresAnteriores, String valoresNuevos, String ip) {
        public static BitacoraDto de(Bitacora b) {
            return new BitacoraDto(b.getId(), b.getFecha(), b.getUsername(), b.getAccion(), b.getEntidad(),
                    b.getEntidadId(), b.getValoresAnteriores(), b.getValoresNuevos(), b.getIp());
        }
    }
}

package com.diseconstech.ordenes.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "usuarios")
public class Usuario extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String username;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(nullable = false, length = 150, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_autenticacion", nullable = false, length = 10)
    private TipoAutenticacion tipoAutenticacion;

    /** Solo usuarios LOCAL (externos). Nunca se serializa hacia el cliente. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departamento_id")
    private Departamento departamento;

    /** Monto máximo que puede aprobar solo; null = no aprueba. */
    @Column(name = "limite_aprobacion", precision = 18, scale = 2)
    private BigDecimal limiteAprobacion;

    @Column(name = "intentos_fallidos", nullable = false)
    private Short intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private OffsetDateTime bloqueadoHasta;

    @Column(name = "debe_cambiar_password", nullable = false)
    private boolean debeCambiarPassword = false;

    @Column(name = "ultimo_acceso")
    private OffsetDateTime ultimoAcceso;

    @Column(nullable = false)
    private boolean activo = true;

    // EAGER: son 1-2 roles por usuario y se necesitan en cada request para autorizar.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuario_rol",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    private Set<Rol> roles = new HashSet<>();

    public boolean tieneRol(String codigo) {
        return roles.stream().anyMatch(r -> r.getCodigo().equals(codigo));
    }

    public boolean esInterno() {
        return tipoAutenticacion == TipoAutenticacion.LDAP;
    }

    public boolean estaBloqueado() {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(OffsetDateTime.now());
    }
}

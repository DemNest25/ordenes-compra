package com.diseconstech.ordenes.repositorio;

import com.diseconstech.ordenes.modelo.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Login: sin distinguir mayúsculas (en LDAP "JPerez" y "jperez" son el mismo). */
    Optional<Usuario> findByUsernameIgnoreCaseAndEliminadoFalse(String username);

    // Updates directos (sin pasar por la entidad): un login no es una "modificación" del usuario,
    // así no se disparan @LastModifiedDate/By ni se pisa actualizado_en en cada inicio de sesión.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Usuario u set u.intentosFallidos = 0, u.bloqueadoHasta = null, u.ultimoAcceso = :ahora where u.id = :id")
    void registrarAccesoExitoso(Long id, OffsetDateTime ahora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Usuario u set u.intentosFallidos = :intentos, u.bloqueadoHasta = :bloqueadoHasta where u.id = :id")
    void registrarIntentoFallido(Long id, short intentos, OffsetDateTime bloqueadoHasta);
}

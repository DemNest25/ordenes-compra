package com.diseconstech.ordenes.repositorio;

import com.diseconstech.ordenes.modelo.Catalogo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CatalogoRepository extends JpaRepository<Catalogo, Long> {

    List<Catalogo> findByTipoAndActivoTrueAndEliminadoFalseOrderByOrden(String tipo);

    /** Valida que un motivo exista y sea del tipo correcto (que no manden una unidad como motivo). */
    Optional<Catalogo> findByIdAndTipoAndActivoTrueAndEliminadoFalse(Long id, String tipo);
}

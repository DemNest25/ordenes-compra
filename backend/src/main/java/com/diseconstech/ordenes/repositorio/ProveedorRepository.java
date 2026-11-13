package com.diseconstech.ordenes.repositorio;

import com.diseconstech.ordenes.modelo.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    List<Proveedor> findByEliminadoFalseOrderByRazonSocial();

    List<Proveedor> findByActivoTrueAndEliminadoFalseOrderByRazonSocial();

    Optional<Proveedor> findByIdAndEliminadoFalse(Long id);
}

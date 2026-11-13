package com.diseconstech.ordenes.repositorio;

import com.diseconstech.ordenes.modelo.Departamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartamentoRepository extends JpaRepository<Departamento, Long> {

    List<Departamento> findByEliminadoFalseOrderByNombre();

    List<Departamento> findByActivoTrueAndEliminadoFalseOrderByNombre();

    Optional<Departamento> findByIdAndEliminadoFalse(Long id);
}

package com.diseconstech.ordenes.repositorio;

import com.diseconstech.ordenes.modelo.OrdenHistorialEstado;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrdenHistorialEstadoRepository extends JpaRepository<OrdenHistorialEstado, Long> {

    @EntityGraph(attributePaths = "usuario")
    List<OrdenHistorialEstado> findByOrdenIdOrderByFecha(Long ordenId);
}

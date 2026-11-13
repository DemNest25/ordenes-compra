package com.diseconstech.ordenes.repositorio;

import com.diseconstech.ordenes.modelo.Orden;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * JpaSpecificationExecutor permite armar filtros dinámicos (estado, fechas, proveedor...)
 * y, sobre todo, agregar "creador = usuario actual" en el servidor para los externos.
 * La visibilidad por dueño se aplica en OrdenService (listar / cargarVisible).
 */
public interface OrdenRepository extends JpaRepository<Orden, Long>, JpaSpecificationExecutor<Orden> {

    /** Orden completa en una sola consulta (evita N+1 al armar la respuesta). */
    @EntityGraph(attributePaths = {"creador", "departamento", "proveedor", "motivoCancelacion", "detalles"})
    @Query("select o from Orden o where o.id = :id and o.eliminado = false")
    Optional<Orden> findDetalleById(Long id);

    /** Listado paginado con filtros; trae creador/proveedor/departamento en el mismo SELECT (sin N+1). */
    @Override
    @EntityGraph(attributePaths = {"creador", "departamento", "proveedor"})
    Page<Orden> findAll(Specification<Orden> spec, Pageable pageable);
}

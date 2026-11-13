package com.diseconstech.ordenes.repositorio;

import com.diseconstech.ordenes.modelo.Bitacora;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BitacoraRepository extends JpaRepository<Bitacora, Long> {

    List<Bitacora> findByEntidadAndEntidadIdOrderByFecha(String entidad, Long entidadId);

    Page<Bitacora> findAllByOrderByFechaDesc(Pageable pageable);
}

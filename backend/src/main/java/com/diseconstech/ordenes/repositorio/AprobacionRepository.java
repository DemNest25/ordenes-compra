package com.diseconstech.ordenes.repositorio;

import com.diseconstech.ordenes.modelo.Aprobacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AprobacionRepository extends JpaRepository<Aprobacion, Long> {

    @EntityGraph(attributePaths = {"aprobador", "motivoRechazo"})
    List<Aprobacion> findByOrdenIdOrderByFecha(Long ordenId);

    List<Aprobacion> findByOrdenIdAndVigenteTrue(Long ordenId);

    Optional<Aprobacion> findByOrdenIdAndNivelAndVigenteTrue(Long ordenId, Short nivel);
}

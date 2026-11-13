package com.diseconstech.ordenes.servicio;

import com.diseconstech.ordenes.modelo.Parametro;
import com.diseconstech.ordenes.repositorio.ParametroRepository;
import org.springframework.stereotype.Service;

/** Lee parámetros en cada uso (sin caché): un cambio en la tabla aplica de inmediato. */
@Service
public class ParametroService {

    private final ParametroRepository repo;

    public ParametroService(ParametroRepository repo) {
        this.repo = repo;
    }

    public int entero(String clave, int porDefecto) {
        return repo.findById(clave).map(Parametro::comoEntero).orElse(porDefecto);
    }

    public int plazoAprobacionDias() {
        return entero(Parametro.PLAZO_APROBACION_DIAS, 2);
    }
}

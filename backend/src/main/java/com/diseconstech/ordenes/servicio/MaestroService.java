package com.diseconstech.ordenes.servicio;

import com.diseconstech.ordenes.dto.MaestroDtos.*;
import com.diseconstech.ordenes.dto.OrdenDtos.Pagina;
import com.diseconstech.ordenes.error.ApiException;
import com.diseconstech.ordenes.modelo.AccionBitacora;
import com.diseconstech.ordenes.modelo.Departamento;
import com.diseconstech.ordenes.modelo.Proveedor;
import com.diseconstech.ordenes.repositorio.BitacoraRepository;
import com.diseconstech.ordenes.repositorio.CatalogoRepository;
import com.diseconstech.ordenes.repositorio.DepartamentoRepository;
import com.diseconstech.ordenes.repositorio.ProveedorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogos y maestros. Los cambios los hace solo ADMIN (lo exige el controlador) y quedan en bitácora.
 * NIT/código duplicados los detecta el UNIQUE de la BD y ManejadorErrores lo traduce al campo.
 */
@Service
@Transactional
public class MaestroService {

    private final CatalogoRepository catalogo;
    private final DepartamentoRepository departamentos;
    private final ProveedorRepository proveedores;
    private final BitacoraRepository bitacoraRepo;
    private final BitacoraService bitacora;

    public MaestroService(CatalogoRepository catalogo, DepartamentoRepository departamentos,
                          ProveedorRepository proveedores, BitacoraRepository bitacoraRepo, BitacoraService bitacora) {
        this.catalogo = catalogo;
        this.departamentos = departamentos;
        this.proveedores = proveedores;
        this.bitacoraRepo = bitacoraRepo;
        this.bitacora = bitacora;
    }

    @Transactional(readOnly = true)
    public List<CatalogoDto> catalogo(String tipo) {
        return catalogo.findByTipoAndActivoTrueAndEliminadoFalseOrderByOrden(tipo).stream().map(CatalogoDto::de).toList();
    }

    // ------------------------------------------------------------------ departamentos

    @Transactional(readOnly = true)
    public List<DepartamentoDto> departamentos(boolean incluirInactivos) {
        return (incluirInactivos ? departamentos.findByEliminadoFalseOrderByNombre()
                : departamentos.findByActivoTrueAndEliminadoFalseOrderByNombre())
                .stream().map(DepartamentoDto::de).toList();
    }

    public DepartamentoDto guardarDepartamento(Long id, DepartamentoRequest req) {
        Departamento d = id == null ? new Departamento() : departamento(id);
        Map<String, Object> antes = id == null ? null : foto(d);
        d.setCodigo(req.codigo().trim().toUpperCase());
        d.setNombre(req.nombre().trim());
        if (req.activo() != null) d.setActivo(req.activo());
        departamentos.saveAndFlush(d);
        bitacora.registrar(id == null ? AccionBitacora.CREAR : AccionBitacora.MODIFICAR, "departamentos", d.getId(), antes, foto(d));
        return DepartamentoDto.de(d);
    }

    public void eliminarDepartamento(Long id) {
        Departamento d = departamento(id);
        Map<String, Object> antes = foto(d);
        d.setEliminado(true);
        departamentos.saveAndFlush(d);
        bitacora.registrar(AccionBitacora.ELIMINAR, "departamentos", id, antes, Map.of("eliminado", true));
    }

    private Departamento departamento(Long id) {
        return departamentos.findByIdAndEliminadoFalse(id).orElseThrow(() -> ApiException.noEncontrado("El departamento", id));
    }

    private static Map<String, Object> foto(Departamento d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", d.getCodigo());
        m.put("nombre", d.getNombre());
        m.put("activo", d.isActivo());
        return m;
    }

    // ------------------------------------------------------------------ proveedores

    @Transactional(readOnly = true)
    public List<ProveedorDto> proveedores(boolean incluirInactivos) {
        return (incluirInactivos ? proveedores.findByEliminadoFalseOrderByRazonSocial()
                : proveedores.findByActivoTrueAndEliminadoFalseOrderByRazonSocial())
                .stream().map(ProveedorDto::de).toList();
    }

    public ProveedorDto guardarProveedor(Long id, ProveedorRequest req) {
        Proveedor p = id == null ? new Proveedor() : proveedor(id);
        Map<String, Object> antes = id == null ? null : foto(p);
        p.setNit(req.nit().trim().toUpperCase());
        p.setRazonSocial(req.razonSocial().trim());
        p.setNombreComercial(vacioANull(req.nombreComercial()));
        p.setTelefono(vacioANull(req.telefono()));
        p.setEmail(vacioANull(req.email()));
        p.setDireccion(vacioANull(req.direccion()));
        if (req.activo() != null) p.setActivo(req.activo());
        proveedores.saveAndFlush(p);
        bitacora.registrar(id == null ? AccionBitacora.CREAR : AccionBitacora.MODIFICAR, "proveedores", p.getId(), antes, foto(p));
        return ProveedorDto.de(p);
    }

    public void eliminarProveedor(Long id) {
        Proveedor p = proveedor(id);
        Map<String, Object> antes = foto(p);
        p.setEliminado(true);
        proveedores.saveAndFlush(p);
        bitacora.registrar(AccionBitacora.ELIMINAR, "proveedores", id, antes, Map.of("eliminado", true));
    }

    private Proveedor proveedor(Long id) {
        return proveedores.findByIdAndEliminadoFalse(id).orElseThrow(() -> ApiException.noEncontrado("El proveedor", id));
    }

    private static Map<String, Object> foto(Proveedor p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nit", p.getNit());
        m.put("razonSocial", p.getRazonSocial());
        m.put("nombreComercial", p.getNombreComercial());
        m.put("telefono", p.getTelefono());
        m.put("email", p.getEmail());
        m.put("direccion", p.getDireccion());
        m.put("activo", p.isActivo());
        return m;
    }

    // ------------------------------------------------------------------ bitácora (consulta)

    @Transactional(readOnly = true)
    public Pagina<BitacoraDto> bitacora(String entidad, Long entidadId, int pagina, int tamano) {
        if (entidad != null && entidadId != null) {
            List<BitacoraDto> filas = bitacoraRepo.findByEntidadAndEntidadIdOrderByFecha(entidad, entidadId)
                    .stream().map(BitacoraDto::de).toList();
            return new Pagina<>(filas, 0, filas.size(), filas.size(), 1);
        }
        Page<BitacoraDto> page = bitacoraRepo.findAllByOrderByFechaDesc(PageRequest.of(pagina, tamano)).map(BitacoraDto::de);
        return new Pagina<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

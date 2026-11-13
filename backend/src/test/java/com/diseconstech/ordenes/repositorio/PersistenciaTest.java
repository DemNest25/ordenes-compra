package com.diseconstech.ordenes.repositorio;

import com.diseconstech.ordenes.modelo.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba el mapeo contra la BD real (Flyway + triggers). Cada test hace rollback,
 * así que no deja basura en la base de desarrollo.
 */
@SpringBootTest
@Transactional
class PersistenciaTest {

    @Autowired EntityManager em;
    @Autowired OrdenRepository ordenes;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired ParametroRepository parametros;
    @Autowired CatalogoRepository catalogo;
    @Autowired BitacoraRepository bitacora;

    Departamento depto;
    Proveedor proveedor;
    Usuario asesor;

    @BeforeEach
    void datos() {
        depto = new Departamento();
        depto.setCodigo("T-VENTAS");
        depto.setNombre("Ventas (test)");
        em.persist(depto);

        proveedor = new Proveedor();
        proveedor.setNit("T-1234567");
        proveedor.setRazonSocial("Proveedor Test, S.A.");
        em.persist(proveedor);

        asesor = new Usuario();
        asesor.setUsername("t.asesor");
        asesor.setNombreCompleto("Asesor Test");
        asesor.setEmail("t.asesor@test.local");
        asesor.setTipoAutenticacion(TipoAutenticacion.LOCAL);
        asesor.setPasswordHash("$2a$10$hashdeprueba");
        asesor.getRoles().add(roles.findByCodigo(Rol.EXTERNO).orElseThrow());
        usuarios.save(asesor);
    }

    private Orden nuevaOrden() {
        Orden o = new Orden();
        o.setCreador(asesor);
        o.setDepartamento(depto);
        o.setProveedor(proveedor);
        o.setDescripcion("Orden de prueba");
        OrdenDetalle d1 = new OrdenDetalle();
        d1.setLinea((short) 1);
        d1.setDescripcion("Producto A");
        d1.setCantidad(new BigDecimal("3"));
        d1.setPrecioUnitario(new BigDecimal("10.555"));
        o.agregarDetalle(d1);
        OrdenDetalle d2 = new OrdenDetalle();
        d2.setLinea((short) 2);
        d2.setDescripcion("Producto B");
        d2.setCantidad(new BigDecimal("1.50"));
        d2.setPrecioUnitario(new BigDecimal("100.00"));
        o.agregarDetalle(d2);
        o.setMontoTotal(o.calcularTotal());
        return o;
    }

    @Test
    void guardaOrdenConValoresGeneradosPorLaBD() {
        Orden o = ordenes.saveAndFlush(nuevaOrden());

        assertTrue(o.getNumero().matches("OC-\\d{4}-\\d{6}"), "numero generado: " + o.getNumero());
        assertEquals(0, o.getVersion());
        assertEquals(EstadoOrden.BORRADOR, o.getEstado());
        assertNotNull(asesor.getCreadoEn(), "auditoría JPA llenó creado_en");
        assertNull(asesor.getActualizadoEn(), "modifyOnCreate=false");

        em.clear();
        Orden leida = ordenes.findDetalleById(o.getId()).orElseThrow();
        assertEquals(2, leida.getDetalles().size());
        // 10.555 se guarda como 10.56 (NUMERIC(18,2)); 3 × 10.56 = 31.68
        assertEquals(new BigDecimal("31.68"), leida.getDetalles().get(0).getSubtotal());
        assertEquals(new BigDecimal("150.00"), leida.getDetalles().get(1).getSubtotal());
        assertEquals("GTQ", leida.getMoneda());
    }

    @Test
    void lineaEliminadaLogicamenteNoSeCarga() {
        Orden o = ordenes.saveAndFlush(nuevaOrden());
        o.getDetalles().get(1).setEliminado(true);
        ordenes.saveAndFlush(o);
        em.clear();

        Orden leida = ordenes.findDetalleById(o.getId()).orElseThrow();
        assertEquals(1, leida.getDetalles().size());
    }

    @Test
    void bloqueoOptimistaIncrementaVersion() {
        Orden o = ordenes.saveAndFlush(nuevaOrden());
        o.setDescripcion("Cambiada");
        ordenes.saveAndFlush(o);
        assertEquals(1, o.getVersion());
        assertNotNull(o.getActualizadoEn());
    }

    @Test
    void triggerImpideTransicionInvalida() {
        Orden o = ordenes.saveAndFlush(nuevaOrden());
        o.setEstado(EstadoOrden.CERRADA); // BORRADOR -> CERRADA no está permitido
        Exception ex = assertThrows(Exception.class, () -> ordenes.saveAndFlush(o));
        assertTrue(causaRaiz(ex).contains("Transición de estado no permitida"), causaRaiz(ex));
    }

    @Test
    void triggerImpideQueElCreadorApruebe() {
        Orden o = ordenes.saveAndFlush(nuevaOrden());
        Aprobacion a = new Aprobacion();
        a.setOrden(o);
        a.setAprobador(asesor);
        a.setNivel((short) 1);
        a.setDecision(DecisionAprobacion.APROBADA);
        a.setMontoOrden(o.getMontoTotal());
        a.setLimiteAprobador(BigDecimal.ZERO);
        // Con IDENTITY el INSERT ocurre en el persist (necesita el id), ahí salta el trigger
        Exception ex = assertThrows(Exception.class, () -> em.persist(a));
        assertTrue(causaRaiz(ex).contains("creador de la orden no puede"), causaRaiz(ex));
    }

    @Test
    void triggerImpideBorradoFisico() {
        Orden o = ordenes.saveAndFlush(nuevaOrden());
        ordenes.delete(o);
        Exception ex = assertThrows(Exception.class, () -> ordenes.flush());
        assertTrue(causaRaiz(ex).contains("Borrado físico prohibido"), causaRaiz(ex));
    }

    @Test
    void bitacoraGuardaJsonb() {
        Bitacora b = new Bitacora();
        b.setUsername("t.asesor");
        b.setUsuarioId(asesor.getId());
        b.setAccion(AccionBitacora.MODIFICAR);
        b.setEntidad("ordenes");
        b.setEntidadId(1L);
        b.setValoresAnteriores("{\"descripcion\":\"antes\"}");
        b.setValoresNuevos("{\"descripcion\":\"después\"}");
        bitacora.saveAndFlush(b);
        em.clear();

        Bitacora leida = bitacora.findById(b.getId()).orElseThrow();
        assertTrue(leida.getValoresNuevos().contains("después"));
    }

    @Test
    void datosInicialesDisponibles() {
        assertEquals(2, parametros.findById(Parametro.PLAZO_APROBACION_DIAS).orElseThrow().comoEntero());
        assertEquals(5, catalogo.findByTipoAndActivoTrueAndEliminadoFalseOrderByOrden(Catalogo.MOTIVO_RECHAZO).size());
        assertTrue(usuarios.findByUsernameIgnoreCaseAndEliminadoFalse("T.ASESOR").orElseThrow().tieneRol(Rol.EXTERNO));
    }

    private static String causaRaiz(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return String.valueOf(t.getMessage());
    }
}

package com.example.auditoria.domain.entity;

import com.example.auditoria.domain.valueobject.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class HallazgoAuditoriaTest {

    private HallazgoAuditoria nuevoHallazgo() {
        return new HallazgoAuditoria(
                HallazgoId.nuevo(),
                "Titulo de prueba",
                "Descripcion de prueba",
                "Infraestructura",
                Severidad.ALTA,
                LocalDate.of(2026, 8, 1)
        );
    }

    private PlanRemediacion plan() {
        return new PlanRemediacion("Equipo de Infraestructura", LocalDate.of(2026, 8, 20), "Rotar credenciales");
    }

    @Test
    void iniciarRemediacionDesdeAbiertoCambiaEstadoYDevuelveElAnterior() {
        HallazgoAuditoria h = nuevoHallazgo();
        EstadoHallazgo anterior = h.iniciarRemediacion(plan());

        assertEquals(EstadoHallazgo.ABIERTO, anterior);
        assertEquals(EstadoHallazgo.EN_REMEDIACION, h.getEstado());
    }

    @Test
    void cerrarSinPlanLanzaIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> nuevoHallazgo().cerrar());
    }

    @Test
    void reabrirUnHallazgoAbiertoLanzaTransicionInvalidaException() {
        assertThrows(TransicionInvalidaException.class, () -> nuevoHallazgo().reabrir());
    }

    @Test
    void cicloCompletoAbiertoRemediacionCerradoReabrirRemediacion() {
        HallazgoAuditoria h = nuevoHallazgo();
        h.iniciarRemediacion(plan());
        h.cerrar();
        assertNotNull(h.getFechaCierre());

        h.reabrir();
        assertNull(h.getFechaCierre());

        h.iniciarRemediacion(plan());
        assertEquals(EstadoHallazgo.EN_REMEDIACION, h.getEstado());
    }

    @Test
    void reconstituirHallazgoReabiertoPermiteContinuarElCiclo() {
        HallazgoAuditoria h = HallazgoAuditoria.reconstituir(
                HallazgoId.nuevo(),
                "Titulo",
                "Descripcion",
                "Infraestructura",
                Severidad.ALTA,
                LocalDate.of(2026, 8, 1),
                EstadoHallazgo.REABIERTO,
                plan(),
                null
        );

        h.iniciarRemediacion(plan());
        assertEquals(EstadoHallazgo.EN_REMEDIACION, h.getEstado());
    }

    @Test
    void reconstituirHallazgoCerradoConservaLaFechaDeCierreGuardada() {
        LocalDate fecha = LocalDate.of(2026, 8, 15);
        HallazgoAuditoria h = HallazgoAuditoria.reconstituir(
                HallazgoId.nuevo(),
                "Titulo",
                "Descripcion",
                "Infraestructura",
                Severidad.ALTA,
                LocalDate.of(2026, 8, 1),
                EstadoHallazgo.CERRADO,
                plan(),
                fecha
        );

        assertEquals(fecha, h.getFechaCierre());
    }
}
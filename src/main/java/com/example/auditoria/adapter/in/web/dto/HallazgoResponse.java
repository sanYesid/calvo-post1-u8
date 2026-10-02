package com.example.auditoria.adapter.in.web.dto;

import com.example.auditoria.domain.entity.HallazgoAuditoria;
import com.example.auditoria.domain.valueobject.EstadoHallazgo;
import com.example.auditoria.domain.valueobject.Severidad;

import java.time.LocalDate;

public record HallazgoResponse(
    String id,
    String titulo,
    String descripcion,
    String areaResponsable,
    Severidad severidad,
    EstadoHallazgo estado,
    LocalDate fechaDeteccion,
    LocalDate fechaCierre,
    String planResponsable,
    LocalDate planFechaLimite,
    String planNotas
) {
    public static HallazgoResponse desdeDominio(HallazgoAuditoria h) {
        String resp = h.getPlanRemediacion() != null ? h.getPlanRemediacion().responsable() : null;
        LocalDate limite = h.getPlanRemediacion() != null ? h.getPlanRemediacion().fechaLimite() : null;
        String notas = h.getPlanRemediacion() != null ? h.getPlanRemediacion().notas() : null;

        return new HallazgoResponse(
            h.getId().toString(),
            h.getTitulo(),
            h.getDescripcion(),
            h.getAreaResponsable(),
            h.getSeveridad(),
            h.getEstado(),
            h.getFechaDeteccion(),
            h.getFechaCierre(),
            resp,
            limite,
            notas
        );
    }
}
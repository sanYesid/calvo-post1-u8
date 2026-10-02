package com.example.auditoria.usecase.impl;

import com.example.auditoria.domain.valueobject.HallazgoId;
import com.example.auditoria.usecase.ConsultarHistorialUseCase;
import com.example.auditoria.usecase.port.CambioEstadoView;
import com.example.auditoria.usecase.port.HistorialAuditoriaPort;
import java.util.List;

public class ConsultarHistorialService implements ConsultarHistorialUseCase {

    private final HistorialAuditoriaPort historial;

    public ConsultarHistorialService(HistorialAuditoriaPort historial) {
        this.historial = historial;
    }

    @Override
    public List<CambioEstadoView> ejecutar(HallazgoId hallazgoId) {
        return historial.listarPorHallazgo(hallazgoId);
    }
}
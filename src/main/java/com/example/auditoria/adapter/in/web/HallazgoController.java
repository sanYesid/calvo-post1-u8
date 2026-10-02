package com.example.auditoria.adapter.in.web;

import com.example.auditoria.adapter.in.web.dto.*;
import com.example.auditoria.domain.entity.HallazgoAuditoria;
import com.example.auditoria.domain.valueobject.HallazgoId;
import com.example.auditoria.usecase.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/hallazgos")
public class HallazgoController {

    private final RegistrarHallazgoUseCase registrarUseCase;
    private final IniciarRemediacionUseCase iniciarRemediacionUseCase;
    private final CerrarHallazgoUseCase cerrarUseCase;
    private final ReabrirHallazgoUseCase reabrirUseCase;
    private final ConsultarHallazgoUseCase consultarUseCase;

    public HallazgoController(RegistrarHallazgoUseCase registrarUseCase,
                              IniciarRemediacionUseCase iniciarRemediacionUseCase,
                              CerrarHallazgoUseCase cerrarUseCase,
                              ReabrirHallazgoUseCase reabrirUseCase,
                              ConsultarHallazgoUseCase consultarUseCase) {
        this.registrarUseCase = registrarUseCase;
        this.iniciarRemediacionUseCase = iniciarRemediacionUseCase;
        this.cerrarUseCase = cerrarUseCase;
        this.reabrirUseCase = reabrirUseCase;
        this.consultarUseCase = consultarUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> registrar(@RequestBody RegistrarHallazgoRequest req) {
        HallazgoId id = registrarUseCase.ejecutar(
            req.titulo(), req.descripcion(), req.areaResponsable(), req.severidad(), req.fechaDeteccion()
        );
        return Map.of("hallazgoId", id.toString());
    }

    @PatchMapping("/{id}/iniciar-remediacion")
    public Map<String, String> iniciarRemediacion(@PathVariable String id,
                                                  @RequestBody IniciarRemediacionRequest req) {
        iniciarRemediacionUseCase.ejecutar(
            new HallazgoId(UUID.fromString(id)), req.responsable(), req.fechaLimite(), req.notas()
        );
        return Map.of("estado", "EN_REMEDIACION");
    }

    @PatchMapping("/{id}/cerrar")
    public Map<String, String> cerrar(@PathVariable String id) {
        cerrarUseCase.ejecutar(new HallazgoId(UUID.fromString(id)));
        return Map.of("estado", "CERRADO");
    }

    @PatchMapping("/{id}/reabrir")
    public Map<String, String> reabrir(@PathVariable String id, @RequestBody ReabrirRequest req) {
        reabrirUseCase.ejecutar(new HallazgoId(UUID.fromString(id)), req.motivo());
        return Map.of("estado", "REABIERTO");
    }

    @GetMapping("/{id}")
    public HallazgoResponse buscar(@PathVariable String id) {
        HallazgoAuditoria hallazgo = consultarUseCase.buscarPorId(new HallazgoId(UUID.fromString(id)));
        return HallazgoResponse.desdeDominio(hallazgo);
    }

    @GetMapping
    public List<HallazgoResponse> listar() {
        return consultarUseCase.listarTodos().stream()
                .map(HallazgoResponse::desdeDominio)
                .toList();
    }
}
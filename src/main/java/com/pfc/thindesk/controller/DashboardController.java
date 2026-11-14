package com.pfc.thindesk.controller;

import com.pfc.thindesk.dto.Agrupamento;
import com.pfc.thindesk.dto.FiltroDashboard;
import com.pfc.thindesk.dto.FiltrosDisponiveis;
import com.pfc.thindesk.dto.IndicadoresDashboard;
import com.pfc.thindesk.dto.ItemContagem;
import com.pfc.thindesk.dto.PontoPeriodo;
import com.pfc.thindesk.service.DashboardService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/indicadores")
    public IndicadoresDashboard indicadores(FiltroDashboard filtro) {
        return dashboardService.calcularIndicadores(filtro);
    }

    @GetMapping("/periodo")
    public List<PontoPeriodo> periodo(FiltroDashboard filtro, @RequestParam(defaultValue = "DIA") Agrupamento agrupamento) {
        return dashboardService.listarPorPeriodo(filtro, agrupamento);
    }

    @GetMapping("/status")
    public List<ItemContagem> status(FiltroDashboard filtro) {
        return dashboardService.listarPorStatus(filtro);
    }

    @GetMapping("/tipos")
    public List<ItemContagem> tipos(FiltroDashboard filtro, @RequestParam(defaultValue = "5") int limite) {
        return dashboardService.listarTiposMaisAtendidos(filtro, limite);
    }

    @GetMapping("/filtros")
    public FiltrosDisponiveis filtros() {
        return dashboardService.listarFiltros();
    }
}

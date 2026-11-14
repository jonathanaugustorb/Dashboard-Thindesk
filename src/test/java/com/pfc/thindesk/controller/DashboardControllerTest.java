package com.pfc.thindesk.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pfc.SecurityConfig;
import com.pfc.thindesk.dto.Agrupamento;
import com.pfc.thindesk.dto.FiltroDashboard;
import com.pfc.thindesk.dto.IndicadoresDashboard;
import com.pfc.thindesk.dto.PontoPeriodo;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.exception.ParametroInvalidoException;
import com.pfc.thindesk.service.DashboardService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @Test
    @WithMockUser
    void deveRetornarIndicadoresUsandoOsFiltrosRecebidos() throws Exception {
        FiltroDashboard filtro = new FiltroDashboard(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 14), StatusChamado.CONCLUIDO, "Rede", "Maria");
        when(dashboardService.calcularIndicadores(filtro)).thenReturn(new IndicadoresDashboard(10, 0, 0, 10, 0, 35.0));

        mockMvc.perform(get("/api/dashboard/indicadores")
                        .param("inicio", "2026-11-01")
                        .param("fim", "2026-11-14")
                        .param("status", "CONCLUIDO")
                        .param("tipo", "Rede")
                        .param("tecnico", "Maria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.concluidos").value(10))
                .andExpect(jsonPath("$.tempoMedioResolucaoHoras").value(35.0));
    }

    @Test
    @WithMockUser
    void deveAgruparPorDiaQuandoOAgrupamentoNaoForInformado() throws Exception {
        when(dashboardService.listarPorPeriodo(FiltroDashboard.vazio(), Agrupamento.DIA))
                .thenReturn(List.of(new PontoPeriodo("2026-11-14", 2, 1)));

        mockMvc.perform(get("/api/dashboard/periodo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].periodo").value("2026-11-14"))
                .andExpect(jsonPath("$[0].abertos").value(2))
                .andExpect(jsonPath("$[0].concluidos").value(1));
    }

    @Test
    @WithMockUser
    void deveRetornarErroQuandoOStatusForInvalido() throws Exception {
        mockMvc.perform(get("/api/dashboard/indicadores").param("status", "XYZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value("Valor inválido para: status"));
        verifyNoInteractions(dashboardService);
    }

    @Test
    @WithMockUser
    void deveRetornarErroQuandoOAgrupamentoForInvalido() throws Exception {
        mockMvc.perform(get("/api/dashboard/periodo").param("agrupamento", "ANO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Valor inválido para: agrupamento"));
        verifyNoInteractions(dashboardService);
    }

    @Test
    @WithMockUser
    void deveRetornarAMensagemDoServicoQuandoUmParametroForRecusado() throws Exception {
        when(dashboardService.calcularIndicadores(any()))
                .thenThrow(new ParametroInvalidoException("A data inicial não pode ser posterior à data final"));

        mockMvc.perform(get("/api/dashboard/indicadores").param("inicio", "2026-11-14").param("fim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A data inicial não pode ser posterior à data final"));
    }

    @Test
    void deveRedirecionarParaOLoginQuandoNaoHouverUsuarioAutenticado() throws Exception {
        mockMvc.perform(get("/api/dashboard/indicadores"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(dashboardService);
    }
}

package com.pfc.thindesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pfc.thindesk.dto.Agrupamento;
import com.pfc.thindesk.dto.FiltroDashboard;
import com.pfc.thindesk.dto.FiltrosDisponiveis;
import com.pfc.thindesk.dto.IndicadoresDashboard;
import com.pfc.thindesk.dto.ItemContagem;
import com.pfc.thindesk.dto.PontoPeriodo;
import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.exception.ParametroInvalidoException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 14, 12, 0);

    @Mock
    private MongoTemplate mongoTemplate;

    private DashboardService dashboardService;

    @BeforeEach
    void configurar() {
        ZoneId zona = ZoneId.systemDefault();
        Clock clock = Clock.fixed(AGORA.atZone(zona).toInstant(), zona);
        dashboardService = new DashboardService(mongoTemplate, clock);
    }

    @Test
    void deveCalcularIndicadoresPorStatusETempoMedioDeResolucao() {
        retornar(
                chamado(StatusChamado.ABERTO, AGORA.minusDays(1), null),
                chamado(StatusChamado.ABERTO, AGORA.minusDays(1), null),
                chamado(StatusChamado.EM_ANDAMENTO, AGORA.minusDays(1), null),
                chamado(StatusChamado.CONCLUIDO, AGORA.minusHours(10), AGORA.minusHours(8)),
                chamado(StatusChamado.CONCLUIDO, AGORA.minusHours(5), AGORA.minusHours(1)),
                chamado(StatusChamado.CANCELADO, AGORA.minusDays(1), null),
                chamado(null, null, null));

        IndicadoresDashboard indicadores = dashboardService.calcularIndicadores(FiltroDashboard.vazio());

        assertThat(indicadores).isEqualTo(new IndicadoresDashboard(7, 2, 1, 2, 1, 3.0));
    }

    @Test
    void deveRetornarTempoMedioNuloQuandoNaoHaChamadosConcluidos() {
        retornar(chamado(StatusChamado.ABERTO, AGORA, null));

        IndicadoresDashboard indicadores = dashboardService.calcularIndicadores(FiltroDashboard.vazio());

        assertThat(indicadores.tempoMedioResolucaoHoras()).isNull();
    }

    @Test
    void deveAplicarFiltrosDeStatusTipoETecnico() {
        retornar(
                chamadoComTipo(StatusChamado.CONCLUIDO, "Rede", "Maria"),
                chamadoComTipo(StatusChamado.CONCLUIDO, "Rede", "João"),
                chamadoComTipo(StatusChamado.ABERTO, "Rede", "Maria"),
                chamadoComTipo(StatusChamado.CONCLUIDO, "Acesso", "Maria"));
        FiltroDashboard filtro = new FiltroDashboard(null, null, StatusChamado.CONCLUIDO, "Rede", "Maria");

        IndicadoresDashboard indicadores = dashboardService.calcularIndicadores(filtro);

        assertThat(indicadores.total()).isEqualTo(1);
    }

    @Test
    void deveConsultarChamadosPelaDataDeAberturaDoPeriodo() {
        retornar();
        FiltroDashboard filtro = new FiltroDashboard(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 14), null, null, null);

        dashboardService.calcularIndicadores(filtro);

        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(consulta.capture(), eq(Chamado.class));
        Document criterio = (Document) consulta.getValue().getQueryObject().get("dataAbertura");
        assertThat(criterio.get("$gte")).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(criterio.get("$lt")).isEqualTo(LocalDateTime.of(2026, 11, 15, 0, 0));
    }

    @Test
    void deveRecusarDataInicialPosteriorADataFinal() {
        FiltroDashboard filtro = new FiltroDashboard(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 1), null, null, null);

        assertThatThrownBy(() -> dashboardService.calcularIndicadores(filtro))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessage("A data inicial não pode ser posterior à data final");
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void deveMontarSerieDiariaIncluindoDiasSemChamados() {
        retornar(
                chamado(StatusChamado.CONCLUIDO, LocalDateTime.of(2026, 11, 10, 8, 0), LocalDateTime.of(2026, 11, 12, 9, 0)),
                chamado(StatusChamado.ABERTO, LocalDateTime.of(2026, 11, 10, 15, 0), null));
        FiltroDashboard filtro = new FiltroDashboard(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12), null, null, null);

        List<PontoPeriodo> pontos = dashboardService.listarPorPeriodo(filtro, Agrupamento.DIA);

        assertThat(pontos).containsExactly(
                new PontoPeriodo("2026-11-10", 2, 0),
                new PontoPeriodo("2026-11-11", 0, 0),
                new PontoPeriodo("2026-11-12", 0, 1));
    }

    @Test
    void deveAgruparSeriePorMesDaPrimeiraAberturaAteHoje() {
        retornar(
                chamado(StatusChamado.CONCLUIDO, LocalDateTime.of(2026, 9, 20, 10, 0), LocalDateTime.of(2026, 10, 2, 10, 0)),
                chamado(StatusChamado.ABERTO, LocalDateTime.of(2026, 11, 3, 10, 0), null));

        List<PontoPeriodo> pontos = dashboardService.listarPorPeriodo(FiltroDashboard.vazio(), Agrupamento.MES);

        assertThat(pontos).containsExactly(
                new PontoPeriodo("2026-09", 1, 0),
                new PontoPeriodo("2026-10", 0, 1),
                new PontoPeriodo("2026-11", 1, 0));
    }

    @Test
    void deveListarTodosOsStatusEContarChamadosSemStatusComoNaoInformado() {
        retornar(
                chamado(StatusChamado.CONCLUIDO, AGORA, AGORA),
                chamado(null, null, null));

        List<ItemContagem> itens = dashboardService.listarPorStatus(FiltroDashboard.vazio());

        assertThat(itens).containsExactly(
                new ItemContagem("Aberto", 0),
                new ItemContagem("Em andamento", 0),
                new ItemContagem("Concluído", 1),
                new ItemContagem("Cancelado", 0),
                new ItemContagem("Não informado", 1));
    }

    @Test
    void deveOrdenarTiposPelaQuantidadeELimitarOResultado() {
        retornar(
                chamadoComTipo(StatusChamado.ABERTO, "Rede", null),
                chamadoComTipo(StatusChamado.ABERTO, "Rede", null),
                chamadoComTipo(StatusChamado.ABERTO, " Rede ", null),
                chamadoComTipo(StatusChamado.ABERTO, null, null),
                chamadoComTipo(StatusChamado.ABERTO, " ", null),
                chamadoComTipo(StatusChamado.ABERTO, "Acesso", null));

        List<ItemContagem> tipos = dashboardService.listarTiposMaisAtendidos(FiltroDashboard.vazio(), 2);

        assertThat(tipos).containsExactly(
                new ItemContagem("Rede", 3),
                new ItemContagem("Não informado", 2));
    }

    @Test
    void deveRecusarLimiteMenorQueUm() {
        assertThatThrownBy(() -> dashboardService.listarTiposMaisAtendidos(FiltroDashboard.vazio(), 0))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessage("O limite deve ser maior que zero");
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    void deveListarOpcoesDeFiltroSemRepeticaoEEmOrdemAlfabetica() {
        retornar(
                chamadoComTipo(StatusChamado.ABERTO, "Rede", "Maria"),
                chamadoComTipo(StatusChamado.ABERTO, "Acesso", "Carlos"),
                chamadoComTipo(StatusChamado.ABERTO, "Rede", null));

        FiltrosDisponiveis filtros = dashboardService.listarFiltros();

        assertThat(filtros.status()).hasSize(StatusChamado.values().length);
        assertThat(filtros.tipos()).containsExactly("Acesso", "Rede");
        assertThat(filtros.tecnicos()).containsExactly("Carlos", "Maria", "Não informado");
    }

    private void retornar(Chamado... chamados) {
        when(mongoTemplate.find(any(Query.class), eq(Chamado.class))).thenReturn(List.of(chamados));
    }

    private static Chamado chamado(StatusChamado status, LocalDateTime abertura, LocalDateTime conclusao) {
        Chamado chamado = new Chamado();
        chamado.setStatus(status);
        chamado.setDataAbertura(abertura);
        chamado.setDataConclusao(conclusao);
        return chamado;
    }

    private static Chamado chamadoComTipo(StatusChamado status, String tipo, String tecnico) {
        Chamado chamado = chamado(status, AGORA, null);
        chamado.setTipo(tipo);
        chamado.setTecnico(tecnico);
        return chamado;
    }
}

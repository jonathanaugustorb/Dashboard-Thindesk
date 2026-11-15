package com.pfc.thindesk.service;

import com.pfc.thindesk.dto.Agrupamento;
import com.pfc.thindesk.dto.FiltroDashboard;
import com.pfc.thindesk.dto.FiltrosDisponiveis;
import com.pfc.thindesk.dto.IndicadoresDashboard;
import com.pfc.thindesk.dto.ItemContagem;
import com.pfc.thindesk.dto.OpcaoFiltro;
import com.pfc.thindesk.dto.PontoPeriodo;
import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.exception.ParametroInvalidoException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    static final String NAO_INFORMADO = "Não informado";

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public DashboardService(MongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    public IndicadoresDashboard calcularIndicadores(FiltroDashboard filtro) {
        List<Chamado> chamados = buscar(filtro);
        Map<StatusChamado, Long> porStatus = agruparPorStatus(chamados);
        return new IndicadoresDashboard(
                chamados.size(),
                porStatus.getOrDefault(StatusChamado.ABERTO, 0L),
                porStatus.getOrDefault(StatusChamado.EM_ANDAMENTO, 0L),
                porStatus.getOrDefault(StatusChamado.CONCLUIDO, 0L),
                porStatus.getOrDefault(StatusChamado.CANCELADO, 0L),
                calcularTempoMedioResolucao(chamados));
    }

    public List<PontoPeriodo> listarPorPeriodo(FiltroDashboard filtro, Agrupamento agrupamento) {
        List<Chamado> chamados = buscar(filtro);
        LocalDate fim = filtro.fim() != null ? filtro.fim() : LocalDate.now(clock);
        LocalDate inicio = filtro.inicio() != null ? filtro.inicio() : primeiraAbertura(chamados);
        if (inicio == null || inicio.isAfter(fim)) {
            return List.of();
        }
        Map<String, Long> abertos = contarPorPeriodo(chamados, Chamado::getDataAbertura, agrupamento);
        Map<String, Long> concluidos = contarPorPeriodo(chamados, Chamado::getDataConclusao, agrupamento);
        return Stream.iterate(agrupamento.inicioDoPeriodo(inicio), data -> !data.isAfter(fim), agrupamento::proximo)
                .map(agrupamento::rotulo)
                .map(periodo -> new PontoPeriodo(periodo, abertos.getOrDefault(periodo, 0L), concluidos.getOrDefault(periodo, 0L)))
                .toList();
    }

    public List<ItemContagem> listarPorStatus(FiltroDashboard filtro) {
        List<Chamado> chamados = buscar(filtro);
        Map<StatusChamado, Long> porStatus = agruparPorStatus(chamados);
        List<ItemContagem> itens = new ArrayList<>(Arrays.stream(StatusChamado.values())
                .map(status -> new ItemContagem(status.getDescricao(), porStatus.getOrDefault(status, 0L)))
                .toList());
        long semStatus = chamados.stream().filter(chamado -> chamado.getStatus() == null).count();
        if (semStatus > 0) {
            itens.add(new ItemContagem(NAO_INFORMADO, semStatus));
        }
        return itens;
    }

    public List<ItemContagem> listarTiposMaisAtendidos(FiltroDashboard filtro, int limite) {
        if (limite < 1) {
            throw new ParametroInvalidoException("O limite deve ser maior que zero");
        }
        return buscar(filtro).stream()
                .collect(Collectors.groupingBy(chamado -> rotulo(chamado.getTipo()), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limite)
                .map(entrada -> new ItemContagem(entrada.getKey(), entrada.getValue()))
                .toList();
    }

    public FiltrosDisponiveis listarFiltros() {
        List<Chamado> chamados = buscar(FiltroDashboard.vazio());
        List<OpcaoFiltro> status = Arrays.stream(StatusChamado.values())
                .map(item -> new OpcaoFiltro(item.name(), item.getDescricao()))
                .toList();
        return new FiltrosDisponiveis(
                status,
                valoresDistintos(chamados, Chamado::getTipo),
                valoresDistintos(chamados, Chamado::getTecnico));
    }

    private List<Chamado> buscar(FiltroDashboard filtro) {
        validar(filtro);
        Query query = new Query();
        if (filtro.inicio() != null || filtro.fim() != null) {
            Criteria abertura = Criteria.where("dataAbertura");
            if (filtro.inicio() != null) {
                abertura.gte(filtro.inicio().atStartOfDay());
            }
            if (filtro.fim() != null) {
                abertura.lt(filtro.fim().plusDays(1).atStartOfDay());
            }
            query.addCriteria(abertura);
        }
        return mongoTemplate.find(query, Chamado.class).stream()
                .filter(chamado -> atende(chamado, filtro))
                .toList();
    }

    private static void validar(FiltroDashboard filtro) {
        if (filtro.inicio() != null && filtro.fim() != null && filtro.inicio().isAfter(filtro.fim())) {
            throw new ParametroInvalidoException("A data inicial não pode ser posterior à data final");
        }
    }

    private static boolean atende(Chamado chamado, FiltroDashboard filtro) {
        return (filtro.status() == null || filtro.status() == chamado.getStatus())
                && (filtro.tipo() == null || filtro.tipo().equals(rotulo(chamado.getTipo())))
                && (filtro.tecnico() == null || filtro.tecnico().equals(rotulo(chamado.getTecnico())));
    }

    private static Map<StatusChamado, Long> agruparPorStatus(List<Chamado> chamados) {
        return chamados.stream()
                .map(Chamado::getStatus)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), () -> new EnumMap<>(StatusChamado.class), Collectors.counting()));
    }

    private static Double calcularTempoMedioResolucao(List<Chamado> chamados) {
        OptionalDouble mediaEmMinutos = chamados.stream()
                .filter(chamado -> chamado.getStatus() == StatusChamado.CONCLUIDO)
                .filter(chamado -> chamado.getDataAbertura() != null && chamado.getDataConclusao() != null)
                .mapToLong(chamado -> Duration.between(chamado.getDataAbertura(), chamado.getDataConclusao()).toMinutes())
                .average();
        return mediaEmMinutos.isPresent() ? Math.round(mediaEmMinutos.getAsDouble() / 6) / 10.0 : null;
    }

    private static LocalDate primeiraAbertura(List<Chamado> chamados) {
        return chamados.stream()
                .map(Chamado::getDataAbertura)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static Map<String, Long> contarPorPeriodo(List<Chamado> chamados, Function<Chamado, LocalDateTime> data, Agrupamento agrupamento) {
        return chamados.stream()
                .map(data)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(valor -> agrupamento.rotulo(valor.toLocalDate()), Collectors.counting()));
    }

    private static List<String> valoresDistintos(List<Chamado> chamados, Function<Chamado, String> campo) {
        return chamados.stream()
                .map(campo)
                .map(DashboardService::rotulo)
                .distinct()
                .sorted()
                .toList();
    }

    private static String rotulo(String valor) {
        return valor == null || valor.isBlank() ? NAO_INFORMADO : valor.trim();
    }
}

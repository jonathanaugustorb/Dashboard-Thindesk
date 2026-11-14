package com.pfc;

import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.repository.ChamadoRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class CargaDadosExemplo implements CommandLineRunner {

    private static final int QUANTIDADE = 60;
    private static final int DIAS = 90;
    private static final List<String> TIPOS = List.of("Manutenção", "Instalação", "Rede", "Acesso", "Impressora");
    private static final List<String> TECNICOS = List.of("João", "Maria", "Carlos");
    private static final List<String> USUARIOS = List.of("Alberto", "Fernanda", "Ricardo", "Juliana");
    private static final List<StatusChamado> STATUS = List.of(
            StatusChamado.CONCLUIDO, StatusChamado.CONCLUIDO, StatusChamado.CONCLUIDO,
            StatusChamado.ABERTO, StatusChamado.EM_ANDAMENTO, StatusChamado.CANCELADO);

    private final ChamadoRepository chamadoRepository;
    private final Clock clock;

    public CargaDadosExemplo(ChamadoRepository chamadoRepository, Clock clock) {
        this.chamadoRepository = chamadoRepository;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        if (chamadoRepository.count() > 0) {
            return;
        }
        Random random = new Random(42);
        LocalDateTime agora = LocalDateTime.now(clock);
        List<Chamado> chamados = IntStream.range(0, QUANTIDADE)
                .mapToObj(indice -> criarChamado(random, agora))
                .toList();
        chamadoRepository.saveAll(chamados);
    }

    private Chamado criarChamado(Random random, LocalDateTime agora) {
        String tipo = sortear(random, TIPOS);
        StatusChamado status = sortear(random, STATUS);
        LocalDateTime abertura = agora.minusDays(random.nextInt(DIAS)).minusHours(random.nextInt(24));

        Chamado chamado = new Chamado();
        chamado.setDescricao("Chamado de " + tipo.toLowerCase());
        chamado.setTipo(tipo);
        chamado.setTecnico(sortear(random, TECNICOS));
        chamado.setUsuario(sortear(random, USUARIOS));
        chamado.setStatus(status);
        chamado.setDataAbertura(abertura);
        if (status == StatusChamado.CONCLUIDO) {
            LocalDateTime conclusao = abertura.plusHours(1 + random.nextInt(72));
            chamado.setDataConclusao(conclusao.isAfter(agora) ? agora : conclusao);
        }
        return chamado;
    }

    private static <T> T sortear(Random random, List<T> opcoes) {
        return opcoes.get(random.nextInt(opcoes.size()));
    }
}

package com.pfc.thindesk.service;

import com.pfc.thindesk.entity.Chamado;
import com.pfc.thindesk.entity.StatusChamado;
import com.pfc.thindesk.repository.ChamadoRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChamadoService {

    private final ChamadoRepository chamadoRepository;
    private final Clock clock;

    public ChamadoService(ChamadoRepository chamadoRepository, Clock clock) {
        this.chamadoRepository = chamadoRepository;
        this.clock = clock;
    }

    public Chamado criarChamado(Chamado chamado) {
        LocalDateTime agora = LocalDateTime.now(clock);
        StatusChamado status = chamado.getStatus() == null ? StatusChamado.ABERTO : chamado.getStatus();
        chamado.setStatus(status);
        chamado.setDataAbertura(agora);
        chamado.setDataConclusao(status == StatusChamado.CONCLUIDO ? agora : null);
        return chamadoRepository.save(chamado);
    }

    public List<Chamado> listarChamados() {
        return chamadoRepository.findAll();
    }

    public Chamado atualizarChamado(String id, Chamado chamadoAtualizado) {
        return chamadoRepository.findById(id)
                .map(chamado -> {
                    chamado.setDescricao(chamadoAtualizado.getDescricao());
                    chamado.setTipo(chamadoAtualizado.getTipo());
                    chamado.setTecnico(chamadoAtualizado.getTecnico());
                    chamado.setUsuario(chamadoAtualizado.getUsuario());
                    atualizarStatus(chamado, chamadoAtualizado.getStatus());
                    return chamadoRepository.save(chamado);
                })
                .orElseThrow(() -> new RuntimeException("Chamado não encontrado com id: " + id));
    }

    private void atualizarStatus(Chamado chamado, StatusChamado novoStatus) {
        if (novoStatus == null || novoStatus == chamado.getStatus()) {
            return;
        }
        chamado.setStatus(novoStatus);
        chamado.setDataConclusao(novoStatus == StatusChamado.CONCLUIDO ? LocalDateTime.now(clock) : null);
    }
}

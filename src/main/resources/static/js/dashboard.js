const API_DASHBOARD = '/api/dashboard';
const INTERVALO_ATUALIZACAO_MS = 60000;
const DIAS_PADRAO = 30;
const AZUL = '#2a78d6';
const LARANJA = '#eb6834';
const COR_GRADE = '#e1e0d9';
const COR_NAO_INFORMADO = '#c3c2b7';
const CORES_STATUS = {
    'Aberto': AZUL,
    'Em andamento': LARANJA,
    'Concluído': '#1baf7a',
    'Cancelado': '#eda100'
};

const formulario = document.getElementById('filtros');
const botaoAtualizar = document.getElementById('atualizar');
const indicadorCarregando = document.getElementById('carregando');
const mensagemErro = document.getElementById('mensagem-erro');
const mensagemVazio = document.getElementById('mensagem-vazio');
const ultimaAtualizacao = document.getElementById('ultima-atualizacao');
const legendaStatus = document.getElementById('legenda-status');
const formatoNumero = new Intl.NumberFormat('pt-BR');
const formatoHoras = new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const graficos = {};
let carregando = false;

Chart.defaults.font.family = 'system-ui, -apple-system, "Segoe UI", sans-serif';
Chart.defaults.color = '#52514e';

function formatarDataISO(data) {
    const ano = data.getFullYear();
    const mes = String(data.getMonth() + 1).padStart(2, '0');
    const dia = String(data.getDate()).padStart(2, '0');
    return `${ano}-${mes}-${dia}`;
}

function definirPeriodoPadrao() {
    const fim = new Date();
    const inicio = new Date();
    inicio.setDate(fim.getDate() - (DIAS_PADRAO - 1));
    document.getElementById('inicio').value = formatarDataISO(inicio);
    document.getElementById('fim').value = formatarDataISO(fim);
}

function montarParametros() {
    const parametros = new URLSearchParams();
    new FormData(formulario).forEach((valor, chave) => {
        if (valor) {
            parametros.append(chave, valor);
        }
    });
    return parametros;
}

async function consultar(recurso, parametros = new URLSearchParams()) {
    let resposta;
    try {
        resposta = await fetch(`${API_DASHBOARD}/${recurso}?${parametros}`, { headers: { Accept: 'application/json' } });
    } catch {
        throw new Error('Não foi possível conectar à API.');
    }
    if (resposta.redirected) {
        window.location.assign(resposta.url);
        throw new Error('Sessão expirada. Faça login novamente.');
    }
    const corpo = await resposta.json().catch(() => null);
    if (!resposta.ok) {
        throw new Error(corpo?.mensagem ?? `Erro ${resposta.status} ao consultar a API.`);
    }
    return corpo;
}

async function atualizar() {
    if (carregando) {
        return;
    }
    alternarCarregamento(true);
    try {
        const parametros = montarParametros();
        const [filtros, indicadores, periodo, status, tipos] = await Promise.all([
            consultar('filtros'),
            consultar('indicadores', parametros),
            consultar('periodo', parametros),
            consultar('status', parametros),
            consultar('tipos', parametros)
        ]);
        exibirOpcoesDeFiltro(filtros);
        exibirIndicadores(indicadores);
        exibirPeriodo(periodo);
        exibirStatus(status);
        exibirTipos(tipos);
        mensagemVazio.classList.toggle('d-none', indicadores.total > 0);
        mensagemErro.classList.add('d-none');
        ultimaAtualizacao.textContent = `Atualizado às ${new Date().toLocaleTimeString('pt-BR')}`;
    } catch (erro) {
        mensagemErro.textContent = erro.message;
        mensagemErro.classList.remove('d-none');
    } finally {
        alternarCarregamento(false);
    }
}

function alternarCarregamento(ativo) {
    carregando = ativo;
    botaoAtualizar.disabled = ativo;
    indicadorCarregando.classList.toggle('d-none', !ativo);
}

function exibirOpcoesDeFiltro(filtros) {
    preencherSelect('status', filtros.status.map(opcao => [opcao.valor, opcao.rotulo]));
    preencherSelect('tipo', filtros.tipos.map(tipo => [tipo, tipo]));
    preencherSelect('tecnico', filtros.tecnicos.map(tecnico => [tecnico, tecnico]));
}

function preencherSelect(id, opcoes) {
    const select = document.getElementById(id);
    const selecionado = select.value;
    select.replaceChildren(new Option('Todos', ''), ...opcoes.map(([valor, rotulo]) => new Option(rotulo, valor)));
    select.value = opcoes.some(([valor]) => valor === selecionado) ? selecionado : '';
}

function exibirIndicadores(indicadores) {
    document.querySelectorAll('[data-indicador]').forEach(elemento => {
        const chave = elemento.dataset.indicador;
        elemento.textContent = formatarIndicador(chave, indicadores[chave]);
    });
}

function formatarIndicador(chave, valor) {
    if (chave === 'tempoMedioResolucaoHoras') {
        return valor == null ? '-' : `${formatoHoras.format(valor)} h`;
    }
    return formatoNumero.format(valor);
}

function desenhar(id, configuracao) {
    const grafico = graficos[id];
    if (grafico) {
        grafico.data = configuracao.data;
        grafico.update();
        return;
    }
    graficos[id] = new Chart(document.getElementById(id), configuracao);
}

function exibirPeriodo(pontos) {
    desenhar('grafico-periodo', {
        type: 'line',
        data: {
            labels: pontos.map(ponto => formatarPeriodo(ponto.periodo)),
            datasets: [
                serieDeLinha('Abertos', pontos.map(ponto => ponto.abertos), AZUL),
                serieDeLinha('Concluídos', pontos.map(ponto => ponto.concluidos), LARANJA)
            ]
        },
        options: {
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            plugins: {
                legend: { position: 'top', align: 'start', labels: { boxWidth: 16, boxHeight: 2 } }
            },
            scales: {
                x: { grid: { display: false } },
                y: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: COR_GRADE }, border: { display: false } }
            }
        }
    });
}

function serieDeLinha(rotulo, valores, cor) {
    return {
        label: rotulo,
        data: valores,
        borderColor: cor,
        backgroundColor: cor,
        borderWidth: 2,
        pointRadius: 0,
        pointHoverRadius: 4
    };
}

function formatarPeriodo(periodo) {
    const [ano, mes, dia] = periodo.split('-');
    return dia ? `${dia}/${mes}` : `${mes}/${ano}`;
}

function exibirStatus(itens) {
    const cores = itens.map(item => CORES_STATUS[item.rotulo] ?? COR_NAO_INFORMADO);
    desenhar('grafico-status', {
        type: 'doughnut',
        data: {
            labels: itens.map(item => item.rotulo),
            datasets: [{
                data: itens.map(item => item.quantidade),
                backgroundColor: cores,
                borderColor: '#ffffff',
                borderWidth: 2
            }]
        },
        options: {
            maintainAspectRatio: false,
            cutout: '60%',
            plugins: { legend: { display: false } }
        }
    });
    exibirLegendaStatus(itens, cores);
}

function exibirLegendaStatus(itens, cores) {
    const total = itens.reduce((soma, item) => soma + item.quantidade, 0);
    legendaStatus.replaceChildren(...itens.map((item, indice) => {
        const cor = document.createElement('span');
        cor.className = 'cor';
        cor.style.backgroundColor = cores[indice];
        const rotulo = document.createElement('span');
        rotulo.textContent = item.rotulo;
        const quantidade = document.createElement('span');
        quantidade.className = 'quantidade';
        quantidade.textContent = `${formatoNumero.format(item.quantidade)} (${percentual(item.quantidade, total)})`;
        const linha = document.createElement('li');
        linha.append(cor, rotulo, quantidade);
        return linha;
    }));
}

function percentual(valor, total) {
    return total === 0 ? '0%' : `${Math.round(valor / total * 100)}%`;
}

function exibirTipos(itens) {
    desenhar('grafico-tipos', {
        type: 'bar',
        data: {
            labels: itens.map(item => item.rotulo),
            datasets: [{
                label: 'Chamados',
                data: itens.map(item => item.quantidade),
                backgroundColor: AZUL,
                borderRadius: 4,
                borderSkipped: 'start',
                maxBarThickness: 28
            }]
        },
        options: {
            indexAxis: 'y',
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                x: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: COR_GRADE }, border: { display: false } },
                y: { grid: { display: false } }
            }
        }
    });
}

function limparFiltros() {
    formulario.reset();
    definirPeriodoPadrao();
    atualizar();
}

formulario.addEventListener('submit', evento => {
    evento.preventDefault();
    atualizar();
});
botaoAtualizar.addEventListener('click', atualizar);
document.getElementById('limpar').addEventListener('click', limparFiltros);

definirPeriodoPadrao();
atualizar();
setInterval(() => {
    if (!document.hidden) {
        atualizar();
    }
}, INTERVALO_ATUALIZACAO_MS);

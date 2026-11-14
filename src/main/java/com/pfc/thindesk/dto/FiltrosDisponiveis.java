package com.pfc.thindesk.dto;

import java.util.List;

public record FiltrosDisponiveis(List<OpcaoFiltro> status, List<String> tipos, List<String> tecnicos) {
}

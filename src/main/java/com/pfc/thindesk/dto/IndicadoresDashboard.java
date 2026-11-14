package com.pfc.thindesk.dto;

public record IndicadoresDashboard(
        long total,
        long abertos,
        long emAndamento,
        long concluidos,
        long cancelados,
        Double tempoMedioResolucaoHoras) {
}

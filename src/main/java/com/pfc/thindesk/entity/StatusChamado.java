package com.pfc.thindesk.entity;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;

public enum StatusChamado {

    ABERTO("Aberto"),
    EM_ANDAMENTO("Em andamento"),
    CONCLUIDO("Concluído"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusChamado(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public static StatusChamado de(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[\\s-]+", "_")
                .toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.name().equals(normalizado))
                .findFirst()
                .orElse(null);
    }
}

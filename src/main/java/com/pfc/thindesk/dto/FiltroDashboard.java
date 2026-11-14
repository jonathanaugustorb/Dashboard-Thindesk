package com.pfc.thindesk.dto;

import com.pfc.thindesk.entity.StatusChamado;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record FiltroDashboard(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
        StatusChamado status,
        String tipo,
        String tecnico) {

    public FiltroDashboard {
        tipo = normalizar(tipo);
        tecnico = normalizar(tecnico);
    }

    public static FiltroDashboard vazio() {
        return new FiltroDashboard(null, null, null, null, null);
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}

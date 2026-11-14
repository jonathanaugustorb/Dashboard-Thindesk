package com.pfc.thindesk.dto;

import java.time.LocalDate;
import java.time.YearMonth;

public enum Agrupamento {

    DIA,
    MES;

    public LocalDate inicioDoPeriodo(LocalDate data) {
        return this == MES ? data.withDayOfMonth(1) : data;
    }

    public LocalDate proximo(LocalDate data) {
        return this == MES ? data.plusMonths(1) : data.plusDays(1);
    }

    public String rotulo(LocalDate data) {
        return this == MES ? YearMonth.from(data).toString() : data.toString();
    }
}

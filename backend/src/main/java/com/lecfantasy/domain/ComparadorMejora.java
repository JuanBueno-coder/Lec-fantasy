package com.lecfantasy.domain;

import java.math.BigDecimal;

public enum ComparadorMejora {
    MENOR("<"), MENOR_IGUAL("≤"), IGUAL("="), MAYOR_IGUAL("≥"), MAYOR(">");

    private final String simbolo;

    ComparadorMejora(String simbolo) {
        this.simbolo = simbolo;
    }

    public String simbolo() {
        return simbolo;
    }

    public boolean compara(BigDecimal valor, BigDecimal umbral) {
        int c = valor.compareTo(umbral);
        return switch (this) {
            case MENOR -> c < 0;
            case MENOR_IGUAL -> c <= 0;
            case IGUAL -> c == 0;
            case MAYOR_IGUAL -> c >= 0;
            case MAYOR -> c > 0;
        };
    }
}

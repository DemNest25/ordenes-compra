package com.diseconstech.ordenes.servicio;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formato de montos de la app: Q 1,234.56 (GTQ, coma de miles, punto decimal). */
public final class Moneda {

    private Moneda() {
    }

    public static String formatear(BigDecimal monto) {
        if (monto == null) return "";
        // DecimalFormat no es thread-safe: una instancia por llamada
        return "Q " + new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(monto);
    }
}

package com.stockwidget.app;

import java.text.DecimalFormat;

public class StockQuoteFormatter {
    private static final DecimalFormat RATE_FORMAT = new DecimalFormat("0.00");

    public static String formatPrice(long value) {
        return String.format("%,d", value);
    }

    public static String formatChange(StockQuote quote) {
        return formatSignedChange(quote) + " (" + formatSignedRate(quote) + ")";
    }

    public static String formatSignedChange(StockQuote quote) {
        String sign = quote.up ? "+" : "-";
        return sign + String.format("%,d", quote.change);
    }

    public static String formatSignedRate(StockQuote quote) {
        String sign = quote.up ? "+" : "-";
        return sign + RATE_FORMAT.format(quote.changeRate) + "%";
    }

    public static String formatOptionalNumber(long value) {
        return value <= 0 ? "-" : String.format("%,d", value);
    }
}

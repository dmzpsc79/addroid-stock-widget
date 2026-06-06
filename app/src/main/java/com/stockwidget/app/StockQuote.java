package com.stockwidget.app;

public class StockQuote {
    public static final String SOURCE_NAVER = "NAVER";
    public static final String SOURCE_YAHOO = "YAHOO";
    public static final String SOURCE_CACHE = "CACHE";

    public final StockItem item;
    public final long price;
    public final long change;
    public final double changeRate;
    public final long high;
    public final long low;
    public final long volume;
    public final boolean up;
    public final String error;
    public final String source;

    public StockQuote(
            StockItem item,
            long price,
            long change,
            double changeRate,
            long high,
            long low,
            long volume,
            boolean up,
            String error,
            String source
    ) {
        this.item = item;
        this.price = price;
        this.change = change;
        this.changeRate = changeRate;
        this.high = high;
        this.low = low;
        this.volume = volume;
        this.up = up;
        this.error = error;
        this.source = source != null ? source : SOURCE_NAVER;
    }

    public boolean hasError() {
        return error != null && !error.isEmpty();
    }

    public boolean isDelayed() {
        return SOURCE_YAHOO.equals(source);
    }
}

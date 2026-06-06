package com.stockwidget.app;

public class StockQuote {
    public static final String SOURCE_NAVER = "NAVER";
    public static final String SOURCE_NXT   = "NXT";
    public static final String SOURCE_YAHOO = "YAHOO";
    public static final String SOURCE_CACHE = "CACHE";

    public final StockItem item;
    public final long price;      // 정규장 종가 (nv)
    public final long change;     // 정규장 등락 절대값 (cv)
    public final double changeRate; // 정규장 등락률 절대값 (cr)
    public final long high;
    public final long low;
    public final long volume;
    public final boolean up;
    public final String error;
    public final String source;
    public final long nxtPrice;   // NXT 시간외 가격 (없으면 0)

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
            String source,
            long nxtPrice
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
        this.nxtPrice = nxtPrice;
    }

    /** nxtPrice 없는 생성자 (Yahoo / cache 호환) */
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
        this(item, price, change, changeRate, high, low, volume, up, error, source, 0L);
    }

    public boolean hasError() {
        return error != null && !error.isEmpty();
    }

    public boolean isDelayed() {
        return SOURCE_YAHOO.equals(source);
    }
}

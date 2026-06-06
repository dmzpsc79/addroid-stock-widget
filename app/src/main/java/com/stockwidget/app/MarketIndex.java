// 코스피·코스닥 지수 데이터를 담는 모델
package com.stockwidget.app;

public class MarketIndex {
    public final String code;       // "KOSPI" / "KOSDAQ"
    public final String name;       // "코스피" / "코스닥"
    public final double price;
    public final double change;
    public final double changeRate;
    public final boolean up;
    public final String chartUrl;

    public MarketIndex(String code, String name, double price, double change,
                       double changeRate, boolean up, String chartUrl) {
        this.code = code;
        this.name = name;
        this.price = price;
        this.change = change;
        this.changeRate = changeRate;
        this.up = up;
        this.chartUrl = chartUrl;
    }
}

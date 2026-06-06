// 코스피·코스닥 지수 데이터를 담는 모델
package com.stockwidget.app;

public class MarketIndex {
    public final String code;
    public final String name;
    public final double price;
    public final double change;
    public final double changeRate;
    public final boolean up;
    public final double[] sparkData; // 최근 20거래일 종가 (최신→오래된 순)

    public MarketIndex(String code, String name, double price, double change,
                       double changeRate, boolean up, double[] sparkData) {
        this.code = code;
        this.name = name;
        this.price = price;
        this.change = change;
        this.changeRate = changeRate;
        this.up = up;
        this.sparkData = sparkData;
    }
}

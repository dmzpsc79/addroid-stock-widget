package com.stockwidget.app;

public class KisCredentials {
    public final String appKey;
    public final String appSecret;
    public final boolean demo;

    public KisCredentials(String appKey, String appSecret, boolean demo) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.demo = demo;
    }

    public boolean isComplete() {
        return !appKey.isEmpty() && !appSecret.isEmpty();
    }
}

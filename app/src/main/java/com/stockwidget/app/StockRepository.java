package com.stockwidget.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StockRepository {
    private static final String PREF_NAME = "stock_widget_settings";
    private static final String KEY_STOCKS = "stocks";
    private static final String KEY_REFRESH_INTERVAL_MINUTES = "refresh_interval_minutes";
    private static final String KEY_QUOTE_CACHE = "quote_cache";
    private static final String KEY_QUOTE_CACHE_TIME = "quote_cache_time";
    private static final long CACHE_MAX_AGE_MS = 8 * 60 * 60 * 1000L; // 8시간

    public static List<StockItem> loadStocks(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_STOCKS, null);
        if (json == null || json.trim().isEmpty()) {
            List<StockItem> defaults = defaultStocks();
            saveStocks(context, defaults);
            return defaults;
        }

        try {
            JSONArray array = new JSONArray(json);
            List<StockItem> stocks = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String code = obj.optString("code", "").trim();
                String name = obj.optString("name", "").trim();
                if (!code.isEmpty() && !name.isEmpty()) {
                    stocks.add(new StockItem(code, name));
                }
            }
            return stocks.isEmpty() ? defaultStocks() : stocks;
        } catch (Exception e) {
            return defaultStocks();
        }
    }

    public static void saveStocks(Context context, List<StockItem> stocks) {
        JSONArray array = new JSONArray();
        for (StockItem stock : stocks) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("code", stock.code);
                obj.put("name", stock.name);
                array.put(obj);
            } catch (Exception ignored) {
            }
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_STOCKS, array.toString())
                .apply();
    }

    public static int loadRefreshIntervalMinutes(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return Math.max(0, prefs.getInt(KEY_REFRESH_INTERVAL_MINUTES, 0));
    }

    public static void saveRefreshIntervalMinutes(Context context, int minutes) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_REFRESH_INTERVAL_MINUTES, Math.max(0, minutes))
                .apply();
    }

    public static void saveQuoteCache(Context context, List<StockQuote> quotes) {
        JSONArray array = new JSONArray();
        for (StockQuote quote : quotes) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("code", quote.item.code);
                obj.put("name", quote.item.name);
                obj.put("price", quote.price);
                obj.put("change", quote.change);
                obj.put("changeRate", quote.changeRate);
                obj.put("high", quote.high);
                obj.put("low", quote.low);
                obj.put("volume", quote.volume);
                obj.put("up", quote.up);
                obj.put("error", quote.error == null ? "" : quote.error);
                obj.put("source", quote.source != null ? quote.source : StockQuote.SOURCE_NAVER);
                array.put(obj);
            } catch (Exception ignored) {
            }
        }

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_QUOTE_CACHE, array.toString())
                .putLong(KEY_QUOTE_CACHE_TIME, System.currentTimeMillis())
                .apply();
    }

    public static long loadQuoteCacheAgeMs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long savedAt = prefs.getLong(KEY_QUOTE_CACHE_TIME, 0);
        return savedAt == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - savedAt;
    }

    public static boolean isQuoteCacheExpired(Context context) {
        return loadQuoteCacheAgeMs(context) > CACHE_MAX_AGE_MS;
    }

    public static List<StockQuote> loadQuoteCache(Context context, List<StockItem> stocks) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_QUOTE_CACHE, null);
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        if (isQuoteCacheExpired(context)) {
            return new ArrayList<>();
        }

        try {
            JSONArray array = new JSONArray(json);
            List<StockQuote> cached = new ArrayList<>();
            for (StockItem stock : stocks) {
                StockQuote quote = findCachedQuote(array, stock);
                if (quote != null) {
                    cached.add(quote);
                }
            }
            return cached;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static StockQuote findCachedQuote(JSONArray array, StockItem stock) {
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null || !stock.code.equals(obj.optString("code", ""))) {
                continue;
            }
            String source = obj.optString("source", "");
            if (!"NAVER".equals(source) && !"KIS".equals(source) && !"YAHOO".equals(source)) {
                continue;
            }

            String error = obj.optString("error", "");
            return new StockQuote(
                    stock,
                    obj.optLong("price", 0),
                    obj.optLong("change", 0),
                    obj.optDouble("changeRate", 0),
                    obj.optLong("high", 0),
                    obj.optLong("low", 0),
                    obj.optLong("volume", 0),
                    obj.optBoolean("up", true),
                    error.isEmpty() ? null : error,
                    source.isEmpty() ? StockQuote.SOURCE_NAVER : source
            );
        }
        return null;
    }

    public static List<StockItem> defaultStocks() {
        List<StockItem> stocks = new ArrayList<>();
        stocks.add(new StockItem("005930", "삼성전자"));
        stocks.add(new StockItem("000660", "SK하이닉스"));
        return stocks;
    }
}

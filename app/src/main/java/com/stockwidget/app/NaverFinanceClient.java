// 네이버 금융 폴링 API로 주가를 조회하는 클라이언트 (API 키 불필요, Yahoo Finance 폴백 포함)
package com.stockwidget.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NaverFinanceClient {

    public static List<StockQuote> fetchQuotes(List<StockItem> items) {
        List<StockQuote> quotes = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return quotes;
        }

        StringBuilder query = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) query.append("|");
            query.append("SERVICE_ITEM:").append(items.get(i).code);
        }

        JSONObject naverResult = null;
        try {
            naverResult = fetchNaverResult(query.toString());
        } catch (Exception ignored) {
        }

        for (StockItem item : items) {
            if (naverResult != null) {
                try {
                    quotes.add(parseNaverQuote(item, naverResult));
                    continue;
                } catch (Exception ignored) {
                }
            }
            quotes.add(fetchYahooQuote(item));
        }
        return quotes;
    }

    public static boolean hasSuccessfulQuote(List<StockQuote> quotes) {
        for (StockQuote quote : quotes) {
            if (!quote.hasError()) return true;
        }
        return false;
    }

    private static JSONObject fetchNaverResult(String query) throws Exception {
        String url = "https://polling.finance.naver.com/api/realtime?query=" + query;
        JSONObject response = readJson(openConnection(url));
        if (!"success".equals(response.optString("resultCode", ""))) {
            throw new Exception("네이버 금융 응답 오류");
        }
        return response.optJSONObject("result");
    }

    private static StockQuote parseNaverQuote(StockItem item, JSONObject result) throws Exception {
        JSONObject data = result.optJSONObject(item.code);
        if (data == null) throw new Exception("종목 데이터 없음: " + item.code);

        long price = parseLong(data.optString("closePrice", "0"));
        long change = Math.abs(parseLong(data.optString("compareToPreviousClosePrice", "0")));
        double changeRate = Math.abs(parseDouble(data.optString("fluctuationsRatio", "0")));
        long high = parseLong(data.optString("highPrice", "0"));
        long low = parseLong(data.optString("lowPrice", "0"));
        long volume = parseLong(data.optString("accumulatedTradingVolume", "0"));

        boolean up = true;
        JSONObject priceInfo = data.optJSONObject("compareToPreviousPrice");
        if (priceInfo != null) {
            String code = priceInfo.optString("code", "2");
            up = "1".equals(code) || "2".equals(code);
        }

        return new StockQuote(item, price, change, changeRate, high, low, volume, up, null, StockQuote.SOURCE_NAVER);
    }

    private static StockQuote fetchYahooQuote(StockItem item) {
        try {
            String symbol = item.code.contains(".") ? item.code : item.code + ".KS";
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol
                    + "?interval=1d&range=2d";
            JSONObject response = readJson(openConnection(url));

            JSONObject chart = response.optJSONObject("chart");
            JSONArray resultArray = chart != null ? chart.optJSONArray("result") : null;
            JSONObject result = resultArray != null ? resultArray.optJSONObject(0) : null;
            JSONObject meta = result != null ? result.optJSONObject("meta") : null;
            if (meta == null) throw new Exception("Yahoo 응답 파싱 실패");

            long price = (long) meta.optDouble("regularMarketPrice", 0);
            long prevClose = (long) meta.optDouble("chartPreviousClose", 0);
            long change = Math.abs(price - prevClose);
            double changeRate = prevClose > 0 ? (double) change / prevClose * 100.0 : 0;
            boolean up = price >= prevClose;
            long high = (long) meta.optDouble("regularMarketDayHigh", 0);
            long low = (long) meta.optDouble("regularMarketDayLow", 0);
            long volume = meta.optLong("regularMarketVolume", 0);

            return new StockQuote(item, price, change, changeRate, high, low, volume, up, null, StockQuote.SOURCE_YAHOO);
        } catch (Exception e) {
            String msg = e.getMessage();
            return new StockQuote(item, 0, 0, 0, 0, 0, 0, true,
                    msg != null ? msg : "주가 조회에 실패했습니다.", null);
        }
    }

    private static HttpURLConnection openConnection(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        return conn;
    }

    private static JSONObject readJson(HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(stream);
        conn.disconnect();
        if (status < 200 || status >= 300) throw new Exception("HTTP " + status);
        return new JSONObject(body);
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static long parseLong(String value) {
        try {
            return Math.round(Double.parseDouble(value.replace(",", "").trim()));
        } catch (Exception e) {
            return 0;
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }
}

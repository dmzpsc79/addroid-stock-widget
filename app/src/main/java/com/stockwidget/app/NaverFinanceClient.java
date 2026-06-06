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

        // 쿼리 형식: SERVICE_ITEM:005930,000660,... (단일 prefix + 쉼표 구분)
        StringBuilder codes = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) codes.append(",");
            codes.append(items.get(i).code);
        }

        JSONArray datasArray = null;
        try {
            datasArray = fetchNaverDatas(codes.toString());
        } catch (Exception ignored) {
        }

        for (StockItem item : items) {
            if (datasArray != null) {
                try {
                    quotes.add(parseNaverQuote(item, datasArray));
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

    // result.areas[].datas[] 배열을 모아 반환
    private static JSONArray fetchNaverDatas(String codes) throws Exception {
        String url = "https://polling.finance.naver.com/api/realtime?query=SERVICE_ITEM:" + codes;
        JSONObject response = readJson(openConnection(url));
        if (!"success".equals(response.optString("resultCode", ""))) {
            throw new Exception("네이버 금융 응답 오류");
        }
        JSONObject result = response.optJSONObject("result");
        if (result == null) throw new Exception("result 없음");

        // areas 배열에서 datas를 모두 합친다
        JSONArray areas = result.optJSONArray("areas");
        if (areas == null) throw new Exception("areas 없음");

        JSONArray all = new JSONArray();
        for (int i = 0; i < areas.length(); i++) {
            JSONObject area = areas.optJSONObject(i);
            if (area == null) continue;
            JSONArray datas = area.optJSONArray("datas");
            if (datas == null) continue;
            for (int j = 0; j < datas.length(); j++) {
                JSONObject d = datas.optJSONObject(j);
                if (d != null) all.put(d);
            }
        }
        return all;
    }

    private static StockQuote parseNaverQuote(StockItem item, JSONArray datas) throws Exception {
        JSONObject data = null;
        for (int i = 0; i < datas.length(); i++) {
            JSONObject d = datas.optJSONObject(i);
            if (d != null && item.code.equals(d.optString("cd", ""))) {
                data = d;
                break;
            }
        }
        if (data == null) throw new Exception("종목 데이터 없음: " + item.code);

        // 실제 필드명: nv=현재가, cv=전일비, cr=등락률, hv=고가, lv=저가, aq=거래량
        long price = data.optLong("nv", 0);
        long change = Math.abs(data.optLong("cv", 0));
        double changeRate = Math.abs(data.optDouble("cr", 0));
        long high = data.optLong("hv", 0);
        long low = data.optLong("lv", 0);
        long volume = data.optLong("aq", 0);

        // rf: "1"=상한가, "2"=상승, "3"=보합, "4"=하한가, "5"=하락
        String rf = data.optString("rf", "3");
        boolean up = !"4".equals(rf) && !"5".equals(rf);

        // NXT 시간외 가격 조회 (정규장 종가와 다를 때만 저장)
        long nxtPrice = 0;
        String ms = data.optString("ms", "CLOSE");
        JSONObject nxt = data.optJSONObject("nxtOverMarketPriceInfo");
        if (nxt != null && !"OPEN".equals(ms)) {
            long np = parseLong(nxt.optString("overPrice", "0").replace(",", ""));
            if (np > 0 && np != price) {
                nxtPrice = np;
            }
        }

        String source = nxtPrice > 0 ? StockQuote.SOURCE_NXT : StockQuote.SOURCE_NAVER;
        return new StockQuote(item, price, change, changeRate, high, low, volume, up, null, source, nxtPrice);
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

// 네이버 모바일 API로 코스피·코스닥 지수를 조회하는 클라이언트
package com.stockwidget.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NaverIndexClient {

    private static final String[] INDEX_CODES = {"KOSPI", "KOSDAQ"};
    private static final String[] INDEX_NAMES = {"코스피", "코스닥"};

    public static List<MarketIndex> fetchIndices() {
        List<MarketIndex> result = new ArrayList<>();
        for (int i = 0; i < INDEX_CODES.length; i++) {
            try {
                MarketIndex idx = fetchOne(INDEX_CODES[i], INDEX_NAMES[i]);
                if (idx != null) result.add(idx);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static Bitmap loadChart(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(8_000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Referer", "https://m.stock.naver.com");
            int status = conn.getResponseCode();
            if (status != 200) return null;
            try (InputStream is = conn.getInputStream()) {
                return BitmapFactory.decodeStream(is);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static MarketIndex fetchOne(String code, String name) throws Exception {
        String url = "https://m.stock.naver.com/api/index/" + code + "/basic";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Referer", "https://m.stock.naver.com");

        int status = conn.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(stream);
        conn.disconnect();
        if (status < 200 || status >= 300) return null;

        JSONObject j = new JSONObject(body);
        double price = parseDouble(j.optString("closePrice", "0"));
        double change = Math.abs(parseDouble(j.optString("compareToPreviousClosePrice", "0")));
        double changeRate = Math.abs(parseDouble(j.optString("fluctuationsRatio", "0")));

        JSONObject dir = j.optJSONObject("compareToPreviousPrice");
        String dirCode = dir != null ? dir.optString("code", "3") : "3";
        boolean up = !"4".equals(dirCode) && !"5".equals(dirCode);

        // 미니 차트 이미지 URL (당일 차트)
        JSONObject imageCharts = j.optJSONObject("imageCharts");
        String chartUrl = imageCharts != null ? imageCharts.optString("day_up", "") : "";

        return new MarketIndex(code, name, price, change, changeRate, up, chartUrl);
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

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }
}

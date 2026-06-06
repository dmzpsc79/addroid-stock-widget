package com.stockwidget.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class KisFinanceClient {
    private static final String REAL_BASE_URL = "https://openapi.koreainvestment.com:9443";
    private static final String DEMO_BASE_URL = "https://openapivts.koreainvestment.com:29443";
    private static final String TOKEN_PATH = "/oauth2/tokenP";
    private static final String PRICE_PATH = "/uapi/domestic-stock/v1/quotations/inquire-price";
    private static final String PRICE_TR_ID = "FHKST01010100";
    private static final String NO_CREDENTIALS =
            "설정에서 한투 App Key와 App Secret을 입력하세요.";
    private static final Object TOKEN_LOCK = new Object();

    public static List<StockQuote> fetchQuotes(Context context, List<StockItem> items) {
        List<StockQuote> quotes = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return quotes;
        }

        KisCredentials credentials = KisCredentialsStore.loadCredentials(context);
        if (!credentials.isComplete()) {
            for (StockItem item : items) {
                quotes.add(errorQuote(item, NO_CREDENTIALS));
            }
            return quotes;
        }

        String token;
        try {
            token = getAccessToken(context, credentials);
        } catch (Exception e) {
            String message = readableError(e, "한투 인증에 실패했습니다.");
            for (StockItem item : items) {
                quotes.add(errorQuote(item, message));
            }
            return quotes;
        }

        for (int i = 0; i < items.size(); i++) {
            StockItem item = items.get(i);
            try {
                quotes.add(fetchQuote(context, credentials, token, item, true));
            } catch (Exception e) {
                quotes.add(errorQuote(item, readableError(e, "한투 시세 조회에 실패했습니다.")));
            }
            if (i < items.size() - 1) {
                sleep(credentials.demo ? 500L : 60L);
            }
        }
        return quotes;
    }

    public static StockQuote testConnection(Context context, StockItem item) {
        KisCredentials credentials = KisCredentialsStore.loadCredentials(context);
        if (!credentials.isComplete()) {
            return errorQuote(item, NO_CREDENTIALS);
        }
        try {
            String token = getAccessToken(context, credentials);
            return fetchQuote(context, credentials, token, item, true);
        } catch (Exception e) {
            return errorQuote(item, readableError(e, "한투 연결 테스트에 실패했습니다."));
        }
    }

    public static boolean hasSuccessfulQuote(List<StockQuote> quotes) {
        for (StockQuote quote : quotes) {
            if (!quote.hasError()) {
                return true;
            }
        }
        return false;
    }

    private static StockQuote fetchQuote(
            Context context,
            KisCredentials credentials,
            String token,
            StockItem item,
            boolean retryAuthentication
    ) throws Exception {
        try {
            JSONObject output = requestPrice(credentials, token, item.code);
            long signedChange = parseLong(output.optString("prdy_vrss", "0"));
            String changeSign = output.optString("prdy_vrss_sign", "");
            boolean up = signedChange >= 0;
            if ("4".equals(changeSign) || "5".equals(changeSign)) {
                up = false;
            } else if ("1".equals(changeSign) || "2".equals(changeSign)) {
                up = true;
            }

            return new StockQuote(
                    item,
                    Math.abs(parseLong(output.optString("stck_prpr", "0"))),
                    Math.abs(signedChange),
                    Math.abs(parseDouble(output.optString("prdy_ctrt", "0"))),
                    Math.abs(parseLong(output.optString("stck_hgpr", "0"))),
                    Math.abs(parseLong(output.optString("stck_lwpr", "0"))),
                    Math.abs(parseLong(output.optString("acml_vol", "0"))),
                    up,
                    null
            );
        } catch (HttpStatusException e) {
            if (retryAuthentication && e.statusCode == 401) {
                KisCredentialsStore.clearToken(context);
                String newToken = getAccessToken(context, credentials);
                return fetchQuote(context, credentials, newToken, item, false);
            }
            throw e;
        }
    }

    private static JSONObject requestPrice(
            KisCredentials credentials,
            String token,
            String stockCode
    ) throws Exception {
        String query = "?FID_COND_MRKT_DIV_CODE=J&FID_INPUT_ISCD="
                + URLEncoder.encode(stockCode, "UTF-8");
        HttpURLConnection connection = openConnection(
                baseUrl(credentials) + PRICE_PATH + query,
                "GET"
        );
        connection.setRequestProperty("authorization", "Bearer " + token);
        connection.setRequestProperty("appkey", credentials.appKey);
        connection.setRequestProperty("appsecret", credentials.appSecret);
        connection.setRequestProperty("tr_id", PRICE_TR_ID);
        connection.setRequestProperty("custtype", "P");

        JSONObject response = readJsonResponse(connection);
        if (!"0".equals(response.optString("rt_cd", ""))) {
            throw new IllegalStateException(response.optString("msg1", "한투 API 오류"));
        }
        JSONObject output = response.optJSONObject("output");
        if (output == null) {
            throw new IllegalStateException("한투 시세 응답에 현재가 정보가 없습니다.");
        }
        return output;
    }

    private static String getAccessToken(
            Context context,
            KisCredentials credentials
    ) throws Exception {
        synchronized (TOKEN_LOCK) {
            String cachedToken = KisCredentialsStore.loadValidToken(context);
            if (!cachedToken.isEmpty()) {
                return cachedToken;
            }

            HttpURLConnection connection = openConnection(
                    baseUrl(credentials) + TOKEN_PATH,
                    "POST"
            );
            connection.setDoOutput(true);
            JSONObject request = new JSONObject();
            request.put("grant_type", "client_credentials");
            request.put("appkey", credentials.appKey);
            request.put("appsecret", credentials.appSecret);
            byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }

            JSONObject response = readJsonResponse(connection);
            String token = response.optString("access_token", "");
            if (token.isEmpty()) {
                throw new IllegalStateException(response.optString("error_description", "접근 토큰이 없습니다."));
            }
            long expiresAt = parseExpiryMillis(response);
            KisCredentialsStore.saveToken(context, token, expiresAt);
            return token;
        }
    }

    private static HttpURLConnection openConnection(String url, String method) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "StockWidget-Android/1.0");
        return connection;
    }

    private static JSONObject readJsonResponse(HttpURLConnection connection) throws Exception {
        int statusCode = connection.getResponseCode();
        InputStream stream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readAll(stream);
        connection.disconnect();
        if (statusCode < 200 || statusCode >= 300) {
            String message = "HTTP " + statusCode;
            try {
                JSONObject error = new JSONObject(body);
                message = error.optString("msg1",
                        error.optString("error_description", message));
            } catch (Exception ignored) {
            }
            throw new HttpStatusException(statusCode, message);
        }
        return new JSONObject(body);
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private static long parseExpiryMillis(JSONObject response) {
        String expiryText = response.optString("access_token_token_expired", "");
        if (!expiryText.isEmpty()) {
            try {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA);
                format.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
                Date expiry = format.parse(expiryText);
                if (expiry != null) {
                    return expiry.getTime();
                }
            } catch (Exception ignored) {
            }
        }
        long expiresInSeconds = response.optLong("expires_in", 86_400L);
        return System.currentTimeMillis() + Math.max(600L, expiresInSeconds) * 1000L;
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

    private static String baseUrl(KisCredentials credentials) {
        return credentials.demo ? DEMO_BASE_URL : REAL_BASE_URL;
    }

    private static StockQuote errorQuote(StockItem item, String message) {
        return new StockQuote(item, 0, 0, 0, 0, 0, 0, true, message);
    }

    private static String readableError(Exception error, String fallback) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class HttpStatusException extends Exception {
        final int statusCode;

        HttpStatusException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}

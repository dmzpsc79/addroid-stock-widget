package com.stockwidget.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class KisCredentialsStore {
    private static final String PREF_NAME = "kis_secure_settings";
    private static final String KEY_ALIAS = "stock_widget_kis_key";
    private static final String KEY_APP_KEY = "app_key";
    private static final String KEY_APP_SECRET = "app_secret";
    private static final String KEY_DEMO = "demo";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_TOKEN_EXPIRES_AT = "token_expires_at";
    private static final long TOKEN_EXPIRY_MARGIN_MILLIS = 5 * 60_000L;

    public static void saveCredentials(
            Context context,
            String appKey,
            String appSecret,
            boolean demo
    ) throws Exception {
        String normalizedAppKey = appKey.trim();
        String normalizedAppSecret = appSecret.trim();
        KisCredentials current = loadCredentials(context);
        boolean changed = !current.appKey.equals(normalizedAppKey)
                || !current.appSecret.equals(normalizedAppSecret)
                || current.demo != demo;

        SharedPreferences.Editor editor = preferences(context).edit()
                .putString(KEY_APP_KEY, encrypt(normalizedAppKey))
                .putString(KEY_APP_SECRET, encrypt(normalizedAppSecret))
                .putBoolean(KEY_DEMO, demo);
        if (changed) {
            editor.remove(KEY_TOKEN).remove(KEY_TOKEN_EXPIRES_AT);
        }
        editor.apply();
    }

    public static KisCredentials loadCredentials(Context context) {
        SharedPreferences prefs = preferences(context);
        try {
            String appKey = decrypt(prefs.getString(KEY_APP_KEY, ""));
            String appSecret = decrypt(prefs.getString(KEY_APP_SECRET, ""));
            return new KisCredentials(appKey, appSecret, prefs.getBoolean(KEY_DEMO, false));
        } catch (Exception e) {
            return new KisCredentials("", "", prefs.getBoolean(KEY_DEMO, false));
        }
    }

    public static void saveToken(Context context, String token, long expiresAtMillis) throws Exception {
        preferences(context).edit()
                .putString(KEY_TOKEN, encrypt(token))
                .putLong(KEY_TOKEN_EXPIRES_AT, expiresAtMillis)
                .apply();
    }

    public static String loadValidToken(Context context) {
        SharedPreferences prefs = preferences(context);
        long expiresAt = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0);
        if (expiresAt <= System.currentTimeMillis() + TOKEN_EXPIRY_MARGIN_MILLIS) {
            return "";
        }
        try {
            return decrypt(prefs.getString(KEY_TOKEN, ""));
        } catch (Exception e) {
            return "";
        }
    }

    public static void clearToken(Context context) {
        preferences(context).edit()
                .remove(KEY_TOKEN)
                .remove(KEY_TOKEN_EXPIRES_AT)
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static String encrypt(String plainText) throws Exception {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                + ":"
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private static String decrypt(String storedValue) throws Exception {
        if (storedValue == null || storedValue.isEmpty()) {
            return "";
        }
        String[] parts = storedValue.split(":", 2);
        if (parts.length != 2) {
            return "";
        }
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );
        keyGenerator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return keyGenerator.generateKey();
    }
}

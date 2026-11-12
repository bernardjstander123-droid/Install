package com.astra.sevenone;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Color;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpsURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    private static final String KEY_ALIAS = "astra71_openai_key_v1";
    private static final String PREFS = "astra71_secrets";
    private static final String API_URL = "https://api.openai.com/v1/responses";
    private WebView web;
    private final ExecutorService background = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7, 11, 22));
        getWindow().setNavigationBarColor(Color.rgb(7, 11, 22));
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(7, 11, 22));
        WebSettings opts = web.getSettings();
        opts.setJavaScriptEnabled(true);
        opts.setDomStorageEnabled(true);
        opts.setAllowFileAccess(true);
        opts.setAllowContentAccess(false);
        opts.setAllowFileAccessFromFileURLs(false);
        opts.setAllowUniversalAccessFromFileURLs(false);
        opts.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true; // No external navigation or untrusted scripts
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }
        });
        if (Build.VERSION.SDK_INT >= 30) {
            web.setOnApplyWindowInsetsListener((v, ins) -> {
                Insets edges = ins.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(edges.left, edges.top, edges.right, edges.bottom);
                return ins;
            });
        }
        web.addJavascriptInterface(new NativeBridge(), "AstraNative");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private SecretKey getSecretKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey existing = (SecretKey) store.getKey(KEY_ALIAS, null);
        if (existing != null) return existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
          .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    private void storeApiKey(String value) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, getSecretKey());
        byte[] blob = c.doFinal(value.getBytes(StandardCharsets.UTF_8));
        SharedPreferences.Editor edit = getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        edit.putString("secret", Base64.encodeToString(blob, Base64.NO_WRAP));
        edit.putString("iv", Base64.encodeToString(c.getIV(), Base64.NO_WRAP));
        if (!edit.commit()) throw new Exception("Could not save the API key");
    }

    private String readApiKey() throws Exception {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String b64 = prefs.getString("secret", "");
        String iv = prefs.getString("iv", "");
        if (b64.isEmpty() || iv.isEmpty()) return "";
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, getSecretKey(),
            new GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT)));
        return new String(c.doFinal(Base64.decode(b64, Base64.DEFAULT)), StandardCharsets.UTF_8);
    }

    private void reply(String id, String content, String error) {
        runOnUiThread(() -> {
            if (web == null) return;
            String js = "window.astraReceive(" + JSONObject.quote(id) + ","
                      + JSONObject.quote(content == null ? "" : content) + ","
                      + JSONObject.quote(error == null ? "" : error) + ");";
            web.evaluateJavascript(js, null);
        });
    }

    public final class NativeBridge {
        @JavascriptInterface public boolean hasKey() {
            try { return !readApiKey().isEmpty(); }
            catch (Exception ignored) { return false; }
        }
        @JavascriptInterface public boolean saveKey(String value) {
            try {
                if (value == null || value.length() < 10 || value.length() > 512) return false;
                storeApiKey(value.trim());
                return true;
            } catch (Exception ignored) { return false; }
        }
        @JavascriptInterface public void clearKey() {
            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        }
        @JavascriptInterface public void send(String raw) {
            background.execute(() -> handleMessage(raw));
        }
    }

    private void handleMessage(String raw) {
        String requestId = "";
        try {
            JSONObject cmd = new JSONObject(raw);
            requestId = cmd.optString("id", "");
            if (requestId.length() > 60) throw new Exception("Invalid request ID");
            String model = cmd.optString("model", "gpt-6");
            if (!model.matches("[A-Za-z0-9._:/-]{1,90}")) throw new Exception("Invalid model ID");
            JSONArray msgs = cmd.getJSONArray("messages");
            if (msgs.length() < 1 || msgs.length() > 35) throw new Exception("Too many messages in conversation");
            JSONArray input = new JSONArray();
            for (int i = 0; i < msgs.length(); i++) {
                JSONObject m = msgs.getJSONObject(i);
                String role = m.optString("role", "");
                String content = m.optString("content", "");
                if (!role.equals("user") && !role.equals("assistant")) throw new Exception("Invalid role");
                if (content.length() < 1 || content.length() > 8000) throw new Exception("Message too long");
                input.put(new JSONObject().put("role", role).put("content", content));
            }
            String apiKey = readApiKey();
            if (apiKey.isEmpty()) throw new Exception("Add your OpenAI API key in Settings to activate AI replies.");
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("instructions", "You are Astra 7.1, a creative and practical personal AI assistant. Do not pretend to have run tools or verified claims. Be helpful, grounded and concise.");
            body.put("input", input);
            body.put("max_output_tokens", 1800);

            HttpsURLConnection con = (HttpsURLConnection) new URL(API_URL).openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(15000);
            con.setReadTimeout(90000);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            byte[] json = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = con.getOutputStream()) { out.write(json); }
            int status = con.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? con.getInputStream() : con.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (stream != null) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null && sb.length() < 2000000) sb.append(line);
                }
            }
            con.disconnect();
            if (status < 200 || status >= 300) {
                String msg = status == 401 ? "Invalid API key." :
                    status == 429 ? "API rate or billing limit reached." :
                    (status == 400 || status == 404) ? "Model not available. Change your model ID in Settings." :
                    "AI provider error (" + status + ").";
                throw new Exception(msg);
            }
            JSONObject data = new JSONObject(sb.toString());
            String result = data.optString("output_text", "");
            if (result.isEmpty()) {
                JSONArray outputs = data.optJSONArray("output");
                StringBuilder combined = new StringBuilder();
                if (outputs != null) for (int i = 0; i < outputs.length(); i++) {
                    JSONArray parts = outputs.getJSONObject(i).optJSONArray("content");
                    if (parts == null) continue;
                    for (int j = 0; j < parts.length(); j++) {
                        JSONObject p = parts.getJSONObject(j);
                        if (p.optString("type", "").equals("output_text")) combined.append(p.optString("text", "")).append("\n");
                    }
                }
                result = combined.toString().trim();
            }
            if (result.isEmpty()) throw new Exception("The model returned no text.");
            reply(requestId, result, "");
        } catch (Exception e) {
            reply(requestId, "", e.getMessage() == null ? "Unable to complete AI request." : e.getMessage());
        }
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        background.shutdownNow();
        if (web != null) {
            web.removeJavascriptInterface("AstraNative");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}

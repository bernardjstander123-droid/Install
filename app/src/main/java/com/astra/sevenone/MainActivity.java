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
import android.widget.FrameLayout;
import android.net.Uri;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;
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
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity implements SensorEventListener {
    private static final String KEY_ALIAS = "astra71_openai_key_v1";
    private static final String PREFS = "astra71_secrets";
    private static final String API_URL = "https://api.openai.com/v1/responses";
    private WebView web;
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private SensorManager sensors;
    private Sensor accelerometer;
    private boolean motionEnabled = true;
    private long lastSensorMs = 0L;
    private float filteredX = 0f, filteredY = 0f, filteredZ = 9.8f;
    private TextToSpeech speaker;
    private volatile boolean speakerReady = false;

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
        // Android 15+ draws edge-to-edge even for older layouts. Apply the system
        // bar insets on the parent so the WebView is laid out below status/navigation.
        FrameLayout root = new FrameLayout(this);
        root.addView(web, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, ins) -> {
                Insets edges = ins.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(edges.left, edges.top, edges.right, edges.bottom);
                return ins;
            });
        }
        web.addJavascriptInterface(new NativeBridge(), "AstraNative");
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) root.requestApplyInsets();
        // No camera or microphone permission: reacts only to built-in acceleration.
        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensors != null) accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        motionEnabled = getSharedPreferences("astra71_motion", MODE_PRIVATE).getBoolean("enabled", true);
        setUpSpeech();
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
        @JavascriptInterface public void checkModel(String model) {
            background.execute(() -> checkModelAccess(model));
        }
        @JavascriptInterface public void setMotionEnabled(boolean enabled) {
            runOnUiThread(() -> {
                motionEnabled = enabled;
                getSharedPreferences("astra71_motion", MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply();
                updateMotionRegistration();
            });
        }
        @JavascriptInterface public void speak(String text) {
            if (text == null || text.isEmpty()) return;
            final String safeText = text.length() > 3000 ? text.substring(0,3000) : text;
            runOnUiThread(() -> {
                if (speakerReady && speaker != null) {
                    speaker.speak(safeText, TextToSpeech.QUEUE_FLUSH, null,
                        "astra71_" + SystemClock.uptimeMillis());
                }
            });
        }
        @JavascriptInterface public void stopSpeaking() {
            runOnUiThread(() -> {
                if (speaker != null) speaker.stop();
                reportSpeaking(false);
            });
        }
    }

    private void reportSpeaking(boolean on) {
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(
                "window.astraSpeaking(" + (on ? "true" : "false") + ");", null);
        });
    }

    private void setUpSpeech() {
        speaker = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                if (speaker != null) {
                    int language = speaker.setLanguage(Locale.ENGLISH);
                    speakerReady = language != TextToSpeech.LANG_MISSING_DATA
                        && language != TextToSpeech.LANG_NOT_SUPPORTED;
                }
            }
        });
        speaker.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { reportSpeaking(true); }
            @Override public void onDone(String utteranceId) { reportSpeaking(false); }
            @Override public void onError(String utteranceId) { reportSpeaking(false); }
        });
    }

    private void updateMotionRegistration() {
        if (sensors == null || accelerometer == null) return;
        sensors.unregisterListener(this);
        if (motionEnabled) sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override protected void onResume() {
        super.onResume();
        updateMotionRegistration();
        if (web != null) web.onResume();
    }

    @Override protected void onPause() {
        if (sensors != null) sensors.unregisterListener(this);
        if (web != null) web.onPause();
        if (speaker != null) speaker.stop();
        super.onPause();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER || !motionEnabled || web == null)
            return;
        filteredX = .75f*filteredX + .25f*event.values[0];
        filteredY = .75f*filteredY + .25f*event.values[1];
        filteredZ = .75f*filteredZ + .25f*event.values[2];
        long now = SystemClock.elapsedRealtime();
        if (now - lastSensorMs < 100) return;
        lastSensorMs = now;
        String js = String.format(Locale.US, "window.astraMotion(%.3f,%.3f,%.3f);",
            filteredX, filteredY, filteredZ);
        web.evaluateJavascript(js, null);
    }

    private void reportModelStatus(String message) {
        runOnUiThread(() -> {
            if (web != null) {
                web.evaluateJavascript(
                    "window.astraModelStatus(" + JSONObject.quote(message) + ");", null);
            }
        });
    }

    private void checkModelAccess(String model) {
        if (model == null || !model.matches("[A-Za-z0-9._:/-]{1,90}")) {
            reportModelStatus("Enter a valid model ID.");
            return;
        }
        HttpsURLConnection connection = null;
        try {
            String key = readApiKey();
            if (key.isEmpty()) {
                reportModelStatus("Save an API key first.");
                return;
            }
            URL url = new URL("https://api.openai.com/v1/models/" + Uri.encode(model));
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Authorization", "Bearer " + key);
            int status = connection.getResponseCode();
            if (status == 200) {
                reportModelStatus("Model is listed for this key. Save settings and try a message.");
            } else if (status == 401) {
                reportModelStatus("API key was rejected. Generate or select a valid project key.");
            } else if (status == 403 || status == 404) {
                reportModelStatus("This key cannot access " + model + ". Choose another model.");
            } else if (status == 429) {
                reportModelStatus("API limit reached. Check Platform billing or try later.");
            } else {
                reportModelStatus("Model check failed (HTTP " + status + ").");
            }
        } catch (Exception ignored) {
            reportModelStatus("Could not reach OpenAI. Check your connection.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void handleMessage(String raw) {
        String requestId = "";
        try {
            JSONObject cmd = new JSONObject(raw);
            requestId = cmd.optString("id", "");
            if (requestId.length() > 60) throw new Exception("Invalid request ID");
            String model = cmd.optString("model", "gpt-6-luna");
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
                String providerCode = "";
                try {
                    JSONObject providerError = new JSONObject(sb.toString()).optJSONObject("error");
                    if (providerError != null) providerCode = providerError.optString("code", "");
                } catch (Exception ignored) { }
                String msg = status == 401 ? "API key rejected. Check your OpenAI Platform key." :
                    status == 429 ? "API rate or billing limit reached. Check Platform billing." :
                    (status == 404 || "model_not_found".equals(providerCode)) ?
                        "Model " + model + " is not available for this API key. Try gpt-6-luna in Settings." :
                    status == 400 ? "The API rejected this request (400). Check model access and parameters." :
                    status == 403 ? "Your project does not have permission to use this model." :
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
        if (sensors != null) sensors.unregisterListener(this);
        if (speaker != null) { speaker.stop(); speaker.shutdown(); speaker = null; }
        background.shutdownNow();
        if (web != null) {
            web.removeJavascriptInterface("AstraNative");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}

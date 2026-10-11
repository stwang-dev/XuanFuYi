package com.lingo.bubble;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Translator {

    public static final int MAX_CHARS = 4000;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onPartial(String textSoFar);

        void onResult(String text, String error);
    }

    public static void translateAsync(final Context ctx, final String raw, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        final String text = normalize(raw);
        if (text.length() == 0) {
            cb.onResult(null, "没有读到文字");
            return;
        }
        POOL.execute(new Runnable() {
            public void run() {
                String out = null;
                String err = null;
                try {
                    out = translateBlocking(app, text, cb);
                } catch (Throwable t) {
                    err = shortMsg(t);
                }
                final String r = out;
                final String e = err;
                MAIN.post(new Runnable() {
                    public void run() { cb.onResult(r, e); }
                });
            }
        });
    }

    public static String translateBlocking(Context ctx, String text) throws Exception {
        return translateBlocking(ctx, text, null);
    }

    public static String translateBlocking(Context ctx, String text, Callback cb) throws Exception {
        Prefs p = new Prefs(ctx);
        String key = p.apiKey() == null ? "" : p.apiKey().trim();
        if (Prefs.ENGINE_OPENAI.equals(p.engine()) && key.length() > 0) {
            return openAiTranslate(p, text, key, cb);
        }
        return freeTranslate(p, text);
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\r\n", "\n").replace('\r', '\n');
        s = s.replaceAll("-\n", "");
        s = s.replaceAll("[ \t]*\n+[ \t]*", " ");
        s = s.replaceAll("[ \t]{2,}", " ");
        s = s.trim();
        if (s.length() > MAX_CHARS) s = s.substring(0, MAX_CHARS);
        return s;
    }

    public static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
            if (c >= 0x3040 && c <= 0x30FF) return true;
            if (c >= 0xAC00 && c <= 0xD7AF) return true;
        }
        return false;
    }

    public static String targetCode(String pref, String source) {
        boolean cn = hasCjk(source);
        if (pref == null || "auto".equals(pref)) return cn ? "en" : "zh";
        if ("zh".equals(pref)) return cn ? "en" : "zh";
        if ("en".equals(pref)) return cn ? "en" : "zh";
        return pref;
    }

    public static String langName(String code) {
        if ("zh".equals(code)) return "中文";
        if ("en".equals(code)) return "英文";
        if ("ja".equals(code)) return "日文";
        if ("ko".equals(code)) return "韩文";
        if ("de".equals(code)) return "德文";
        if ("fr".equals(code)) return "法文";
        if ("ru".equals(code)) return "俄文";
        if ("es".equals(code)) return "西班牙文";
        return "中文";
    }

    private static String freeTranslate(Prefs p, String text) throws Exception {
        String tgt = targetCode(p.targetLang(), text);
        String sl = hasCjk(text) ? "zh-CN" : "en";
        String tl = "zh".equals(tgt) ? "zh-CN" : tgt;
        StringBuilder errors = new StringBuilder();

        try {
            String url = "https://api.mymemory.translated.net/get?q=" + enc(text) + "&langpair=" + enc(sl + "|" + tl);
            JSONObject j = new JSONObject(httpGet(url));
            JSONObject rd = j.optJSONObject("responseData");
            if (rd != null) {
                String t = rd.optString("translatedText", "");
                String up = t.toUpperCase();
                if (t.length() > 0 && up.indexOf("MYMEMORY WARNING") < 0 && up.indexOf("QUERY LENGTH LIMIT") < 0) {
                    return unescape(t);
                }
            }
            errors.append("免费词典接口没有返回结果；");
        } catch (Exception e) {
            errors.append("免费词典接口失败(").append(shortMsg(e)).append(")；");
        }

        if (p.allowGoogle()) {
            try {
                String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl="
                        + enc(tl) + "&dt=t&q=" + enc(text);
                JSONArray arr = new JSONArray(httpGet(url));
                JSONArray segs = arr.getJSONArray(0);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < segs.length(); i++) {
                    JSONArray s = segs.optJSONArray(i);
                    if (s != null) sb.append(s.optString(0, ""));
                }
                if (sb.length() > 0) return sb.toString();
                errors.append("Google 备用接口没有返回结果；");
            } catch (Exception e) {
                errors.append("Google 备用接口失败(").append(shortMsg(e)).append(")；");
            }
        }

        throw new Exception(errors.length() == 0 ? "翻译失败" : errors.toString());
    }

    private static String openAiTranslate(Prefs p, String text, String key, Callback cb) throws Exception {
        String base = p.baseUrl() == null ? "" : p.baseUrl().trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.length() == 0) throw new Exception("没有填写接口地址");
        String url = base + "/chat/completions";
        if (!url.startsWith("http")) throw new Exception("接口地址要以 http 开头");

        String model = p.model() == null ? "" : p.model().trim();
        if (model.length() == 0) throw new Exception("没有填写模型名");

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("stream", true);
        body.put("max_tokens", maxTokens(text));
        boolean fast = addFastParams(body, model);

        JSONArray msgs = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", sysPrompt(targetCode(p.targetLang(), text)));
        JSONObject usr = new JSONObject();
        usr.put("role", "user");
        usr.put("content", text);
        msgs.put(sys);
        msgs.put(usr);
        body.put("messages", msgs);

        String payload = body.toString();
        String out;
        try {
            out = streamTranslate(url, payload, key, cb);
        } catch (HttpError he) {
            if (!fast || (he.code != 400 && he.code != 422)) throw new Exception(he.getMessage());
            JSONObject plain = new JSONObject(payload);
            plain.remove("thinking");
            plain.remove("enable_thinking");
            out = streamTranslate(url, plain.toString(), key, cb);
        }
        if (out == null || out.trim().length() == 0) {
            out = plainTranslate(url, payload, key);
        }
        out = out == null ? "" : out.trim();
        if (out.length() == 0) throw new Exception("接口没有返回译文");
        return out;
    }

    private static String sysPrompt(String target) {
        return "你是翻译引擎。把用户内容译成" + langName(target)
                + "。只输出译文，不要解释、不要加引号、不要重复原文；保留数字、公式、术语和段落结构。";
    }

    private static int maxTokens(String text) {
        int est = text.length() * 2 + 256;
        return Math.max(512, Math.min(est, 8192));
    }

    private static boolean addFastParams(JSONObject body, String model) {
        String m = model == null ? "" : model.toLowerCase();
        try {
            if (m.startsWith("glm") || m.contains("zhipu")) {
                JSONObject t = new JSONObject();
                t.put("type", "disabled");
                body.put("thinking", t);
                return true;
            }
            if (m.startsWith("qwen") || m.startsWith("kimi") || m.startsWith("minimax") || m.contains("moonshot")) {
                body.put("enable_thinking", false);
                return true;
            }
            if (m.startsWith("deepseek")) {
                JSONObject t = new JSONObject();
                t.put("type", "disabled");
                body.put("thinking", t);
                return true;
            }
        } catch (Throwable ignore) {}
        return false;
    }

    private static String streamTranslate(String url, String payload, String key, Callback cb) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "text/event-stream");
        c.setRequestProperty("Authorization", "Bearer " + key);
        byte[] data = payload.getBytes("UTF-8");
        c.setFixedLengthStreamingMode(data.length);
        OutputStream os = c.getOutputStream();
        os.write(data);
        os.flush();
        os.close();
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            String err = "";
            try { err = readAll(c.getErrorStream()); } catch (Throwable ignore) {}
            if (err == null || err.length() == 0) err = "HTTP " + code;
            throw new HttpError(code, cut(err, 200));
        }
        String ctype = "";
        try { ctype = c.getContentType() == null ? "" : c.getContentType().toLowerCase(); } catch (Throwable ignore) {}
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        boolean sse = ctype.indexOf("event-stream") >= 0;
        String first = null;
        String jsonStart = null;
        for (int k = 0; k < 50; k++) {
            String ln = br.readLine();
            if (ln == null) break;
            String t = ln.trim();
            if (sse) { first = ln; break; }
            if (t.startsWith("data:")) { sse = true; first = ln; break; }
            if (t.length() == 0 || t.startsWith(":") || t.startsWith("event:")) continue;
            jsonStart = ln;
            break;
        }
        if (first == null && jsonStart == null) {
            try { br.close(); } catch (Throwable ignore) {}
            return "";
        }
        if (sse) {
            StringBuilder sb = new StringBuilder();
            long last = 0;
            String line = first;
            while (line != null) {
                String s = line.trim();
                if (s.startsWith("data:")) {
                    s = s.substring(5).trim();
                    if ("[DONE]".equals(s)) break;
                    if (s.length() > 0) {
                        String piece = deltaText(s);
                        if (piece.length() > 0) {
                            sb.append(piece);
                            long now = System.currentTimeMillis();
                            if (cb != null && now - last >= 120) {
                                last = now;
                                pushPartial(cb, sb.toString());
                            }
                        }
                    }
                }
                line = br.readLine();
            }
            try { br.close(); } catch (Throwable ignore) {}
            return sb.toString();
        }
        StringBuilder all = new StringBuilder();
        if (jsonStart != null) all.append(jsonStart).append('\n');
        String rest;
        while ((rest = br.readLine()) != null) all.append(rest).append('\n');
        try { br.close(); } catch (Throwable ignore) {}
        return pickContent(all.toString()).trim();
    }

    private static String deltaText(String json) {
        try {
            JSONObject j = new JSONObject(json);
            JSONArray ch = j.optJSONArray("choices");
            if (ch == null || ch.length() == 0) return "";
            JSONObject c0 = ch.optJSONObject(0);
            if (c0 == null) return "";
            JSONObject d = c0.optJSONObject("delta");
            if (d != null) return d.optString("content", "");
            JSONObject m = c0.optJSONObject("message");
            if (m != null) return m.optString("content", "");
        } catch (Throwable ignore) {}
        return "";
    }

    private static String pickContent(String resp) throws Exception {
        JSONObject j = new JSONObject(resp);
        JSONArray choices = j.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new Exception("接口返回异常：" + cut(resp, 150));
        }
        JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
        String content = msg == null ? "" : msg.optString("content", "");
        return content.trim();
    }

    private static String plainTranslate(String url, String payload, String key) throws Exception {
        String body = payload;
        try {
            JSONObject b = new JSONObject(payload);
            b.remove("stream");
            body = b.toString();
        } catch (Throwable ignore) {}
        return pickContent(httpPostJson(url, body, key));
    }

    private static void pushPartial(final Callback cb, final String text) {
        MAIN.post(new Runnable() {
            public void run() {
                try { cb.onPartial(text); } catch (Throwable ignore) {}
            }
        });
    }

    static class HttpError extends Exception {
        final int code;

        HttpError(int code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) XuanFuYi/1.2");
        int code = c.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        return readAll(c.getInputStream());
    }

    private static String httpPostJson(String url, String json, String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Authorization", "Bearer " + key);
        byte[] data = json.getBytes("UTF-8");
        c.setFixedLengthStreamingMode(data.length);
        OutputStream os = c.getOutputStream();
        os.write(data);
        os.flush();
        os.close();
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) {
            String err = "";
            try { err = readAll(c.getErrorStream()); } catch (Throwable ignore) {}
            if (err == null || err.length() == 0) err = "HTTP " + code;
            throw new Exception(cut(err, 200));
        }
        return readAll(c.getInputStream());
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static String unescape(String s) {
        return s.replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static String cut(String s, int n) {
        if (s == null) return "";
        s = s.trim();
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private static String shortMsg(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.length() == 0) m = t.getClass().getSimpleName();
        if (m.indexOf("UnknownHost") >= 0) m = "网络不可用";
        if (m.indexOf("timed out") >= 0 || m.indexOf("timeout") >= 0) m = "网络超时";
        return cut(m, 120);
    }
}
package com.lingo.bubble;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    public static final String ENGINE_FREE = "free";
    public static final String ENGINE_OPENAI = "openai";

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE);
    }

    public String engine() { return sp.getString("engine", ENGINE_FREE); }
    public void setEngine(String v) { sp.edit().putString("engine", v).apply(); }

    public String baseUrl() { return sp.getString("base_url", "https://api.deepseek.com/v1"); }
    public void setBaseUrl(String v) { sp.edit().putString("base_url", v).apply(); }

    public String apiKey() { return sp.getString("api_key", ""); }
    public void setApiKey(String v) { sp.edit().putString("api_key", v).apply(); }

    public String model() { return sp.getString("model", "deepseek-chat"); }
    public void setModel(String v) { sp.edit().putString("model", v).apply(); }

    public String targetLang() { return sp.getString("target_lang", "auto"); }
    public void setTargetLang(String v) { sp.edit().putString("target_lang", v).apply(); }

    public boolean autoCopy() { return sp.getBoolean("auto_copy", true); }
    public void setAutoCopy(boolean v) { sp.edit().putBoolean("auto_copy", v).apply(); }

    public boolean allowGoogle() { return sp.getBoolean("allow_google", true); }
    public void setAllowGoogle(boolean v) { sp.edit().putBoolean("allow_google", v).apply(); }

    public boolean bootStart() { return sp.getBoolean("boot_start", false); }
    public void setBootStart(boolean v) { sp.edit().putBoolean("boot_start", v).apply(); }

    public boolean running() { return sp.getBoolean("running", false); }
    public void setRunning(boolean v) { sp.edit().putBoolean("running", v).apply(); }

    public String bubbleSize() { return sp.getString("bubble_size", "m"); }
    public void setBubbleSize(String v) { sp.edit().putString("bubble_size", v).apply(); }
    public String textSize() { return sp.getString("text_size", "m"); }
    public void setTextSize(String v) { sp.edit().putString("text_size", v).apply(); }
    public String cardWidth() { return sp.getString("card_width", "m"); }
    public void setCardWidth(String v) { sp.edit().putString("card_width", v).apply(); }

    public int bubbleX() { return sp.getInt("bubble_x", -1); }
    public int bubbleY() { return sp.getInt("bubble_y", 520); }

    public void setBubblePos(int x, int y) {
        sp.edit().putInt("bubble_x", x).putInt("bubble_y", y).apply();
    }
}
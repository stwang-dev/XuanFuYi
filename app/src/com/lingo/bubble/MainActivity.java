package com.lingo.bubble;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private Prefs prefs;
    private CheckBox cbShow;
    private CheckBox cbBoot;
    private CheckBox cbAutoCopy;
    private CheckBox cbGoogle;
    private Spinner spEngine;
    private Spinner spTarget;
    private Spinner spSize;
    private Spinner spFont;
    private Spinner spWidth;
    private EditText etBase;
    private EditText etKey;
    private EditText etModel;
    private TextView tvTest;
    private LinearLayout openAiBox;

    private static final String[] ENGINE_LABELS = {"免费公开接口（不用配置）", "OpenAI 兼容接口（推荐）"};
    private static final String[] ENGINE_VALUES = {Prefs.ENGINE_FREE, Prefs.ENGINE_OPENAI};
    private static final String[] TARGET_LABELS = {"自动（中英互译）", "中文", "英文", "日文", "韩文", "德文", "法文", "俄文"};
    private static final String[] TARGET_VALUES = {"auto", "zh", "en", "ja", "ko", "de", "fr", "ru"};
    private static final String[] SIZE_LABELS = {"小", "中", "大"};
    private static final String[] SIZE_VALUES = {"s", "m", "l"};
    private static final String[] FONT_LABELS = {"小", "中", "大", "特大"};
    private static final String[] FONT_VALUES = {"s", "m", "l", "xl"};
    private static final String[] WIDTH_LABELS = {"窄", "中", "宽", "铺满"};
    private static final String[] WIDTH_VALUES = {"s", "m", "l", "f"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFF6F7F9);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        sv.addView(root);

        root.addView(title("悬浮译 · 用法"));
        root.addView(body("1. 在 Zotero 里用笔选中词语或句子\n"
                + "2. 点弹出菜单里的「拷贝」\n"
                + "3. 点屏幕上的蓝色悬浮球「译」\n"
                + "4. 译文气泡立刻弹出，可一键拷贝译文\n\n"
                + "长按悬浮球 = 打开本设置页；拖动悬浮球可换位置。\n"
                + "也可以在 Zotero 里点「共享」把选中的文字发到本应用。"));

        cbShow = check("显示悬浮球（点「译」即翻译剪贴板内容）", prefs.running());
        root.addView(cbShow);
        cbShow.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton v, boolean on) {
                if (on) {
                    if (!Settings.canDrawOverlays(MainActivity.this)) {
                        Toast.makeText(MainActivity.this, "请先打开「悬浮窗」权限", Toast.LENGTH_LONG).show();
                        openOverlaySettings();
                        return;
                    }
                    prefs.setRunning(true);
                    BubbleService.start(MainActivity.this);
                    Toast.makeText(MainActivity.this, "悬浮球已开启", Toast.LENGTH_SHORT).show();
                } else {
                    prefs.setRunning(false);
                    BubbleService.stop(MainActivity.this);
                }
            }
        });

        cbBoot = check("开机后自动开启悬浮球", prefs.bootStart());
        root.addView(cbBoot);
        cbBoot.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton v, boolean on) {
                prefs.setBootStart(on);
            }
        });

        root.addView(section("权限（鸿蒙 / 华为平板需要）"));
        root.addView(body("若点悬浮球没反应，请到 设置 → 应用 → 应用管理 → 悬浮译 → 权限 里，"
                + "打开「悬浮窗」和「后台弹出界面」；再到「应用启动管理」选择「手动管理」，"
                + "允许自启动、关联启动、后台活动。"));
        root.addView(button("① 打开悬浮窗权限", new View.OnClickListener() {
            public void onClick(View v) { openOverlaySettings(); }
        }));
        root.addView(button("② 忽略电池优化（防止被系统杀掉）", new View.OnClickListener() {
            public void onClick(View v) { askBattery(); }
        }));
        root.addView(button("③ 允许通知（保证后台常驻）", new View.OnClickListener() {
            public void onClick(View v) { askNotification(); }
        }));

        root.addView(section("翻译引擎"));
        spEngine = spinner(ENGINE_LABELS, indexOf(ENGINE_VALUES, prefs.engine()));
        root.addView(spEngine);

        openAiBox = new LinearLayout(this);
        openAiBox.setOrientation(LinearLayout.VERTICAL);
        openAiBox.setPadding(0, dp(8), 0, 0);

        openAiBox.addView(label("接口地址 Base URL"));
        etBase = edit(prefs.baseUrl(), "https://api.deepseek.com/v1");
        openAiBox.addView(etBase);

        openAiBox.addView(label("API Key"));
        etKey = edit(prefs.apiKey(), "sk-...");
        etKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        openAiBox.addView(etKey);

        openAiBox.addView(label("模型名"));
        etModel = edit(prefs.model(), "deepseek-chat");
        openAiBox.addView(etModel);

        openAiBox.addView(label("快速填入（点一下自动填好地址和模型）"));
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.addView(preset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"));
        presetRow.addView(preset("智谱", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"));
        openAiBox.addView(presetRow);
        LinearLayout presetRow2 = new LinearLayout(this);
        presetRow2.setOrientation(LinearLayout.HORIZONTAL);
        presetRow2.addView(preset("通义", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"));
        presetRow2.addView(preset("Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k"));
        openAiBox.addView(presetRow2);
        root.addView(openAiBox);
        openAiBox.setVisibility(Prefs.ENGINE_OPENAI.equals(prefs.engine()) ? View.VISIBLE : View.GONE);

        spEngine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView p, View v, int pos, long id) {
                openAiBox.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
            }
            public void onNothingSelected(AdapterView p) {}
        });

        root.addView(label("翻译成"));
        spTarget = spinner(TARGET_LABELS, indexOf(TARGET_VALUES, prefs.targetLang()));
        root.addView(spTarget);

        cbAutoCopy = check("翻译完成后自动把译文放进剪贴板", prefs.autoCopy());
        root.addView(cbAutoCopy);

        cbGoogle = check("允许使用 Google 备用接口（选“免费公开接口”时生效，可能需要科学上网）", prefs.allowGoogle());
        root.addView(cbGoogle);

        root.addView(section("外观"));
        root.addView(label("悬浮球大小（屏幕上那个蓝色「译」球）"));
        spSize = spinner(SIZE_LABELS, indexOf(SIZE_VALUES, prefs.bubbleSize()));
        root.addView(spSize);

        root.addView(label("译文气泡宽度（窄一点，文字就会多换行）"));
        spWidth = spinner(WIDTH_LABELS, indexOf(WIDTH_VALUES, prefs.cardWidth()));
        root.addView(spWidth);

        root.addView(label("气泡里的译文字号"));
        spFont = spinner(FONT_LABELS, indexOf(FONT_VALUES, prefs.textSize()));
        root.addView(spFont);

        root.addView(body("改完点「保存并应用」立刻生效，不用重装。"));
        root.addView(button("保存并应用", new View.OnClickListener() {
            public void onClick(View v) { save(true); }
        }));
        root.addView(button("测试翻译一句", new View.OnClickListener() {
            public void onClick(View v) { test(); }
        }));

        tvTest = body("");
        root.addView(tvTest);

        root.addView(section("说明"));
        root.addView(body("· 免费公开接口不用注册，但句子长了偶尔会失败或翻译质量一般。\n"
                + "· OpenAI 兼容接口需要你自己在 DeepSeek / 智谱 / 通义 / Kimi 等平台申请一个 Key，填进来即可，质量最好。\n"
                + "· 你的 Key 只保存在本机，不会上传到任何第三方。\n"
                + "· 气泡太长时可以在气泡里上下滑动，按钮始终可见。\n"
                + "· 本应用不会修改 Zotero 里的任何内容。"));

        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cbShow != null) cbShow.setChecked(prefs.running());
    }

    private void save(boolean apply) {
        prefs.setEngine(ENGINE_VALUES[spEngine.getSelectedItemPosition()]);
        prefs.setTargetLang(TARGET_VALUES[spTarget.getSelectedItemPosition()]);
        prefs.setBubbleSize(SIZE_VALUES[spSize.getSelectedItemPosition()]);
        prefs.setCardWidth(WIDTH_VALUES[spWidth.getSelectedItemPosition()]);
        prefs.setTextSize(FONT_VALUES[spFont.getSelectedItemPosition()]);
        prefs.setAutoCopy(cbAutoCopy.isChecked());
        prefs.setAllowGoogle(cbGoogle.isChecked());
        prefs.setBootStart(cbBoot.isChecked());
        prefs.setBaseUrl(etBase.getText().toString().trim());
        prefs.setApiKey(etKey.getText().toString().trim());
        prefs.setModel(etModel.getText().toString().trim());
        if (apply) {
            if (prefs.running()) {
                BubbleService.stop(this);
                BubbleService.start(this);
            }
            Toast.makeText(this, "已保存，立刻生效", Toast.LENGTH_SHORT).show();
        }
    }

    private void test() {
        save(false);
        tvTest.setTextColor(0xFF1A73E8);
        tvTest.setText("正在测试…");
        final long t0 = System.currentTimeMillis();
        Translator.translateAsync(this, "Ontologies remain a central mechanism for turning knowledge into reusable assets.",
                new Translator.Callback() {
                    public void onPartial(String soFar) {
                        tvTest.setTextColor(0xFF1A73E8);
                        tvTest.setText("译文：" + soFar);
                    }

                    public void onResult(String out, String err) {
                        String cost = "（用时 " + String.format(java.util.Locale.US, "%.1f", (System.currentTimeMillis() - t0) / 1000.0) + " 秒）";
                        if (out != null && out.length() > 0) {
                            tvTest.setTextColor(0xFF111111);
                            tvTest.setText("译文：" + out + "\n" + cost);
                        } else {
                            tvTest.setTextColor(0xFFC62828);
                            tvTest.setText("失败：" + (err == null ? "未知原因" : err) + cost
                                    + "\n\n提示：换成 OpenAI 兼容接口并填入 Key 通常最稳。");
                        }
                    }
                });
    }

    private void openOverlaySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); } catch (Throwable ignore) {}
        }
    }

    private void askBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable t) {
            Toast.makeText(this, "请到 设置 → 电池 里把本应用设为「不受限制」", Toast.LENGTH_LONG).show();
        }
    }

    private void askNotification() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 100);
        } else {
            Toast.makeText(this, "当前系统不需要额外授权", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private int indexOf(String[] arr, String value) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(value)) return i;
        return 0;
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF111111);
        t.setPadding(0, 0, 0, dp(10));
        return t;
    }

    private TextView section(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF1A73E8);
        t.setPadding(0, dp(22), 0, dp(6));
        return t;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(0xFF444444);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private TextView body(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setTextColor(0xFF333333);
        t.setLineSpacing(dp(4), 1f);
        return t;
    }

    private CheckBox check(String s, boolean value) {
        CheckBox c = new CheckBox(this);
        c.setText(s);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        c.setTextColor(0xFF222222);
        c.setChecked(value);
        c.setPadding(0, dp(8), 0, dp(8));
        return c;
    }

    private EditText edit(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setSingleLine(true);
        return e;
    }

    private Spinner spinner(String[] labels, int sel) {
        Spinner s = new Spinner(this);
        ArrayAdapter ad = new ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels);
        s.setAdapter(ad);
        s.setSelection(sel);
        return s;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private Button preset(final String name, final String base, final String model) {
        Button b = new Button(this);
        b.setText(name);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setPadding(0, 0, 0, 0);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                etBase.setText(base);
                etModel.setText(model);
                spEngine.setSelection(1);
                Toast.makeText(MainActivity.this, name + " 已填入，请补上 API Key", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }
}
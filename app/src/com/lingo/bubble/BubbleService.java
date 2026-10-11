package com.lingo.bubble;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class BubbleService extends Service {

    public static final String ACTION_STOP = "com.lingo.bubble.STOP";

    public static volatile BubbleService INSTANCE;
    private static volatile String PENDING_TEXT;

    private static final String CHANNEL_ID = "bubble_channel";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager wm;
    private View bubble;
    private WindowManager.LayoutParams lpBubble;

    private LinearLayout card;
    private WindowManager.LayoutParams lpCard;
    private TextView tvSrc;
    private TextView tvDst;
    private TextView tvInfo;

    private String lastSource;
    private boolean busy;
    private boolean cardDismissed;
    private int screenW;
    private int screenH;

    private final Handler main = new Handler(Looper.getMainLooper());

    public static void start(Context c) {
        Intent i = new Intent(c, BubbleService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, BubbleService.class));
    }

    public static void postText(Context c, String text) {
        if (text == null || text.trim().length() == 0) return;
        BubbleService svc = INSTANCE;
        if (svc != null) {
            svc.translateText(text);
        } else {
            PENDING_TEXT = text;
            start(c);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForeground(NOTIFICATION_ID, buildNotification());
        addBubble();
        String pending = PENDING_TEXT;
        if (pending != null) {
            PENDING_TEXT = null;
            final String t = pending;
            main.postDelayed(new Runnable() {
                public void run() { translateText(t); }
            }, 400);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            new Prefs(this).setRunning(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        String pending = PENDING_TEXT;
        if (pending != null) {
            PENDING_TEXT = null;
            final String t = pending;
            main.postDelayed(new Runnable() {
                public void run() { translateText(t); }
            }, 400);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeCard();
        if (bubble != null) {
            try { wm.removeView(bubble); } catch (Throwable ignore) {}
            bubble = null;
        }
        INSTANCE = null;
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "悬浮译", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(this, BubbleService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent piStop = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_sort_by_size)
                .setContentTitle("悬浮译正在运行")
                .setContentText("选词 → 拷贝 → 点悬浮球看译文")
                .setOngoing(true)
                .setContentIntent(pi);
        b.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "停止", piStop).build());
        return b.build();
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private int bubbleSizePx() {
        String s = new Prefs(this).bubbleSize();
        if ("s".equals(s)) return dp(42);
        if ("l".equals(s)) return dp(66);
        return dp(52);
    }

    private void addBubble() {
        int size = bubbleSizePx();
        screenW = getResources().getDisplayMetrics().widthPixels;
        screenH = getResources().getDisplayMetrics().heightPixels;

        TextView tv = new TextView(this);
        tv.setText("译");
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size <= dp(44) ? 16 : (size >= dp(64) ? 24 : 20));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xE61A73E8);
        bg.setStroke(dp(1), 0x55FFFFFF);
        tv.setBackground(bg);
        tv.setAlpha(0.92f);

        lpBubble = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lpBubble.gravity = Gravity.TOP | Gravity.START;
        Prefs p = new Prefs(this);
        int bx = p.bubbleX();
        int by = p.bubbleY();
        lpBubble.x = bx < 0 ? screenW - size - dp(6) : Math.min(bx, screenW - size);
        lpBubble.y = Math.max(0, Math.min(by, screenH - size - dp(6)));

        tv.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX, downRawY;
            private int startX, startY;
            private long downTime;
            private boolean dragged;

            public boolean onTouch(View v, MotionEvent e) {
                int act = e.getActionMasked();
                if (act == MotionEvent.ACTION_DOWN) {
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    startX = lpBubble.x;
                    startY = lpBubble.y;
                    downTime = System.currentTimeMillis();
                    dragged = false;
                    v.setAlpha(1f);
                    return true;
                }
                if (act == MotionEvent.ACTION_MOVE) {
                    int dx = (int) (e.getRawX() - downRawX);
                    int dy = (int) (e.getRawY() - downRawY);
                    if (!dragged && (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6))) dragged = true;
                    if (dragged) {
                        lpBubble.x = Math.max(0, Math.min(startX + dx, screenW - lpBubble.width));
                        lpBubble.y = Math.max(0, Math.min(startY + dy, screenH - lpBubble.height));
                        try { wm.updateViewLayout(v, lpBubble); } catch (Throwable ignore) {}
                    }
                    return true;
                }
                if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(0.92f);
                    long dur = System.currentTimeMillis() - downTime;
                    if (dragged) {
                        new Prefs(BubbleService.this).setBubblePos(lpBubble.x, lpBubble.y);
                    } else if (dur >= 600) {
                        openSettings();
                    } else {
                        onBubbleTap();
                    }
                    return true;
                }
                return false;
            }
        });

        try {
            wm.addView(tv, lpBubble);
            bubble = tv;
        } catch (Throwable t) {
            toast("悬浮球没有显示：请先授予「悬浮窗」权限");
        }
    }

    private void openSettings() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Throwable t) { toast("请从桌面打开「悬浮译」"); }
    }

    private void onBubbleTap() {
        String t = readClipboard();
        if (t != null && t.trim().length() > 0) {
            translateText(t);
        } else {
            Intent i = new Intent(this, ClipActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            try { startActivity(i); } catch (Throwable e) { toast("没读到文字：请先选中文字并点「拷贝」"); }
        }
    }

    public String readClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null) return null;
            ClipData cd = cm.getPrimaryClip();
            if (cd == null || cd.getItemCount() == 0) return null;
            CharSequence cs = cd.getItemAt(0).coerceToText(this);
            return cs == null ? null : cs.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    public void translateText(final String raw) {
        final String text = Translator.normalize(raw);
        if (text.length() == 0) {
            toast("没有读到文字");
            return;
        }
        lastSource = text;
        busy = true;
        cardDismissed = false;
        final long t0 = System.currentTimeMillis();
        showCard(text, "翻译中…", false);
        setInfo("翻译中…");
        Translator.translateAsync(this, text, new Translator.Callback() {
            public void onPartial(String soFar) {
                updateDst(soFar);
                setInfo("翻译中… " + sec(System.currentTimeMillis() - t0) + " 秒");
            }

            public void onResult(String out, String err) {
                busy = false;
                boolean shut = cardDismissed;
                if (out != null && out.length() > 0) {
                    if (!shut) {
                        showCard(text, out, true);
                        setInfo("用时 " + sec(System.currentTimeMillis() - t0) + " 秒");
                    }
                    if (new Prefs(BubbleService.this).autoCopy()) copyText(out, "译文已复制");
                } else if (shut) {
                    toast("翻译失败：" + (err == null ? "未知原因" : err));
                } else {
                    showCard(text, "翻译失败：" + (err == null ? "未知原因" : err), false);
                    setInfo("");
                }
            }
        });
    }

    private static String sec(long ms) {
        return String.format(java.util.Locale.US, "%.1f", ms / 1000.0);
    }

    private void updateDst(String s) {
        if (tvDst == null || cardDismissed) return;
        if (card == null || card.getParent() == null) {
            showCard(lastSource, s, false);
            return;
        }
        tvDst.setText(s);
        tvDst.setTextColor(0xFF1A73E8);
    }

    private void copyText(String s, String tip) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("translate", s));
            toast(tip);
        } catch (Throwable t) {
            toast("复制失败");
        }
    }

    private TextView makeButton(String label) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setTextColor(0xFF1A73E8);
        b.setPadding(dp(12), dp(7), dp(12), dp(7));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0x141A73E8);
        b.setBackground(bg);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        return b;
    }

    private void buildCard() {
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0xF7FFFFFF);
        bg.setStroke(dp(1), 0x33000000);
        card.setBackground(bg);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);

        tvSrc = new TextView(this);
        tvSrc.setTextColor(0xFF777777);
        tvSrc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSrc.setMaxLines(4);
        tvSrc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        inner.addView(tvSrc);

        tvDst = new TextView(this);
        tvDst.setTextColor(0xFF111111);
        tvDst.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(8);
        inner.addView(tvDst, dlp);

        MaxScroll scroll = new MaxScroll(this);
        scroll.setMaxHeight((int) (screenH * 0.55f));
        scroll.addView(inner, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

        card.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(12);

        TextView btnCopy = makeButton("拷贝译文");
        TextView btnRetry = makeButton("重译");
        TextView btnClose = makeButton("关闭");
        row.addView(btnCopy);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gap.leftMargin = dp(8);
        row.addView(btnRetry, gap);
        LinearLayout.LayoutParams gap2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gap2.leftMargin = dp(8);
        row.addView(btnClose, gap2);
        card.addView(row, rlp);

        tvInfo = new TextView(this);
        tvInfo.setTextColor(0xFF999999);
        tvInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(8);
        card.addView(tvInfo, ilp);

        btnCopy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String t = tvDst.getText().toString();
                if (t.length() > 0) copyText(t, "译文已复制");
            }
        });
        btnRetry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (lastSource != null) translateText(lastSource);
            }
        });
        btnClose.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { removeCard(); }
        });

        tvSrc.setOnTouchListener(new View.OnTouchListener() {
            private float downRawY;
            private int startY;

            public boolean onTouch(View v, MotionEvent e) {
                int act = e.getActionMasked();
                if (act == MotionEvent.ACTION_DOWN) {
                    downRawY = e.getRawY();
                    startY = lpCard.y;
                    return true;
                }
                if (act == MotionEvent.ACTION_MOVE) {
                    lpCard.y = Math.max(0, Math.min(startY + (int) (e.getRawY() - downRawY), screenH - dp(80)));
                    try { wm.updateViewLayout(card, lpCard); } catch (Throwable ignore) {}
                    return true;
                }
                return false;
            }
        });
    }

    private void showCard(String src, String dst, boolean finalResult) {
        if (card == null) {
            try {
                buildCard();
            } catch (Throwable t) {
                card = null;
            }
            if (card == null) buildFallbackCard();
        }
        tvSrc.setText(src);
        tvDst.setText(dst);
        tvDst.setTextSize(TypedValue.COMPLEX_UNIT_SP, dstSp());
        tvSrc.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(11, dstSp() - 5));
        tvDst.setTextColor(finalResult ? 0xFF111111 : 0xFF1A73E8);

        int width = cardWidthPx();
        if (lpCard == null) {
            lpCard = new WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lpCard.gravity = Gravity.TOP | Gravity.START;
            lpCard.y = dp(96);
        }
        lpCard.width = width;
        lpCard.x = Math.max(0, (screenW - width) / 2);
        if (card.getParent() == null) {
            try { wm.addView(card, lpCard); } catch (Throwable t) { toast("无法显示翻译气泡"); }
        } else {
            try { wm.updateViewLayout(card, lpCard); } catch (Throwable ignore) {}
        }
    }

    private void buildFallbackCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(0xF7FFFFFF);
        bg.setStroke(dp(1), 0x33000000);
        c.setBackground(bg);

        tvSrc = new TextView(this);
        tvSrc.setTextColor(0xFF777777);
        tvSrc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSrc.setMaxLines(3);
        tvSrc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        c.addView(tvSrc);

        tvDst = new TextView(this);
        tvDst.setTextColor(0xFF111111);
        tvDst.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        c.addView(tvDst);

        TextView close = makeButton("关闭");
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { removeCard(); }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(10);
        c.addView(close, clp);

        tvInfo = null;
        card = c;
    }
    private void removeCard() {
        cardDismissed = true;
        if (card != null && card.getParent() != null) {
            try { wm.removeView(card); } catch (Throwable ignore) {}
        }
    }

    private int cardWidthPx() {
        String s = new Prefs(this).cardWidth();
        int pct = 60;
        if ("s".equals(s)) pct = 45;
        else if ("l".equals(s)) pct = 80;
        else if ("f".equals(s)) pct = 96;
        int w = (int) (screenW * pct / 100.0);
        return Math.max(dp(160), Math.min(w, screenW - dp(8)));
    }

    private static class MaxScroll extends android.widget.ScrollView {
        private int maxH;

        public MaxScroll(android.content.Context c) {
            super(c);
        }

        public void setMaxHeight(int h) {
            maxH = h;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            if (maxH > 0 && getMeasuredHeight() > maxH) {
                setMeasuredDimension(getMeasuredWidth(), maxH);
            }
        }
    }
    private int dstSp() {
        String s = new Prefs(this).textSize();
        if ("s".equals(s)) return 14;
        if ("l".equals(s)) return 21;
        if ("xl".equals(s)) return 26;
        return 17;
    }
    private void setInfo(String s) {
        if (tvInfo == null) return;
        tvInfo.setText(s == null ? "" : s);
    }

    private void toast(String s) {
        try { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
    }
}
package com.lingo.bubble;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Toast;

public class ClipActivity extends Activity {

    private boolean handled;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.width = 1;
            lp.height = 1;
            lp.alpha = 0f;
            lp.dimAmount = 0f;
            getWindow().setAttributes(lp);
        } catch (Throwable ignore) {}

        BubbleService.start(this);

        final Handler h = new Handler(Looper.getMainLooper());
        Runnable tryRead = new Runnable() {
            public void run() { tryHandle(); }
        };
        h.post(tryRead);
        h.postDelayed(tryRead, 120);
        h.postDelayed(tryRead, 350);
        h.postDelayed(new Runnable() {
            public void run() {
                if (!handled) {
                    Toast.makeText(ClipActivity.this, "没读到文字：请先在 Zotero 里选中文字并点「拷贝」", Toast.LENGTH_SHORT).show();
                    finish0();
                }
            }
        }, 1500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        tryHandle();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) tryHandle();
    }

    private void tryHandle() {
        if (handled) return;
        BubbleService svc = BubbleService.INSTANCE;
        if (svc == null) return;
        String t = svc.readClipboard();
        if (t == null || t.trim().length() == 0) return;
        handled = true;
        BubbleService.postText(this, t);
        finish0();
    }

    private void finish0() {
        finish();
        overridePendingTransition(0, 0);
    }
}
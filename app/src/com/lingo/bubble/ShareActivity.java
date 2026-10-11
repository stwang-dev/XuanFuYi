package com.lingo.bubble;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class ShareActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String t = null;
        Intent it = getIntent();
        if (it != null) {
            t = it.getStringExtra(Intent.EXTRA_TEXT);
            if (t == null) t = it.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
            if (t == null && it.getData() != null) t = String.valueOf(it.getData());
        }
        if (t != null && t.trim().length() > 0) {
            BubbleService.start(this);
            BubbleService.postText(this, t);
        } else {
            Toast.makeText(this, "没有收到文字", Toast.LENGTH_SHORT).show();
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
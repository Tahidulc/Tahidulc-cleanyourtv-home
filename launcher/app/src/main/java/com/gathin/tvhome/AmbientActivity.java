package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * Ambient mode over Home (AmbientScene), started by Home after the theme's "ambient_minutes" without a key. Any key
 * goes back to Home, and that key does nothing else. It never keeps the screen on: the TV's own sleep timer still
 * turns it off.
 */
public class AmbientActivity extends Activity {

    private AmbientScene scene;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scene = new AmbientScene(this);
        setContentView(scene.view());
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scene.start();
        long ms = minutes() * 60_000L;
        if (ms > 0) handler.postDelayed(toScreensaver, ms);
    }

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    /**
     * After the theme's "ambient_screensaver_minutes" (10 when not set; 0: never) the TV's own screensaver takes over,
     * started as Settings' "Start now" does (Android's Somnambulator); on a TV without it, the launcher's clock
     * screensaver, which moves on black.
     */
    private final Runnable toScreensaver = () -> {
        android.content.Intent dream = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (dream.resolveActivity(getPackageManager()) != null) startActivity(dream);
            else startActivity(new android.content.Intent(this, ClockActivity.class));
        } catch (RuntimeException e) {
            startActivity(new android.content.Intent(this, ClockActivity.class));
        }
        finish();
    };

    private long minutes() {
        try {
            return Math.max(0, Math.min(240, Long.parseLong(Config.themeString(this, "ambient_screensaver_minutes", "10").trim())));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(toScreensaver);
        scene.stop();
        super.onPause();
        finish();   // never left behind in the back stack
    }

    @Override
    protected void onDestroy() {
        scene.close();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        finish();
        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}

package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * A playlist's event channel full screen ("03. Elversberg vs Mainz 19:30"), for a game no broadcaster in TiviMate has:
 * TiviMate keeps those groups out of its own list on some TVs and offers no way in by link, nor does Kleva TV. The
 * stream straight from the playlist, the transport stream first and HLS if this TV's player turns that down, on the TV's
 * video hardware; the game's name and LIVE for a few seconds; Back closes it.
 */
public class StreamActivity extends Activity {
    private static final String URL = "url", TITLE = "title", LINE = "line";

    private MediaPlayer player;
    private SurfaceHolder holder;
    private String base;
    private View wait, banner;

    static void play(Context c, String base, String title, String line) {
        Intent i = new Intent(c, StreamActivity.class).putExtra(URL, base).putExtra(TITLE, title).putExtra(LINE, line);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        base = getIntent().getStringExtra(URL);
        float d = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        SurfaceView video = new SurfaceView(this);
        root.addView(video, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        // Until the first picture: the game's name over black.
        TextView loading = text(getIntent().getStringExtra(TITLE), 26, true, Color.WHITE);
        loading.setGravity(Gravity.CENTER);
        wait = loading;
        root.addView(loading, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The banner: LIVE, the game, where it is from; gone after a few seconds.
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Math.round(18 * d), Math.round(10 * d), Math.round(22 * d), Math.round(10 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xB3000000);
        bg.setCornerRadius(14 * d);
        b.setBackground(bg);
        TextView live = text("LIVE", 12, true, Color.WHITE);
        GradientDrawable lb = new GradientDrawable();
        lb.setColor(0xFFE0263A);
        lb.setCornerRadius(5 * d);
        live.setBackground(lb);
        live.setPadding(Math.round(8 * d), Math.round(2 * d), Math.round(8 * d), Math.round(2 * d));
        b.addView(live);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(getIntent().getStringExtra(TITLE), 18, true, Color.WHITE));
        String line = getIntent().getStringExtra(LINE);
        if (line != null) words.addView(text(line, 12, false, 0xCCFFFFFF));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wl.leftMargin = Math.round(14 * d);
        b.addView(words, wl);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        bl.leftMargin = Math.round(40 * d);
        bl.topMargin = Math.round(30 * d);
        root.addView(b, bl);
        banner = b;
        setContentView(root);
        video.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder h) { holder = h; start(false); }
            @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) { }
            @Override public void surfaceDestroyed(SurfaceHolder h) { holder = null; release(); }
        });
    }

    private TextView text(String s, float sp, boolean bold, int colour) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(colour);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private void start(boolean hls) {
        if (holder == null || base == null) return;
        release();
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            mp.setDisplay(holder);
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "TiviMate/5.1.6 (Android 14)");
            mp.setDataSource(this, Uri.parse(base + (hls ? ".m3u8" : ".ts")), headers);
            mp.setScreenOnWhilePlaying(true);
            mp.setOnPreparedListener(m -> { if (player == m) m.start(); });
            mp.setOnInfoListener((m, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && player == m) {
                    wait.animate().alpha(0f).setDuration(300).start();
                    banner.animate().alpha(0f).setStartDelay(5000).setDuration(600).start();
                }
                return false;
            });
            mp.setOnErrorListener((m, what, extra) -> {
                if (player != m) return true;
                release();
                if (!hls) start(true);
                else ((TextView) wait).setText("This channel isn't showing anything just now");
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            release();
            if (!hls) start(true);
        }
    }

    private void release() {
        MediaPlayer mp = player;
        player = null;
        if (mp == null) return;
        try { mp.stop(); } catch (Exception ignored) { }
        mp.release();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        // OK or any arrow: the banner back for a moment.
        if (code != KeyEvent.KEYCODE_BACK && banner != null) {
            banner.animate().cancel();
            banner.setAlpha(1f);
            banner.animate().alpha(0f).setStartDelay(4000).setDuration(600).start();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override protected void onStop() {
        super.onStop();
        release();
        finish();
    }
}

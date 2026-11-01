package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * An official video from YouTube (a broadcaster's or a league's own channel) played inside the launcher's overlays
 * through YouTube's own embedded player, as any site may embed it: the Live Centre's goal clips and highlights. Its
 * events (ready, playing, ended, an error such as a video not shown in this country) come back to the caller.
 */
final class YouTubeBox {
    interface Events { void on(String event); }

    private static final Handler main = new Handler(Looper.getMainLooper());

    private YouTubeBox() { }

    /** The player for one video, starting by itself; muted for a clip over the channel's own sound. */
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    static WebView make(Context c, String videoId, boolean muted, Events events) {
        WebView w = new WebView(c);
        w.setBackgroundColor(Color.BLACK);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDomStorageEnabled(true);
        w.setFocusable(false);
        w.addJavascriptInterface(new Object() {
            @JavascriptInterface public void ev(String e) { main.post(() -> { if (events != null) events.on(e); }); }
        }, "Box");
        String id = videoId.replaceAll("[^A-Za-z0-9_-]", "");
        String html = "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p,iframe{width:100%;height:100%;border:0}</style>"
                + "</head><body><div id=p></div><script src='https://www.youtube.com/iframe_api'></script><script>"
                + "function onYouTubeIframeAPIReady(){new YT.Player('p',{videoId:'" + id + "',playerVars:{autoplay:1,mute:" + (muted ? 1 : 0)
                + ",controls:0,rel:0,playsinline:1,iv_load_policy:3,fs:0,disablekb:1,origin:'https://com.gathin.tvhome'},events:{"
                + "onReady:function(e){Box.ev('ready');e.target.playVideo();},"
                + "onStateChange:function(e){Box.ev('state'+e.data);},"
                + "onError:function(e){Box.ev('error'+e.data);}}});}</script></body></html>";
        w.loadDataWithBaseURL("https://com.gathin.tvhome/", html, "text/html", "utf-8", null);
        return w;
    }

    /**
     * adb only: each video played for a while on a display of the app's own (nothing on screen), its player's events
     * with the seconds they came at written to files/yt.txt: whether YouTube plays it here, or why not. An id ending
     * in "!" is played with its sound (as the highlights are), else muted (as the goal clips are).
     */
    static void test(Context ctx, String[] ids) {
        Context c = ctx.getApplicationContext();
        StringBuilder log = new StringBuilder();
        ImageReader reader = ImageReader.newInstance(1280, 720, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(r -> { android.media.Image i = r.acquireLatestImage(); if (i != null) i.close(); }, main);
        VirtualDisplay vd = c.getSystemService(DisplayManager.class).createVirtualDisplay("yt", 1280, 720,
                c.getResources().getDisplayMetrics().densityDpi, reader.getSurface(), 0);
        Presentation p = new Presentation(c, vd.getDisplay(), android.R.style.Theme_DeviceDefault_NoActionBar);
        android.widget.FrameLayout root = new android.widget.FrameLayout(c);
        p.setContentView(root);
        p.show();
        int[] at = {0};
        long[] start = {0};
        Runnable next = new Runnable() {
            @Override public void run() {
                root.removeAllViews();
                if (at[0] >= ids.length) {
                    try { p.dismiss(); } catch (RuntimeException ignored) { }
                    vd.release();
                    reader.close();
                    try (FileOutputStream o = new FileOutputStream(new File(c.getExternalFilesDir(null), "yt.txt"))) {
                        o.write(log.toString().getBytes(StandardCharsets.UTF_8));
                    } catch (Exception ignored) { }
                    return;
                }
                String id = ids[at[0]++];
                boolean sound = id.endsWith("!");
                if (sound) id = id.substring(0, id.length() - 1);
                log.append(id).append(sound ? " (sound)" : "").append(':');
                start[0] = System.currentTimeMillis();
                root.addView(make(c, id, !sound, e -> log.append(' ').append(e).append('@')
                        .append(String.format(Locale.ROOT, "%.1f", (System.currentTimeMillis() - start[0]) / 1000.0))));
                main.postDelayed(() -> { log.append('\n'); run(); }, 14_000L);
            }
        };
        main.post(next);
    }
}

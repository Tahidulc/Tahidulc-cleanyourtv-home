package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * What plays inside launcher v2's focused tile: a film's or series' trailer (YouTube's embedded player, its page
 * loaded ahead so a trailer starts at once) or a channel live from the playlist, with sound. One at a time; stop()
 * takes it off the tile. The trailer fades in only once YouTube's title and logo have gone from its first seconds,
 * and is drawn larger than the tile so its black bars and corner marks fall outside.
 */
final class TilePlayer {
    /** YouTube shows the video's title over its first seconds: the trailer stays hidden under the picture until then. */
    private static final long TITLE_MS = 4_200L;
    private final Context c;
    private final Handler main = new Handler(Looper.getMainLooper());
    private android.webkit.WebView web;
    private boolean ready;
    private String pending;
    private FrameLayout tile;
    private View video;
    /**
     * A slower TV (lite_home): the channel on a SurfaceView, laid over by the TV's own video hardware, not copied
     * into the launcher's picture each frame. A 4K channel through a TextureView made the graphics chip shrink 4K
     * frames into the tile fifty times a second and redraw the screen with each one.
     */
    private boolean overlay;

    void useOverlay(boolean on) {
        overlay = on;
    }
    private MediaPlayer player;
    /** The live picture's surface on the tile's texture, let go with the player (else it leaked each time). */
    private android.view.Surface surface;
    /** The tile's own picture, over the trailer until it is really playing (and again whenever YouTube stops). */
    private View[] covers = new View[0];
    private boolean revealed;
    /** The tile was kept as a finished picture before the trailer came onto it. */
    private boolean layered;
    private final Runnable reveal = () -> {
        boolean channel = video != null && !(video instanceof TextureView);
        if (!channel && (web == null || web.getParent() == null)) return;
        revealed = true;
        for (View c : covers) if (c != null) c.animate().alpha(0f).setDuration(450).start();
    };

    /** YouTube loading or paused: the tile's pictures back over it. */
    private void covered() {
        main.removeCallbacks(reveal);
        if (!revealed) return;
        revealed = false;
        for (View c : covers) if (c != null) { c.animate().cancel(); c.animate().alpha(1f).setDuration(150).start(); }
    }

    TilePlayer(Context c) {
        this.c = c;
    }

    /** Loads YouTube's player page now, out of sight, so the first trailer does not wait for it. */
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    void warm() {
        if (web != null) return;
        try {
            web = new android.webkit.WebView(c);
        } catch (RuntimeException e) {
            return;   // no WebView on this TV: no trailers
        }
        android.webkit.WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setDomStorageEnabled(true);
        web.setBackgroundColor(Color.BLACK);
        web.setFocusable(false);
        web.setFocusableInTouchMode(false);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface public void ready() {
                main.post(() -> { ready = true; if (pending != null) { String id = pending; pending = null; cue(id); } });
            }
            @android.webkit.JavascriptInterface public void playing() {
                main.post(() -> { main.removeCallbacks(reveal); main.postDelayed(reveal, TITLE_MS); });
            }
            @android.webkit.JavascriptInterface public void waiting() {
                // Loading or paused: YouTube draws its logo and spinner over the picture, so the trailer hides again.
                main.post(TilePlayer.this::covered);
            }
            @android.webkit.JavascriptInterface public void ended() {
                main.post(TilePlayer.this::stop);
            }
        }, "Home");
        String pkg = c.getPackageName();
        String html = "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;inset:0;width:100%;height:100%;transform:scale(1.75)}iframe{pointer-events:none}</style>"
                + "</head><body><div id=p></div><script src='https://www.youtube.com/iframe_api'></script><script>"
                + "var pl;function onYouTubeIframeAPIReady(){pl=new YT.Player('p',{width:'100%',height:'100%',"
                + "playerVars:{autoplay:1,controls:0,rel:0,playsinline:1,modestbranding:1,iv_load_policy:3,disablekb:1,fs:0,cc_load_policy:0,showinfo:0,"
                + "origin:'https://" + pkg + "'},"
                + "events:{onReady:function(){Home.ready();},"
                + "onStateChange:function(e){if(e.data==1)Home.playing();else if(e.data==3||e.data==2||e.data==-1||e.data==5)Home.waiting();if(e.data==2)e.target.playVideo();if(e.data==0)Home.ended();}}});}"
                + "function play(id){try{pl.unloadModule('captions');}catch(x){}pl.setVolume(80);pl.loadVideoById({videoId:id,startSeconds:6});}"
                + "function stop(){try{pl.stopVideo();}catch(x){}}"
                + "</script></body></html>";
        web.loadDataWithBaseURL("https://" + pkg, html, "text/html", "utf-8", null);
    }

    /**
     * The trailer (a YouTube id) across the tile at index 'at', under the tile's picture (cover): YouTube's own logo,
     * title, spinner and pause sign are never seen, as the picture lifts only while the trailer is playing.
     */
    void trailer(FrameLayout into, int at, String id, View... covers) {
        stop();
        warm();
        if (web == null) return;
        tile = into;
        this.covers = covers;
        revealed = false;
        // YouTube's player cannot draw into a tile kept as a finished picture (a slower TV's Home): the tile is drawn
        // live while the trailer plays, and kept again after.
        layered = into.getLayerType() == View.LAYER_TYPE_HARDWARE;
        if (layered) into.setLayerType(View.LAYER_TYPE_NONE, null);
        web.setAlpha(1f);
        into.addView(web, Math.min(at, into.getChildCount()), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (ready) cue(id); else pending = id;
    }

    private void cue(String id) {
        if (web != null && web.getParent() != null) web.evaluateJavascript("play('" + id.replace("'", "") + "')", null);
    }

    /** A channel live across the tile, with sound: the playlist's stream (base without extension), HLS as the fallback. */
    void live(FrameLayout into, int at, String base, View cover, boolean hdr) {
        stop();
        tile = into;
        // A tile kept as a finished picture would hold the first frame still (sound on, picture frozen): drawn live
        // while the channel plays, kept again after.
        layered = into.getLayerType() == View.LAYER_TYPE_HARDWARE;
        if (layered) into.setLayerType(View.LAYER_TYPE_NONE, null);
        // An HDR channel (the UHD ones) through the TV's video hardware would switch the whole screen into HDR, and the
        // TV says so in its corner ("HDR10") on Home: those are drawn in the launcher's own picture instead, which
        // stays as it is; the TV switches (and says so) only once the channel is watched in an app.
        if (overlay && !hdr) {
            // Under the tile's own picture, which fades away once the channel is really showing; the tile's rounded
            // corners stay, as the window's own pixels cover the video there.
            android.view.SurfaceView sv = new android.view.SurfaceView(c);
            video = sv;
            covers = new View[]{cover};
            into.addView(sv, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            sv.getHolder().addCallback(new android.view.SurfaceHolder.Callback() {
                @Override public void surfaceCreated(android.view.SurfaceHolder holder) { if (video == sv) stream(sv, holder.getSurface(), base, false); }
                @Override public void surfaceChanged(android.view.SurfaceHolder holder, int f, int w, int h) { }
                @Override public void surfaceDestroyed(android.view.SurfaceHolder holder) { if (video == sv) release(); }
            });
            return;
        }
        TextureView v = new TextureView(c);
        v.setAlpha(0f);
        video = v;
        into.addView(v, Math.min(at, into.getChildCount()), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        v.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int w, int h) {
                if (video != v) return;
                if (surface != null) surface.release();
                surface = new android.view.Surface(st);
                stream(v, surface, base, false);
            }
            @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st, int w, int h) { }
            @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) { return true; }
            @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) { }
        });
    }

    private void stream(View v, android.view.Surface into, String base, boolean hls) {
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            mp.setSurface(into);
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            headers.put("User-Agent", "TiviMate/5.1.6 (Android 14)");
            mp.setDataSource(c, android.net.Uri.parse(base + (hls ? ".m3u8" : ".ts")), headers);
            mp.setOnPreparedListener(m -> {
                if (player != m) return;
                m.start();
                float vw = m.getVideoWidth(), vh = m.getVideoHeight();
                if (v instanceof TextureView && vw > 0 && vh > 0 && v.getWidth() > 0) {
                    android.graphics.Matrix mx = new android.graphics.Matrix();
                    float scale = Math.max(v.getWidth() / vw, v.getHeight() / vh);
                    mx.setScale(vw * scale / v.getWidth(), vh * scale / v.getHeight(), v.getWidth() / 2f, v.getHeight() / 2f);
                    ((TextureView) v).setTransform(mx);
                }
            });
            mp.setOnInfoListener((m, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && player == m) {
                    if (v instanceof TextureView) v.animate().alpha(1f).setDuration(350).start();
                    else main.post(reveal);
                }
                return false;
            });
            mp.setOnErrorListener((m, what, extra) -> {
                if (player != m) return true;
                releasePlayer();
                if (!hls && video == v) main.post(() -> { if (video == v) stream(v, into, base, true); });
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            release();
        }
    }

    private void release() {
        releasePlayer();
        if (surface != null) { surface.release(); surface = null; }
    }

    /** The player alone: its surface kept for the HLS try that follows. */
    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    /** Off the tile: the trailer silenced (its page kept for the next), the channel's player let go. */
    void stop() {
        main.removeCallbacks(reveal);
        // Moving on: the first picture (the poster, a tile's own) comes back at once; the rest belong to the page.
        if (revealed && covers.length > 0 && covers[0] != null) { covers[0].animate().cancel(); covers[0].setAlpha(1f); }
        revealed = false;
        covers = new View[0];
        pending = null;
        if (layered && tile != null) tile.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        layered = false;
        if (web != null && web.getParent() instanceof ViewGroup) {
            web.evaluateJavascript("stop()", null);
            web.animate().cancel();
            ((ViewGroup) web.getParent()).removeView(web);
        }
        release();
        if (video != null && video.getParent() instanceof ViewGroup) ((ViewGroup) video.getParent()).removeView(video);
        video = null;
        tile = null;
    }

    boolean on(View t) {
        return tile == t;
    }

    void destroy() {
        stop();
        if (web != null) { web.destroy(); web = null; ready = false; }
    }
}

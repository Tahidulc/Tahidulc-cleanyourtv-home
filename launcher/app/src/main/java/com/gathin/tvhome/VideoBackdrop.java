package com.gathin.tvhome;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;

/**
 * A looping, muted video behind Home: background.mp4 in the launcher's external files directory (where
 * tools/theme.sh puts a theme's files). Plays only while Home is in front; the player is released on pause, so
 * the hardware decoder is free for the app or HDMI input that comes next. No file, no view.
 */
final class VideoBackdrop implements TextureView.SurfaceTextureListener {
    private final File file;
    private final TextureView view;
    private MediaPlayer player;
    private Surface surface;
    private boolean wanted;

    VideoBackdrop(Context context, FrameLayout root) {
        File dir = context.getExternalFilesDir(null);
        file = dir == null ? null : new File(dir, "background.mp4");
        if (file == null || !file.isFile()) {
            view = null;
            return;
        }
        view = new TextureView(context);
        view.setSurfaceTextureListener(this);
        view.setAlpha(0f);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Above the root's black, below the sky, the glow and everything else.
        root.addView(view, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    boolean active() {
        return view != null;
    }

    void start() {
        wanted = true;
        if (view != null && surface != null && player == null) open();
    }

    void stop() {
        wanted = false;
        release();
    }

    private void open() {
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(file.getAbsolutePath());
            mp.setSurface(surface);
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            mp.setOnPreparedListener(p -> {
                if (!wanted || player != p) { p.release(); return; }
                p.start();
                view.animate().alpha(1f).setDuration(600).start();
            });
            mp.setOnErrorListener((p, what, extra) -> { release(); return true; });
            player = mp;
            mp.prepareAsync();
        } catch (Exception e) {
            release();
        }
    }

    private void release() {
        if (view != null) {
            view.animate().cancel();
            view.setAlpha(0f);
        }
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        surface = new Surface(st);
        if (wanted) open();
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        release();
        if (surface != null) surface.release();
        surface = null;
        return true;
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }
}

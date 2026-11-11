package com.gathin.tvhomephone.live;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

/**
 * Live TV's picture (the full screen's and the guide's): a player view with no controls of its own that can be told a
 * shape (16:9 or 4:3) in place of the stream's own, and how to fit it (Aspect).
 */
@OptIn(markerClass = UnstableApi.class)
final class Video extends PlayerView {
    private float forced, natural;

    Video(Context c) {
        this(c, null);
    }

    Video(Context c, AttributeSet a) {
        super(c, a);
        setUseController(false);
        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        setShutterBackgroundColor(0xFF000000);
        setKeepContentOnPlayerReset(true);
    }

    @Override protected void onContentAspectRatioChanged(AspectRatioFrameLayout frame, float ratio) {
        natural = ratio;
        super.onContentAspectRatioChanged(frame, forced > 0 ? forced : ratio);
    }

    private void force(float ratio) {
        forced = ratio;
        AspectRatioFrameLayout f = findViewById(androidx.media3.ui.R.id.exo_content_frame);
        float r = ratio > 0 ? ratio : natural;
        if (f != null && r > 0) f.setAspectRatio(r);
    }

    /** The stream's picture in its own pixels {width, height}: {0, 0} while not known. */
    int[] videoPixels() {
        androidx.media3.common.Player p = getPlayer();
        if (p == null) return new int[]{0, 0};
        androidx.media3.common.VideoSize v = p.getVideoSize();
        return new int[]{v.width, v.height};
    }

    /** The surface the video is drawn on (for its frames: ScoreOverlay's look at the channel's scoreboard). */
    android.view.View surface() {
        return getVideoSurfaceView();
    }

    /**
     * Where the picture is in this view, into out: the content frame's bounds (within the view), or a 16:9 picture fitted
     * in it when the frame fills a view of another shape (no picture's shape known yet).
     */
    void picture(android.graphics.RectF out) {
        int w = getWidth(), h = getHeight();
        out.set(0, 0, w, h);
        android.view.View f = findViewById(androidx.media3.ui.R.id.exo_content_frame);
        if (f != null && f.getWidth() > 0 && f.getHeight() > 0) {
            float x = 0, y = 0;
            for (android.view.View v = f; v != null && v != this; v = (android.view.View) v.getParent()) {
                x += v.getLeft();
                y += v.getTop();
                if (!(v.getParent() instanceof android.view.View)) break;
            }
            out.set(Math.max(0, x), Math.max(0, y), Math.min(w, x + f.getWidth()), Math.min(h, y + f.getHeight()));
        }
        boolean whole = out.width() >= w - 1 && out.height() >= h - 1;
        if (whole && w > 0 && h > 0 && Math.abs((float) w / h - 16f / 9f) > 0.25f) {
            float pw = Math.min(w, h * 16f / 9f), ph = pw * 9f / 16f;
            out.set((w - pw) / 2f, (h - ph) / 2f, (w + pw) / 2f, (h + ph) / 2f);
        }
    }

    /** Fits the picture as the shape says. */
    void apply(Aspect a) {
        switch (a) {
            case FILL: setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM); break;
            case STRETCH: setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL); break;
            default: setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT); break;
        }
        force(a.ratio);
    }
}

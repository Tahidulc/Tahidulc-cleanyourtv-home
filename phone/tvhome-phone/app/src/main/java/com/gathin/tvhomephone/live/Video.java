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

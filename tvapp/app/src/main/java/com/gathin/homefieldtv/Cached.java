package com.gathin.homefieldtv;

import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.os.Build;

/**
 * A part of a view that stays the same from frame to frame (a page's ground and shade, the tab bar's mark, name, clock
 * and glass, a block of words) drawn once and kept: on the TV's hardware canvas it is recorded into a RenderNode only
 * when its key changes, and each frame draws that in one call, its opacity and its rise applied as the node's own
 * properties (the UI thread records nothing again; with a layer, the graphics chip draws it from a picture of itself
 * made once, so a page of five full-screen fills costs one). On any other canvas (previews, tests) it is simply drawn.
 *
 * <pre>
 *   private final Cached ground = new Cached(true);              with the view (true: kept as a picture, a layer)
 *   private final Cached.Painter paintGround = this::paintGround;   kept (no lambda made a frame)
 *   ground.draw(c, w, h, key, paintGround);                      each frame; key: whatever the part depends on
 *   words.draw(c, w, h, key, paintWords, alpha, rise);           faded and risen without recording again; the painter
 *                                                                draws with words.alpha() (255 while recorded)
 *   ground.forget();                                             the view gone or hidden (its layer let go)
 * </pre>
 */
final class Cached {
    interface Painter {
        void paint(Canvas c);
    }

    /** Tests: told of each layer drawn on a plain canvas (to count a blit for one kept, not its whole drawing). */
    interface LayerModel {
        /** A kept layer w x h drawn; changed: recorded again this frame. False: its drawing is skipped (only blitted). */
        boolean layer(Canvas c, int w, int h, boolean changed);
    }

    static LayerModel model;

    private final boolean layer;
    private Object node;
    private long key = Long.MIN_VALUE;
    private Object keyObject;
    private int w, h, paintAlpha = 255;
    /** Tests (model set): when it was last recorded. */
    private long recordedAt = -1;
    /** Tests: how many times it was recorded. */
    int recorded;

    /** layer: kept as a picture of itself (for a large part drawn under moving ones: one blit a frame). */
    Cached(boolean layer) {
        this.layer = layer;
    }

    /** The alpha the painter draws with: 255 while it is recorded (the node is faded), else the opacity asked. */
    int alpha() {
        return paintAlpha;
    }

    /** The part at (0, 0), w x h (screen px); the painter runs only when key or the size changed (or on a plain canvas). */
    void draw(Canvas c, int width, int height, long k, Painter painter) {
        draw(c, width, height, null, k, painter, 1f, 0f);
    }

    /**
     * The part faded to alpha (0 to 1) and moved down by dy (screen px), its key an object (compared as itself) and a
     * number: recorded again only when either changes.
     */
    void draw(Canvas c, int width, int height, Object ko, long k, Painter painter, float alpha, float dy) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        if (a <= 0) return;
        boolean changed = k != key || ko != keyObject || width != w || height != h;
        if (Build.VERSION.SDK_INT < 29 || !c.isHardwareAccelerated() || width <= 0 || height <= 0) {
            if (model != null) {
                long now = android.os.SystemClock.uptimeMillis();
                if (changed) recordedAt = now;
                if (layer && !model.layer(c, width, height, recordedAt == now)) {
                    remember(ko, k, width, height);
                    return;
                }
            }
            remember(ko, k, width, height);
            paintAlpha = a;
            if (dy != 0f) {
                c.save();
                c.translate(0, dy);
            }
            painter.paint(c);
            if (dy != 0f) c.restore();
            paintAlpha = 255;
            return;
        }
        RenderNode n = (RenderNode) node;
        if (n == null) {
            n = new RenderNode("homefield-cached");
            if (layer) n.setUseCompositingLayer(true, null);
            else n.setHasOverlappingRendering(false);
            n.setClipToBounds(false);
            node = n;
        }
        if (changed || !n.hasDisplayList()) {
            n.setPosition(0, 0, width, height);
            RecordingCanvas rc = n.beginRecording(width, height);
            try {
                paintAlpha = 255;
                painter.paint(rc);
            } finally {
                n.endRecording();
            }
            remember(ko, k, width, height);
            recorded++;
            if (model != null) recordedAt = android.os.SystemClock.uptimeMillis();
        }
        n.setAlpha(a / 255f);
        n.setTranslationY(dy);
        c.drawRenderNode(n);
    }

    private void remember(Object ko, long k, int width, int height) {
        keyObject = ko;
        key = k;
        w = width;
        h = height;
    }

    /** Recorded again at the next frame (something it draws changed without its key: a picture came). */
    void stale() {
        key = Long.MIN_VALUE;
        keyObject = null;
    }

    /** Let go (the view hidden or gone): its display list and its layer. */
    void forget() {
        stale();
        if (Build.VERSION.SDK_INT >= 29 && node != null) ((RenderNode) node).discardDisplayList();
    }

    /** Pure: a key from a few values (mixed so a change in any changes it). */
    static long key(long a, long b) {
        return a * 0x9E3779B97F4A7C15L + b;
    }
}

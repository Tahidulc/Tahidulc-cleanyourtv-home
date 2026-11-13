package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.R;

/**
 * The guide's own live picture, with sound: at the top of the tab on a phone held upright (16:9, above the guide), beside
 * the details on a tablet held upright, and in the pane beside the grid on anything on its side. It plays through the
 * one shared player (Playback), so full screen takes the same stream over and gives it back. Tap for its controls (the
 * channel and what's on, ✕, full screen), double tap or pinch between Fit and Fill, swipe up for full screen, left for
 * the next channel and right for the one before. Stopped (the tab left, or the ✕), it shows the channel's logo with a
 * play button: a tap plays it again.
 */
@SuppressLint({"ViewConstructor", "ClickableViewAccessibility"})
final class Inline extends FrameLayout implements Playback.Host {
    interface Listener {
        /** The full screen button, or a swipe up. */
        void onFullScreen();

        /** Swipe left (+1, the next channel) or right (-1, the one before). */
        void onStep(int by);

        /** The ✕. */
        void onClose();

        /** Another channel is in the picture (the full screen changed channel before handing it back). */
        void onShowing(Channel ch);
    }

    private static final long CONTROLS_MS = 3500;

    private final Library lib;
    private final Playback pb;
    private final Aspect.Store store;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Video video;
    private final FrameLayout cover;
    private final ImageView logo;
    private final TextView initials, message, zoom, line;
    private final ImageButton play, full, close;
    private final ProgressBar spinner;
    private final LinearLayout bar;
    private final View touch;
    private final boolean upright;
    private Listener listener;
    private Channel ch;
    private Aspect aspect = Aspect.defaultFor(true);
    private boolean controls = true, quiet;
    private final Runnable hideControls = () -> controls(false);
    private final Runnable hideZoom = () -> zoomLabel(false);

    /** upright: a phone held upright (full width, 16:9, its ✕ puts it away); otherwise a pane (its ✕ stops it). */
    Inline(Context c, Library lib, boolean upright) {
        super(c);
        this.lib = lib;
        this.upright = upright;
        pb = Playback.get(c);
        store = Aspect.prefs(c);
        setBackground(Ui.round(c, 0xFF000000, upright ? 0 : 12));
        setClipToOutline(true);

        video = new Video(c);
        addView(video, new LayoutParams(-1, -1));

        cover = new FrameLayout(c);
        cover.setBackgroundColor(0xFF050B2C);
        initials = Ui.text(c, "", 22, Ui.SOFT, true);
        initials.setGravity(Gravity.CENTER);
        cover.addView(initials, new LayoutParams(-1, -1));
        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(c, upright ? 40 : 28);
        logo.setPadding(p, p, p, p);
        cover.addView(logo, new LayoutParams(-1, -1));
        addView(cover, new LayoutParams(-1, -1));

        touch = new View(c);
        touch.setOnTouchListener(new Gestures(c, gestures(), 32));
        touch.setContentDescription(c.getString(R.string.live_inline_picture));
        touch.setFocusable(true);
        touch.setClickable(true);
        touch.setOnClickListener(v -> tapped());
        addView(touch, new LayoutParams(-1, -1));

        spinner = new ProgressBar(c);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.TEXT));
        spinner.setVisibility(GONE);
        addView(spinner, new LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36), Gravity.CENTER));

        message = Ui.text(c, "", 13, Ui.TEXT, true);
        message.setGravity(Gravity.CENTER);
        message.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
        message.setBackground(Ui.round(c, 0xCC0B0C21, 12));
        message.setVisibility(GONE);
        LayoutParams mp = new LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        mp.bottomMargin = Ui.dp(c, 14);
        mp.leftMargin = mp.rightMargin = Ui.dp(c, 16);
        addView(message, mp);

        zoom = Ui.text(c, "", 15, Ui.TEXT, true);
        zoom.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        zoom.setBackground(Ui.round(c, 0xCC0A1A4C, 18));
        zoom.setVisibility(GONE);
        addView(zoom, new LayoutParams(-2, -2, Gravity.CENTER));

        play = Ui.icon(c, R.drawable.live_ic_play, R.string.live_play);
        play.setBackground(Ui.touch(c, false, 0xCC0B0C21, 28));
        play.setOnClickListener(v -> resume());
        addView(play, new LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56), Gravity.CENTER));

        // The channel and what's on, with ✕, along the top.
        bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(c, 12), 0, 0, Ui.dp(c, 10));
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xCC000000, 0x00000000}));
        line = Ui.text(c, "", 13, Ui.TEXT, true);
        line.setSingleLine(true);
        line.setEllipsize(TextUtils.TruncateAt.END);
        line.setShadowLayer(4, 0, 1, 0xFF000000);
        bar.addView(line, new LinearLayout.LayoutParams(0, -2, 1f));
        close = Ui.icon(c, R.drawable.live_ic_close, upright ? R.string.live_close_player : R.string.live_stop);
        close.setOnClickListener(v -> {
            if (listener != null) listener.onClose();
        });
        bar.addView(close, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        addView(bar, new LayoutParams(-1, -2, Gravity.TOP));

        full = Ui.icon(c, R.drawable.live_ic_fullscreen, R.string.live_fullscreen);
        full.setBackground(Ui.touch(c, false, 0x990B0C21, 24));
        full.setOnClickListener(v -> {
            if (listener != null && ch != null) listener.onFullScreen();
        });
        LayoutParams fp = new LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44), Gravity.BOTTOM | Gravity.END);
        fp.rightMargin = fp.bottomMargin = Ui.dp(c, 8);
        addView(full, fp);
        render();
    }

    void setListener(Listener l) { listener = l; }

    /** What it shows: playing, or stopped (paused until tapped); null for nothing yet. */
    Channel channel() { return ch; }

    /** This channel's stream is open here. */
    boolean playing(Channel c) {
        return pb.isFront(this) && pb.live(c);
    }

    /** Something is open here. */
    boolean live() {
        return pb.isFront(this) && ch != null && pb.live(ch);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (upright) {
            int w = MeasureSpec.getSize(widthSpec);
            heightSpec = MeasureSpec.makeMeasureSpec(w * 9 / 16, MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    // ---- what the guide asks --------------------------------------------------------------------------------

    /** The tab is on screen: the picture is ours again (unless the full screen has it, then it waits). */
    void start() {
        pb.offer(this);
        if (pb.isFront(this) && pb.live()) setChannel(pb.channel());
        render();
    }

    /** The tab goes: keep (turning round, or the full screen taking over) leaves the stream playing for what's next. */
    void leave(boolean keep) {
        pb.leave(this, keep);
        render();
    }

    /** Plays a channel here, with sound. */
    void play(Channel c) {
        if (c == null) return;
        // Quiet while it changes over: what was playing before isn't news.
        quiet = true;
        pb.take(this);
        setChannel(c);
        pb.play(c);
        quiet = false;
        controls(true);
        render();
    }

    /** Shows a channel stopped (its logo and play), when nothing is open here. */
    void show(Channel c) {
        if (c == null || live()) return;
        setChannel(c);
        render();
    }

    /** Stops the stream (the ✕). */
    void halt() {
        if (pb.isFront(this)) pb.stop();
        controls(true);
        render();
    }

    /** As time moves on: what's on now in the top line. */
    void refresh() {
        if (ch != null) line.setText(describe(ch));
    }

    /** The playlist changed: the same channel, as it is now. */
    void rebind() {
        if (ch == null) return;
        Channel now = lib.byKey(ch.key);
        if (now != null && now != ch) {
            ch = now;
            refresh();
        }
    }

    private void resume() {
        if (ch == null) return;
        if (pb.isFront(this) && pb.paused() && pb.live(ch)) pb.resume();
        else if (pb.isFront(this) && pb.state() == Playback.FAILED && sameAsPlayer()) pb.restart();
        else play(ch);
        render();
    }

    private boolean sameAsPlayer() {
        Channel p = pb.channel();
        return p != null && ch != null && p.key.equals(ch.key);
    }

    private void tapped() {
        if (ch == null) return;
        if (!live() || pb.paused()) {
            resume();
            return;
        }
        controls(!controls);
    }

    private void setChannel(Channel c) {
        boolean changed = ch == null || !ch.key.equals(c.key);
        ch = c;
        if (!changed) return;
        aspect = Aspect.get(store, c.key, true);
        video.apply(aspect);
        initials.setText(c.logo.isEmpty() ? Ui.initials(c.name) : "");
        if (c.logo.isEmpty()) Glide.with(logo).clear(logo);
        else LogoTiles.loadReady(logo, c.logo);
        line.setText(describe(c));
        play.setContentDescription(getContext().getString(R.string.live_play_named, c.name));
    }

    private String describe(Channel c) {
        int n = lib.number(c);
        Guide.Schedule s = lib.schedule(c.key);
        int at = s == null ? -1 : s.at(System.currentTimeMillis());
        String t = at >= 0 && !s.title[at].isEmpty() ? "  ·  " + s.title[at] : "";
        return (n > 0 ? n + "  " : "") + c.name + t;
    }

    // ---- the shared player's host ---------------------------------------------------------------------------

    @Override public PlayerView view() { return video; }

    @Override public boolean full() { return false; }

    @Override public void onState(int state) {
        if (!quiet && pb.isFront(this) && pb.live()) {
            Channel now = pb.channel();
            if (ch == null || !now.key.equals(ch.key)) {
                setChannel(now);
                if (listener != null) listener.onShowing(now);
            }
        }
        render();
    }

    @Override public void onFirstFrame() {
        render();
    }

    private void render() {
        Context c = getContext();
        boolean front = pb.isFront(this), mine = front && sameAsPlayer();
        boolean open = mine && pb.live();
        int s = mine ? pb.state() : Playback.IDLE;
        boolean paused = open && pb.paused();
        spinner.setVisibility(open && !paused && (s == Playback.LOADING || s == Playback.RECONNECTING) ? VISIBLE : GONE);
        String m = "";
        if (s == Playback.RECONNECTING) m = c.getString(R.string.live_reconnecting);
        else if (s == Playback.FAILED) m = c.getString(R.string.live_inline_failed);
        else if (!front && pb.fullScreen()) m = c.getString(R.string.live_playing_full);
        message.setText(m);
        message.setVisibility(m.isEmpty() ? GONE : VISIBLE);
        play.setVisibility(ch != null && (!open || paused) && s != Playback.FAILED ? VISIBLE : GONE);
        cover.setVisibility(open && pb.firstFrame() ? GONE : VISIBLE);
        full.setVisibility(ch != null && controls ? VISIBLE : GONE);
        bar.setVisibility(ch != null && controls ? VISIBLE : GONE);
        // The pane's ✕ stops what plays; upright, it puts the picture away whatever it's doing.
        close.setVisibility(upright || open ? VISIBLE : GONE);
        setKeepScreenOn(open && !paused);
        h.removeCallbacks(hideControls);
        if (controls && open && !paused) h.postDelayed(hideControls, CONTROLS_MS);
    }

    private void controls(boolean on) {
        controls = on;
        render();
    }

    private void zoomLabel(boolean on) {
        zoom.setVisibility(on ? VISIBLE : GONE);
    }

    private void setAspect(Aspect a) {
        if (ch == null) return;
        aspect = a;
        Aspect.set(store, ch.key, true, a);
        video.apply(a);
        zoom.setText(a == Aspect.FILL ? R.string.live_fill : R.string.live_fit);
        zoomLabel(true);
        h.removeCallbacks(hideZoom);
        h.postDelayed(hideZoom, 900);
    }

    private Gestures.Callback gestures() {
        return new Gestures.Callback() {
            @Override public void onTap() { tapped(); }

            @Override public void onDoubleTap() {
                if (live()) setAspect(aspect.toggled());
            }

            @Override public void onPinch(float scale) {
                if (!live()) return;
                float s = Math.max(0.85f, Math.min(1.2f, scale));
                video.setScaleX(s);
                video.setScaleY(s);
            }

            @Override public void onPinchEnd(float scale) {
                video.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                if (!live()) return;
                Aspect a = aspect.pinched(scale);
                if (a != aspect) setAspect(a);
            }

            @Override public void onSwipe(int d) {
                if (listener == null || ch == null) return;
                if (d == Gestures.UP) listener.onFullScreen();
                else if (d == Gestures.LEFT) listener.onStep(1);
                else if (d == Gestures.RIGHT) listener.onStep(-1);
            }
        };
    }

    @Override protected void onDetachedFromWindow() {
        h.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }
}

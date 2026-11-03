package com.gathin.tvhome;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A highlight played large on Home (OK on a highlight on the Sports page): the video grows out of its card into a big
 * frame (1280 x 720) with Home dimmed behind it, and plays there with sound in Home's own YouTube player (the trailers'
 * one, TilePlayer: a second YouTube page never finished loading on the TCL). Along the top, the event, the video's
 * title, its channel and when it went up; down the right, the event's details as the video plays: a football match's
 * score (each crest beside its own club, FT after the fixture), scorers and red cards, and its stats from ESPN; the
 * Formula 1 race's top ten; the fight's result; the tennis match's sets; an NFL or NBA game's score by quarter and its
 * leaders; for the rest, what the channel says about the video. Along the foot, the sport's other highlights: Down to
 * them, Left and Right along, OK plays one (a crossfade). OK on the video pauses and plays; Left and Right go back or on
 * ten seconds. Back shrinks it into its card again. When YouTube will not play it here, "Open in YouTube".
 */
final class HighlightPlayer implements TilePlayer.Film {
    /** The Sports page under it. */
    interface Host {
        /** The card of the row's i-th highlight (null when there is none). */
        View card(int i);
        /** The page's focus on the row's i-th highlight (when another one was played here). */
        void focus(int i);
        void openInYouTube(String id);
    }

    /** Where things go, in Home's 1920 x 1080 design. */
    private static final int FX = 56, FY = 128, FW = 1280, FH = 720, PX = 1368, PW = 496, NY = 872, CW = 240, CH = 135, CG = 20;
    private static final int Z_VIDEO = 0, Z_NEXT = 1;
    private static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final long FIRST_REVEAL_MS = 2_600L;

    private final HomeV2 h;
    private final Host host;
    private final List<SportsHighlights.Clip> clips;
    private int at, nextIdx, zone = Z_VIDEO;
    private final int opened;
    private boolean showing, closing, revealed, paused, failed, everPlayed;
    private volatile int generation;

    private FrameLayout overlay, frame, cover, upRow;
    private View dim, glow, progress, progressTrack;
    private ImageView coverPic;
    private TextView coverWords, timeText, seekBadge, titleView, subView, chipView, nextLabel, openButton;
    private LinearLayout topStrip, panel, panelBody;
    private android.widget.ProgressBar spinner;
    private final Runnable reveal = this::reveal;
    /** Nothing from YouTube for a long while (its page never loaded, the network): offered in YouTube instead. */
    private final Runnable stalled = () -> { if (showing && !closing && !everPlayed && !failed) failed(-2); };
    /** The events' lookups, one at a time, off Home's own readers (a football one can ask ESPN a few dozen times). */
    private static final java.util.concurrent.ExecutorService LOOKUP = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Runnable hideBadge = () -> { if (seekBadge != null) seekBadge.animate().alpha(0f).setDuration(250).start(); };
    private Typeface outfitBold, outfitHeavy;

    HighlightPlayer(HomeV2 h, Host host, List<SportsHighlights.Clip> clips, int at) {
        this.h = h;
        this.host = host;
        this.clips = new ArrayList<>(clips);
        this.at = Math.max(0, Math.min(at, clips.size() - 1));
        this.opened = this.at;
    }

    boolean isShowing() {
        return showing;
    }

    private int px(float v) {
        return h.px(v);
    }

    private SportsHighlights.Clip clip() {
        return clips.get(at);
    }

    // ---- opening and closing ---------------------------------------------------------------------------------------

    /** Grows out of the card (its place and size on screen) into the big frame; the rest comes in around it. */
    void open(View card) {
        if (clips.isEmpty()) return;
        showing = true;
        outfitBold = h.a.getResources().getFont(R.font.outfit_bold);
        outfitHeavy = h.a.getResources().getFont(R.font.outfit_extrabold);
        build();
        float[] from = rectOf(card);
        frame.setPivotX(0f);
        frame.setPivotY(0f);
        if (from != null) {
            frame.setScaleX(from[2] / px(FW));
            frame.setScaleY(from[3] / px(FH));
            frame.setTranslationX(from[0] - px(FX));
            frame.setTranslationY(from[1] - px(FY));
        } else {
            frame.setScaleX(0.9f);
            frame.setScaleY(0.9f);
            frame.setAlpha(0f);
        }
        frame.animate().scaleX(1f).scaleY(1f).translationX(0).translationY(0).alpha(1f).setDuration(480).setInterpolator(EASE).start();
        dim.setAlpha(0f);
        dim.animate().alpha(1f).setDuration(360).start();
        glow.setAlpha(0f);
        glow.animate().alpha(1f).setStartDelay(200).setDuration(700).start();
        enter(topStrip, 0, -px(18), 220);
        enter(panel, px(40), 0, 280);
        enter(nextLabel, 0, px(24), 340);
        enter(upRow, 0, px(24), 360);
        play(false);
    }

    private static void enter(View v, float dx, float dy, long delay) {
        v.setAlpha(0f);
        v.setTranslationX(dx);
        v.setTranslationY(dy);
        v.animate().alpha(1f).translationX(0).translationY(0).setStartDelay(delay).setDuration(420).setInterpolator(EASE).start();
    }

    /** Back: the sound stops at once, the frame shrinks into its card (the one now playing), Home comes back. */
    void close() {
        if (!showing || closing) return;
        closing = true;
        h.main.removeCallbacks(reveal);
        h.players.filmOff();
        cover.animate().cancel();
        cover.setAlpha(1f);
        if (at != opened) host.focus(at);
        for (View v : new View[]{topStrip, panel, nextLabel, upRow, glow}) v.animate().cancel();
        for (View v : new View[]{topStrip, panel, nextLabel, upRow, glow}) v.animate().alpha(0f).setStartDelay(0).setDuration(160).start();
        float[] to = rectOf(host.card(at));
        if (to == null) to = rectOf(host.card(opened));
        if (to != null) {
            frame.animate().scaleX(to[2] / px(FW)).scaleY(to[3] / px(FH)).translationX(to[0] - px(FX)).translationY(to[1] - px(FY))
                    .setStartDelay(60).setDuration(400).setInterpolator(EASE).withEndAction(this::removeNow).start();
        } else {
            frame.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).setDuration(260).withEndAction(this::removeNow).start();
        }
        dim.animate().alpha(0f).setStartDelay(120).setDuration(380).start();
    }

    /** Gone at once (the page closing, Home going into the background). */
    void removeNow() {
        if (overlay == null) return;
        h.main.removeCallbacks(reveal);
        h.main.removeCallbacks(hideBadge);
        h.main.removeCallbacks(stalled);
        generation++;
        if (!closing) h.players.filmOff();
        showing = false;
        FrameLayout o = overlay;
        overlay = null;
        if (o.getParent() instanceof ViewGroup) ((ViewGroup) o.getParent()).removeView(o);
    }

    /** A view's place and size on screen, relative to Home's root: {x, y, w, h}; null when it is not laid out. */
    private float[] rectOf(View v) {
        if (v == null || !v.isAttachedToWindow() || v.getWidth() == 0) return null;
        int[] a = new int[2], r = new int[2];
        v.getLocationOnScreen(a);
        h.root.getLocationOnScreen(r);
        float sx = v.getScaleX(), sy = v.getScaleY();
        for (View p = (View) v.getParent(); p != null && p != h.root && p.getParent() instanceof View; p = (View) p.getParent()) {
            sx *= p.getScaleX();
            sy *= p.getScaleY();
        }
        return new float[]{a[0] - r[0], a[1] - r[1], v.getWidth() * sx, v.getHeight() * sy};
    }

    // ---- building ----------------------------------------------------------------------------------------------------

    private void place(FrameLayout parent, View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? w : px(w), hgt < 0 ? hgt : px(hgt));
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        parent.addView(v, lp);
    }

    private void build() {
        overlay = new FrameLayout(h.a);
        overlay.setClipChildren(false);
        h.root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setElevation(px(40));
        dim = new View(h.a);
        dim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF205071C, 0xF00A0E2E, 0xF705071C}));
        overlay.addView(dim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // A soft light behind the video, as if it lit the room.
        glow = new View(h.a);
        GradientDrawable gd = new GradientDrawable();
        gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gd.setGradientRadius(px(820));
        gd.setColors(new int[]{0x553A4CC0, 0x221B2591, 0x00000000});
        glow.setBackground(gd);
        place(overlay, glow, FX - 300, FY - 260, FW + 600, FH + 520);

        // The top: what it is (the competition, else the sport), the video's title, its channel and when.
        topStrip = new LinearLayout(h.a);
        topStrip.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line = new LinearLayout(h.a);
        line.setGravity(Gravity.CENTER_VERTICAL);
        chipView = h.text(15, h.semi, Color.WHITE);
        chipView.setLetterSpacing(0.1f);
        chipView.setSingleLine(true);
        chipView.setPadding(px(12), px(5), px(12), px(5));
        chipView.setBackground(h.fill(9, 0xFF3A4CC0));
        line.addView(chipView);
        titleView = h.text(30, outfitBold, Color.WHITE);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tl.leftMargin = px(16);
        line.addView(titleView, tl);
        topStrip.addView(line);
        LinearLayout sub = new LinearLayout(h.a);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        subView = h.text(18, h.regular, 0xB8FFFFFF);
        subView.setSingleLine(true);
        subView.setEllipsize(TextUtils.TruncateAt.END);
        sub.addView(subView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView keys = h.text(15, h.regular, 0x8CFFFFFF);
        keys.setText("OK  pause  ·  ◀ ▶  10 s  ·  ▼  up next  ·  BACK  close");
        keys.setSingleLine(true);
        sub.addView(keys);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = px(10);
        topStrip.addView(sub, sl);
        place(overlay, topStrip, FX, 34, 1920 - 2 * FX, 84);

        // The video's frame: the player goes in under the cover (the video's picture), which lifts once it plays.
        frame = new FrameLayout(h.a);
        frame.setOutlineProvider(h.round(22));
        frame.setClipToOutline(true);
        frame.setBackgroundColor(Color.BLACK);
        cover = new FrameLayout(h.a);
        coverPic = new ImageView(h.a);
        coverPic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.addView(coverPic, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View shade = new View(h.a);
        shade.setBackgroundColor(0x66000000);
        cover.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        spinner = new android.widget.ProgressBar(h.a);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        cover.addView(spinner, new FrameLayout.LayoutParams(px(64), px(64), Gravity.CENTER));
        coverWords = h.text(24, h.semi, Color.WHITE);
        coverWords.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams cwl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        cwl.topMargin = px(84);
        cover.addView(coverWords, cwl);
        // Shown when YouTube will not play it here.
        openButton = h.text(22, h.semi, 0xFF1A1D24);
        openButton.setText("Open in YouTube");
        openButton.setGravity(Gravity.CENTER);
        openButton.setPadding(px(30), 0, px(30), 0);
        openButton.setBackground(h.fill(30, 0xF2FFFFFF));
        android.graphics.drawable.Drawable yt = h.a.getDrawable(R.drawable.ic_v2_youtube);
        if (yt != null) { yt = yt.mutate(); yt.setBounds(0, 0, px(30), px(30)); openButton.setCompoundDrawables(yt, null, null, null); openButton.setCompoundDrawablePadding(px(12)); }
        FrameLayout.LayoutParams obl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(60), Gravity.CENTER);
        obl.topMargin = px(170);
        openButton.setVisibility(View.GONE);
        cover.addView(openButton, obl);
        frame.addView(cover, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // How far through: a fine line along the foot, the time at its right.
        progressTrack = new View(h.a);
        progressTrack.setBackgroundColor(0x40FFFFFF);
        FrameLayout.LayoutParams ptl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(5), Gravity.BOTTOM);
        frame.addView(progressTrack, ptl);
        progress = new View(h.a);
        progress.setBackgroundColor(Color.WHITE);
        progress.setPivotX(0f);
        progress.setScaleX(0f);
        frame.addView(progress, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(5), Gravity.BOTTOM));
        timeText = h.text(16, h.semi, Color.WHITE);
        timeText.setPadding(px(10), px(4), px(10), px(4));
        timeText.setBackground(h.fill(8, 0x99000000));
        timeText.setAlpha(0f);
        FrameLayout.LayoutParams ttl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        ttl.rightMargin = px(16);
        ttl.bottomMargin = px(18);
        frame.addView(timeText, ttl);
        seekBadge = h.text(26, outfitBold, Color.WHITE);
        seekBadge.setPadding(px(18), px(8), px(18), px(8));
        seekBadge.setBackground(h.fill(14, 0xA6000000));
        seekBadge.setAlpha(0f);
        frame.addView(seekBadge, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        place(overlay, frame, FX, FY, FW, FH);

        // The event's details down the right.
        panel = new LinearLayout(h.a);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x26FFFFFF, 0x0FFFFFFF});
        pg.setCornerRadius(px(24));
        pg.setStroke(Math.max(1, px(1.5f)), 0x2EFFFFFF);
        panel.setBackground(pg);
        panel.setPadding(px(26), px(24), px(26), px(22));
        panelBody = new LinearLayout(h.a);
        panelBody.setOrientation(LinearLayout.VERTICAL);
        panel.addView(panelBody, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        place(overlay, panel, PX, FY, PW, FH);

        // The sport's other highlights along the foot.
        nextLabel = h.text(16, h.semi, 0xBFFFFFFF);
        nextLabel.setLetterSpacing(0.11f);
        place(overlay, nextLabel, FX, NY, 1200, 24);
        FrameLayout rowBox = new FrameLayout(h.a);
        rowBox.setClipChildren(true);
        rowBox.setOutlineProvider(android.view.ViewOutlineProvider.BOUNDS);
        rowBox.setClipToOutline(true);
        upRow = new FrameLayout(h.a);
        upRow.setClipChildren(false);
        FrameLayout.LayoutParams url = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        rowBox.addView(upRow, url);
        place(overlay, rowBox, FX - 16, NY + 22, 1920 - 2 * FX + 32, CH + 30);
        for (int i = 0; i < clips.size(); i++) upRow.addView(nextCard(clips.get(i)), cardParams(i));
        showTexts();
        paintFocus();
    }

    private FrameLayout.LayoutParams cardParams(int i) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(CW), px(CH));
        lp.leftMargin = px(16 + i * (CW + CG));
        lp.topMargin = px(14);
        return lp;
    }

    private View nextCard(SportsHighlights.Clip c) {
        FrameLayout f = new FrameLayout(h.a);
        f.setOutlineProvider(h.round(12));
        f.setClipToOutline(true);
        f.setBackground(h.fill(12, 0xFF15182E));
        ImageView pic = new ImageView(h.a);
        pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        f.addView(pic, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        h.image(pic, "https://i.ytimg.com/vi/" + c.id + "/hqdefault.jpg#yt", px(CW));
        View shade = new View(h.a);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xE6000000}));
        f.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView n = h.text(14, h.semi, Color.WHITE);
        n.setText(c.title);
        n.setMaxLines(2);
        n.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        nl.leftMargin = px(10);
        nl.rightMargin = px(10);
        nl.bottomMargin = px(8);
        f.addView(n, nl);
        TextView now = h.text(12, h.semi, Color.WHITE);
        now.setTag("now");
        now.setText("PLAYING");
        now.setLetterSpacing(0.1f);
        now.setPadding(px(8), px(3), px(8), px(3));
        now.setBackground(h.fill(6, 0xFFE8112D));
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        wl.leftMargin = px(8);
        wl.topMargin = px(8);
        f.addView(now, wl);
        f.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        return f;
    }

    // ---- the video ---------------------------------------------------------------------------------------------------

    /** The current clip into the frame: its picture over the player until it really plays. */
    private void play(boolean crossfade) {
        SportsHighlights.Clip c = clip();
        generation++;
        int gen = generation;
        revealed = false;
        paused = false;
        failed = false;
        everPlayed = false;
        h.main.removeCallbacks(reveal);
        h.main.removeCallbacks(stalled);
        h.main.postDelayed(stalled, 20_000L);
        openButton.setVisibility(View.GONE);
        coverWords.setText("");
        spinner.setVisibility(View.VISIBLE);
        progress.setScaleX(0f);
        timeText.setAlpha(0f);
        Runnable start = () -> {
            if (gen != generation || !showing) return;
            h.image(coverPic, "https://i.ytimg.com/vi/" + c.id + "/hqdefault.jpg#yt", px(FW));
            cover.animate().cancel();
            cover.setAlpha(1f);
            h.players.film(frame, c.id, this);
        };
        if (crossfade && cover.getAlpha() < 1f) {
            cover.animate().cancel();
            // The new picture fades over the old video, then the old one stops under it.
            h.image(coverPic, "https://i.ytimg.com/vi/" + c.id + "/hqdefault.jpg#yt", px(FW));
            cover.animate().alpha(1f).setDuration(260).withEndAction(start).start();
        } else start.run();
        loadInfo(c, gen);
    }

    private void reveal() {
        if (!showing || closing || paused || failed) return;
        revealed = true;
        spinner.setVisibility(View.GONE);
        cover.animate().cancel();
        cover.animate().alpha(0f).setDuration(450).start();
        timeText.animate().alpha(1f).setDuration(300).start();
    }

    @Override public void state(int s) {
        if (!showing || closing) return;
        // While it plays the screen stays on (the TV's own screensaver would come over it); not while paused.
        if (overlay != null) overlay.setKeepScreenOn(s == 1 || s == 3);
        if (s == 1) {
            h.main.removeCallbacks(stalled);
            boolean first = !everPlayed;
            everPlayed = true;
            paused = false;
            coverWords.setText("");
            h.main.removeCallbacks(reveal);
            // YouTube writes the video's title over its first seconds: the picture stays over it until then.
            h.main.postDelayed(reveal, first ? FIRST_REVEAL_MS : 450L);
        } else if (s == 2) {
            // Paused: its picture back over it (YouTube's own pause screen never seen), with the word.
            paused = true;
            h.main.removeCallbacks(reveal);
            revealed = false;
            spinner.setVisibility(View.GONE);
            coverWords.setText("❚❚  Paused");
            cover.animate().cancel();
            cover.animate().alpha(1f).setDuration(180).start();
        } else if (s == 0) {
            // Finished: on to the next one along, if there is one.
            if (at + 1 < clips.size()) { at++; nextIdx = at; play(true); showTexts(); paintFocus(); }
            else {
                coverWords.setText("Finished  ·  OK to watch again");
                cover.animate().alpha(1f).setDuration(220).start();
            }
        }
    }

    @Override public void time(double now, double length) {
        if (!showing || length <= 0) return;
        progress.setScaleX((float) Math.max(0, Math.min(1, now / length)));
        timeText.setText(clock(now) + "  /  " + clock(length));
    }

    @Override public void failed(int code) {
        if (!showing || closing) return;
        failed = true;
        h.main.removeCallbacks(reveal);
        h.main.removeCallbacks(stalled);
        spinner.setVisibility(View.GONE);
        cover.animate().cancel();
        cover.setAlpha(1f);
        coverWords.setText(code == 101 || code == 150 ? "This video can only be played in YouTube" : "This video won't play here");
        openButton.setVisibility(View.VISIBLE);
        paintFocus();
    }

    @Override public void gone() {
        // Home went into the background (or another player took the page): closed, nothing left behind.
        if (showing) { closing = true; removeNow(); }
    }

    private static String clock(double secs) {
        long s = Math.max(0, Math.round(secs));
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private void badge(String words) {
        seekBadge.setText(words);
        seekBadge.animate().cancel();
        seekBadge.setAlpha(1f);
        h.main.removeCallbacks(hideBadge);
        h.main.postDelayed(hideBadge, 700);
    }

    // ---- keys ----------------------------------------------------------------------------------------------------------

    /** Every key while it is up is its own. */
    boolean key(KeyEvent e) {
        if (!showing || closing) return true;
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP) okPressed();
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (code) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (!failed) h.players.toggle();
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (!failed) { h.players.seek(30); badge("+30 s"); }
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (!failed) { h.players.seek(-30); badge("−30 s"); }
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                if (at + 1 < clips.size()) switchTo(at + 1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (zone == Z_VIDEO && clips.size() > 1) { zone = Z_NEXT; nextIdx = Math.min(clips.size() - 1, at + 1 < clips.size() ? at + 1 : at); }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (zone == Z_NEXT) zone = Z_VIDEO;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (zone == Z_NEXT) { if (nextIdx > 0) nextIdx--; }
                else if (!failed && everPlayed) { h.players.seek(-10); badge("−10 s"); }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (zone == Z_NEXT) { if (nextIdx < clips.size() - 1) nextIdx++; }
                else if (!failed && everPlayed) { h.players.seek(10); badge("+10 s"); }
                break;
            default:
                return true;
        }
        paintFocus();
        return true;
    }

    private void okPressed() {
        if (zone == Z_NEXT) {
            if (nextIdx != at) switchTo(nextIdx);
            zone = Z_VIDEO;
            paintFocus();
            return;
        }
        if (failed) {
            String id = clip().id;
            close();
            host.openInYouTube(id);
            return;
        }
        if (!everPlayed && coverWords.getText().length() == 0) return;   // still loading
        if (coverWords.getText().toString().startsWith("Finished")) { play(false); return; }
        h.players.toggle();
    }

    private void switchTo(int i) {
        if (i < 0 || i >= clips.size()) return;
        at = i;
        nextIdx = i;
        play(true);
        // The words change with a crossfade.
        for (View v : new View[]{topStrip, panelBody}) v.animate().alpha(0f).setDuration(140).withEndAction(() -> {
            showTexts();
            v.animate().alpha(1f).setDuration(260).start();
        }).start();
    }

    private void paintFocus() {
        if (overlay == null) return;
        frame.setForeground(zone == Z_VIDEO && !failed ? h.ring(22) : null);
        openButton.setBackground(h.fill(30, zone == Z_VIDEO ? 0xFFFFFFFF : 0xB3FFFFFF));
        openButton.setTextColor(0xFF1A1D24);
        for (int i = 0; i < upRow.getChildCount(); i++) {
            View c = upRow.getChildAt(i);
            boolean on = zone == Z_NEXT && i == nextIdx;
            c.animate().scaleX(on ? 1.08f : 1f).scaleY(on ? 1.08f : 1f).setDuration(160).setInterpolator(HomeV2.SKY).start();
            ((FrameLayout) c).setForeground(on ? h.ring(12) : null);
            c.setElevation(on ? px(8) : 0);
            View now = c.findViewWithTag("now");
            if (now != null) now.setVisibility(i == at ? View.VISIBLE : View.GONE);
            c.setAlpha(i == at && !on ? 0.55f : 1f);
        }
        // The row slides so the focused card (or the one playing) is in sight: six fit across.
        int focus = zone == Z_NEXT ? nextIdx : at;
        int first = Math.max(0, Math.min(focus - 5, clips.size() - 7));
        upRow.animate().translationX(-first * px(CW + CG)).setDuration(280).setInterpolator(HomeV2.SKY).start();
    }

    // ---- the words ----------------------------------------------------------------------------------------------------

    private Info info;

    /** The top's words for the clip now playing (the event's name once it is known). */
    private void showTexts() {
        SportsHighlights.Clip c = clip();
        String sport = c.sport == null ? c.mark : c.sport;
        chipView.setText(info != null && info.clip == c && info.competition != null ? info.competition.toUpperCase(Locale.UK) : sport);
        titleView.setText(c.title);
        String when = c.published > 0 ? DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK).withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(c.published)) : "";
        String age = SportsHighlights.age(c.published);
        StringBuilder sb = new StringBuilder(c.channel == null ? "" : c.channel);
        if (!age.isEmpty()) sb.append("  ·  ").append(age);
        if (!when.isEmpty()) sb.append("  ·  ").append(when);
        subView.setText(sb);
        nextLabel.setText("UP NEXT  ·  " + sport);
        if (info == null || info.clip != c) placeholders();
    }

    /** Grey lines where the details will be, until they come. */
    private void placeholders() {
        panelBody.removeAllViews();
        panelBody.addView(heading("FINDING THE EVENT…"));
        int[] widths = {380, 300, 420, 260, 400, 340, 220};
        for (int w : widths) {
            View bar = new View(h.a);
            bar.setBackground(h.fill(8, 0x1AFFFFFF));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(w), px(22));
            lp.topMargin = px(22);
            panelBody.addView(bar, lp);
            bar.setAlpha(0.5f);
            android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofFloat(bar, View.ALPHA, 0.35f, 1f);
            pulse.setDuration(900);
            pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            pulse.setRepeatCount(6);
            pulse.start();
        }
    }

    private TextView heading(String s) {
        TextView t = h.text(15, h.semi, 0xB3FFFFFF);
        t.setText(s);
        t.setLetterSpacing(0.12f);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private void loadInfo(SportsHighlights.Clip c, int gen) {
        String[] leagues = h.a.getResources().getStringArray(R.array.score_leagues);
        LOOKUP.execute(() -> {
            if (gen != generation) return;   // another one was played since
            Info found;
            try {
                found = find(c, leagues);
            } catch (Exception e) {
                found = null;
            }
            Info got = found != null ? found : new Info();
            got.clip = c;
            h.main.post(() -> {
                if (!showing || gen != generation) return;
                info = got;
                showTexts();
                panelBody.setAlpha(0f);
                fillPanel(got);
                panelBody.animate().alpha(1f).setDuration(320).start();
            });
        });
    }

    // ---- what the panel shows ---------------------------------------------------------------------------------------

    /** What was found about the clip's event; kind NONE: the video's own details. */
    static final class Info {
        static final int NONE = 0, FOOTBALL = 1, F1 = 2, FIGHT = 3, TENNIS = 4, GAME = 5;
        int kind = NONE;
        SportsHighlights.Clip clip;
        String competition, status, method;
        /** Football and NFL/NBA: {name, abbreviation, logo, score, colour}. */
        String[] home, away;
        /** Football: {home|away, goal|pen|og|red, who, minute}. */
        final List<String[]> events = new ArrayList<>();
        /** {label, home, away, home share 0..1 (or "")}. */
        final List<String[]> stats = new ArrayList<>();
        /** NFL/NBA: the periods' scores, home and away. */
        final List<String> homeLines = new ArrayList<>(), awayLines = new ArrayList<>();
        /** NFL/NBA leaders, fight card or extra lines: {who, what}. */
        final List<String[]> lines = new ArrayList<>();
        F1Results.Board board;
        /** A fight or a tennis match: {name, record or seed, "1" when the winner}, and the sets. */
        String[] a, b;
        final List<String[]> sets = new ArrayList<>();
    }

    private void fillPanel(Info in) {
        panelBody.removeAllViews();
        switch (in.kind) {
            case Info.FOOTBALL: football(in); break;
            case Info.F1: f1(in); break;
            case Info.FIGHT: fight(in); break;
            case Info.TENNIS: tennis(in); break;
            case Info.GAME: game(in); break;
            default: about(in.clip); break;
        }
    }

    /** "[crest] NEW  0 – 1  EVE [crest]  FT": each crest beside its own club, the state after the whole fixture. */
    private View scoreLine(String[] home, String[] away, String status, boolean big) {
        LinearLayout row = new LinearLayout(h.a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int crest = px(big ? 52 : 40);
        ImageView hc = new ImageView(h.a);
        hc.setScaleType(ImageView.ScaleType.FIT_CENTER);
        h.image(hc, home[2], crest);
        row.addView(hc, new LinearLayout.LayoutParams(crest, crest));
        TextView hn = h.text(big ? 24 : 20, outfitBold, Color.WHITE);
        hn.setText(home[1]);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.leftMargin = px(10);
        row.addView(hn, hl);
        TextView sc = h.text(big ? 44 : 34, outfitHeavy, Color.WHITE);
        sc.setText(home[3] + " – " + away[3]);
        sc.setGravity(Gravity.CENTER);
        row.addView(sc, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView an = h.text(big ? 24 : 20, outfitBold, Color.WHITE);
        an.setText(away[1]);
        row.addView(an);
        ImageView ac = new ImageView(h.a);
        ac.setScaleType(ImageView.ScaleType.FIT_CENTER);
        h.image(ac, away[2], crest);
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(crest, crest);
        al.leftMargin = px(10);
        row.addView(ac, al);
        if (status != null && !status.isEmpty()) {
            TextView st = h.text(18, h.semi, 0xFFFFD04D);
            st.setText(status);
            LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stl.leftMargin = px(14);
            row.addView(st, stl);
        }
        return row;
    }

    private void addGap(int dp) {
        panelBody.addView(new View(h.a), new LinearLayout.LayoutParams(1, px(dp)));
    }

    private void rule() {
        View r = new View(h.a);
        r.setBackgroundColor(0x24FFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(1.5f)));
        lp.topMargin = px(18);
        lp.bottomMargin = px(16);
        panelBody.addView(r, lp);
    }

    private void football(Info in) {
        panelBody.addView(heading(("FT".equals(in.status) ? "FULL TIME" : in.status == null ? "RESULT" : in.status.toUpperCase(Locale.UK))
                + (in.competition == null ? "" : "  ·  " + in.competition.toUpperCase(Locale.UK))));
        addGap(18);
        panelBody.addView(scoreLine(in.home, in.away, in.status, true));
        // The goals and red cards, each under its own side.
        if (!in.events.isEmpty()) {
            addGap(12);
            int shown = 0;
            for (String[] ev : in.events) {
                if (shown++ >= 7) break;
                boolean home = "home".equals(ev[0]);
                String mark = "red".equals(ev[1]) ? "🟥" : "⚽";
                String what = ev[2] + ("pen".equals(ev[1]) ? " (pen)" : "og".equals(ev[1]) ? " (og)" : "") + "  " + ev[3];
                TextView t = h.text(17, h.regular, 0xE6FFFFFF);
                t.setText(home ? mark + "  " + what : what + "  " + mark);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setGravity(home ? Gravity.START : Gravity.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = px(7);
                panelBody.addView(t, lp);
            }
        }
        if (!in.stats.isEmpty()) {
            rule();
            panelBody.addView(heading("MATCH STATS"));
            int hc = colour(in.home[4], 0xFFFFFFFF), ac = colour(in.away[4], 0xFF7FA7FF);
            if (close(hc, ac)) ac = 0xFF7FA7FF;
            for (String[] s : in.stats) statRow(s, hc, ac);
        }
    }

    /** A stat: the home figure, its name, the away one, and a bar split between them in the clubs' colours. */
    private void statRow(String[] s, int hc, int ac) {
        LinearLayout box = new LinearLayout(h.a);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout nums = new LinearLayout(h.a);
        nums.setGravity(Gravity.CENTER_VERTICAL);
        TextView a = h.text(19, outfitBold, Color.WHITE);
        a.setText(s[1]);
        nums.addView(a, new LinearLayout.LayoutParams(px(80), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView name = h.text(16, h.regular, 0xB3FFFFFF);
        name.setText(s[0]);
        name.setGravity(Gravity.CENTER);
        nums.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView b = h.text(19, outfitBold, Color.WHITE);
        b.setText(s[2]);
        b.setGravity(Gravity.END);
        nums.addView(b, new LinearLayout.LayoutParams(px(80), ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(nums);
        float share = 0.5f;
        try { share = Float.parseFloat(s[3]); } catch (Exception ignored) { }
        LinearLayout bar = new LinearLayout(h.a);
        bar.setOutlineProvider(h.round(3));
        bar.setClipToOutline(true);
        View hv = new View(h.a);
        hv.setBackgroundColor(hc);
        View gap = new View(h.a);
        View av = new View(h.a);
        av.setBackgroundColor(ac);
        float hs = Math.max(0.02f, Math.min(0.98f, share));
        bar.addView(hv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, hs));
        bar.addView(gap, new LinearLayout.LayoutParams(px(4), ViewGroup.LayoutParams.MATCH_PARENT));
        bar.addView(av, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1 - hs));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(6));
        bl.topMargin = px(6);
        box.addView(bar, bl);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(14);
        panelBody.addView(box, lp);
    }

    private static int colour(String hex, int fallback) {
        if (hex == null || hex.length() < 6) return fallback;
        try {
            int c = 0xFF000000 | Integer.parseInt(hex.substring(hex.length() - 6), 16);
            float[] hsv = new float[3];
            Color.colorToHSV(c, hsv);
            // Kits as dark as the panel are lifted so the bar reads.
            if (hsv[2] < 0.45f) { hsv[2] = 0.75f; if (hsv[1] < 0.15f) return 0xFFE6E8F2; }
            return Color.HSVToColor(hsv);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean close(int a, int b) {
        return Math.abs(Color.red(a) - Color.red(b)) + Math.abs(Color.green(a) - Color.green(b)) + Math.abs(Color.blue(a) - Color.blue(b)) < 90;
    }

    private void f1(Info in) {
        panelBody.addView(heading((in.status == null ? "RACE RESULT" : in.status) + "  ·  " + F1Results.shortName(in.board.name).toUpperCase(Locale.UK)));
        addGap(8);
        int n = 0;
        for (F1Results.Row r : in.board.rows) {
            if (n++ >= 10) break;
            LinearLayout row = new LinearLayout(h.a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView pos = h.text(20, outfitHeavy, n <= 3 ? 0xFFFFD04D : Color.WHITE);
            pos.setText(String.valueOf(r.pos));
            row.addView(pos, new LinearLayout.LayoutParams(px(38), ViewGroup.LayoutParams.WRAP_CONTENT));
            View bar = new View(h.a);
            bar.setBackground(h.fill(2, F1Results.teamColour(r.teamId)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(5), px(26));
            bl.rightMargin = px(12);
            row.addView(bar, bl);
            TextView who = h.text(19, h.semi, Color.WHITE);
            who.setText((r.first.isEmpty() ? "" : r.first.charAt(0) + ". ") + r.last);
            who.setSingleLine(true);
            who.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(who, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView gap = h.text(16, h.regular, 0xB3FFFFFF);
            gap.setText(n == 1 && r.gap.contains(":") ? r.gap : r.gap.isEmpty() ? r.status : r.gap.startsWith("+") || r.gap.contains("lap") || "DNF".equals(r.gap) ? r.gap : "+" + r.gap);
            row.addView(gap);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(54));
            panelBody.addView(row, lp);
        }
    }

    private void fight(Info in) {
        panelBody.addView(heading(in.competition == null ? "RESULT" : in.competition.toUpperCase(Locale.UK)));
        if (in.a != null) {
            addGap(16);
            for (String[] f : new String[][]{in.a, in.b}) {
                LinearLayout row = new LinearLayout(h.a);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView n = h.text(26, outfitBold, Color.WHITE);
                n.setText(f[0]);
                n.setSingleLine(true);
                n.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                if ("1".equals(f[2])) {
                    TextView w = h.text(14, h.semi, 0xFF0B2E1A);
                    w.setText("WIN");
                    w.setLetterSpacing(0.1f);
                    w.setPadding(px(10), px(3), px(10), px(3));
                    w.setBackground(h.fill(6, 0xFF3DDC84));
                    row.addView(w);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = px(10);
                panelBody.addView(row, lp);
                if (f[1] != null && !f[1].isEmpty()) {
                    TextView rec = h.text(16, h.regular, 0x99FFFFFF);
                    rec.setText(f[1]);
                    panelBody.addView(rec);
                }
            }
            if (in.method != null) {
                rule();
                TextView m = h.text(22, outfitBold, 0xFFFFD04D);
                m.setText(in.method);
                panelBody.addView(m);
            }
            if (in.status != null) {
                TextView s = h.text(17, h.regular, 0xB3FFFFFF);
                s.setText(in.status);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = px(6);
                panelBody.addView(s, lp);
            }
        }
        if (!in.lines.isEmpty()) {
            rule();
            panelBody.addView(heading(in.a == null ? "RESULTS" : "ON THE CARD"));
            for (String[] l : in.lines) lineRow(l);
        }
    }

    private void lineRow(String[] l) {
        LinearLayout box = new LinearLayout(h.a);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView a = h.text(18, h.semi, Color.WHITE);
        a.setText(l[0]);
        a.setSingleLine(true);
        a.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(a);
        if (l.length > 1 && l[1] != null && !l[1].isEmpty()) {
            TextView b = h.text(15, h.regular, 0xA6FFFFFF);
            b.setText(l[1]);
            b.setMaxLines(2);
            b.setEllipsize(TextUtils.TruncateAt.END);
            box.addView(b);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(12);
        panelBody.addView(box, lp);
    }

    private void tennis(Info in) {
        panelBody.addView(heading(in.competition == null ? "MATCH" : in.competition.toUpperCase(Locale.UK)));
        if (in.status != null) {
            TextView s = h.text(17, h.regular, 0xB3FFFFFF);
            s.setText(in.status);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = px(6);
            panelBody.addView(s, lp);
        }
        addGap(14);
        String[][] players = {in.a, in.b};
        for (int p = 0; p < 2; p++) {
            String[] f = players[p];
            LinearLayout row = new LinearLayout(h.a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView n = h.text(22, "1".equals(f[2]) ? outfitHeavy : outfitBold, "1".equals(f[2]) ? Color.WHITE : 0xCCFFFFFF);
            n.setText(("1".equals(f[2]) ? "✓ " : "") + f[0]);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            for (String[] set : in.sets) {
                TextView g = h.text(24, outfitHeavy, Color.WHITE);
                g.setText(set[p]);
                g.setGravity(Gravity.CENTER);
                row.addView(g, new LinearLayout.LayoutParams(px(40), ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(52));
            panelBody.addView(row, lp);
        }
    }

    private void game(Info in) {
        panelBody.addView(heading((in.status == null ? "FINAL" : in.status.toUpperCase(Locale.UK))
                + (in.competition == null ? "" : "  ·  " + in.competition.toUpperCase(Locale.UK))));
        addGap(18);
        panelBody.addView(scoreLine(in.home, in.away, in.status, true));
        if (!in.homeLines.isEmpty()) {
            rule();
            panelBody.addView(heading("BY PERIOD"));
            String[][] sides = {in.home, in.away};
            List<List<String>> lines = new ArrayList<>();
            lines.add(in.homeLines);
            lines.add(in.awayLines);
            for (int s = 0; s < 2; s++) {
                LinearLayout row = new LinearLayout(h.a);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView n = h.text(18, outfitBold, Color.WHITE);
                n.setText(sides[s][1]);
                row.addView(n, new LinearLayout.LayoutParams(px(80), ViewGroup.LayoutParams.WRAP_CONTENT));
                for (String v : lines.get(s)) {
                    TextView g = h.text(18, h.regular, 0xD9FFFFFF);
                    g.setText(v);
                    g.setGravity(Gravity.CENTER);
                    row.addView(g, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(40));
                lp.topMargin = px(4);
                panelBody.addView(row, lp);
            }
        }
        if (!in.lines.isEmpty()) {
            rule();
            panelBody.addView(heading("LEADERS"));
            for (String[] l : in.lines) lineRow(l);
        }
    }

    /** No event found (or a sport without one: boxing, cricket, rugby): the video's own details. */
    private void about(SportsHighlights.Clip c) {
        panelBody.addView(heading("ABOUT THIS VIDEO"));
        addGap(14);
        TextView ch = h.text(22, outfitBold, Color.WHITE);
        ch.setText(c.channel);
        panelBody.addView(ch);
        if (c.published > 0) {
            TextView w = h.text(17, h.regular, 0xB3FFFFFF);
            w.setText("Published " + DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm", Locale.UK).withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(c.published)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = px(6);
            panelBody.addView(w, lp);
        }
        String d = describe(c.description);
        if (!d.isEmpty()) {
            rule();
            TextView t = h.text(18, h.regular, 0xE6FFFFFF);
            t.setText(d);
            t.setMaxLines(16);
            t.setEllipsize(TextUtils.TruncateAt.END);
            panelBody.addView(t);
        }
    }

    /** The channel's words without links, hashtags and "subscribe" lines; at most about 600 letters. */
    static String describe(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (String line : s.split("\n")) {
            String l = line.trim();
            String low = l.toLowerCase(Locale.ROOT);
            if (l.isEmpty() || low.contains("http") || low.startsWith("#") || low.contains("subscribe") || low.contains("follow us")
                    || low.contains("www.") || low.startsWith("►") || low.startsWith("▶")) continue;
            if (out.length() > 0) out.append("\n\n");
            out.append(l);
            if (out.length() > 600) break;
        }
        String r = out.toString();
        return r.length() > 620 ? r.substring(0, 600).trim() + "…" : r;
    }

    // ---- finding the event (off the main thread) -------------------------------------------------------------------

    private static final Map<String, Object[]> boards = new HashMap<>();
    private static final String ESPN = "https://site.api.espn.com/apis/site/v2/sports/";

    /** A scoreboard (ESPN's, a sport path and a day), kept ten minutes. */
    private static JSONObject board(String path, String day) {
        String key = path + "@" + day;
        synchronized (boards) {
            Object[] k = boards.get(key);
            if (k != null && System.currentTimeMillis() - (long) k[0] < 600_000L) return (JSONObject) k[1];
        }
        JSONObject j = null;
        try {
            j = new JSONObject(ScoresTicker.get(ESPN + path + "/scoreboard" + (day == null ? "" : "?dates=" + day)));
        } catch (Exception ignored) {
        }
        synchronized (boards) {
            boards.put(key, new Object[]{System.currentTimeMillis(), j});
        }
        return j;
    }

    /** The days a video's event may have been on: the day it went up, and the three before (the TV's own days). */
    private static List<String> days(long published) {
        long p = published > 0 ? published : System.currentTimeMillis();
        List<String> out = new ArrayList<>();
        DateTimeFormatter f = DateTimeFormatter.BASIC_ISO_DATE.withZone(ZoneId.systemDefault());
        for (int d = 0; d <= 3; d++) {
            String day = f.format(Instant.ofEpochMilli(p - d * 86_400_000L));
            if (!out.contains(day)) out.add(day);
        }
        return out;
    }

    static Info find(SportsHighlights.Clip c, String[] leagues) {
        String sport = c.sport == null ? "" : c.sport;
        if (sport.startsWith("FOOTBALL")) return football(c, leagues);
        if (sport.startsWith("FORMULA")) return f1(c);
        if (sport.contains("UFC")) return ufc(c);
        if (sport.startsWith("TENNIS")) return tennis(c);
        if (sport.equals("NFL")) return game(c, "football/nfl", "NFL");
        if (sport.equals("NBA")) return game(c, "basketball/nba", "NBA");
        return null;
    }

    /** The leagues worth asking about a football video: named in its title first, then the usual ones. */
    private static List<String> footballLeagues(SportsHighlights.Clip c, String t, String[] configured) {
        List<String> out = new ArrayList<>();
        String[][] named = {{"premier league", "eng.1"}, {"championship", "eng.2"}, {"efl", "eng.2"}, {"league one", "eng.3"},
                {"league two", "eng.4"}, {"spfl", "sco.1"}, {"scottish", "sco.1"}, {"serie a", "ita.1"}, {"laliga", "esp.1"},
                {"la liga", "esp.1"}, {"bundesliga", "ger.1"}, {"ligue 1", "fra.1"}, {"saudi", "ksa.1"}, {"champions league", "uefa.champions"},
                {"europa league", "uefa.europa"}, {"conference league", "uefa.europa.conf"}, {"fa cup", "eng.fa"},
                {"carabao", "eng.league_cup"}, {"league cup", "eng.league_cup"}, {"eredivisie", "ned.1"}, {"mls", "usa.1"},
                {"wsl", "eng.w.1"}};
        for (String[] n : named) if (t.contains(n[0]) && !out.contains(n[1])) out.add(n[1]);
        if (c.channel != null && c.channel.contains("Premier League") && !out.contains("eng.1")) out.add(0, "eng.1");
        for (String e : configured) {
            String slug = e.split("\\|")[0].trim();
            if (!slug.isEmpty() && !out.contains(slug) && out.size() < 8) out.add(slug);
        }
        for (String s : new String[]{"eng.1", "eng.2", "uefa.champions", "uefa.europa", "sco.1", "esp.1", "ita.1", "ger.1"}) {
            if (!out.contains(s) && out.size() < 9) out.add(s);
        }
        return out;
    }

    private static Info football(SportsHighlights.Clip c, String[] configured) {
        String t = OfficialVideos.plain(c.title);
        List<String> days = days(c.published);
        long latest = (c.published > 0 ? c.published : System.currentTimeMillis()) + 3_600_000L;
        for (String slug : footballLeagues(c, t, configured)) {
            for (String day : days) {
                JSONObject b = board("soccer/" + slug, day);
                JSONArray events = b == null ? null : b.optJSONArray("events");
                for (int i = 0; events != null && i < events.length(); i++) {
                    JSONObject e = events.optJSONObject(i);
                    JSONObject comp = e == null || e.optJSONArray("competitions") == null ? null : e.optJSONArray("competitions").optJSONObject(0);
                    if (comp == null) continue;
                    JSONObject home = null, away = null;
                    JSONArray cs = comp.optJSONArray("competitors");
                    for (int k = 0; cs != null && k < cs.length(); k++) {
                        JSONObject x = cs.optJSONObject(k);
                        if ("home".equals(x.optString("homeAway"))) home = x; else away = x;
                    }
                    if (home == null || away == null) continue;
                    JSONObject ht = home.optJSONObject("team"), at = away.optJSONObject("team");
                    if (ht == null || at == null) continue;
                    if (!OfficialVideos.named(t, ht.optString("displayName")) || !OfficialVideos.named(t, at.optString("displayName"))) continue;
                    if (espnTime(e.optString("date")) > latest) continue;
                    Info in = new Info();
                    in.kind = Info.FOOTBALL;
                    JSONArray lg = b.optJSONArray("leagues");
                    in.competition = lg != null && lg.optJSONObject(0) != null ? leagueName(lg.optJSONObject(0)) : null;
                    in.home = side(home);
                    in.away = side(away);
                    JSONObject st = comp.optJSONObject("status");
                    String state = st == null ? "" : st.optJSONObject("type") == null ? "" : st.optJSONObject("type").optString("state");
                    in.status = "post".equals(state) ? "FT" : st == null ? null : st.optJSONObject("type").optString("shortDetail");
                    String homeId = ht.optString("id");
                    JSONArray det = comp.optJSONArray("details");
                    for (int k = 0; det != null && k < det.length(); k++) {
                        JSONObject d = det.optJSONObject(k);
                        if (d == null) continue;
                        boolean red = d.optBoolean("redCard"), goal = d.optBoolean("scoringPlay");
                        if (!red && !goal) continue;
                        JSONObject team = d.optJSONObject("team");
                        String side = team != null && homeId.equals(team.optString("id")) ? "home" : "away";
                        boolean og = d.optBoolean("ownGoal");
                        // An own goal is credited to the side it counted for.
                        if (og) side = "home".equals(side) ? "away" : "home";
                        JSONArray who = d.optJSONArray("athletesInvolved");
                        JSONObject p = who == null ? null : who.optJSONObject(0);
                        String name = p == null ? "" : p.optString("shortName", p.optString("displayName"));
                        String min = d.optJSONObject("clock") == null ? "" : d.optJSONObject("clock").optString("displayValue");
                        in.events.add(new String[]{side, red ? "red" : og ? "og" : d.optBoolean("penaltyKick") ? "pen" : "goal", name, min});
                    }
                    stats(in, "soccer/" + slug, e.optString("id"), homeId);
                    return in;
                }
            }
        }
        return null;
    }

    /** ESPN's "2026-11-02T20:00Z" (no seconds) or a full instant, in milliseconds; 0 when unreadable. */
    private static long espnTime(String s) {
        try {
            return Instant.parse(s).toEpochMilli();
        } catch (Exception e) {
            try {
                return Instant.parse(s.replace("Z", ":00Z")).toEpochMilli();
            } catch (Exception e2) {
                return 0L;
            }
        }
    }

    private static String leagueName(JSONObject league) {
        String n = league.optString("name", league.optString("abbreviation"));
        return n.replace("English ", "").replace("Spanish ", "").replace("Italian ", "").replace("German ", "").replace("French ", "");
    }

    /** {name, abbreviation, logo, score, colour} of a side on ESPN's scoreboard. */
    private static String[] side(JSONObject x) {
        JSONObject t = x.optJSONObject("team");
        String logo = t == null ? null : t.optString("logo", null);
        if ((logo == null || logo.isEmpty()) && t != null && t.optJSONArray("logos") != null && t.optJSONArray("logos").optJSONObject(0) != null)
            logo = t.optJSONArray("logos").optJSONObject(0).optString("href");
        String abbr = t == null ? "" : t.optString("abbreviation", t.optString("shortDisplayName"));
        return new String[]{t == null ? "" : t.optString("displayName"), abbr, logo, x.optString("score", ""), t == null ? null : t.optString("color", null)};
    }

    /** The match's stats from ESPN's summary: possession, shots, on target, corners, fouls. */
    private static void stats(Info in, String path, String eventId, String homeId) {
        try {
            JSONObject s = new JSONObject(ScoresTicker.get(ESPN + path + "/summary?event=" + eventId));
            JSONArray teams = s.optJSONObject("boxscore") == null ? null : s.optJSONObject("boxscore").optJSONArray("teams");
            if (teams == null || teams.length() < 2) return;
            Map<String, String> home = new HashMap<>(), away = new HashMap<>();
            for (int i = 0; i < teams.length(); i++) {
                JSONObject t = teams.optJSONObject(i);
                boolean isHome = homeId.equals(t.optJSONObject("team") == null ? "" : t.optJSONObject("team").optString("id"));
                JSONArray st = t.optJSONArray("statistics");
                for (int k = 0; st != null && k < st.length(); k++) {
                    JSONObject x = st.optJSONObject(k);
                    (isHome ? home : away).put(x.optString("name"), x.optString("displayValue"));
                }
            }
            String[][] wanted = {{"possessionPct", "Possession"}, {"totalShots", "Shots"}, {"shotsOnTarget", "On target"},
                    {"wonCorners", "Corners"}, {"foulsCommitted", "Fouls"}, {"saves", "Saves"}};
            for (String[] w : wanted) {
                String a = home.get(w[0]), b = away.get(w[0]);
                if (a == null || b == null) continue;
                float x = num(a), y = num(b);
                String share = x + y > 0 ? String.valueOf(x / (x + y)) : "0.5";
                if (w[0].equals("possessionPct")) { a = Math.round(x) + "%"; b = Math.round(y) + "%"; }
                in.stats.add(new String[]{w[1], a, b, share});
            }
        } catch (Exception ignored) {
        }
    }

    private static float num(String s) {
        try { return Float.parseFloat(s.replace("%", "").trim()); } catch (Exception e) { return 0f; }
    }

    /** Formula 1: the last race's top ten (the sprint's for a sprint), Jolpica's, with the teams' colours. */
    private static Info f1(SportsHighlights.Clip c) {
        String t = c.title.toLowerCase(Locale.ROOT);
        boolean sprint = t.contains("sprint") && !t.contains("sprint qualifying");
        F1Results.Board b = F1Results.jolpicaResult(null, sprint);
        if (b == null && sprint) b = F1Results.jolpicaResult(null, false);
        if (b == null || b.rows.isEmpty()) return null;
        Info in = new Info();
        in.kind = Info.F1;
        in.board = b;
        in.competition = F1Results.shortName(b.name);
        boolean thisRace = F1Results.sameGrandPrix(c.title, b.name) || t.contains(F1Results.key(F1Results.shortName(b.name)).replace(" grand prix", ""));
        in.status = sprint ? "SPRINT RESULT" : thisRace ? "RACE RESULT" : "LATEST RACE";
        return in;
    }

    /** Words of a name worth matching in a title: its last word, accents and case aside ("Pereira", "Alcaraz"). */
    private static String surname(String name) {
        String p = OfficialVideos.plain(name);
        int sp = p.lastIndexOf(' ');
        return sp < 0 ? p : p.substring(sp + 1);
    }

    /** UFC: the fight in the title on ESPN's card for those days (winner, method, round); else the card's results. */
    private static Info ufc(SportsHighlights.Clip c) {
        String t = " " + OfficialVideos.plain(c.title) + " ";
        JSONObject card = null;
        for (String day : days(c.published)) {
            JSONObject b = board("mma/ufc", day);
            JSONArray events = b == null ? null : b.optJSONArray("events");
            for (int i = 0; events != null && i < events.length(); i++) {
                JSONObject e = events.optJSONObject(i);
                JSONArray comps = e.optJSONArray("competitions");
                for (int k = 0; comps != null && k < comps.length(); k++) {
                    JSONObject comp = comps.optJSONObject(k);
                    JSONArray cs = comp.optJSONArray("competitors");
                    if (cs == null || cs.length() < 2) continue;
                    String an = athlete(cs.optJSONObject(0)), bn = athlete(cs.optJSONObject(1));
                    if (t.contains(" " + surname(an) + " ") && t.contains(" " + surname(bn) + " ")) {
                        Info in = new Info();
                        in.kind = Info.FIGHT;
                        in.competition = e.optString("name");
                        in.a = new String[]{an, record(cs.optJSONObject(0)), cs.optJSONObject(0).optBoolean("winner") ? "1" : ""};
                        in.b = new String[]{bn, record(cs.optJSONObject(1)), cs.optJSONObject(1).optBoolean("winner") ? "1" : ""};
                        fightResult(in, comp);
                        return in;
                    }
                }
                if (card == null && comps != null && comps.length() > 0) card = e;
            }
        }
        if (card == null) return null;
        // No fight named: the card's results, the main event first.
        Info in = new Info();
        in.kind = Info.FIGHT;
        in.competition = card.optString("name");
        JSONArray comps = card.optJSONArray("competitions");
        for (int k = comps.length() - 1; k >= 0 && in.lines.size() < 7; k--) {
            JSONObject comp = comps.optJSONObject(k);
            JSONArray cs = comp.optJSONArray("competitors");
            if (cs == null || cs.length() < 2) continue;
            JSONObject w = cs.optJSONObject(0).optBoolean("winner") ? cs.optJSONObject(0) : cs.optJSONObject(1).optBoolean("winner") ? cs.optJSONObject(1) : null;
            JSONObject l = w == null ? null : w == cs.optJSONObject(0) ? cs.optJSONObject(1) : cs.optJSONObject(0);
            Info r = new Info();
            fightResult(r, comp);
            if (w != null) in.lines.add(new String[]{athlete(w) + " def. " + athlete(l), r.method == null ? "" : r.method + (r.status == null ? "" : "  ·  " + r.status)});
            else in.lines.add(new String[]{athlete(cs.optJSONObject(0)) + " v " + athlete(cs.optJSONObject(1)), comp.optJSONObject("type") == null ? "" : comp.optJSONObject("type").optString("abbreviation")});
        }
        return in.lines.isEmpty() ? null : in;
    }

    private static String athlete(JSONObject x) {
        JSONObject a = x == null ? null : x.optJSONObject("athlete");
        return a == null ? "" : a.optString("displayName");
    }

    private static String record(JSONObject x) {
        JSONArray r = x == null ? null : x.optJSONArray("records");
        return r != null && r.optJSONObject(0) != null ? r.optJSONObject(0).optString("summary") : "";
    }

    /** A fight's method ("KO/TKO") and when ("Round 2 · 3:14"), as ESPN gives them; its weight class. */
    private static void fightResult(Info in, JSONObject comp) {
        JSONObject st = comp.optJSONObject("status");
        if (st == null) return;
        JSONObject res = st.optJSONObject("result");
        String method = res == null ? null : res.optString("displayName", res.optString("name", null));
        JSONObject type = st.optJSONObject("type");
        boolean done = type != null && "post".equals(type.optString("state"));
        if (method == null && done) method = type.optString("detail", null);
        in.method = method;
        int round = st.optInt("period", 0);
        String clock = st.optString("displayClock", "");
        String weight = comp.optJSONObject("type") == null ? "" : comp.optJSONObject("type").optString("abbreviation");
        StringBuilder sb = new StringBuilder();
        if (done && round > 0) sb.append("Round ").append(round).append(clock.isEmpty() ? "" : "  ·  " + clock);
        else if (!done && type != null) sb.append(type.optString("shortDetail"));
        if (!weight.isEmpty()) sb.append(sb.length() > 0 ? "  ·  " : "").append(weight);
        in.status = sb.length() > 0 ? sb.toString() : null;
    }

    /** Tennis: the match of the two players in the title, ATP's or WTA's, its sets and the winner. */
    private static Info tennis(SportsHighlights.Clip c) {
        String t = " " + OfficialVideos.plain(c.title) + " ";
        for (String tour : new String[]{"tennis/atp", "tennis/wta"}) {
            List<String> ds = new ArrayList<>();
            ds.add(null);
            ds.addAll(days(c.published));
            for (String day : ds) {
                JSONObject b = board(tour, day);
                JSONArray events = b == null ? null : b.optJSONArray("events");
                for (int i = 0; events != null && i < events.length(); i++) {
                    JSONObject e = events.optJSONObject(i);
                    JSONArray groups = e.optJSONArray("groupings");
                    for (int g = 0; groups != null && g < groups.length(); g++) {
                        JSONArray comps = groups.optJSONObject(g).optJSONArray("competitions");
                        for (int k = 0; comps != null && k < comps.length(); k++) {
                            JSONObject comp = comps.optJSONObject(k);
                            JSONArray cs = comp.optJSONArray("competitors");
                            if (cs == null || cs.length() < 2) continue;
                            String an = athlete(cs.optJSONObject(0)), bn = athlete(cs.optJSONObject(1));
                            if (an.isEmpty() || bn.isEmpty() || !t.contains(" " + surname(an) + " ") || !t.contains(" " + surname(bn) + " ")) continue;
                            Info in = new Info();
                            in.kind = Info.TENNIS;
                            in.competition = e.optString("name");
                            JSONObject round = comp.optJSONObject("round");
                            JSONObject st = comp.optJSONObject("status");
                            String rs = round == null ? "" : round.optString("displayName");
                            String ss = st == null || st.optJSONObject("type") == null ? "" : st.optJSONObject("type").optString("shortDetail");
                            in.status = rs + (rs.isEmpty() || ss.isEmpty() ? "" : "  ·  ") + ss;
                            in.a = new String[]{an, "", cs.optJSONObject(0).optBoolean("winner") ? "1" : ""};
                            in.b = new String[]{bn, "", cs.optJSONObject(1).optBoolean("winner") ? "1" : ""};
                            JSONArray la = cs.optJSONObject(0).optJSONArray("linescores"), lb = cs.optJSONObject(1).optJSONArray("linescores");
                            for (int s = 0; la != null && lb != null && s < Math.min(la.length(), lb.length()); s++) {
                                in.sets.add(new String[]{games(la.optJSONObject(s)), games(lb.optJSONObject(s))});
                            }
                            return in;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String games(JSONObject l) {
        if (l == null) return "";
        double v = l.optDouble("value", Double.NaN);
        return Double.isNaN(v) ? l.optString("displayValue", "") : String.valueOf((int) v);
    }

    /** NFL and NBA: the game of the two teams in the title, its score, by period, and its leaders. */
    private static Info game(SportsHighlights.Clip c, String path, String league) {
        String t = " " + OfficialVideos.plain(c.title) + " ";
        for (String day : days(c.published)) {
            JSONObject b = board(path, day);
            JSONArray events = b == null ? null : b.optJSONArray("events");
            for (int i = 0; events != null && i < events.length(); i++) {
                JSONObject e = events.optJSONObject(i);
                JSONObject comp = e.optJSONArray("competitions") == null ? null : e.optJSONArray("competitions").optJSONObject(0);
                if (comp == null) continue;
                JSONObject home = null, away = null;
                JSONArray cs = comp.optJSONArray("competitors");
                for (int k = 0; cs != null && k < cs.length(); k++) {
                    JSONObject x = cs.optJSONObject(k);
                    if ("home".equals(x.optString("homeAway"))) home = x; else away = x;
                }
                if (home == null || away == null || !team(t, home) || !team(t, away)) continue;
                Info in = new Info();
                in.kind = Info.GAME;
                in.competition = league;
                in.home = side(home);
                in.away = side(away);
                JSONObject st = comp.optJSONObject("status");
                in.status = st == null || st.optJSONObject("type") == null ? null : st.optJSONObject("type").optString("shortDetail");
                JSONArray hl = home.optJSONArray("linescores"), al = away.optJSONArray("linescores");
                for (int k = 0; hl != null && al != null && k < Math.min(hl.length(), al.length()); k++) {
                    in.homeLines.add(games(hl.optJSONObject(k)));
                    in.awayLines.add(games(al.optJSONObject(k)));
                }
                JSONArray leaders = comp.optJSONArray("leaders");
                for (int k = 0; leaders != null && k < leaders.length() && in.lines.size() < 3; k++) {
                    JSONObject l = leaders.optJSONObject(k);
                    JSONArray ls = l == null ? null : l.optJSONArray("leaders");
                    JSONObject top = ls == null ? null : ls.optJSONObject(0);
                    if (top == null) continue;
                    String who = top.optJSONObject("athlete") == null ? "" : top.optJSONObject("athlete").optString("displayName");
                    in.lines.add(new String[]{who, l.optString("displayName") + "  ·  " + top.optString("displayValue")});
                }
                return in;
            }
        }
        return null;
    }

    /** Whether a title names a team: its nickname ("Chiefs") or its place ("Kansas City"). */
    private static boolean team(String t, JSONObject side) {
        JSONObject x = side.optJSONObject("team");
        if (x == null) return false;
        for (String n : new String[]{x.optString("name"), x.optString("shortDisplayName"), x.optString("displayName")}) {
            String p = OfficialVideos.plain(n);
            if (p.length() >= 3 && t.contains(" " + p + " ")) return true;
        }
        return false;
    }
}

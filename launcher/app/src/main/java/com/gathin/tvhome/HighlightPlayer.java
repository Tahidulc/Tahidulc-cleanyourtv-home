package com.gathin.tvhome;

import android.graphics.Bitmap;
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
    private static final int FX = 56, FY = 132, FW = 1216, FH = 684, PX = 1304, PW = 560, NY = 834, CW = 232, CH = 130, CG = 18;
    /** Up next: how many cards fit whole across its row (the focused one kept inside it with room either side). */
    private static final int NEXT_SEEN = 7;
    /** The scrim once open: opaque, so Home under it need not be drawn while the video plays. */
    private static final int SCRIM = 0xFF070A22;
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
    private LinearLayout topStrip, panel, panelBody, headline;
    private ImageView compLogo;
    private TextView kindView;
    /** The competition's second colour (headings, the kind of video, the line under the top). */
    private int accent = 0xFF7FA7FF;
    /** Home's views hidden under the opaque scrim while the video plays (shown again on closing). */
    private final List<View> hiddenUnder = new ArrayList<>();
    /** The frame's rounded corners while it plays: a still mask instead of a clip of the moving picture. */
    private View cornerMask;
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
        frame.animate().scaleX(1f).scaleY(1f).translationX(0).translationY(0).alpha(1f).setDuration(480).setInterpolator(EASE)
                .withEndAction(this::settle).start();
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

    /**
     * Opened: what made every frame of the video cost more goes. The frame left exactly at its size (no scale, alpha
     * or layer on it or the player), the scrim made opaque so Home under it is no longer drawn at all (its pages,
     * trailers and tickers), the rounded clip of the playing picture swapped for a still mask over its corners (a
     * WebView clipped to a rounded shape is drawn twice each frame), the glow drawn once into a layer.
     */
    private void settle() {
        if (!showing || closing || overlay == null) return;
        frame.setScaleX(1f);
        frame.setScaleY(1f);
        frame.setTranslationX(0f);
        frame.setTranslationY(0f);
        frame.setAlpha(1f);
        frame.setLayerType(View.LAYER_TYPE_NONE, null);
        dim.animate().cancel();
        dim.setAlpha(1f);
        dim.setBackgroundColor(SCRIM);
        hiddenUnder.clear();
        for (int i = 0; i < h.root.getChildCount(); i++) {
            View v = h.root.getChildAt(i);
            if (v == overlay || v.getVisibility() != View.VISIBLE) continue;
            v.setVisibility(View.INVISIBLE);
            hiddenUnder.add(v);
        }
        frame.setClipToOutline(false);
        cornerMask.setVisibility(View.VISIBLE);
        glow.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    /** Home back under it, the frame clipped round again: before it shrinks into its card. */
    private void unsettle() {
        for (View v : hiddenUnder) if (v.getVisibility() == View.INVISIBLE) v.setVisibility(View.VISIBLE);
        hiddenUnder.clear();
        if (frame != null) frame.setClipToOutline(true);
        if (cornerMask != null) cornerMask.setVisibility(View.GONE);
        if (glow != null) glow.setLayerType(View.LAYER_TYPE_NONE, null);
    }

    /** Back: the sound stops at once, the frame shrinks into its card (the one now playing), Home comes back. */
    void close() {
        if (!showing || closing) return;
        closing = true;
        unsettle();
        h.main.removeCallbacks(reveal);
        h.players.filmOff();
        cover.animate().cancel();
        cover.setVisibility(View.VISIBLE);
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
        unsettle();
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

        // The top: the event as a headline in its competition's colours (headline()): its logo, the clubs' crests either
        // side of the score (or the drivers', fighters', players'), what kind of video and when; the channel small at the right.
        topStrip = new LinearLayout(h.a);
        topStrip.setOrientation(LinearLayout.HORIZONTAL);
        topStrip.setGravity(Gravity.CENTER_VERTICAL);
        topStrip.setPadding(px(18), px(8), px(24), px(8));
        compLogo = new ImageView(h.a);
        compLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        compLogo.setAdjustViewBounds(true);
        compLogo.setMaxWidth(px(170));
        compLogo.setMaxHeight(px(70));
        compLogo.setMinimumWidth(px(70));
        topStrip.addView(compLogo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(70)));
        LinearLayout words = new LinearLayout(h.a);
        words.setOrientation(LinearLayout.VERTICAL);
        headline = new LinearLayout(h.a);
        headline.setGravity(Gravity.CENTER_VERTICAL);
        words.addView(headline, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(52)));
        kindView = h.text(17, h.semi, Color.WHITE);
        kindView.setSingleLine(true);
        kindView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams kvl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        kvl.topMargin = px(2);
        words.addView(kindView, kvl);
        LinearLayout.LayoutParams wl0 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wl0.leftMargin = px(22);
        topStrip.addView(words, wl0);
        LinearLayout right = new LinearLayout(h.a);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        chipView = h.text(16, h.semi, 0xD9FFFFFF);
        chipView.setSingleLine(true);
        right.addView(chipView);
        subView = h.text(15, h.regular, 0xA6FFFFFF);
        subView.setSingleLine(true);
        right.addView(subView);
        TextView keys = h.text(13, h.regular, 0x80FFFFFF);
        Keys.set(keys, "OK pause  ·  ◀ ▶ 10 s  ·  ▼ up next  ·  BACK close");
        keys.setSingleLine(true);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        kl.topMargin = px(4);
        right.addView(keys, kl);
        LinearLayout.LayoutParams rl0 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rl0.leftMargin = px(20);
        topStrip.addView(right, rl0);
        titleView = h.text(28, outfitBold, Color.WHITE);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        place(overlay, topStrip, FX, 18, 1920 - 2 * FX, 102);

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
        timeText.setGravity(Gravity.CENTER);
        timeText.setSingleLine(true);
        timeText.setBackground(h.fill(8, 0x99000000));
        timeText.setAlpha(0f);
        // A fixed size: its words change each second without the whole pop-up being laid out again.
        FrameLayout.LayoutParams ttl = new FrameLayout.LayoutParams(px(186), px(32), Gravity.BOTTOM | Gravity.END);
        ttl.rightMargin = px(16);
        ttl.bottomMargin = px(18);
        frame.addView(timeText, ttl);
        seekBadge = h.text(26, outfitBold, Color.WHITE);
        seekBadge.setPadding(px(18), px(8), px(18), px(8));
        seekBadge.setBackground(h.fill(14, 0xA6000000));
        seekBadge.setAlpha(0f);
        frame.addView(seekBadge, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        cornerMask = new CornerMask(SCRIM, px(22));
        cornerMask.setVisibility(View.GONE);
        frame.addView(cornerMask, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
        // As wide as NEXT_SEEN whole cards (and the room a focused one grows into): no card cut at its end.
        place(overlay, rowBox, FX - 16, NY + 22, 16 + NEXT_SEEN * (CW + CG) - CG + 16, CH + 30);
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
        // The same clean card as on the Sports page: the picture, what it is and the competition's logo, no title.
        FrameLayout f = SportsHighlights.card(h, c, CW, CH, true);
        TextView now = h.text(12, h.semi, Color.WHITE);
        now.setTag("now");
        now.setText("PLAYING");
        now.setLetterSpacing(0.1f);
        now.setPadding(px(8), px(3), px(8), px(3));
        now.setBackground(h.fill(6, 0xFFE8112D));
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        wl.leftMargin = px(8);
        wl.bottomMargin = px(8);
        f.addView(now, wl);
        f.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        return f;
    }

    /** The scrim's colour over a rounded rectangle's corners: the frame looks round without clipping its video. */
    private final class CornerMask extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private final float r;

        CornerMask(int colour, float radius) {
            super(h.a);
            paint.setColor(colour);
            r = radius;
        }

        @Override protected void onSizeChanged(int w, int hgt, int ow, int oh) {
            path.reset();
            path.setFillType(android.graphics.Path.FillType.EVEN_ODD);
            path.addRect(0, 0, w, hgt, android.graphics.Path.Direction.CW);
            path.addRoundRect(0, 0, w, hgt, r, r, android.graphics.Path.Direction.CW);
        }

        @Override protected void onDraw(android.graphics.Canvas c) {
            c.drawPath(path, paint);
        }
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
            cover.setVisibility(View.VISIBLE);
            cover.setAlpha(1f);
            h.players.film(frame, c.id, this);
        };
        if (crossfade && cover.getAlpha() < 1f) {
            cover.animate().cancel();
            // The new picture fades over the old video, then the old one stops under it.
            h.image(coverPic, "https://i.ytimg.com/vi/" + c.id + "/hqdefault.jpg#yt", px(FW));
            coverShown().animate().alpha(1f).setDuration(260).withEndAction(start).start();
        } else start.run();
        loadInfo(c, gen);
    }

    private void reveal() {
        if (!showing || closing || paused || failed) return;
        revealed = true;
        spinner.setVisibility(View.GONE);
        cover.animate().cancel();
        // Gone from the drawing once faded (not drawn at no opacity over every frame of the video).
        cover.animate().alpha(0f).setDuration(450).withEndAction(() -> { if (revealed && !paused && !failed) cover.setVisibility(View.INVISIBLE); }).start();
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
            coverShown().animate().alpha(1f).setDuration(180).start();
        } else if (s == 0) {
            // Finished: on to the next one along, if there is one.
            if (at + 1 < clips.size()) { at++; nextIdx = at; play(true); showTexts(); paintFocus(); }
            else {
                Keys.set(coverWords, "Finished  ·  OK to watch again");
                coverShown().animate().alpha(1f).setDuration(220).start();
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
        cover.setVisibility(View.VISIBLE);
        cover.setAlpha(1f);
        coverWords.setText(code == 101 || code == 150 ? "This video can only be played in YouTube" : "This video won't play here");
        openButton.setVisibility(View.VISIBLE);
        paintFocus();
    }

    @Override public void gone() {
        // Home went into the background (or another player took the page): closed, nothing left behind.
        if (showing) { closing = true; removeNow(); }
    }

    private View coverShown() {
        cover.setVisibility(View.VISIBLE);
        return cover;
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
        // The row slides so the focused card (or the one playing) is whole and in sight, with one either side of it
        // where there is one: at the row's end the last card stays inside, never cut.
        int focus = zone == Z_NEXT ? nextIdx : at;
        int first = Math.max(0, Math.min(focus - NEXT_SEEN / 2, clips.size() - NEXT_SEEN));
        upRow.animate().translationX(-first * px(CW + CG)).setDuration(280).setInterpolator(HomeV2.SKY).start();
    }

    // ---- the words ----------------------------------------------------------------------------------------------------

    private Info info;

    /** The clubs' crests from the title's fixture, looked up before the game itself is found ({home, away}). */
    private String[] preCrests;
    private SportsHighlights.Clip preCrestsFor;

    /**
     * The top for the clip now playing, never just its YouTube title: the competition's logo and colours, then the
     * event made into a headline (football: each club's crest beside it, the score between, FT after the whole fixture;
     * F1: the Grand Prix; a fight or a tennis match: the two with their headshots and flags; NFL and NBA: the teams'
     * logos and the score), and under it what kind of video, when, the round and where. Before the event is found: the
     * clubs read from the title; failing all that, the title cut down to its words.
     */
    private void showTexts() {
        SportsHighlights.Clip c = clip();
        Info in = info != null && info.clip == c ? info : null;
        SportsHighlights.Brand b = in != null && in.brand != null ? in.brand : SportsHighlights.brand(c);
        String logo = in != null && in.compLogo != null ? in.compLogo : b.logo;
        accent = b.accent;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{
                (b.colour & 0xFFFFFF) | 0xF2000000, (b.colour & 0xFFFFFF) | 0xA6000000, (b.colour & 0xFFFFFF) | 0x26000000});
        g.setCornerRadius(px(24));
        g.setStroke(Math.max(1, px(1.5f)), (b.accent & 0xFFFFFF) | 0x73000000);
        topStrip.setBackground(g);
        if (logo != null) {
            compLogo.setVisibility(View.VISIBLE);
            h.image(compLogo, logo, px(170));
        } else {
            compLogo.setVisibility(View.GONE);
        }
        // The light behind the video in the competition's colours.
        GradientDrawable gd = new GradientDrawable();
        gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gd.setGradientRadius(px(820));
        gd.setColors(new int[]{(b.accent & 0xFFFFFF) | 0x40000000, (b.colour & 0xFFFFFF) | 0x33000000, 0x00000000});
        glow.setBackground(gd);
        headline.removeAllViews();
        String sport = c.sport == null ? "" : c.sport;
        String[] fx = fixture(c.title);
        if (in != null && (in.kind == Info.FOOTBALL || in.kind == Info.GAME || in.kind == Info.RUGBY) && in.home != null) {
            fixtureLine(in.home[2], in.home[0], in.home[3], in.away[3], in.away[0], in.away[2], in.status);
        } else if (in != null && in.kind == Info.CRICKET && in.home != null) {
            // Cricket's innings are too long for the headline: the two sides, their logos; the scores in the panel.
            vsLine(in.home[2], in.home[0], in.away[0], in.away[2]);
        } else if (in != null && in.kind == Info.FACTS && in.a != null && in.b != null) {
            if (in.score != null) fixtureLine(in.aPic, in.a[0], in.score[0], in.score[1], in.b[0], in.bPic, null);
            else vsLine(in.aPic, in.a[0], in.b[0], in.bPic);
        } else if (in != null && (in.kind == Info.FIGHT || in.kind == Info.TENNIS) && in.a != null) {
            twoSides(in.a[0], in.aPic, in.aFlag, in.b[0], in.bPic, in.bFlag, in.kind == Info.TENNIS);
        } else if (sport.startsWith("FOOTBALL") && fx != null) {
            String[] cr = preCrestsFor == c && preCrests != null ? preCrests : new String[2];
            fixtureLine(cr[0], fx[0], fx[1], fx[2], fx[3], cr[1], null);
        } else {
            String name = in != null && in.eventName != null && (in.kind == Info.F1 || in.kind == Info.FACTS) ? in.eventName
                    : sport.startsWith("FORMULA") ? grandPrix(c.title) : null;
            titleView.setText(name != null ? name : cleanTitle(c.title));
            headline.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        // Under it: what kind of video (in the competition's second colour), when, the round, where.
        List<String> bits = new ArrayList<>();
        if (in != null && in.kind == Info.FIGHT && in.eventName != null) bits.add(in.eventName);
        if (in != null && in.kind == Info.TENNIS && in.eventName != null) bits.add(in.eventName);
        if (in != null && in.round != null) bits.add(in.round);
        String when = in != null && in.when != null ? in.when : c.published > 0 ? DAY.format(Instant.ofEpochMilli(c.published)) : null;
        if (when != null) bits.add(when);
        if (in != null && in.venue != null && !in.venue.isEmpty()) bits.add(in.venue);
        String kind = kindWords(c);
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(kind);
        sb.setSpan(new android.text.style.ForegroundColorSpan(lighter(accent)), 0, kind.length(), 0);
        sb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, kind.length(), 0);
        for (String x : bits) sb.append("   ·   ").append(x);
        kindView.setText(sb);
        kindView.setTextColor(0xD9FFFFFF);
        chipView.setText(c.channel == null ? "" : c.channel);
        subView.setText(SportsHighlights.age(c.published));
        nextLabel.setText("UP NEXT  ·  " + (c.sport == null ? c.mark : c.sport));
        if (in == null) placeholders();
    }

    /** A colour bright enough for words on the dark strip (a dark second colour lifted). */
    private static int lighter(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        if (hsv[2] < 0.75f) hsv[2] = 0.9f;
        if (hsv[1] > 0.85f) hsv[1] = 0.85f;
        return Color.HSVToColor(hsv);
    }

    /** "[crest] Newcastle United  0 – 1  Everton [crest]  FT": each crest beside its own club, the state after the fixture. */
    private void fixtureLine(String homeCrest, String home, String hs, String as, String away, String awayCrest, String status) {
        int size = px(48);
        if (homeCrest != null) headline.addView(crest(homeCrest, size), new LinearLayout.LayoutParams(size, size));
        TextView hn = h.text(28, outfitBold, Color.WHITE);
        hn.setText(home);
        hn.setSingleLine(true);
        hn.setEllipsize(TextUtils.TruncateAt.END);
        hn.setMaxWidth(px(360));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.leftMargin = px(homeCrest != null ? 12 : 0);
        headline.addView(hn, hl);
        TextView sc = h.text(34, outfitHeavy, Color.WHITE);
        sc.setText(hs + " – " + as);
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.leftMargin = px(20);
        sl.rightMargin = px(20);
        headline.addView(sc, sl);
        TextView an = h.text(28, outfitBold, Color.WHITE);
        an.setText(away);
        an.setSingleLine(true);
        an.setEllipsize(TextUtils.TruncateAt.END);
        an.setMaxWidth(px(360));
        headline.addView(an);
        if (awayCrest != null) {
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(size, size);
            al.leftMargin = px(12);
            headline.addView(crest(awayCrest, size), al);
        }
        if (status != null && !status.isEmpty()) {
            TextView st = h.text(18, h.semi, 0xFF1A1D24);
            st.setText(status);
            st.setPadding(px(10), px(3), px(10), px(3));
            st.setBackground(h.fill(8, lighter(accent)));
            LinearLayout.LayoutParams stl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stl.leftMargin = px(16);
            headline.addView(st, stl);
        }
    }

    /** "[logo] England  v  India [logo]": two sides without a score, each logo beside its own (none: just the names). */
    private void vsLine(String aLogo, String a, String b, String bLogo) {
        int size = px(48);
        if (aLogo != null) headline.addView(crest(aLogo, size), new LinearLayout.LayoutParams(size, size));
        TextView an = h.text(28, outfitBold, Color.WHITE);
        an.setText(a);
        an.setSingleLine(true);
        an.setEllipsize(TextUtils.TruncateAt.END);
        an.setMaxWidth(px(380));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        al.leftMargin = px(aLogo != null ? 12 : 0);
        headline.addView(an, al);
        TextView v = h.text(24, outfitBold, lighter(accent));
        v.setText("v");
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vl.leftMargin = px(18);
        vl.rightMargin = px(18);
        headline.addView(v, vl);
        TextView bn = h.text(28, outfitBold, Color.WHITE);
        bn.setText(b);
        bn.setSingleLine(true);
        bn.setEllipsize(TextUtils.TruncateAt.END);
        bn.setMaxWidth(px(380));
        headline.addView(bn);
        if (bLogo != null) {
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(size, size);
            bl.leftMargin = px(12);
            headline.addView(crest(bLogo, size), bl);
        }
    }

    /** "[photo][flag] Pereira  v  Ankalaev [flag][photo]": a fight's or a tennis match's two, each with their own. */
    private void twoSides(String a, String aPic, String aFlag, String b, String bPic, String bFlag, boolean tennis) {
        int size = px(50);
        headline.addView(face(aPic, size), new LinearLayout.LayoutParams(size, size));
        if (aFlag != null) headline.addView(flagView(aFlag), flagParams(10, 0));
        TextView an = h.text(28, outfitBold, Color.WHITE);
        an.setText(a);
        an.setSingleLine(true);
        an.setEllipsize(TextUtils.TruncateAt.END);
        an.setMaxWidth(px(340));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        al.leftMargin = px(12);
        headline.addView(an, al);
        TextView v = h.text(24, outfitBold, lighter(accent));
        v.setText("v");
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vl.leftMargin = px(18);
        vl.rightMargin = px(18);
        headline.addView(v, vl);
        TextView bn = h.text(28, outfitBold, Color.WHITE);
        bn.setText(b);
        bn.setSingleLine(true);
        bn.setEllipsize(TextUtils.TruncateAt.END);
        bn.setMaxWidth(px(340));
        headline.addView(bn);
        if (bFlag != null) headline.addView(flagView(bFlag), flagParams(12, 0));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(size, size);
        bl.leftMargin = px(10);
        headline.addView(face(bPic, size), bl);
    }

    /** A crest (or a team's logo) at this size, fetched as Home fetches its pictures. */
    private ImageView crest(String url, int size) {
        ImageView v = new ImageView(h.a);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        h.image(v, url, size * 2);
        return v;
    }

    /** A headshot in a circle; the circle stays empty (a soft disc) when there is none. */
    private ImageView face(String url, int size) {
        ImageView v = new ImageView(h.a);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor((accent & 0xFFFFFF) | 0x40000000);
        v.setBackground(disc);
        v.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline o) {
                o.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        v.setClipToOutline(true);
        if (url != null) h.image(v, url, size * 2);
        return v;
    }

    private ImageView face(Bitmap b, int size) {
        ImageView v = face((String) null, size);
        if (b != null) v.setImageBitmap(b);
        return v;
    }

    private ImageView flagView(String url) {
        ImageView f = new ImageView(h.a);
        f.setScaleType(ImageView.ScaleType.FIT_CENTER);
        h.image(f, url, px(64));
        return f;
    }

    private LinearLayout.LayoutParams flagParams(int left, int right) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(34), px(24));
        lp.leftMargin = px(left);
        lp.rightMargin = px(right);
        return lp;
    }

    /** What kind of video, in words for the top: EXTENDED HIGHLIGHTS, ALL GOALS, RACE HIGHLIGHTS, FULL FIGHT… */
    private static String kindWords(SportsHighlights.Clip c) {
        String k = SportsHighlights.kind(c), t = c.title == null ? "" : c.title.toLowerCase(Locale.UK), sport = c.sport == null ? "" : c.sport;
        if (sport.startsWith("FORMULA")) {
            if (t.contains("qualifying")) return "QUALIFYING HIGHLIGHTS";
            if (t.contains("sprint")) return "SPRINT HIGHLIGHTS";
            if ("EXTENDED".equals(k)) return "EXTENDED RACE HIGHLIGHTS";
            if (t.contains("race")) return "RACE HIGHLIGHTS";
            return k == null ? "FORMULA 1" : "HIGHLIGHTS".equals(k) ? "HIGHLIGHTS" : k;
        }
        if ("EXTENDED".equals(k)) return "EXTENDED HIGHLIGHTS";
        if ("EVERY SCORE".equals(k)) return sport.startsWith("FOOTBALL") ? "ALL GOALS" : "EVERY SCORE";
        if ("FULL GAME".equals(k)) return sport.startsWith("FOOTBALL") ? "FULL MATCH" : "FULL GAME";
        if (k != null) return k;
        return c.highlight ? "HIGHLIGHTS" : "VIDEO";
    }

    /** A Grand Prix's name in an F1 title ("Mexico City Grand Prix"); null without one. */
    private static String grandPrix(String title) {
        if (title == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Z][A-Za-z\\u00C0-\\u017F.' ]{2,40}?)\\s+(Grand Prix|GP)\\b").matcher(title);
        return m.find() ? m.group(1).trim().replaceAll("^(?i)(the|extended|race|highlights?)\\s+", "") + " Grand Prix" : null;
    }

    /** A YouTube title cut down to its words: its first part, without emoji, hashtags or the channel's tail. */
    static String cleanTitle(String title) {
        if (title == null) return "";
        String first = title.split("[|\u2022]")[0];
        String t = first.replaceAll("[\\p{So}\\p{Cn}\\x{1F000}-\\x{1FFFF}]", " ").replaceAll("#\\S+", " ")
                .replaceAll("(?i)\\s*[-–]\\s*(sky sports|tnt sports|dazn|bt sport)\\b.*$", "")
                .replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) t = title.trim();
        return t.length() > 80 ? t.substring(0, 78).trim() + "…" : t;
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
            // Still (no pulse): nothing beside the video redrawn while it plays.
            bar.setAlpha(0.6f);
        }
    }

    private TextView heading(String s) {
        TextView t = h.text(15, h.semi, lighter(accent));
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
            // A football video: the clubs' crests from its title's fixture first, so the top shows them at once.
            String sp = c.sport == null ? "" : c.sport;
            String[] fx = sp.startsWith("FOOTBALL") ? fixture(c.title) : null;
            if (fx != null) {
                String[] cr = {PlaylistSport.crest(fx[0]), PlaylistSport.crest(fx[3])};
                h.main.post(() -> {
                    if (!showing || gen != generation || (info != null && info.clip == c)) return;
                    preCrests = cr;
                    preCrestsFor = c;
                    showTexts();
                });
            }
            Info found;
            try {
                found = find(c, leagues);
            } catch (Exception e) {
                found = null;
            }
            // F1: each of the top ten's headshot and team logo, fetched now (off the main thread).
            if (found != null && found.kind == Info.F1 && found.board != null) {
                int n = 0;
                for (F1Results.Row r : found.board.rows) {
                    if (n++ >= 10 || gen != generation) break;
                    Bitmap face = null, team = null;
                    try { face = PlayerPhotos.driver(h.a, r.last); } catch (Exception ignored) { }
                    try { if (!r.teamId.isEmpty()) team = ScoresTicker.cachedLogo(h.a, F1Results.logo(r.teamId), px(72)); } catch (Exception ignored) { }
                    found.pics.add(face);
                    found.teams.add(team);
                }
            }
            // Nothing found (or the lookup failed): the event's own facts, never the video's description.
            Info got = found != null ? found : safeFacts(c);
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
        static final int NONE = 0, FOOTBALL = 1, F1 = 2, FIGHT = 3, TENNIS = 4, GAME = 5, RUGBY = 6, CRICKET = 7, FACTS = 8;
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
        /** The competition's look (logo, colours) once known, and its logo's address when it is not one of ours. */
        SportsHighlights.Brand brand;
        String compLogo;
        /** When the event was ("Sat 1 Nov"), its round ("Round 20", "Week 9"), where, and its name (a Grand Prix's). */
        String when, round, venue, eventName;
        /** F1: each of the top ten's headshot and team logo (fetched with the result). */
        final List<Bitmap> pics = new ArrayList<>(), teams = new ArrayList<>();
        /** A fight or a tennis match: each side's headshot and flag (addresses). */
        String aPic, bPic, aFlag, bFlag;
        /** Cricket: the result in words ("Tasmania won by an inns & 81 runs") and the match ("8th Match · First-class"). */
        String result, description;
        /** Cricket: the player of the match {name, what, headshot}; null when not named. */
        String[] potm;
        /** The facts alone: the score the title gives ({home, away}), null when it gives none. */
        String[] score;
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK).withZone(ZoneId.systemDefault());

    /** "Sat 1 Nov" for an ESPN date; null when unreadable. */
    private static String day(String espnDate) {
        long t = espnTime(espnDate);
        return t > 0 ? DAY.format(Instant.ofEpochMilli(t)) : null;
    }

    private void fillPanel(Info in) {
        panelBody.removeAllViews();
        switch (in.kind) {
            case Info.FOOTBALL: football(in); break;
            case Info.F1: f1(in); break;
            case Info.FIGHT: fight(in); break;
            case Info.TENNIS: tennis(in); break;
            case Info.GAME: game(in); break;
            case Info.RUGBY: rugby(in); break;
            case Info.CRICKET: cricket(in); break;
            default: facts(in); break;
        }
    }

    /** "[crest] NEW  0 – 1  EVE [crest]  FT": each crest beside its own club, the state after the whole fixture. */
    private View scoreLine(String[] home, String[] away, String status, boolean big) {
        LinearLayout row = new LinearLayout(h.a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int crest = px(big ? 64 : 40);
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
        // The competition: its logo and name over the result.
        compHead(in);
        addGap(16);
        panelBody.addView(scoreLine(in.home, in.away, in.status, true));
        // The goals and red cards, each under its own side, its club's crest beside it.
        events(in, 7);
        if (!in.stats.isEmpty()) {
            rule();
            panelBody.addView(heading("MATCH STATS"));
            int hc = colour(in.home[4], 0xFFFFFFFF), ac = colour(in.away[4], 0xFF7FA7FF);
            if (close(hc, ac)) ac = 0xFF7FA7FF;
            for (String[] st : in.stats) statRow(st, hc, ac);
        }
    }

    /** The competition's logo and name over a result, with its state ("FULL TIME") first. */
    private void compHead(Info in) {
        SportsHighlights.Brand b = in.brand != null ? in.brand : SportsHighlights.brand(in.clip);
        String logo = in.compLogo != null ? in.compLogo : b.logo;
        LinearLayout top = new LinearLayout(h.a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (logo != null) {
            ImageView l = crest(logo, px(34));
            top.addView(l, new LinearLayout.LayoutParams(px(34), px(34)));
        }
        String comp = in.competition != null ? in.competition : b.name;
        TextView hd = heading(("FT".equals(in.status) ? "FULL TIME" : in.status == null || in.status.isEmpty() ? "RESULT" : in.status.toUpperCase(Locale.UK))
                + (comp == null ? "" : "  ·  " + comp.toUpperCase(Locale.UK)));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hl.leftMargin = px(logo != null ? 12 : 0);
        top.addView(hd, hl);
        panelBody.addView(top);
    }

    /**
     * The goals, tries and cards, each under its own side with its crest beside it: {home|away, goal|pen|og|try|
     * yellow|red, who, minute}; at most max of them.
     */
    private void events(Info in, int max) {
        if (!in.events.isEmpty()) {
            addGap(10);
            int shown = 0;
            for (String[] ev : in.events) {
                if (shown++ >= max) break;
                boolean home = "home".equals(ev[0]);
                boolean red = "red".equals(ev[1]), yellow = "yellow".equals(ev[1]);
                String what = ev[2] + ("pen".equals(ev[1]) ? " (pen)" : "og".equals(ev[1]) ? " (og)" : "") + "  " + ev[3];
                LinearLayout row = new LinearLayout(h.a);
                row.setGravity(Gravity.CENTER_VERTICAL | (home ? Gravity.START : Gravity.END));
                ImageView cr = crest((home ? in.home : in.away)[2], px(24));
                View mark = red ? redCard() : yellow ? yellowCard() : null;
                TextView t = h.text(17, h.regular, 0xE6FFFFFF);
                t.setText(what);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(px(24), px(24));
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (home) {
                    row.addView(cr, il);
                    if (mark != null) row.addView(mark, redParams(8));
                    tl.leftMargin = px(10);
                    row.addView(t, tl);
                } else {
                    row.addView(t, tl);
                    if (mark != null) row.addView(mark, redParams(10));
                    il.leftMargin = px(10);
                    row.addView(cr, il);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = px(8);
                panelBody.addView(row, lp);
            }
        }
    }

    /** A red card: a small red upright card. */
    private View redCard() {
        View v = new View(h.a);
        v.setBackground(h.fill(2, 0xFFE8112D));
        return v;
    }

    /** A yellow card: a small yellow upright card. */
    private View yellowCard() {
        View v = new View(h.a);
        v.setBackground(h.fill(2, 0xFFFFD000));
        return v;
    }

    private LinearLayout.LayoutParams redParams(int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(13), px(18));
        lp.leftMargin = px(left);
        return lp;
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
        LinearLayout top = new LinearLayout(h.a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(crest(F1Results.F1_LOGO, px(30)), new LinearLayout.LayoutParams(px(60), px(30)));
        TextView hd = heading((in.status == null ? "RACE RESULT" : in.status) + "  ·  " + F1Results.shortName(in.board.name).toUpperCase(Locale.UK));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hl.leftMargin = px(12);
        top.addView(hd, hl);
        panelBody.addView(top);
        addGap(6);
        int n = 0, most = in.lines.isEmpty() ? 10 : 9;
        for (F1Results.Row r : in.board.rows) {
            if (n >= most) break;
            Bitmap face = n < in.pics.size() ? in.pics.get(n) : null, team = n < in.teams.size() ? in.teams.get(n) : null;
            n++;
            LinearLayout row = new LinearLayout(h.a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView pos = h.text(20, outfitHeavy, n <= 3 ? 0xFFFFD04D : Color.WHITE);
            pos.setText(String.valueOf(r.pos));
            row.addView(pos, new LinearLayout.LayoutParams(px(34), ViewGroup.LayoutParams.WRAP_CONTENT));
            View bar = new View(h.a);
            bar.setBackground(h.fill(2, F1Results.teamColour(r.teamId)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(px(5), px(34));
            bl.rightMargin = px(10);
            row.addView(bar, bl);
            // His headshot, on his team's colour.
            ImageView f = face(face, px(42));
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor((F1Results.teamColour(r.teamId) & 0xFFFFFF) | 0x59000000);
            f.setBackground(disc);
            row.addView(f, new LinearLayout.LayoutParams(px(42), px(42)));
            TextView who = h.text(19, h.semi, Color.WHITE);
            who.setText((r.first.isEmpty() ? "" : r.first.charAt(0) + ". ") + r.last);
            who.setSingleLine(true);
            who.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            wl.leftMargin = px(12);
            row.addView(who, wl);
            if (team != null) {
                ImageView tl = new ImageView(h.a);
                tl.setScaleType(ImageView.ScaleType.FIT_CENTER);
                tl.setImageBitmap(team);
                LinearLayout.LayoutParams tll = new LinearLayout.LayoutParams(px(30), px(30));
                tll.rightMargin = px(12);
                row.addView(tl, tll);
            }
            TextView gap = h.text(16, h.regular, 0xB3FFFFFF);
            // A race's gap to the winner ("+5.123"); qualifying's lap times and the championship's points as they are.
            boolean asIs = "QUALIFYING".equals(in.status) || "CHAMPIONSHIP".equals(in.status);
            gap.setText(asIs ? (r.gap.isEmpty() ? r.status : r.gap)
                    : n == 1 && r.gap.contains(":") ? r.gap : r.gap.isEmpty() ? r.status : r.gap.startsWith("+") || r.gap.contains("lap") || "DNF".equals(r.gap) ? r.gap : "+" + r.gap);
            gap.setGravity(Gravity.END);
            row.addView(gap, new LinearLayout.LayoutParams(px(96), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(54));
            panelBody.addView(row, lp);
        }
        // Under the board: the fastest lap (a race's, a sprint's), as a fact.
        for (String[] l : in.lines) factRow(l[0], l[1]);
    }

    private void fight(Info in) {
        panelBody.addView(heading(in.competition == null ? "RESULT" : in.competition.toUpperCase(Locale.UK)));
        if (in.a != null) {
            addGap(16);
            String[][] pics = {{in.aPic, in.aFlag}, {in.bPic, in.bFlag}};
            int which = 0;
            for (String[] f : new String[][]{in.a, in.b}) {
                LinearLayout row = new LinearLayout(h.a);
                row.setGravity(Gravity.CENTER_VERTICAL);
                // The fighter's headshot and flag.
                String[] pf = pics[which++];
                row.addView(face(pf[0], px(72)), new LinearLayout.LayoutParams(px(72), px(72)));
                if (pf[1] != null) row.addView(flagView(pf[1]), flagParams(12, 0));
                TextView n = h.text(26, outfitBold, Color.WHITE);
                n.setText(f[0]);
                n.setSingleLine(true);
                n.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nl0 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                nl0.leftMargin = px(14);
                row.addView(n, nl0);
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
        // A leader's headshot beside the words, where ESPN gives one.
        if (l.length > 2 && l[2] != null && !l[2].isEmpty()) {
            LinearLayout row = new LinearLayout(h.a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(face(l[2], px(48)), new LinearLayout.LayoutParams(px(48), px(48)));
            LinearLayout box = new LinearLayout(h.a);
            box.setOrientation(LinearLayout.VERTICAL);
            TextView a = h.text(18, h.semi, Color.WHITE);
            a.setText(l[0]);
            a.setSingleLine(true);
            a.setEllipsize(TextUtils.TruncateAt.END);
            box.addView(a);
            TextView b = h.text(15, h.regular, 0xA6FFFFFF);
            b.setText(l[1]);
            b.setMaxLines(2);
            b.setEllipsize(TextUtils.TruncateAt.END);
            box.addView(b);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            bl.leftMargin = px(12);
            row.addView(box, bl);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = px(12);
            panelBody.addView(row, lp);
            return;
        }
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
        String[][] pics = {{in.aPic, in.aFlag}, {in.bPic, in.bFlag}};
        for (int p = 0; p < 2; p++) {
            String[] f = players[p];
            LinearLayout row = new LinearLayout(h.a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            // The player's flag, and the headshot where ESPN has one.
            if (pics[p][1] != null) row.addView(flagView(pics[p][1]), flagParams(0, 10));
            ImageView pf = face((String) null, px(44));
            pf.setVisibility(View.GONE);
            if (pics[p][0] != null) h.image(pf, pics[p][0], px(88), () -> pf.setVisibility(View.VISIBLE));
            LinearLayout.LayoutParams pfl = new LinearLayout.LayoutParams(px(44), px(44));
            pfl.rightMargin = px(10);
            row.addView(pf, pfl);
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

    /** Rugby: the result with each side's crest, the tries and cards under their own side, then the scoring by type. */
    private void rugby(Info in) {
        compHead(in);
        addGap(16);
        panelBody.addView(scoreLine(in.home, in.away, in.status, true));
        events(in, in.stats.isEmpty() ? 9 : 5);
        if (!in.stats.isEmpty()) {
            rule();
            panelBody.addView(heading("MATCH STATS"));
            int hc = colour(in.home[4], 0xFFFFFFFF), ac = colour(in.away[4], 0xFF7FA7FF);
            if (close(hc, ac)) ac = 0xFF7FA7FF;
            for (String[] st : in.stats) statRow(st, hc, ac);
        }
    }

    /** Cricket: each side with its logo and its innings, the result in words, the player of the match. */
    private void cricket(Info in) {
        SportsHighlights.Brand b = in.brand != null ? in.brand : SportsHighlights.brand(in.clip);
        String comp = in.competition != null ? in.competition : b.name;
        panelBody.addView(heading((in.status == null || in.status.isEmpty() ? "RESULT" : in.status.toUpperCase(Locale.UK))
                + (comp == null ? "" : "  ·  " + comp.toUpperCase(Locale.UK))));
        if (in.description != null && !in.description.isEmpty()) {
            TextView d = h.text(16, h.regular, 0xB3FFFFFF);
            d.setText(in.description);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dl.topMargin = px(6);
            panelBody.addView(d, dl);
        }
        addGap(10);
        for (String[] side : new String[][]{in.home, in.away}) {
            LinearLayout row = new LinearLayout(h.a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(crest(side[2], px(52)), new LinearLayout.LayoutParams(px(52), px(52)));
            LinearLayout words = new LinearLayout(h.a);
            words.setOrientation(LinearLayout.VERTICAL);
            TextView n = h.text(19, h.semi, 0xD9FFFFFF);
            n.setText(side[0]);
            n.setSingleLine(true);
            n.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(n);
            TextView sc = h.text(26, outfitHeavy, Color.WHITE);
            sc.setText(side[3] == null || side[3].isEmpty() ? "–" : side[3]);
            sc.setSingleLine(true);
            sc.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(sc);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            wl.leftMargin = px(14);
            row.addView(words, wl);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = px(14);
            panelBody.addView(row, lp);
        }
        if (in.result != null && !in.result.isEmpty()) {
            rule();
            TextView r = h.text(22, outfitBold, 0xFFFFD04D);
            r.setText(in.result);
            r.setMaxLines(2);
            r.setEllipsize(TextUtils.TruncateAt.END);
            panelBody.addView(r);
        }
        if (in.potm != null) {
            rule();
            panelBody.addView(heading("PLAYER OF THE MATCH"));
            lineRow(in.potm);
        }
    }

    /**
     * Nothing more found about the event: what is known of it, set out as its facts. The two sides (each crest beside
     * its own club where there is one, the score from the title between), then the competition, the day, the round,
     * where, the coverage and the kind of video. Never the video's description.
     */
    private void facts(Info in) {
        SportsHighlights.Clip c = in.clip;
        SportsHighlights.Brand b = in.brand != null ? in.brand : SportsHighlights.brand(c);
        String logo = in.compLogo != null ? in.compLogo : b.logo;
        LinearLayout top = new LinearLayout(h.a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (logo != null) top.addView(crest(logo, px(34)), new LinearLayout.LayoutParams(px(34), px(34)));
        TextView hd = heading("THE EVENT  ·  " + (in.competition != null ? in.competition : b.name).toUpperCase(Locale.UK));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hl.leftMargin = px(logo != null ? 12 : 0);
        top.addView(hd, hl);
        panelBody.addView(top);
        if (in.a != null && in.b != null) {
            addGap(12);
            String[][] sides = {{in.a[0], in.aPic, in.score == null ? null : in.score[0]}, {in.b[0], in.bPic, in.score == null ? null : in.score[1]}};
            for (String[] sd : sides) {
                LinearLayout row = new LinearLayout(h.a);
                row.setGravity(Gravity.CENTER_VERTICAL);
                // The club's crest where it has one; otherwise a soft disc with the side's first letter.
                if (sd[1] != null) {
                    row.addView(crest(sd[1], px(52)), new LinearLayout.LayoutParams(px(52), px(52)));
                } else {
                    TextView disc = h.text(22, outfitBold, Color.WHITE);
                    disc.setText(sd[0].isEmpty() ? "" : sd[0].substring(0, 1).toUpperCase(Locale.UK));
                    disc.setGravity(Gravity.CENTER);
                    GradientDrawable g = new GradientDrawable();
                    g.setShape(GradientDrawable.OVAL);
                    g.setColor((accent & 0xFFFFFF) | 0x40000000);
                    disc.setBackground(g);
                    row.addView(disc, new LinearLayout.LayoutParams(px(52), px(52)));
                }
                TextView n = h.text(24, outfitBold, Color.WHITE);
                n.setText(sd[0]);
                n.setSingleLine(true);
                n.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                nl.leftMargin = px(14);
                row.addView(n, nl);
                if (sd[2] != null) {
                    TextView sc = h.text(34, outfitHeavy, Color.WHITE);
                    sc.setText(sd[2]);
                    row.addView(sc);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = px(12);
                panelBody.addView(row, lp);
            }
        }
        rule();
        if (b.name != null && !b.name.equalsIgnoreCase(c.sport)) factRow("Competition", in.competition != null ? in.competition : b.name);
        factRow("Sport", sportName(c.sport));
        String when = in.when != null ? in.when : c.published > 0 ? DAY.format(Instant.ofEpochMilli(c.published)) : null;
        if (when != null) factRow("Date", when);
        if (in.round != null) factRow("Round", in.round);
        if (in.venue != null && !in.venue.isEmpty()) factRow("Venue", in.venue);
        if (c.channel != null && !c.channel.isEmpty()) factRow("Coverage", c.channel);
        factRow("Video", titleCase(kindWords(c)));
    }

    /** A fact: its name at the left, what it is at the right. */
    private void factRow(String label, String value) {
        if (value == null || value.isEmpty()) return;
        LinearLayout row = new LinearLayout(h.a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = h.text(16, h.regular, 0xB3FFFFFF);
        l.setText(label);
        row.addView(l, new LinearLayout.LayoutParams(px(150), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView v = h.text(18, h.semi, Color.WHITE);
        v.setText(value);
        v.setGravity(Gravity.END);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px(12);
        panelBody.addView(row, lp);
    }

    /** A sport's row name in words for a fact ("MMA · UFC" → "MMA · UFC", "FORMULA 1" → "Formula 1"). */
    private static String sportName(String sport) {
        if (sport == null || sport.isEmpty()) return null;
        if (sport.equals("NFL") || sport.equals("NBA") || sport.contains("UFC")) return sport;
        return titleCase(sport);
    }

    /** "EXTENDED HIGHLIGHTS" as "Extended highlights". */
    private static String titleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        String low = s.toLowerCase(Locale.UK);
        return Character.toUpperCase(low.charAt(0)) + low.substring(1);
    }

    // ---- finding the event (off the main thread) -------------------------------------------------------------------

    private static final Map<String, Object[]> boards = new HashMap<>();
    private static final String ESPN = "https://site.api.espn.com/apis/site/v2/sports/";

    /** A scoreboard (ESPN's, a sport path and a day), kept ten minutes. */
    private static JSONObject board(String path, String day) {
        return board(path, "/scoreboard" + (day == null ? "" : "?dates=" + day), day);
    }

    /** ESPN's answer for a sport path and what is asked of it ("/scoreboard?dates=…"), kept ten minutes. */
    private static JSONObject board(String path, String what, String day) {
        String key = path + what + "@" + day;
        synchronized (boards) {
            Object[] k = boards.get(key);
            if (k != null && System.currentTimeMillis() - (long) k[0] < 600_000L) return (JSONObject) k[1];
        }
        JSONObject j = null;
        try {
            j = new JSONObject(ScoresTicker.get(ESPN + path + what));
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

    /**
     * The event a video is of, with its stats: football's game (any competition ESPN covers, by the fixture in the
     * title, with or without its score, else both clubs named, else the one club's latest game), F1's session (the
     * Grand Prix's race, sprint or qualifying; the championship for a practice), UFC's fight, tennis's match, rugby's
     * and cricket's game, NFL's and NBA's; when none can be found, the event's own facts (never null).
     */
    static Info find(SportsHighlights.Clip c, String[] leagues) {
        String sport = c.sport == null ? "" : c.sport;
        Info in = null;
        try {
            if (sport.startsWith("FOOTBALL")) {
                in = footballAll(c);
                if (in == null) in = football(c, leagues);
                if (in == null) in = footballOne(c);
            } else if (sport.startsWith("FORMULA")) {
                in = f1(c);
            } else if (sport.contains("UFC")) {
                in = ufc(c);
            } else if (sport.startsWith("TENNIS")) {
                in = tennis(c);
            } else if (sport.equals("NFL")) {
                in = game(c, "football/nfl", "NFL");
            } else if (sport.equals("NBA")) {
                in = game(c, "basketball/nba", "NBA");
            } else if (sport.startsWith("RUGBY")) {
                in = teamSport(c, "rugby", Info.RUGBY);
            } else if (sport.startsWith("CRICKET")) {
                in = teamSport(c, "cricket", Info.CRICKET);
            }
        } catch (Exception e) {
            in = null;
        }
        return in != null ? in : facts(c);
    }

    /** The facts, and should even reading them fail, the bare facts the clip itself gives. */
    private static Info safeFacts(SportsHighlights.Clip c) {
        try {
            return facts(c);
        } catch (Exception e) {
            Info in = new Info();
            in.kind = Info.FACTS;
            in.clip = c;
            return in;
        }
    }

    /**
     * What is known of an event without its result: the two sides the title names ("A v B", "A 2-1 B"; football's
     * crests looked up), the score the title gives, when the video went up.
     */
    static Info facts(SportsHighlights.Clip c) {
        Info in = new Info();
        in.kind = Info.FACTS;
        in.clip = c;
        String[] fx = fixture(c.title);
        String[] v = fx != null ? new String[]{fx[0], fx[3]} : vsFixture(c.title);
        if (v != null) {
            in.a = new String[]{v[0], "", ""};
            in.b = new String[]{v[1], "", ""};
            if (fx != null) in.score = new String[]{fx[1], fx[2]};
            if (c.sport != null && c.sport.startsWith("FOOTBALL")) {
                try {
                    in.aPic = PlaylistSport.crest(v[0]);
                    in.bPic = PlaylistSport.crest(v[1]);
                } catch (Exception ignored) {
                    // no crests: the discs with the first letters
                }
            }
        }
        if (c.sport != null && c.sport.startsWith("FORMULA")) in.eventName = grandPrix(c.title);
        return in;
    }

    private static final java.util.regex.Pattern VS = java.util.regex.Pattern.compile(
            "^(.*?\\S)\\s+(?:v|vs|vs\\.|versus)\\s+(\\S.*)$", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** "Arsenal v Chelsea", "Usyk vs Fury" anywhere in a title (each part between "|" tried): {first, second}; null without. */
    static String[] vsFixture(String title) {
        if (title == null) return null;
        for (String part : title.split("[|•]")) {
            String seg = part.replaceAll("[\\p{So}\\p{Cn}\\x{1F000}-\\x{1FFFF}]", " ").replaceAll("#\\S+", " ").trim();
            java.util.regex.Matcher m = VS.matcher(seg);
            if (!m.find()) continue;
            String a = tidyClub(m.group(1), true), b = tidyClub(m.group(2), false)
                    .replaceAll("(?i)\\s+(full fight|fight|extended|knockout|ko|press conference|weigh.?in|preview)\\b.*$", "").trim();
            if (a.length() < 2 || b.length() < 2 || a.length() > 32 || b.length() > 32) continue;
            return new String[]{a, b};
        }
        return null;
    }

    // ---- football: any game ESPN covers, by the score in the title ------------------------------------------------

    private static final java.util.regex.Pattern SCORE = java.util.regex.Pattern.compile(
            "^(.*?[^0-9\\s])\\s+(\\d{1,2})\\s*[-–—]\\s*(\\d{1,2})\\s+([^0-9].*?)$");

    /**
     * "Newcastle 0-1 Everton" anywhere in a title (its parts between "|" each tried): {home, home goals, away goals,
     * away}; null when there is none. The words round it (Highlights, the competition) are left off the names.
     */
    static String[] fixture(String title) {
        if (title == null) return null;
        for (String part : title.split("[|•]")) {
            String seg = part.replaceAll("[\\p{So}\\p{Cn}\\x{1F000}-\\x{1FFFF}]", " ").replaceAll("#\\S+", " ").trim();
            java.util.regex.Matcher m = SCORE.matcher(seg);
            if (!m.find()) continue;
            String home = tidyClub(m.group(1), true), away = tidyClub(m.group(4), false);
            if (home.length() < 2 || away.length() < 2 || home.length() > 32 || away.length() > 32) continue;
            return new String[]{home, m.group(2), m.group(3), away};
        }
        return null;
    }

    /** A club's name out of the words round a score: what comes before ":" or "!" on its side goes, as do the tails. */
    private static String tidyClub(String s, boolean home) {
        String t = s.trim();
        if (home) {
            int cut = Math.max(Math.max(t.lastIndexOf(':'), t.lastIndexOf('!')), Math.max(t.lastIndexOf('?'), t.lastIndexOf(" - ")));
            if (cut >= 0) t = t.substring(cut + 1);
            t = t.replaceAll("(?i)^(extended |full )?(highlights?|recap|report)\\s*", "");
        } else {
            t = t.replaceAll("(?i)\\s*[-:(\\[].*$", "").replaceAll("(?i)\\s+(extended |full )?(highlights?|recap|goals?|report)\\b.*$", "");
        }
        return t.replaceAll("\\s+", " ").trim();
    }

    /** A club's name for matching: lower case, no accents, the words every club has (FC, AFC, United's Utd) evened out. */
    private static String clubKey(String s) {
        String p = " " + OfficialVideos.plain(s) + " ";
        p = p.replace(" utd ", " united ").replace(" and ", " ").replace(" st ", " saint ");
        for (String w : new String[]{" fc ", " afc ", " cf ", " sc ", " ac ", " cfc ", " fk ", " sk ", " cd ", " ud ", " ssc ", " as ", " sv ", " vfb ", " vfl ", " the "}) p = p.replace(w, " ");
        return p.replaceAll("\\s+", " ").trim();
    }

    /** The names a title may call a club by, beyond ESPN's own: {one of ESPN's, what titles say}. */
    private static final String[][] ALSO = {{"internazionale", "inter"}, {"manchester united", "man united"}, {"manchester city", "man city"},
            {"tottenham hotspur", "spurs"}, {"wolverhampton wanderers", "wolves"}, {"paris saint germain", "psg"}, {"atletico madrid", "atleti"},
            {"barcelona", "barca"}, {"nottingham forest", "forest"}, {"brighton hove albion", "brighton"}, {"west ham united", "west ham"},
            {"newcastle united", "newcastle"}, {"bayern munich", "bayern"}, {"borussia dortmund", "dortmund"}, {"juventus", "juve"},
            {"milan", "milan"}, {"al hilal", "al hilal"}, {"celtic", "celtic"}, {"rangers", "rangers"}};

    /** Whether a name from a title is this side of ESPN's game (its display, short, plain and place names, its abbreviation). */
    private static boolean sameClub(String fromTitle, JSONObject competitor) {
        JSONObject t = competitor == null ? null : competitor.optJSONObject("team");
        if (t == null) return false;
        String want = clubKey(fromTitle);
        if (want.isEmpty()) return false;
        if (fromTitle.trim().equalsIgnoreCase(t.optString("abbreviation"))) return true;
        for (String n : new String[]{t.optString("displayName"), t.optString("shortDisplayName"), t.optString("name"), t.optString("location")}) {
            String k = clubKey(n);
            if (k.isEmpty()) continue;
            if (k.equals(want)) return true;
            // One inside the other as whole words: "newcastle" and "newcastle united", "milan" and "ac milan".
            if ((" " + k + " ").contains(" " + want + " ") || (" " + want + " ").contains(" " + k + " ")) return true;
            for (String[] a : ALSO) if (k.contains(a[0]) && (want.equals(a[1]) || want.equals(a[0]))) return true;
        }
        return false;
    }

    /** One day of every football game ESPN covers, its events (kept ten minutes, the big answer read once). */
    private static final Map<String, Object[]> allDays = new HashMap<>();

    private static JSONArray allGames(String day) {
        synchronized (allDays) {
            Object[] k = allDays.get(day);
            if (k != null && System.currentTimeMillis() - (long) k[0] < 600_000L) return (JSONArray) k[1];
        }
        JSONArray ev = null;
        try {
            // A busy Saturday is over 2.5 MB: read in full (ScoresTicker.get stops at 2), then only what is used kept.
            ev = new JSONObject(bigGet(ESPN + "soccer/all/scoreboard?dates=" + day + "&limit=500")).optJSONArray("events");
            for (int i = 0; ev != null && i < ev.length(); i++) prune(ev.optJSONObject(i));
        } catch (Exception | OutOfMemoryError ignored) {
            ev = null;
        }
        synchronized (allDays) {
            allDays.put(day, new Object[]{System.currentTimeMillis(), ev});
            // Only the last few days kept.
            if (allDays.size() > 6) allDays.keySet().removeIf(d -> !d.equals(day) && allDays.size() > 6);
        }
        return ev;
    }

    /**
     * A football video's game from ESPN's scoreboard of every competition: the score in its title ("Falkirk 2-0
     * Celtic"), both clubs matched loosely, on the day it went up or the four before; a game with that very score
     * first. Then its stats, its competition (logo and colours) and where it was played.
     */
    private static Info footballAll(SportsHighlights.Clip c) {
        String[] fx = fixture(c.title);
        // "Arsenal v Chelsea | Highlights": the two clubs without the score (the game nearest the upload, then).
        if (fx == null) {
            String[] v = vsFixture(c.title);
            if (v == null) return null;
            fx = new String[]{v[0], "", "", v[1]};
        }
        long p = c.published > 0 ? c.published : System.currentTimeMillis();
        DateTimeFormatter f = DateTimeFormatter.BASIC_ISO_DATE.withZone(java.time.ZoneOffset.UTC);
        List<String> days = new ArrayList<>();
        for (int d = 0; d <= 4; d++) {
            String day = f.format(Instant.ofEpochMilli(p - d * 86_400_000L));
            if (!days.contains(day)) days.add(day);
        }
        JSONObject best = null;
        boolean bestExact = false, bestSwapped = false;
        for (String day : days) {
            JSONArray ev = allGames(day);
            for (int i = 0; ev != null && i < ev.length(); i++) {
                JSONObject e = ev.optJSONObject(i);
                JSONObject comp = e == null || e.optJSONArray("competitions") == null ? null : e.optJSONArray("competitions").optJSONObject(0);
                JSONArray cs = comp == null ? null : comp.optJSONArray("competitors");
                if (cs == null || cs.length() < 2) continue;
                JSONObject home = null, away = null;
                for (int k = 0; k < cs.length(); k++) {
                    JSONObject x = cs.optJSONObject(k);
                    if ("home".equals(x.optString("homeAway"))) home = x; else away = x;
                }
                if (home == null || away == null) continue;
                if (espnTime(e.optString("date")) > p + 3_600_000L) continue;
                boolean straight = sameClub(fx[0], home) && sameClub(fx[3], away), swapped = !straight && sameClub(fx[0], away) && sameClub(fx[3], home);
                if (!straight && !swapped) continue;
                String hs = home.optString("score"), as = away.optString("score");
                boolean exact = straight ? hs.equals(fx[1]) && as.equals(fx[2]) : hs.equals(fx[2]) && as.equals(fx[1]);
                if (best == null || (exact && !bestExact)) { best = e; bestExact = exact; bestSwapped = swapped; }
                if (exact) break;
            }
            if (best != null && bestExact) break;
        }
        return best == null ? null : fromAllGame(best);
    }

    /** A game off ESPN's every-competition board, with its goals, its competition (logo, colours), stats and venue. */
    private static Info fromAllGame(JSONObject best) {
        JSONObject comp = best.optJSONArray("competitions").optJSONObject(0);
        JSONObject home = null, away = null;
        JSONArray cs = comp.optJSONArray("competitors");
        for (int k = 0; k < cs.length(); k++) {
            JSONObject x = cs.optJSONObject(k);
            if ("home".equals(x.optString("homeAway"))) home = x; else away = x;
        }
        Info in = new Info();
        in.kind = Info.FOOTBALL;
        in.home = side(home);
        in.away = side(away);
        JSONObject st = comp.optJSONObject("status");
        String state = st == null || st.optJSONObject("type") == null ? "" : st.optJSONObject("type").optString("state");
        in.status = "post".equals(state) ? "FT" : st == null || st.optJSONObject("type") == null ? null : st.optJSONObject("type").optString("shortDetail");
        in.when = day(best.optString("date"));
        JSONObject v = comp.optJSONObject("venue");
        if (v == null) v = best.optJSONObject("venue");
        if (v != null) in.venue = v.optString("fullName", v.optString("displayName", null));
        String homeId = home.optJSONObject("team") == null ? "" : home.optJSONObject("team").optString("id");
        goals(in, comp, homeId);
        // Its competition and stats from the game's own summary (any competition's, else its league's).
        String id = best.optString("id");
        JSONObject sum = null;
        try {
            sum = new JSONObject(ScoresTicker.get(ESPN + "soccer/all/summary?event=" + id));
        } catch (Exception ignored) {
        }
        JSONObject league = sum == null || sum.optJSONObject("header") == null ? null : sum.optJSONObject("header").optJSONObject("league");
        String slug = league == null ? null : league.optString("slug", null);
        if (league != null) in.competition = leagueName(league);
        if (slug != null) {
            in.brand = SportsHighlights.footballBrandBySlug(slug);
            if (in.brand == null) in.compLogo = leagueLogo("soccer/" + slug);
            if (sum == null || sum.optJSONObject("boxscore") == null) stats(in, "soccer/" + slug, id, homeId);
        }
        if (sum != null && sum.optJSONObject("boxscore") != null) statsFrom(in, sum, homeId);
        if (in.competition == null) in.competition = best.optJSONObject("season") == null ? null : best.optJSONObject("season").optString("name", null);
        return in;
    }

    /** A whole answer, up to 8 MB (ESPN's day of every competition). */
    private static String bigGet(String url) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(20000);
        try (java.io.InputStream in = conn.getInputStream()) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(1 << 20);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0 && out.size() < 8 * 1024 * 1024) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    /** A game on the day's board without what is never read here (broadcasts, odds, headlines, players' stats). */
    private static void prune(JSONObject e) {
        if (e == null) return;
        e.remove("links");
        JSONArray comps = e.optJSONArray("competitions");
        for (int i = 0; comps != null && i < comps.length(); i++) {
            JSONObject c = comps.optJSONObject(i);
            if (c == null) continue;
            for (String k : new String[]{"broadcasts", "geoBroadcasts", "broadcast", "headlines", "odds", "notes", "format", "playByPlayAthletes", "leaders"}) c.remove(k);
            JSONArray cs = c.optJSONArray("competitors");
            for (int j = 0; cs != null && j < cs.length(); j++) {
                JSONObject x = cs.optJSONObject(j);
                if (x == null) continue;
                for (String k : new String[]{"statistics", "leaders", "records", "form", "links", "linescores"}) x.remove(k);
                JSONObject t = x.optJSONObject("team");
                if (t != null) t.remove("links");
            }
        }
    }

    /** A league's own logo (ESPN's, for a dark ground where it has one) from its scoreboard; null when it has none. */
    private static String leagueLogo(String path) {
        JSONObject b = board(path, null);
        JSONArray lg = b == null ? null : b.optJSONArray("leagues");
        JSONObject l = lg == null ? null : lg.optJSONObject(0);
        JSONArray logos = l == null ? null : l.optJSONArray("logos");
        String any = null;
        for (int i = 0; logos != null && i < logos.length(); i++) {
            JSONObject x = logos.optJSONObject(i);
            String href = x.optString("href", null);
            if (href == null) continue;
            if (any == null) any = href;
            JSONArray rel = x.optJSONArray("rel");
            if (rel != null && rel.toString().contains("dark")) return href;
        }
        return any;
    }

    /** The goals and red cards of a game on ESPN's scoreboard: {home|away, goal|pen|og|red, who, minute}. */
    private static void goals(Info in, JSONObject comp, String homeId) {
        JSONArray det = comp.optJSONArray("details");
        for (int k = 0; det != null && k < det.length(); k++) {
            JSONObject d = det.optJSONObject(k);
            if (d == null) continue;
            boolean red = d.optBoolean("redCard"), goal = d.optBoolean("scoringPlay");
            if (!red && !goal) continue;
            JSONObject team = d.optJSONObject("team");
            String side = team != null && homeId.equals(team.optString("id")) ? "home" : "away";
            boolean og = d.optBoolean("ownGoal");
            // ESPN already gives an own goal the side it counted for.
            JSONArray who = d.optJSONArray("athletesInvolved");
            JSONObject pl = who == null ? null : who.optJSONObject(0);
            String name = pl == null ? "" : pl.optString("shortName", pl.optString("displayName"));
            String min = d.optJSONObject("clock") == null ? "" : d.optJSONObject("clock").optString("displayValue");
            in.events.add(new String[]{side, red ? "red" : og ? "og" : d.optBoolean("penaltyKick") ? "pen" : "goal", name, min});
        }
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
                    goals(in, comp, homeId);
                    in.when = day(e.optString("date"));
                    JSONObject v = comp.optJSONObject("venue");
                    if (v != null) in.venue = v.optString("fullName", null);
                    in.brand = SportsHighlights.footballBrandBySlug(slug);
                    if (in.brand == null) in.compLogo = leagueLogo("soccer/" + slug);
                    stats(in, "soccer/" + slug, e.optString("id"), homeId);
                    return in;
                }
            }
        }
        return null;
    }

    /**
     * A highlights video that names one club only ("Haaland's hat-trick | Man City highlights"): that club's latest
     * finished game, on the day it went up or the two before, from ESPN's board of every competition.
     */
    private static Info footballOne(SportsHighlights.Clip c) {
        String low = c.title == null ? "" : c.title.toLowerCase(Locale.UK);
        if (!c.highlight && !low.contains("highlight") && !low.contains("goals") && !low.contains("extended")) return null;
        String t = " " + OfficialVideos.plain(c.title) + " ";
        long p = c.published > 0 ? c.published : System.currentTimeMillis();
        DateTimeFormatter f = DateTimeFormatter.BASIC_ISO_DATE.withZone(java.time.ZoneOffset.UTC);
        for (int d = 0; d <= 2; d++) {
            JSONArray ev = allGames(f.format(Instant.ofEpochMilli(p - d * 86_400_000L)));
            JSONObject latest = null;
            long latestAt = 0;
            for (int i = 0; ev != null && i < ev.length(); i++) {
                JSONObject e = ev.optJSONObject(i);
                JSONObject comp = e == null || e.optJSONArray("competitions") == null ? null : e.optJSONArray("competitions").optJSONObject(0);
                JSONObject st = comp == null ? null : comp.optJSONObject("status");
                if (st == null || st.optJSONObject("type") == null || !"post".equals(st.optJSONObject("type").optString("state"))) continue;
                long at = espnTime(e.optString("date"));
                if (at > p) continue;
                JSONArray cs = comp.optJSONArray("competitors");
                boolean named = false;
                for (int k = 0; cs != null && k < cs.length() && !named; k++) {
                    JSONObject team = cs.optJSONObject(k).optJSONObject("team");
                    if (team == null) continue;
                    // Its whole name, or its short one when that is distinctive (four letters and more).
                    String full = OfficialVideos.plain(team.optString("displayName")), sh = OfficialVideos.plain(team.optString("shortDisplayName"));
                    named = full.length() >= 4 && t.contains(" " + full + " ") || sh.length() >= 4 && t.contains(" " + sh + " ");
                }
                if (named && at > latestAt) { latest = e; latestAt = at; }
            }
            if (latest != null) return fromAllGame(latest);
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
            statsFrom(in, new JSONObject(ScoresTicker.get(ESPN + path + "/summary?event=" + eventId)), homeId);
        } catch (Exception ignored) {
        }
    }

    private static void statsFrom(Info in, JSONObject s, String homeId) {
        try {
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
                    {"wonCorners", "Corners"}, {"foulsCommitted", "Fouls"}, {"saves", "Saves"}, {"offsides", "Offsides"}};
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

    /** The season's Grand Prix weekends (Jolpica's schedule), kept an hour. */
    private static JSONArray f1Season;
    private static long f1SeasonAt;

    private static synchronized JSONArray f1Schedule() {
        if (f1Season != null && System.currentTimeMillis() - f1SeasonAt < 3_600_000L) return f1Season;
        try {
            f1Season = new JSONObject(ScoresTicker.get("https://api.jolpi.ca/ergast/f1/current.json"))
                    .getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races");
            f1SeasonAt = System.currentTimeMillis();
        } catch (Exception ignored) {
            // asked again next time
        }
        return f1Season;
    }

    /** A weekend's day as millis (its date at noon UTC); 0 when unreadable. */
    private static long f1Day(JSONObject o) {
        if (o == null) return 0;
        try {
            return java.time.LocalDate.parse(o.optString("date")).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * The weekend a video is of: the Grand Prix its title names ("Mexico City Grand Prix", or the place: "Mexico",
     * "Brazil", "Las Vegas"), else the latest whose first practice came before the video went up.
     */
    private static JSONObject f1Weekend(SportsHighlights.Clip c) {
        JSONArray races = f1Schedule();
        if (races == null) return null;
        String gp = grandPrix(c.title), t = " " + OfficialVideos.plain(c.title) + " ";
        long up = c.published > 0 ? c.published : System.currentTimeMillis();
        JSONObject named = null, latest = null;
        for (int i = 0; i < races.length(); i++) {
            JSONObject r = races.optJSONObject(i);
            if (r == null) continue;
            String name = r.optString("raceName");
            JSONObject loc = r.optJSONObject("Circuit") == null ? null : r.optJSONObject("Circuit").optJSONObject("Location");
            String place = OfficialVideos.plain(name.replace("Grand Prix", ""));
            boolean says = gp != null && F1Results.sameGrandPrix(gp, name) || place.length() >= 4 && t.contains(" " + place + " ")
                    || loc != null && (t.contains(" " + OfficialVideos.plain(loc.optString("country")) + " ") && OfficialVideos.plain(loc.optString("country")).length() >= 4
                    || t.contains(" " + OfficialVideos.plain(loc.optString("locality")) + " ") && OfficialVideos.plain(loc.optString("locality")).length() >= 4);
            long start = f1Day(r.optJSONObject("FirstPractice"));
            if (start == 0) start = f1Day(r) - 2 * 86_400_000L;
            // Named and already under way when the video went up (a season can visit a country twice).
            if (says && start <= up + 86_400_000L) named = r;
            if (start <= up + 86_400_000L) latest = r;
        }
        return named != null ? named : latest;
    }

    /** A session's classification for a round (results, sprint, qualifying), top to bottom; null when not run yet. */
    private static F1Results.Board f1Board(String round, String kind, List<String[]> fastest) {
        try {
            JSONObject root = new JSONObject(ScoresTicker.get("https://api.jolpi.ca/ergast/f1/current/" + round + "/" + kind + ".json"));
            JSONArray races = root.getJSONObject("MRData").getJSONObject("RaceTable").getJSONArray("Races");
            if (races.length() == 0) return null;
            JSONObject race = races.getJSONObject(0);
            F1Results.Board b = new F1Results.Board();
            b.name = race.optString("raceName");
            b.round = race.optString("round");
            boolean quali = kind.equals("qualifying");
            JSONArray results = race.getJSONArray(quali ? "QualifyingResults" : kind.equals("sprint") ? "SprintResults" : "Results");
            String fastName = null, fastTime = null, fastLap = null;
            for (int i = 0; i < results.length(); i++) {
                JSONObject x = results.getJSONObject(i);
                F1Results.Row r = F1Results.driver(x);
                r.pos = i + 1;
                if (quali) {
                    r.gap = x.optString("Q3", x.optString("Q2", x.optString("Q1", "")));
                    r.status = x.has("Q3") ? "Q3" : x.has("Q2") ? "Q2" : "Q1";
                } else {
                    r.status = x.optString("status");
                    try { r.points = (int) Math.round(Double.parseDouble(x.optString("points", ""))); } catch (NumberFormatException ignored) { }
                    JSONObject time = x.optJSONObject("Time");
                    if (time != null) r.gap = time.optString("time");
                    else if (r.status.startsWith("+")) r.gap = r.status.replace(" Laps", " laps").replace(" Lap", " lap");
                    else if (!"Finished".equals(r.status) && !r.status.contains("Lap")) r.gap = "DNF";
                    JSONObject fl = x.optJSONObject("FastestLap");
                    if (fl != null && "1".equals(fl.optString("rank"))) {
                        fastName = (r.first.isEmpty() ? "" : r.first.charAt(0) + ". ") + r.last;
                        fastTime = fl.optJSONObject("Time") == null ? "" : fl.optJSONObject("Time").optString("time");
                        fastLap = fl.optString("lap");
                    }
                }
                b.rows.add(r);
            }
            if (fastName != null && fastest != null) {
                fastest.add(new String[]{"Fastest lap", fastName + "  ·  " + fastTime + (fastLap == null || fastLap.isEmpty() ? "" : "  (lap " + fastLap + ")")});
            }
            return b.rows.isEmpty() ? null : b;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Formula 1: the session the video is of, for its own Grand Prix: the race's classification (the sprint's, or
     * qualifying's), with the fastest lap; a practice, or a session not run yet, the championship as it stands. The
     * round, the day it was run and the circuit with it.
     */
    private static Info f1(SportsHighlights.Clip c) {
        String t = c.title == null ? "" : c.title.toLowerCase(Locale.ROOT);
        boolean sprintQuali = t.contains("sprint qualifying") || t.contains("sprint shootout");
        boolean quali = !sprintQuali && t.contains("qualifying");
        boolean sprint = !sprintQuali && t.contains("sprint");
        boolean practice = t.matches("(?s).*\\b(fp1|fp2|fp3|practice)\\b.*");
        JSONObject w = f1Weekend(c);
        if (w == null) {
            // No schedule to go by: the last race, as before.
            F1Results.Board b = F1Results.jolpicaResult(null, sprint);
            if (b == null && sprint) b = F1Results.jolpicaResult(null, false);
            if (b == null || b.rows.isEmpty()) return null;
            Info in = new Info();
            in.kind = Info.F1;
            in.board = b;
            in.competition = F1Results.shortName(b.name);
            in.status = sprint ? "SPRINT RESULT" : "RACE RESULT";
            in.eventName = F1Results.shortName(b.name);
            if (b.round != null && !b.round.isEmpty()) in.round = "Round " + b.round;
            return in;
        }
        String round = w.optString("round");
        Info in = new Info();
        in.kind = Info.F1;
        F1Results.Board b = null;
        JSONObject session = w;
        if (!practice && !sprintQuali) {
            if (quali) {
                b = f1Board(round, "qualifying", null);
                in.status = "QUALIFYING";
                session = w.optJSONObject("Qualifying");
            } else if (sprint) {
                b = f1Board(round, "sprint", in.lines);
                in.status = "SPRINT RESULT";
                session = w.optJSONObject("Sprint");
            } else {
                b = f1Board(round, "results", in.lines);
                in.status = "RACE RESULT";
            }
        }
        if (b == null) {
            // A practice, or a session still to come: the drivers' championship as it stands, its points.
            List<F1Results.Row> st = F1Results.standings(null);
            if (st.isEmpty()) return null;
            b = new F1Results.Board();
            b.name = w.optString("raceName");
            b.round = round;
            for (int i = 0; i < Math.min(10, st.size()); i++) {
                F1Results.Row r = st.get(i);
                r.pos = i + 1;
                b.rows.add(r);
            }
            in.lines.clear();
            in.status = "CHAMPIONSHIP";
            session = null;
        }
        in.board = b;
        in.competition = F1Results.shortName(w.optString("raceName"));
        in.eventName = in.competition;
        if (!round.isEmpty()) in.round = "Round " + round;
        long day = f1Day(session != null ? session : w);
        if (day > 0) in.when = DAY.format(Instant.ofEpochMilli(day));
        JSONObject circuit = w.optJSONObject("Circuit");
        if (circuit != null) {
            JSONObject loc = circuit.optJSONObject("Location");
            in.venue = circuit.optString("circuitName") + (loc == null ? "" : ", " + loc.optString("locality"));
        }
        return in;
    }

    /** ESPN's scorepanel (every competition of a sport, for a day), kept ten minutes. */
    private static JSONObject panelBoard(String sport, String day) {
        return board(sport, "/scorepanel?dates=" + day, day);
    }

    /**
     * Rugby and cricket: the game of the two sides in the title ("England v New Zealand", either way round), else
     * both named in it, from ESPN's every-competition board for the days the video may be of. Rugby: the score, the
     * tries and cards under their own side, and the scoring by type; cricket: each side's innings, the result, the
     * player of the match.
     */
    private static Info teamSport(SportsHighlights.Clip c, String sport, int kind) {
        String[] fx = fixture(c.title);
        String[] v = fx != null ? new String[]{fx[0], fx[3]} : vsFixture(c.title);
        String t = " " + OfficialVideos.plain(c.title) + " ";
        long latest = (c.published > 0 ? c.published : System.currentTimeMillis()) + 3_600_000L;
        for (String day : days(c.published)) {
            Object[] loose = null;
            JSONObject b = panelBoard(sport, day);
            JSONArray groups = b == null ? null : b.optJSONArray("scores");
            for (int g = 0; groups != null && g < groups.length(); g++) {
                JSONObject grp = groups.optJSONObject(g);
                JSONArray events = grp == null ? null : grp.optJSONArray("events");
                JSONArray lgs = grp == null ? null : grp.optJSONArray("leagues");
                JSONObject league = lgs == null ? null : lgs.optJSONObject(0);
                for (int i = 0; events != null && i < events.length(); i++) {
                    JSONObject e = events.optJSONObject(i);
                    JSONObject comp = e == null || e.optJSONArray("competitions") == null ? null : e.optJSONArray("competitions").optJSONObject(0);
                    JSONArray cs = comp == null ? null : comp.optJSONArray("competitors");
                    if (cs == null || cs.length() < 2) continue;
                    JSONObject home = null, away = null;
                    for (int k = 0; k < cs.length(); k++) {
                        JSONObject x = cs.optJSONObject(k);
                        if ("home".equals(x.optString("homeAway")) && home == null) home = x; else away = x;
                    }
                    if (home == null || away == null) continue;
                    if (espnTime(e.optString("date")) > latest) continue;
                    boolean match = v != null
                            ? sameClub(v[0], home) && sameClub(v[1], away) || sameClub(v[0], away) && sameClub(v[1], home)
                            : home.optJSONObject("team") != null && away.optJSONObject("team") != null
                            && OfficialVideos.named(t, home.optJSONObject("team").optString("displayName"))
                            && OfficialVideos.named(t, away.optJSONObject("team").optString("displayName"));
                    if (!match) continue;
                    // Both sides by their very names first ("England", not "England Women" or "England A").
                    boolean exact = v != null && (exactClub(v[0], home) && exactClub(v[1], away) || exactClub(v[0], away) && exactClub(v[1], home));
                    Object[] found = {e, comp, home, away, league};
                    if (exact || v == null) return teamGame(kind, found);
                    if (loose == null) loose = found;
                }
            }
            if (loose != null) return teamGame(kind, loose);
        }
        return null;
    }

    private static Info teamGame(int kind, Object[] f) {
        JSONObject e = (JSONObject) f[0], comp = (JSONObject) f[1], home = (JSONObject) f[2], away = (JSONObject) f[3], league = (JSONObject) f[4];
        return kind == Info.CRICKET ? cricketGame(e, comp, home, away, league) : rugbyGame(e, comp, home, away, league);
    }

    /** Whether a name from a title is exactly this side's (its display or short name, or its abbreviation). */
    private static boolean exactClub(String fromTitle, JSONObject competitor) {
        JSONObject t = competitor == null ? null : competitor.optJSONObject("team");
        if (t == null) return false;
        String want = clubKey(fromTitle);
        if (fromTitle.trim().equalsIgnoreCase(t.optString("abbreviation"))) return true;
        return !want.isEmpty() && (want.equals(clubKey(t.optString("displayName"))) || want.equals(clubKey(t.optString("shortDisplayName"))));
    }

    private static Info rugbyGame(JSONObject e, JSONObject comp, JSONObject home, JSONObject away, JSONObject league) {
        Info in = new Info();
        in.kind = Info.RUGBY;
        in.home = side(home);
        in.away = side(away);
        in.competition = league == null ? null : league.optString("name", null);
        in.compLogo = leagueLogoOf(league);
        JSONObject st = comp.optJSONObject("status");
        String state = st == null || st.optJSONObject("type") == null ? "" : st.optJSONObject("type").optString("state");
        in.status = "post".equals(state) ? "FT" : st == null || st.optJSONObject("type") == null ? null : st.optJSONObject("type").optString("shortDetail");
        in.when = day(e.optString("date"));
        JSONObject vn = comp.optJSONObject("venue");
        if (vn != null) in.venue = vn.optString("fullName", null);
        String homeId = home.optJSONObject("team") == null ? "" : home.optJSONObject("team").optString("id");
        // The scoring by type, and the tries and cards each under its own side.
        String[] kinds = {"try", "conversion", "penalty goal", "drop goal", "yellow card", "red card"};
        String[] names = {"Tries", "Conversions", "Penalty goals", "Drop goals", "Yellow cards", "Red cards"};
        int[] hc = new int[kinds.length], ac = new int[kinds.length];
        JSONArray det = comp.optJSONArray("details");
        for (int k = 0; det != null && k < det.length(); k++) {
            JSONObject d = det.optJSONObject(k);
            if (d == null || d.optJSONObject("type") == null) continue;
            String what = d.optJSONObject("type").optString("text").toLowerCase(Locale.ROOT);
            if (what.equals("penalty try")) what = "try";
            int idx = java.util.Arrays.asList(kinds).indexOf(what);
            if (idx < 0) continue;
            boolean isHome = d.optJSONObject("team") != null && homeId.equals(d.optJSONObject("team").optString("id"));
            if (isHome) hc[idx]++; else ac[idx]++;
            if (idx == 0 || idx >= 4) {
                JSONArray who = d.optJSONArray("athletesInvolved");
                JSONObject pl = who == null ? null : who.optJSONObject(0);
                String name = pl == null ? "Penalty try" : pl.optString("shortName", pl.optString("displayName"));
                String min = d.optJSONObject("clock") == null ? "" : d.optJSONObject("clock").optString("displayValue");
                in.events.add(new String[]{isHome ? "home" : "away", idx == 0 ? "try" : idx == 4 ? "yellow" : "red", (idx == 0 ? "Try  " : "") + name, min});
            }
        }
        for (int k = 0; k < kinds.length; k++) {
            if (hc[k] + ac[k] == 0 && k >= 3) continue;   // no drop goals or cards: not listed
            in.stats.add(new String[]{names[k], String.valueOf(hc[k]), String.valueOf(ac[k]),
                    hc[k] + ac[k] > 0 ? String.valueOf(hc[k] / (float) (hc[k] + ac[k])) : "0.5"});
        }
        return in;
    }

    private static Info cricketGame(JSONObject e, JSONObject comp, JSONObject home, JSONObject away, JSONObject league) {
        Info in = new Info();
        in.kind = Info.CRICKET;
        in.home = side(home);
        in.away = side(away);
        in.competition = league == null ? null : league.optString("name", null);
        in.compLogo = leagueLogoOf(league);
        JSONObject st = comp.optJSONObject("status");
        in.status = st == null || st.optJSONObject("type") == null ? null : st.optJSONObject("type").optString("shortDetail");
        String sum = st == null ? null : st.optString("summary", null);
        if (sum != null) in.result = android.text.Html.fromHtml(sum, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim();
        String desc = comp.optString("description", "");
        JSONObject cls = comp.optJSONObject("class");
        String card = cls == null ? "" : cls.optString("generalClassCard", "");
        in.description = desc + (desc.isEmpty() || card.isEmpty() ? "" : "  ·  ") + card;
        JSONArray fa = st == null ? null : st.optJSONArray("featuredAthletes");
        for (int k = 0; fa != null && k < fa.length(); k++) {
            JSONObject x = fa.optJSONObject(k);
            if (x == null || !"POTM".equals(x.optString("abbreviation"))) continue;
            JSONObject a = x.optJSONObject("athlete");
            if (a == null) continue;
            String pic = a.optString("headshot", null);
            if (pic != null && pic.contains("default")) pic = null;
            in.potm = new String[]{a.optString("displayName"), x.optString("displayName", "Player of the match"), pic};
            break;
        }
        in.when = day(e.optString("date"));
        JSONObject vn = comp.optJSONObject("venue");
        if (vn != null) in.venue = vn.optString("fullName", null);
        return in;
    }

    /** A competition's own logo from a board's league (ESPN's, for a dark ground where it has one); null without. */
    private static String leagueLogoOf(JSONObject l) {
        JSONArray logos = l == null ? null : l.optJSONArray("logos");
        String any = null;
        for (int i = 0; logos != null && i < logos.length(); i++) {
            JSONObject x = logos.optJSONObject(i);
            String href = x == null ? null : x.optString("href", null);
            if (href == null) continue;
            if (any == null) any = href;
            JSONArray rel = x.optJSONArray("rel");
            if (rel != null && rel.toString().contains("dark")) return href;
        }
        return any;
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
                        // ESPN's headshots by the fighters' ids, and their flags.
                        in.aPic = "https://a.espncdn.com/i/headshots/mma/players/full/" + cs.optJSONObject(0).optString("id") + ".png";
                        in.bPic = "https://a.espncdn.com/i/headshots/mma/players/full/" + cs.optJSONObject(1).optString("id") + ".png";
                        in.aFlag = flag(cs.optJSONObject(0));
                        in.bFlag = flag(cs.optJSONObject(1));
                        in.eventName = e.optString("name");
                        in.when = day(e.optString("date"));
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
        in.eventName = card.optString("name");
        in.when = day(card.optString("date"));
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

    /** A competitor's flag (ESPN's, by the country they fight or play for); null when it has none. */
    private static String flag(JSONObject x) {
        JSONObject a = x == null ? null : x.optJSONObject("athlete");
        JSONObject f = a == null ? null : a.optJSONObject("flag");
        return f == null ? null : f.optString("href", null);
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
                            in.aFlag = flag(cs.optJSONObject(0));
                            in.bFlag = flag(cs.optJSONObject(1));
                            in.aPic = "https://a.espncdn.com/i/headshots/tennis/players/full/" + cs.optJSONObject(0).optString("id") + ".png";
                            in.bPic = "https://a.espncdn.com/i/headshots/tennis/players/full/" + cs.optJSONObject(1).optString("id") + ".png";
                            in.eventName = e.optString("name");
                            in.round = rs.isEmpty() ? null : rs;
                            in.when = day(comp.optString("date", e.optString("date")));
                            in.brand = SportsHighlights.tourBrand(tour.endsWith("wta"));
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
                    JSONObject ath = top.optJSONObject("athlete");
                    String who = ath == null ? "" : ath.optString("displayName");
                    Object hs = ath == null ? null : ath.opt("headshot");
                    String pic = hs instanceof JSONObject ? ((JSONObject) hs).optString("href", null) : hs instanceof String ? (String) hs : null;
                    in.lines.add(new String[]{who, l.optString("displayName") + "  ·  " + top.optString("displayValue"), pic});
                }
                in.when = day(e.optString("date"));
                JSONObject wk = e.optJSONObject("week");
                if (wk != null && wk.optInt("number", 0) > 0) in.round = "Week " + wk.optInt("number");
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

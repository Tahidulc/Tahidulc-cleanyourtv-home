package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The starting elevens before kick-off, in the style of Sky Sports' Premier League line-ups, small, in the bottom left
 * corner over whatever is on: a navy band with the competition, the club's crest and its name up the side; on a pale
 * streaked ground the players a page at a time, head and shoulders in the club's shirt, each with his number, flag,
 * first name and surname. Both elevens, then the fixture, the kick-off and the channel showing it, where OK watches it.
 * Sized from the screen's width (the mock-up's 700 by 232 of 1920 by 1080), so it is the same on every TV.
 */
final class LineupCard {
    /** Everything it shows, fetched before it opens (GoalWatchService, off the main thread). */
    static final class Data {
        Bitmap leagueLogo;
        Team home, away;
        String kickoff, channel;
        Bitmap channelLogo;
        /** OK on the last page: the channel opened (null without one). */
        Runnable watch;
        String remote = "";
    }

    static final class Team {
        String name, formation = "", kitName = "Home kit";
        Bitmap crest;
        int kit;
        final List<Player> players = new ArrayList<>();
    }

    static final class Player {
        String first = "", last = "", number = "";
        Bitmap photo, flag;
        /** A photo rather than a cut-out (Wikipedia's): shown in a round frame in the club's colour. */
        boolean framed;
    }

    private static final int NAVY = 0xFF151A63;
    private static final long PAGE_MS = 3_600L, LAST_MS = 5_000L, WATCH_MS = 9_000L;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static FrameLayout host;
    private static float k;
    private static Runnable step;

    private LineupCard() { }

    private static int px(float v) {
        return Math.max(1, Math.round(v * k));
    }

    /** Opens the card (on the main thread), in place of one already showing. */
    static void show(Context ctx, Data d) {
        Context c = ctx.getApplicationContext();
        close(false);
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f;
        int w = px(700), h = px(232), band = px(84), margin = px(10);

        FrameLayout win = new FrameLayout(c);
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        float r = px(14);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        card.setClipToOutline(true);
        card.setElevation(px(10));

        // The band: the competition, the club's crest and its name up the side.
        FrameLayout side = new FrameLayout(c);
        side.setBackground(new Streaks(0xFF1B2591, 0xFF0D1360, 0x12FFFFFF, px(21), px(3)));
        ImageView league = new ImageView(c);
        league.setImageBitmap(d.leagueLogo);
        league.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(px(38), px(38), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        ll.topMargin = px(10);
        side.addView(league, ll);
        FrameLayout disc = new FrameLayout(c);
        GradientDrawable white = new GradientDrawable();
        white.setShape(GradientDrawable.OVAL);
        white.setColor(0xFFFFFFFF);
        disc.setBackground(white);
        disc.setElevation(px(4));
        ImageView crest = new ImageView(c);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        disc.addView(crest, new FrameLayout.LayoutParams(px(42), px(42), Gravity.CENTER));
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(px(56), px(56), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        dl.topMargin = px(56);
        side.addView(disc, dl);
        Upright name = new Upright(c, px(24));
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(px(40), px(108), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        nl.bottomMargin = px(10);
        side.addView(name, nl);
        card.addView(side, new LinearLayout.LayoutParams(band, h));

        // The stage: the pale streaked ground the players stand on, or the last page's navy.
        FrameLayout stage = new FrameLayout(c);
        card.addView(stage, new LinearLayout.LayoutParams(w - band, h));

        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(w, h);
        cl.setMargins(margin, margin, margin, margin);
        win.addView(card, cl);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w + 2 * margin, h + 2 * margin,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.x = px(34);
        lp.y = px(34);
        lp.setTitle("LineupCard");
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            return;
        }
        host = win;

        // The pages: each eleven in two (the keeper and defence first), then the fixture and the channel.
        List<Object[]> pages = new ArrayList<>();
        for (Team t : new Team[]{d.home, d.away}) {
            if (t == null || t.players.isEmpty()) continue;
            int split = Math.min(t.players.size(), (t.players.size() + 1) / 2);
            pages.add(new Object[]{t, t.players.subList(0, split)});
            if (split < t.players.size()) pages.add(new Object[]{t, t.players.subList(split, t.players.size())});
        }
        pages.add(new Object[]{null, null});
        int[] at = {0};
        step = new Runnable() {
            @Override public void run() {
                if (host != win) return;
                Object[] page = pages.get(at[0]);
                boolean last = page[0] == null;
                if (last) {
                    crest.setImageBitmap(d.home != null ? d.home.crest : null);
                    disc.setVisibility(View.INVISIBLE);
                    name.setText("");
                    slate(c, stage, d);
                } else {
                    Team t = (Team) page[0];
                    @SuppressWarnings("unchecked") List<Player> ps = (List<Player>) page[1];
                    disc.setVisibility(View.VISIBLE);
                    crest.setImageBitmap(t.crest);
                    name.setText(t.name);
                    players(c, stage, d, t, ps, at[0], pages.size() - 1);
                }
                if (last) {
                    if (d.watch != null) offerWatch(win, d);
                    main.postDelayed(() -> close(true), d.watch != null ? WATCH_MS : LAST_MS);
                    return;
                }
                at[0]++;
                main.postDelayed(this, PAGE_MS);
            }
        };
        card.setAlpha(0f);
        card.setTranslationX(-px(40));
        card.animate().alpha(1f).translationX(0f).setDuration(340).setInterpolator(new DecelerateInterpolator(2f)).start();
        step.run();
    }

    /** A page of players: the header (the club, its eleven's shape, the kit, kick-off and channel), then the row. */
    private static void players(Context c, FrameLayout stage, Data d, Team t, List<Player> ps, int page, int pages) {
        stage.removeAllViews();
        stage.setBackground(new Streaks(0xFFF4F5FD, 0xFFC2C5EE, 0x8CFFFFFF, px(86), px(36)));
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(px(12), px(6), px(12), 0);
        head.addView(text(c, t.name, 15, NAVY, Typeface.create("sans-serif-black", Typeface.NORMAL)));
        TextView xi = text(c, ("Starting XI" + (t.formation.isEmpty() ? "" : " · " + t.formation)).toUpperCase(java.util.Locale.ROOT),
                12.5f, NAVY, Typeface.create("sans-serif-condensed", Typeface.BOLD));
        xi.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams xl = new LinearLayout.LayoutParams(0, -2, 1f);
        xl.leftMargin = px(8);
        head.addView(xi, xl);
        // The kit it plays in: a little shirt in its colour, "Home kit" or "Away kit".
        ImageView shirt = new ImageView(c);
        shirt.setImageDrawable(new Shirt(t.kit != 0 ? t.kit : NAVY));
        head.addView(shirt, new LinearLayout.LayoutParams(px(16), px(16)));
        TextView kit = text(c, t.kitName, 11.5f, NAVY, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(-2, -2);
        kl.leftMargin = px(4);
        kl.rightMargin = px(10);
        head.addView(kit, kl);
        LinearLayout dots = new LinearLayout(c);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < pages; i++) {
            View dot = new View(c);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(px(3));
            g.setColor(i == page ? NAVY : 0x40151A63);
            dot.setBackground(g);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(i == page ? px(15) : px(5), px(5));
            p.leftMargin = px(4);
            dots.addView(dot, p);
        }
        head.addView(dots);
        if (d.kickoff != null) {
            TextView ko = text(c, "KO " + d.kickoff, 12, NAVY, Typeface.create("sans-serif-medium", Typeface.BOLD));
            LinearLayout.LayoutParams kol = new LinearLayout.LayoutParams(-2, -2);
            kol.leftMargin = px(10);
            head.addView(ko, kol);
        }
        View ch = channel(c, d, 22);
        if (ch != null) {
            LinearLayout.LayoutParams chl = new LinearLayout.LayoutParams(-2, px(22));
            chl.leftMargin = px(8);
            head.addView(ch, chl);
        }
        stage.addView(head, new FrameLayout.LayoutParams(-1, px(36), Gravity.TOP));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(px(8), 0, px(8), 0);
        for (int i = 0; i < ps.size(); i++) {
            View col = player(c, t, ps.get(i));
            row.addView(col, new LinearLayout.LayoutParams(0, -1, 1f));
            col.setAlpha(0f);
            col.setTranslationY(px(14));
            col.animate().alpha(1f).translationY(0f).setStartDelay(60L * i).setDuration(280)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-1, -1);
        rl.topMargin = px(36);
        stage.addView(row, rl);
    }

    /** One player: head and shoulders, the number and flag at his shoulder, first name and surname under him. */
    private static View player(Context c, Team t, Player p) {
        FrameLayout col = new FrameLayout(c);
        FrameLayout frame = new FrameLayout(c);
        ImageView photo = new ImageView(c);
        photo.setImageBitmap(p.photo != null ? p.photo : stand(t.kit != 0 ? t.kit : NAVY, p.number));
        photo.setScaleType(ImageView.ScaleType.FIT_START);
        frame.addView(photo, new FrameLayout.LayoutParams(px(150), px(150), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -1);
        fl.bottomMargin = px(40);
        col.addView(frame, fl);
        col.setClipChildren(true);
        frame.setClipChildren(true);
        TextView num = text(c, p.number, 22, NAVY, Typeface.create("sans-serif-black", Typeface.NORMAL));
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        nl.leftMargin = px(6);
        nl.topMargin = px(2);
        col.addView(num, nl);
        if (p.flag != null) {
            ImageView flag = new ImageView(c);
            flag.setImageBitmap(p.flag);
            flag.setScaleType(ImageView.ScaleType.CENTER_CROP);
            flag.setElevation(px(1));
            FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(px(22), px(14), Gravity.TOP | Gravity.START);
            gl.leftMargin = px(7);
            gl.topMargin = px(29);
            col.addView(flag, gl);
        }
        LinearLayout nm = new LinearLayout(c);
        nm.setOrientation(LinearLayout.VERTICAL);
        nm.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView first = text(c, p.first.isEmpty() ? " " : p.first, 11, NAVY, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        first.setGravity(Gravity.CENTER);
        nm.addView(first, new LinearLayout.LayoutParams(-1, -2));
        TextView last = text(c, p.last, 17, NAVY, Typeface.create("sans-serif-black", Typeface.NORMAL));
        last.setGravity(Gravity.CENTER);
        // A long surname fits by getting smaller.
        last.setAutoSizeTextTypeUniformWithConfiguration(px(10), px(17), 1, TypedValue.COMPLEX_UNIT_PX);
        nm.addView(last, new LinearLayout.LayoutParams(-1, px(22)));
        col.addView(nm, new FrameLayout.LayoutParams(-1, px(38), Gravity.BOTTOM));
        return col;
    }

    /** The last page: the two clubs, the kick-off, and the channel showing it with OK to watch. */
    private static void slate(Context c, FrameLayout stage, Data d) {
        stage.removeAllViews();
        stage.setBackground(new Streaks(0xFF2A35B8, 0xFF080B3A, 0x0EFFFFFF, px(60), px(4)));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout vs = new LinearLayout(c);
        vs.setGravity(Gravity.CENTER_VERTICAL);
        vs.addView(club(c, d.home));
        TextView mid = text(c, d.kickoff != null ? d.kickoff : "v", 30, 0xFFFFFFFF, Typeface.create("sans-serif-black", Typeface.NORMAL));
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
        ml.leftMargin = ml.rightMargin = px(26);
        vs.addView(mid, ml);
        vs.addView(club(c, d.away));
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-2, -2);
        vl.topMargin = px(22);
        col.addView(vs, vl);
        LinearLayout bottom = new LinearLayout(c);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        View ch = channel(c, d, 34);
        if (ch != null) {
            bottom.addView(text(c, "Live on", 15, 0xFFFFFFFF, Typeface.create("sans-serif-medium", Typeface.NORMAL)));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, px(34));
            cl.leftMargin = px(10);
            bottom.addView(ch, cl);
        }
        if (d.watch != null) {
            LinearLayout ok = new LinearLayout(c);
            ok.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable pg = new GradientDrawable();
            pg.setColor(0xFFFFFFFF);
            pg.setCornerRadius(px(17));
            ok.setBackground(pg);
            ok.setPadding(px(4), px(4), px(12), px(4));
            ImageView key = new ImageView(c);
            key.setImageBitmap(GoalWatchService.okButton(d.remote.isEmpty() ? "tcl" : d.remote, k * 1.4f, false));
            ok.addView(key, new LinearLayout.LayoutParams(px(26), px(26)));
            TextView watch = text(c, "Watch", 14, 0xFF111214, Typeface.create("sans-serif-medium", Typeface.BOLD));
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
            wl.leftMargin = px(6);
            ok.addView(watch, wl);
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, px(34));
            ol.leftMargin = px(14);
            bottom.addView(ok, ol);
        }
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
        bl.topMargin = px(18);
        col.addView(bottom, bl);
        stage.addView(col, new FrameLayout.LayoutParams(-1, -1));
        col.setAlpha(0f);
        col.animate().alpha(1f).setDuration(300).start();
    }

    private static View club(Context c, Team t) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout disc = new FrameLayout(c);
        GradientDrawable white = new GradientDrawable();
        white.setShape(GradientDrawable.OVAL);
        white.setColor(0xFFFFFFFF);
        white.setStroke(px(4), 0x26FFFFFF);
        disc.setBackground(white);
        ImageView crest = new ImageView(c);
        crest.setImageBitmap(t == null ? null : t.crest);
        crest.setScaleType(ImageView.ScaleType.FIT_CENTER);
        disc.addView(crest, new FrameLayout.LayoutParams(px(50), px(50), Gravity.CENTER));
        v.addView(disc, new LinearLayout.LayoutParams(px(70), px(70)));
        TextView n = text(c, t == null ? "" : t.name, 16, 0xFFFFFFFF, Typeface.create("sans-serif-black", Typeface.NORMAL));
        n.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(px(170), -2);
        nl.topMargin = px(6);
        v.addView(n, nl);
        return v;
    }

    /** The channel's logo on a black chip, or its name when there is no logo; null without a channel. */
    private static View channel(Context c, Data d, int height) {
        if (d.channel == null && d.channelLogo == null) return null;
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0xFF000000);
        chip.setCornerRadius(px(5));
        chip.setStroke(Math.max(1, px(1.5f)), 0xE6FFFFFF);
        if (d.channelLogo != null) {
            ImageView logo = new ImageView(c);
            logo.setImageBitmap(d.channelLogo);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setAdjustViewBounds(true);
            logo.setBackground(chip);
            int pad = px(height / 7f);
            logo.setPadding(pad * 2, pad, pad * 2, pad);
            logo.setMaxWidth(px(height * 5.5f));
            return logo;
        }
        TextView name = text(c, d.channel, height * 0.55f, 0xFFFFFFFF, Typeface.create("sans-serif-black", Typeface.NORMAL));
        name.setBackground(chip);
        name.setGravity(Gravity.CENTER);
        name.setPadding(px(8), 0, px(8), 0);
        return name;
    }

    /** The window takes the remote's keys on the last page: OK watches the game, anything else closes the card. */
    private static void offerWatch(FrameLayout win, Data d) {
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) win.getLayoutParams();
            lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            win.getContext().getSystemService(WindowManager.class).updateViewLayout(win, lp);
        } catch (RuntimeException e) {
            return;
        }
        win.setFocusable(true);
        win.setFocusableInTouchMode(true);
        win.requestFocus();
        final boolean[] down = {false};
        win.setOnKeyListener((v, code, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN) { down[0] = true; return true; }
            if (e.getAction() != KeyEvent.ACTION_UP || !down[0]) return true;
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
            close(true);
            if (ok) d.watch.run();
            return true;
        });
    }

    /** The card away: faded, or at once when another takes its place. */
    static void close(boolean fade) {
        if (step != null) main.removeCallbacks(step);
        main.removeCallbacksAndMessages(null);
        FrameLayout v = host;
        host = null;
        if (v == null) return;
        v.setOnKeyListener(null);
        Runnable gone = () -> {
            try { v.getContext().getSystemService(WindowManager.class).removeView(v); } catch (RuntimeException ignored) { }
        };
        if (!fade) { gone.run(); return; }
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getLayoutParams();
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            v.getContext().getSystemService(WindowManager.class).updateViewLayout(v, lp);
        } catch (RuntimeException ignored) { }
        v.animate().alpha(0f).translationX(-px(30)).setDuration(240).withEndAction(gone).start();
    }

    private static TextView text(Context c, String s, float sizePx, int colour, Typeface face) {
        TextView v = new TextView(c);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * k);
        v.setTextColor(colour);
        v.setTypeface(face);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        v.setIncludeFontPadding(false);
        return v;
    }

    /**
     * A player with no photo anywhere: never an empty box, but a figure in the club's colours with his number on the
     * chest, head and shoulders as the photos are.
     */
    static Bitmap stand(int colour, String number) {
        int s = 300;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xEBEBEEF8);
        cv.drawCircle(s / 2f, s * 0.30f, s * 0.16f, p);
        Path body = new Path();
        body.moveTo(s * 0.12f, s);
        body.lineTo(s * 0.16f, s * 0.66f);
        body.quadTo(s * 0.19f, s * 0.52f, s * 0.35f, s * 0.47f);
        body.lineTo(s * 0.42f, s * 0.45f);
        body.quadTo(s * 0.5f, s * 0.52f, s * 0.58f, s * 0.45f);
        body.lineTo(s * 0.65f, s * 0.47f);
        body.quadTo(s * 0.81f, s * 0.52f, s * 0.84f, s * 0.66f);
        body.lineTo(s * 0.88f, s);
        body.close();
        p.setShader(new LinearGradient(0, s * 0.45f, 0, s, colour, darker(colour), Shader.TileMode.CLAMP));
        cv.drawPath(body, p);
        p.setShader(null);
        p.setColor(lum(colour) > 0.6 ? 0xFF151A63 : 0xFFFFFFFF);
        p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(s * 0.24f);
        cv.drawText(number == null ? "" : number, s / 2f, s * 0.86f, p);
        return b;
    }

    private static int darker(int c) {
        return 0xFF000000 | (((c >> 16) & 0xFF) * 6 / 10 << 16) | (((c >> 8) & 0xFF) * 6 / 10 << 8) | ((c & 0xFF) * 6 / 10);
    }

    private static double lum(int c) {
        return (0.299 * ((c >> 16) & 0xFF) + 0.587 * ((c >> 8) & 0xFF) + 0.114 * (c & 0xFF)) / 255.0;
    }

    /** How far apart two colours are (0 the same, about 440 black from white): a clash when close. */
    static double distance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF), dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF), db = (a & 0xFF) - (b & 0xFF);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    // ---- drawing -------------------------------------------------------------------------------------------------

    /** A vertical gradient with diagonal bands across it, as Sky's line-up ground and band have. */
    private static final class Streaks extends Drawable {
        private final Paint fill = new Paint(), band = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int top, bottom, gap, width;

        Streaks(int top, int bottom, int bandColour, int gap, int width) {
            this.top = top;
            this.bottom = bottom;
            this.gap = Math.max(4, gap);
            this.width = Math.max(1, width);
            band.setColor(bandColour);
        }

        @Override public void draw(Canvas c) {
            Rect r = getBounds();
            fill.setShader(new LinearGradient(0, r.top, 0, r.bottom, top, bottom, Shader.TileMode.CLAMP));
            c.drawRect(r, fill);
            float slant = r.height() * 0.36f;
            Path p = new Path();
            for (float x = r.left - slant; x < r.right + slant; x += gap + width) {
                p.reset();
                p.moveTo(x, r.bottom);
                p.lineTo(x + width, r.bottom);
                p.lineTo(x + width + slant, r.top);
                p.lineTo(x + slant, r.top);
                p.close();
                c.drawPath(p, band);
            }
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    /** The club's name up the band, bottom to top. */
    private static final class Upright extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String text = "";

        Upright(Context c, float size) {
            super(c);
            p.setColor(0xFFFFFFFF);
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            p.setTextSize(size);
            p.setTextAlign(Paint.Align.LEFT);
        }

        void setText(String t) {
            text = t == null ? "" : t;
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            if (text.isEmpty()) return;
            float size = p.getTextSize();
            // Smaller to fit a long name in the band's height.
            float w = p.measureText(text);
            if (w > getHeight()) p.setTextSize(size * getHeight() / w);
            Paint.FontMetrics fm = p.getFontMetrics();
            c.save();
            c.rotate(-90);
            c.drawText(text, -getHeight(), getWidth() / 2f - (fm.ascent + fm.descent) / 2f, p);
            c.restore();
            p.setTextSize(size);
        }
    }

    /** A small shirt in the kit's colour, for "Home kit" or "Away kit". */
    private static final class Shirt extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);

        Shirt(int colour) {
            fill.setColor(colour);
            edge.setColor(lum(colour) > 0.75 ? 0x99151A63 : 0x66FFFFFF);
            edge.setStyle(Paint.Style.STROKE);
        }

        @Override public void draw(Canvas c) {
            Rect r = getBounds();
            float s = Math.min(r.width(), r.height()) / 40f;
            Path p = new Path();
            p.moveTo(13 * s, 4 * s);
            p.lineTo(7 * s, 7 * s);
            p.lineTo(2 * s, 15 * s);
            p.lineTo(8 * s, 19 * s);
            p.lineTo(10 * s, 16 * s);
            p.lineTo(10 * s, 36 * s);
            p.lineTo(30 * s, 36 * s);
            p.lineTo(30 * s, 16 * s);
            p.lineTo(32 * s, 19 * s);
            p.lineTo(38 * s, 15 * s);
            p.lineTo(33 * s, 7 * s);
            p.lineTo(27 * s, 4 * s);
            p.quadTo(20 * s, 9 * s, 13 * s, 4 * s);
            p.close();
            p.offset(r.left, r.top);
            edge.setStrokeWidth(Math.max(1f, 1.6f * s));
            c.drawPath(p, fill);
            c.drawPath(p, edge);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ---- flags ---------------------------------------------------------------------------------------------------

    private static final java.util.Map<String, String> NATIONS = new java.util.HashMap<>();

    static {
        String[] pairs = {"England:gb-eng", "Scotland:gb-sct", "Wales:gb-wls", "Northern Ireland:gb-nir",
                "Republic of Ireland:ie", "Ireland:ie", "France:fr", "Spain:es", "Portugal:pt", "Italy:it", "Germany:de",
                "Netherlands:nl", "Belgium:be", "Brazil:br", "Argentina:ar", "Uruguay:uy", "Colombia:co", "Ecuador:ec",
                "Paraguay:py", "Chile:cl", "Peru:pe", "Venezuela:ve", "Mexico:mx", "United States:us", "USA:us",
                "Canada:ca", "Jamaica:jm", "Croatia:hr", "Serbia:rs", "Slovenia:si", "Slovakia:sk", "Czech Republic:cz",
                "Czechia:cz", "Poland:pl", "Hungary:hu", "Austria:at", "Switzerland:ch", "Denmark:dk", "Sweden:se",
                "Norway:no", "Finland:fi", "Iceland:is", "Ukraine:ua", "Russia:ru", "Georgia:ge", "Armenia:am",
                "Greece:gr", "Turkey:tr", "Türkiye:tr", "Albania:al", "Kosovo:xk", "North Macedonia:mk",
                "Bosnia and Herzegovina:ba", "Montenegro:me", "Romania:ro", "Bulgaria:bg", "Cyprus:cy", "Israel:il",
                "Morocco:ma", "Algeria:dz", "Tunisia:tn", "Egypt:eg", "Senegal:sn", "Mali:ml", "Ivory Coast:ci",
                "Côte d'Ivoire:ci", "Ghana:gh", "Nigeria:ng", "Cameroon:cm", "Gabon:ga", "Guinea:gn", "Burkina Faso:bf",
                "DR Congo:cd", "Congo:cg", "Zambia:zm", "Zimbabwe:zw", "South Africa:za", "Kenya:ke", "Angola:ao",
                "Cape Verde:cv", "Gambia:gm", "Benin:bj", "Togo:tg", "Mozambique:mz", "Japan:jp", "South Korea:kr",
                "Korea Republic:kr", "China:cn", "Australia:au", "New Zealand:nz", "Iran:ir", "Saudi Arabia:sa",
                "Qatar:qa", "Uzbekistan:uz", "Costa Rica:cr", "Honduras:hn", "Panama:pa", "Haiti:ht", "Suriname:sr",
                "Curaçao:cw", "Luxembourg:lu", "Estonia:ee", "Latvia:lv", "Lithuania:lt", "Belarus:by", "Moldova:md",
                "Malta:mt", "Faroe Islands:fo", "Grenada:gd", "Trinidad and Tobago:tt", "Bolivia:bo", "Guinea-Bissau:gw",
                "Equatorial Guinea:gq", "Sierra Leone:sl", "Liberia:lr", "Libya:ly", "Sudan:sd", "Uganda:ug",
                "Tanzania:tz", "Madagascar:mg", "Comoros:km", "Mauritania:mr", "Burundi:bi", "Rwanda:rw", "Iraq:iq",
                "Jordan:jo", "Syria:sy", "Lebanon:lb", "Philippines:ph", "Indonesia:id", "Thailand:th", "Vietnam:vn",
                "India:in"};
        for (String pair : pairs) {
            int at = pair.lastIndexOf(':');
            NATIONS.put(pair.substring(0, at).toLowerCase(java.util.Locale.ROOT), pair.substring(at + 1));
        }
    }

    /** A country's flag, small (flagcdn's 80-pixel PNG); null for a country it does not know. */
    static String flagUrl(String nation) {
        if (nation == null || nation.isEmpty()) return null;
        String code = NATIONS.get(nation.trim().toLowerCase(java.util.Locale.ROOT));
        return code == null ? null : "https://flagcdn.com/w80/" + code + ".png";
    }
}

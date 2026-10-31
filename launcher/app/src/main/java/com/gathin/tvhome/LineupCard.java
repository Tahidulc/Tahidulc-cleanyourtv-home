package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
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
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The starting elevens before kick-off, after Sky Sports' Premier League line-ups, small in the bottom left corner
 * over whatever is on. A navy band down the left (the competition, the club's crest, its name up the side); beside it
 * the players a page at a time on a pale streaked ground, head and shoulders, each with his number and flag above
 * and first name and surname below; then both elevens together on one pitch in their shapes, faces, numbers and
 * names; last the two clubs, the kick-off and the channel. The pages slide by themselves, a line along the foot
 * filling as each runs; OK at any time watches the game (InputKeyService passes it here; on a TV without that helper,
 * on the last page). Sized from the screen's width, so it is the same on every TV.
 */
final class LineupCard {
    /** Everything it shows, fetched before it opens (GoalWatchService, off the main thread). */
    static final class Data {
        Bitmap leagueLogo;
        Team home, away;
        String kickoff, channel, venue, competition;
        long kickoffAt;
        Bitmap channelLogo;
        /** OK: the game's channel opened (null without one). */
        Runnable watch;
        String remote = "";
    }

    static final class Team {
        String name, formation = "", kitName = "Home kit";
        Bitmap crest;
        /** The kit it wears today: shirt body and sleeves (0 when not known), for its colours on the pitch. */
        int kit, sleeves;
        /** The kit its players' photos show (its home kit): a player without a photo wears it too, like the rest. */
        int photoKit, photoSleeves;
        final List<Player> players = new ArrayList<>();
    }

    static final class Player {
        String first = "", last = "", number = "", position = "";
        Bitmap photo, flag;
        /** A photo rather than a cut-out (Wikipedia's, cut to the face): shown framed on shoulders in the kit. */
        boolean framed;
    }

    private static final int NAVY = 0xFF151A63;
    /** The card's size in the design's pixels on a 1920-wide screen, and its scale there. */
    private static final int W = 700, H = 232, BAND = 84;
    private static final float SCALE = 0.88f;
    /** How long each page stays: the fixture, each half eleven, and the pitch (long, for time to press OK). */
    private static final long FIRST_MS = 11_000L, PLAYERS_MS = 8_000L, PITCH_MS = 16_000L;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private static FrameLayout host;
    private static Data showing;
    private static float k;

    private LineupCard() { }

    /** Whether the card is on screen (the score cards wait for it). */
    static boolean isShowing() {
        return host != null;
    }

    /** Drawn smaller than it came: with mip-maps it scales down smoothly instead of jagged (see ScoreCard). */
    private static Bitmap sharp(Bitmap b) {
        if (b != null) b.setHasMipMap(true);
        return b;
    }

    private static int px(float v) {
        return Math.max(1, Math.round(v * k));
    }

    /**
     * The remote's OK while the card shows (InputKeyService, before any app): the game's channel opened. True when
     * the card took the key.
     */
    static boolean key(KeyEvent e) {
        Data d = showing;
        if (host == null || d == null) return false;
        int code = e.getKeyCode();
        // Back: the card closes.
        if (code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(() -> close(true));
            return true;
        }
        if (d.watch == null) return false;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
        if (!ok) return false;
        if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) main.post(() -> {
            close(true);
            d.watch.run();
        });
        return true;
    }

    /** The card's height while it shows (the camera card goes above it); 0 otherwise. */
    static int shownHeight() {
        return host != null ? host.getHeight() : 0;
    }

    /** Opens the card (on the main thread), in place of one already showing; after the camera card, if that is up. */
    static void show(Context ctx, Data d) {
        Context c = ctx.getApplicationContext();
        if (CameraAlert.isShowing()) {
            main.postDelayed(() -> show(c, d), 2_000L);
            return;
        }
        close(false);
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f * SCALE;
        int w = px(W), h = px(H), margin = px(10);

        FrameLayout win = new FrameLayout(c);
        FrameLayout card = new FrameLayout(c);
        float r = px(14);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        card.setClipToOutline(true);
        card.setBackgroundColor(0xFF0D1360);

        // The band down the left, its contents changed with each page; the stage beside it, where pages slide.
        FrameLayout band = new FrameLayout(c);
        band.setBackground(new Streaks(0xFF1B2591, 0xFF0D1360, 0x12FFFFFF, px(21), px(3)));
        card.addView(band, new FrameLayout.LayoutParams(px(BAND), h));
        FrameLayout stage = new FrameLayout(c);
        stage.setClipChildren(true);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(w - px(BAND), h);
        sl.leftMargin = px(BAND);
        card.addView(stage, sl);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(w, h);
        cl.setMargins(margin, margin, margin, margin);
        win.addView(card, cl);
        // The remote's Back key on the top right corner: Back closes the card.
        ImageView back = new ImageView(c);
        back.setImageBitmap(GoalWatchService.backButton(Config.themeString(c, "ok_button", "tcl"), 2 * margin));
        win.addView(back, new FrameLayout.LayoutParams(2 * margin, 2 * margin, Gravity.TOP | Gravity.END));

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
        showing = d;

        // The pages: the fixture and its channel (OK to watch), each eleven in two (the keeper and defence first), then
        // both on one pitch. Each comes in behind a slanted wipe across the stage, as Sky's line-ups change.
        List<Runnable> pages = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        pages.add(() -> turn(c, stage, () -> { band.removeAllViews(); league(c, band, d, true); stage.addView(first(c, d), full()); }));
        times.add(FIRST_MS);
        for (Team t : new Team[]{d.home, d.away}) {
            if (t == null || t.players.isEmpty()) continue;
            // A page a line: the keeper and the defence, the midfield, the attack (a striker or two with the midfield).
            List<String> captions = new ArrayList<>();
            List<List<Player>> lines = byLines(t, captions);
            for (int i = 0; i < lines.size(); i++) {
                List<Player> ps = lines.get(i);
                String caption = captions.get(i);
                pages.add(() -> turn(c, stage, () -> { teamBand(c, band, t, caption); stage.addView(players(c, t, ps), full()); }));
                // The keeper's page is short; with three lines after it, each a little shorter than with two.
                times.add("GOALKEEPER".equals(caption) ? 5_000L : lines.size() > 3 ? PLAYERS_MS - 1_500L : PLAYERS_MS);
            }
        }
        if (d.home != null && d.away != null && !d.home.players.isEmpty() && !d.away.players.isEmpty()) {
            pages.add(() -> turn(c, stage, () -> { pitchBand(c, band, d); stage.addView(pitch(c, d), full()); }));
            times.add(PITCH_MS);
        }
        // Without the key helper (a TV that has only the screen helper), the first page takes the keys: OK watches.
        boolean helper = InputKeyService.running;
        int[] at = {0};
        Runnable step = new Runnable() {
            @Override public void run() {
                if (host != win) return;
                int i = at[0];
                pages.get(i).run();
                // Without the key helper (the TCL): the card takes the remote's keys while it shows, OK watching
                // (when there is a channel), Back or any other key closing it.
                if (i == 0 && !helper) offerWatch(win, d);
                if (i == pages.size() - 1) {
                    main.postDelayed(() -> { if (host == win) close(true); }, times.get(i));
                    return;
                }
                at[0]++;
                main.postDelayed(this, times.get(i));
            }
        };
        card.setAlpha(0f);
        card.setTranslationX(-px(40));
        card.animate().alpha(1f).translationX(0f).setDuration(340).setInterpolator(new DecelerateInterpolator(2f)).start();
        step.run();
    }

    private static FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(-1, -1);
    }

    /**
     * The next page: the first at once; the rest behind a wipe, slanted bands in the competition's navy and a lighter
     * blue sweeping across the stage, the new page put in while it is covered and uncovered as the bands go on.
     */
    private static void turn(Context c, FrameLayout stage, Runnable swap) {
        if (stage.getChildCount() == 0) { swap.run(); return; }
        // The panel is drawn once (a layer on the graphics chip) and slid across: no page is redrawn while it moves.
        int w = stage.getWidth(), h = stage.getHeight();
        float slant = h * 0.42f;
        int pw = Math.round(w + 2 * slant);
        Wipe wipe = new Wipe(c);
        wipe.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        stage.addView(wipe, new FrameLayout.LayoutParams(pw, h));
        wipe.setTranslationX(-pw);
        boolean[] swapped = {false};
        // Half way it covers the whole stage: the new page goes in under it then, and its back uncovers it.
        wipe.animate().translationX(w).setDuration(900).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .setUpdateListener(a -> {
                    if (swapped[0] || a.getAnimatedFraction() < 0.5f) return;
                    swapped[0] = true;
                    for (int i = stage.getChildCount() - 1; i >= 0; i--) if (stage.getChildAt(i) != wipe) stage.removeViewAt(i);
                    swap.run();
                    wipe.bringToFront();
                })
                .withEndAction(() -> stage.removeView(wipe)).start();
    }

    /** The wipe: a slanted navy panel edged in lighter blue and white, its front then its back crossing the stage. */
    private static final class Wipe extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Wipe(Context c) { super(c); }

        /** Drawn once: the navy panel, slanted, its front edge (right) and back edge (left) in light blue and white. */
        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            float slant = h * 0.42f, body = w - slant, edge = px(10);
            panel(cv, 0, body, h, slant, 0xFF151A63);
            panel(cv, body - edge, body, h, slant, 0xFF3D4BD6);
            panel(cv, body - edge * 0.35f, body, h, slant, 0xFFFFFFFF);
            panel(cv, 0, edge, h, slant, 0xFF3D4BD6);
            panel(cv, 0, edge * 0.35f, h, slant, 0xE6FFFFFF);
        }

        private void panel(Canvas cv, float from, float to, int h, float slant, int colour) {
            if (to <= from) return;
            Path path = new Path();
            path.moveTo(from, h);
            path.lineTo(to, h);
            path.lineTo(to + slant, 0);
            path.lineTo(from + slant, 0);
            path.close();
            p.setColor(colour);
            cv.drawPath(path, p);
        }
    }

    // ---- the band ------------------------------------------------------------------------------------------------

    /**
     * The competition's mark in the band: big and in the middle on the fixture page (big = true), where the band has
     * nothing else; small at the top above the crests on the players' and the pitch's pages, clear of them.
     */
    private static void league(Context c, FrameLayout band, Data d, boolean big) {
        ImageView league = new ImageView(c);
        league.setImageBitmap(sharp(d.leagueLogo));
        league.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (big) {
            band.addView(league, new FrameLayout.LayoutParams(px(58), px(58), Gravity.CENTER));
            return;
        }
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(px(28), px(28), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        ll.topMargin = px(12);
        band.addView(league, ll);
    }

    /** A crest as it is (no disc), with a soft shadow so a dark one still shows on the navy. */
    private static ImageView crest(Context c, Bitmap b) {
        ImageView v = new ImageView(c);
        v.setImageBitmap(sharp(b));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setOutlineProvider(null);
        return v;
    }

    /** The competition, the club's crest, and its name up the side, fitted to the room under the crest. */
    /**
     * The eleven by its lines, from each player's position (G, a defender's CD/LB/RB…, a forward's F/LF/RF…, else
     * midfield): the keeper on his own; the defence; the midfield; the attack, or the midfield and attack together
     * when the attack is one or two. Without positions, the formation's numbers in ESPN's order; without those, two
     * halves.
     */
    static List<List<Player>> byLines(Team t, List<String> captions) {
        List<Player> back = new ArrayList<>(), mid = new ArrayList<>(), front = new ArrayList<>();
        boolean known = true;
        for (Player p : t.players) {
            String pos = p.position == null ? "" : p.position.toUpperCase(java.util.Locale.ROOT);
            if (pos.isEmpty()) { known = false; break; }
            if (pos.equals("G") || pos.equals("GK")) back.add(0, p);
            else if (pos.startsWith("CD") || pos.startsWith("CB") || pos.equals("LB") || pos.equals("RB") || pos.endsWith("WB")
                    || pos.equals("D") || pos.equals("SW") || pos.startsWith("D-")) back.add(p);
            else if (pos.equals("F") || pos.startsWith("CF") || pos.equals("LF") || pos.equals("RF") || pos.equals("ST")
                    || pos.equals("SS") || pos.equals("LW") || pos.equals("RW") || pos.startsWith("F-")) front.add(p);
            else mid.add(p);
        }
        if (!known || back.size() < 2 || mid.isEmpty() && front.isEmpty()) {
            back.clear(); mid.clear(); front.clear();
            int[] n = formationLines(t.formation);
            if (n != null && 1 + sum(n) == t.players.size()) {
                back.addAll(t.players.subList(0, 1 + n[0]));
                mid.addAll(t.players.subList(1 + n[0], t.players.size() - n[n.length - 1]));
                front.addAll(t.players.subList(t.players.size() - n[n.length - 1], t.players.size()));
            } else {
                int split = (t.players.size() + 1) / 2;
                List<List<Player>> halves = new ArrayList<>();
                halves.add(new ArrayList<>(t.players.subList(0, split)));
                if (split < t.players.size()) halves.add(new ArrayList<>(t.players.subList(split, t.players.size())));
                for (int i = 0; i < halves.size(); i++) captions.add("");
                return halves;
            }
        }
        List<List<Player>> out = new ArrayList<>();
        // The keeper on a page of his own, then the defence.
        Player keeper = back.get(0);
        String kp = keeper.position == null ? "" : keeper.position.toUpperCase(java.util.Locale.ROOT);
        if (kp.equals("G") || kp.equals("GK") || !known) {
            List<Player> alone = new ArrayList<>();
            alone.add(keeper);
            out.add(alone);
            captions.add("GOALKEEPER");
            back = new ArrayList<>(back.subList(1, back.size()));
        }
        out.add(back);
        captions.add("DEFENCE");
        if (front.size() <= 2 && mid.size() + front.size() <= 7 || mid.isEmpty() || front.isEmpty()) {
            List<Player> rest = new ArrayList<>(mid);
            rest.addAll(front);
            if (!rest.isEmpty()) {
                out.add(rest);
                captions.add(mid.isEmpty() ? "ATTACK" : front.isEmpty() ? "MIDFIELD" : "MIDFIELD\n& ATTACK");
            }
        } else {
            out.add(mid);
            captions.add("MIDFIELD");
            out.add(front);
            captions.add("ATTACK");
        }
        return out;
    }

    /**
     * A little upright pitch (the side attacking upwards) with the line's part lit: GOALKEEPER the goal area at its
     * foot, DEFENCE the back third, MIDFIELD the middle, ATTACK the front, both for the midfield and attack together.
     * Drawn at the size it shows.
     */
    static Bitmap lineIcon(String caption, int w, int h) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float s = Math.max(1f, w / 16f), in = s / 2f;
        RectF field = new RectF(in, in, w - in, h - in);
        float third = field.height() / 3f;
        // The lit part.
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xE6FFFFFF);
        float r = s * 1.5f;
        switch (caption) {
            case "GOALKEEPER": {
                float bw = field.width() * 0.5f, bh = field.height() * 0.16f;
                cv.drawRoundRect(new RectF(w / 2f - bw / 2, field.bottom - bh, w / 2f + bw / 2, field.bottom), r, r, p);
                break;
            }
            case "DEFENCE":
                cv.drawRoundRect(new RectF(field.left, field.bottom - third, field.right, field.bottom), r, r, p);
                break;
            case "MIDFIELD":
                cv.drawRect(new RectF(field.left, field.top + third, field.right, field.bottom - third), p);
                break;
            case "ATTACK":
                cv.drawRoundRect(new RectF(field.left, field.top, field.right, field.top + third), r, r, p);
                break;
            default:   // the midfield and the attack together
                cv.drawRoundRect(new RectF(field.left, field.top, field.right, field.bottom - third), r, r, p);
        }
        // The pitch's lines over it: its edge, halfway, the centre circle and both boxes.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s);
        p.setColor(0x99FFFFFF);
        cv.drawRoundRect(field, r, r, p);
        cv.drawLine(field.left, h / 2f, field.right, h / 2f, p);
        cv.drawCircle(w / 2f, h / 2f, field.width() * 0.18f, p);
        float boxW = field.width() * 0.5f, boxH = field.height() * 0.16f;
        cv.drawRect(w / 2f - boxW / 2, field.top, w / 2f + boxW / 2, field.top + boxH, p);
        cv.drawRect(w / 2f - boxW / 2, field.bottom - boxH, w / 2f + boxW / 2, field.bottom, p);
        return b;
    }

    private static int[] formationLines(String formation) {
        if (formation == null || !formation.matches("\\d+(-\\d+)+")) return null;
        String[] parts = formation.split("-");
        int[] n = new int[parts.length];
        for (int i = 0; i < parts.length; i++) n[i] = Integer.parseInt(parts[i]);
        return n;
    }

    private static int sum(int[] n) {
        int s = 0;
        for (int x : n) s += x;
        return s;
    }

    private static void teamBand(Context c, FrameLayout band, Team t, String caption) {
        band.removeAllViews();
        league(c, band, showing, false);
        // As on the fixture page: the club's crest big (no name), and its formation under it.
        LinearLayout club = new LinearLayout(c);
        club.setOrientation(LinearLayout.VERTICAL);
        club.setGravity(Gravity.CENTER_HORIZONTAL);
        club.addView(crest(c, t.crest), new LinearLayout.LayoutParams(px(58), px(58)));
        if (t.formation != null && !t.formation.isEmpty()) {
            TextView f = text(c, t.formation, 14, 0xFFFFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
            f.setLetterSpacing(0.1f);
            f.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-2, -2);
            fl.topMargin = px(10);
            club.addView(f, fl);
        }
        // Which line this page is: a little pitch with that part lit (the goal, the back, the middle, the front),
        // and its name under it.
        if (caption != null && !caption.isEmpty()) {
            int iw = px(26), ih = px(36);
            ImageView line = new ImageView(c);
            line.setImageBitmap(lineIcon(caption, iw, ih));
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(iw, ih);
            ll.topMargin = px(8);
            club.addView(line, ll);
            TextView name = text(c, caption, caption.length() > 8 && !caption.contains("\n") ? 8.5f : 9.5f, 0xD9FFFFFF,
                    Typeface.create("sans-serif-condensed", Typeface.BOLD));
            name.setLetterSpacing(caption.length() > 8 && !caption.contains("\n") ? 0.02f : 0.1f);
            name.setSingleLine(false);
            name.setMaxLines(2);
            name.setEllipsize(null);
            name.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(px(BAND) - px(6), -2);
            nl.topMargin = px(5);
            club.addView(name, nl);
        }
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        cl.topMargin = px(20);
        band.addView(club, cl);
    }

    /** For the pitch: both crests, each with its eleven's shape under it. */
    private static void pitchBand(Context c, FrameLayout band, Data d) {
        band.removeAllViews();
        league(c, band, d, false);
        // Both crests a size up, each over its formation, in the room under the competition's mark (12 to 40).
        int y = 50;
        for (Team t : new Team[]{d.home, d.away}) {
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(px(54), px(54), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            cl.topMargin = px(y);
            band.addView(crest(c, t.crest), cl);
            TextView f = text(c, t.formation, 12.5f, 0xFFFFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
            f.setLetterSpacing(0.1f);
            f.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
            fl.topMargin = px(y + 57);
            band.addView(f, fl);
            y += 88;
        }
    }

    // ---- a page of players ---------------------------------------------------------------------------------------

    private static View players(Context c, Team t, List<Player> ps) {
        FrameLayout page = new FrameLayout(c);
        page.setBackground(new Streaks(0xFFF4F5FD, 0xFFC2C5EE, 0x8CFFFFFF, px(86), px(36)));
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(px(6), 0, px(6), 0);
        for (int i = 0; i < ps.size(); i++) {
            View col = player(c, t, ps.get(i));
            row.addView(col, new LinearLayout.LayoutParams(0, -1, 1f));
            // In from the right one after another, as the wipe uncovers them; each drawn once into a layer that moves.
            col.setAlpha(0f);
            col.setTranslationX(px(46));
            col.animate().alpha(1f).translationX(0f).setStartDelay(120L + 70L * i).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator(2.2f)).withLayer().start();
        }
        page.addView(row, new FrameLayout.LayoutParams(-1, -1));
        return page;
    }

    /** One player: his number and flag on a line above him, head and shoulders, first name and surname below. */
    private static View player(Context c, Team t, Player p) {
        FrameLayout col = new FrameLayout(c);
        col.setClipChildren(true);
        FrameLayout frame = new FrameLayout(c);
        frame.setClipChildren(true);
        ImageView photo = new ImageView(c);
        Bitmap shown = p.photo == null ? avatar(t.photoKit != 0 ? t.photoKit : t.kit, t.photoKit != 0 ? t.photoSleeves : t.sleeves, p.number)
                : p.framed ? framed(p.photo, t.kit) : p.photo;
        photo.setImageBitmap(sharp(shown));
        // A square cut-out (TheSportsDB's) 150 wide; the Premier League's own, upright and framed closer, 128 wide at
        // its own height, so the faces come out the same size.
        boolean upright = shown != null && shown.getHeight() > shown.getWidth() * 1.05f;
        int pw = upright ? px(128) : px(150), ph = upright ? Math.round(pw * shown.getHeight() / (float) shown.getWidth()) : px(150);
        photo.setScaleType(upright ? ImageView.ScaleType.FIT_XY : ImageView.ScaleType.FIT_START);
        frame.addView(photo, new FrameLayout.LayoutParams(pw, ph, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -1);
        fl.topMargin = px(36);
        fl.bottomMargin = px(46);
        col.addView(frame, fl);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(c, p.number, 24, NAVY, Typeface.create("sans-serif-black", Typeface.NORMAL)));
        if (p.flag != null) {
            ImageView flag = new ImageView(c);
            flag.setImageBitmap(sharp(p.flag));
            flag.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(px(23), px(15));
            gl.leftMargin = px(7);
            top.addView(flag, gl);
        }
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tl.leftMargin = px(8);
        tl.topMargin = px(8);
        col.addView(top, tl);

        LinearLayout nm = new LinearLayout(c);
        nm.setOrientation(LinearLayout.VERTICAL);
        nm.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView first = text(c, p.first.isEmpty() ? " " : p.first, 11, NAVY, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        first.setGravity(Gravity.CENTER);
        nm.addView(first, new LinearLayout.LayoutParams(-1, -2));
        TextView last = text(c, p.last, 17, NAVY, Typeface.create("sans-serif-black", Typeface.NORMAL));
        last.setGravity(Gravity.CENTER);
        // A long surname fits by getting smaller, never cut short.
        last.setSingleLine(false);
        last.setMaxLines(1);
        last.setEllipsize(null);
        last.setAutoSizeTextTypeUniformWithConfiguration(px(9), px(17), 1, TypedValue.COMPLEX_UNIT_PX);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, px(22));
        ll.topMargin = px(1);
        nm.addView(last, ll);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        nl.bottomMargin = px(8);
        nl.leftMargin = nl.rightMargin = px(2);
        col.addView(nm, nl);
        return col;
    }

    // ---- both elevens on one pitch -------------------------------------------------------------------------------

    private static View pitch(Context c, Data d) {
        FrameLayout page = new FrameLayout(c);
        page.setBackground(new Streaks(0xFF1B2591, 0xFF0D1360, 0x10FFFFFF, px(60), px(4)));
        Pitch field = new Pitch(c, d);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -1);
        fl.setMargins(px(8), px(8), px(8), px(30));
        page.addView(field, fl);
        // Under the pitch, clear of every name: the channel's logo, and the OK button.
        View logo = channel(c, d, 15);
        if (logo != null) {
            FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, px(15), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            ll.bottomMargin = px(8);
            page.addView(logo, ll);
        }
        if (d.watch != null) {
            TextView ok = text(c, "OK", 8, 0xFF111214, Typeface.create("sans-serif-black", Typeface.NORMAL));
            ok.setGravity(Gravity.CENTER);
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(0xFFFFFFFF);
            ok.setBackground(disc);
            FrameLayout.LayoutParams ol = new FrameLayout.LayoutParams(px(20), px(20), Gravity.BOTTOM | Gravity.END);
            ol.bottomMargin = px(5);
            ol.rightMargin = px(12);
            page.addView(ok, ol);
        }
        field.reveal();
        return page;
    }

    /** Each player's place: x along the pitch (0 his own goal, 1 the other), y across it (0 top), for one team. */
    private static float[][] places(Team t, boolean names) {
        int n = t.players.size();
        float[][] at = new float[n][2];
        if (n == 0) return at;
        // The lines from the shape ("4-2-3-1"), the keeper first; the outfield players in the order of their
        // positions from defence to attack, each line across from the left side to the right.
        List<Integer> lines = new ArrayList<>();
        lines.add(1);
        int total = 1;
        for (String part : t.formation.split("-")) {
            try { int v = Integer.parseInt(part.trim()); lines.add(v); total += v; } catch (NumberFormatException ignored) { }
        }
        if (total != n) {
            lines.clear();
            lines.add(1);
            int[] count = new int[4];
            for (int i = 1; i < n; i++) {
                int dp = depth(t.players.get(i).position);
                count[dp <= 1 ? 1 : dp <= 4 ? 2 : 3]++;
            }
            for (int g = 1; g < 4; g++) if (count[g] > 0) lines.add(count[g]);
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        int keeper = 0;
        for (int i = 0; i < n; i++) if ("G".equalsIgnoreCase(t.players.get(i).position)) { keeper = i; break; }
        order.remove(Integer.valueOf(keeper));
        List<Integer> outfield = new ArrayList<>(order);
        outfield.sort((a, b) -> Integer.compare(depth(t.players.get(a).position), depth(t.players.get(b).position)));
        at[keeper] = new float[]{0.06f, 0.5f};
        int next = 0;
        float[] xs = xFor(lines.size() - 1);
        for (int li = 1; li < lines.size(); li++) {
            int k = lines.get(li);
            List<Integer> line = new ArrayList<>();
            for (int j = 0; j < k && next < outfield.size(); j++) line.add(outfield.get(next++));
            line.sort((a, b) -> Integer.compare(side(t.players.get(a).position), side(t.players.get(b).position)));
            float span = names ? (k == 4 ? 0.92f : 0.88f) : 0.80f;
            float top0 = (1 - span) / 2;
            if (names) { span -= 0.08f; top0 += 0.08f; }
            for (int j = 0; j < line.size(); j++) {
                float x = xs[li - 1];
                // A line of four: the two wide players a step forward, so no name hides another.
                if (names && line.size() == 4) x += (j == 0 || j == 3) ? 0.06f : -0.02f;
                at[line.get(j)] = new float[]{x, top0 + (j + 0.5f) / line.size() * span};
            }
        }
        while (next < outfield.size()) at[outfield.get(next++)] = new float[]{0.5f, 0.5f};
        return at;
    }

    /** The lines' places along the pitch, for three, four or five lines. */
    private static float[] xFor(int lines) {
        if (lines <= 3) return new float[]{0.30f, 0.60f, 0.90f};
        if (lines == 4) return new float[]{0.27f, 0.47f, 0.70f, 0.92f};
        return new float[]{0.26f, 0.42f, 0.58f, 0.75f, 0.92f};
    }

    /** How far forward a position plays (ESPN's: G, CD-L, LB, DM, CM-R, AM-L, F and the rest). */
    private static int depth(String pos) {
        String p = pos == null ? "" : pos.toUpperCase(java.util.Locale.ROOT);
        if (p.equals("G")) return 0;
        if (p.startsWith("CD") || p.equals("LB") || p.equals("RB") || p.equals("D") || p.startsWith("SW") || p.endsWith("WB")) return 1;
        if (p.startsWith("DM") || p.equals("CDM")) return 2;
        if (p.startsWith("CM") || p.equals("LM") || p.equals("RM") || p.equals("M")) return 3;
        if (p.startsWith("AM")) return 4;
        if (p.equals("LW") || p.equals("RW")) return 5;
        return 6;
    }

    /** Where across the pitch a position plays: 0 wide left (LB, LM, LW), 1 left of centre (CD-L, AM-L), 2 centre, 3, 4. */
    private static int side(String pos) {
        String p = pos == null ? "" : pos.toUpperCase(java.util.Locale.ROOT);
        if (p.startsWith("L") && p.length() <= 3) return 0;
        if (p.endsWith("-L")) return 1;
        if (p.endsWith("-R")) return 3;
        if (p.startsWith("R") && p.length() <= 3) return 4;
        return 2;
    }

    /**
     * The pitch, drawn: stripes, lines, every player as a face in a ring with his number and name, the channel's logo
     * at the foot and "Press OK to watch" in the top corner. Players whose names would touch (or touch the logo or the
     * pill) are eased apart up or down the pitch, so every name reads.
     */
    private static final class Pitch extends View {
        private final Data d;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Team> teams = new ArrayList<>();
        private final List<Player> who = new ArrayList<>();
        private float[] xs = new float[0], ys = new float[0], tws = new float[0], sizes = new float[0];
        private final RectF logoAt = new RectF(), pillAt = new RectF();
        /** The players coming on, left to right (0 none yet, 1 all). */
        private float shown = 1f;

        void reveal() {
            shown = 0f;
            android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(1300);
            a.setStartDelay(150);
            a.setInterpolator(new DecelerateInterpolator(1.4f));
            a.addUpdateListener(v -> { shown = (float) v.getAnimatedValue(); invalidate(); });
            a.start();
        }

        Pitch(Context c, Data d) {
            super(c);
            this.d = d;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            float[][] home = places(d.home, true), away = places(d.away, true);
            teams.clear();
            who.clear();
            int n = d.home.players.size() + d.away.players.size();
            xs = new float[n]; ys = new float[n]; tws = new float[n]; sizes = new float[n];
            int i = 0;
            for (int j = 0; j < d.home.players.size(); j++, i++) {
                teams.add(d.home); who.add(d.home.players.get(j));
                xs[i] = w * (0.04f + (0.02f + home[j][0] * 0.46f) * 0.92f);
                ys[i] = h * home[j][1];
            }
            for (int j = 0; j < d.away.players.size(); j++, i++) {
                // Attacking the other way: its left side at the bottom.
                teams.add(d.away); who.add(d.away.players.get(j));
                xs[i] = w * (0.04f + (0.52f + (1 - away[j][0]) * 0.46f) * 0.92f);
                ys[i] = h * (1 - away[j][1]);
            }
            p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            for (i = 0; i < n; i++) {
                String name = who.get(i).last;
                p.setTextSize(px(name.length() > 8 ? 8.5f : 9.5f));
                float tw = p.measureText(name), maxW = px(62);
                sizes[i] = tw > maxW ? p.getTextSize() * maxW / tw : p.getTextSize();
                tws[i] = Math.min(tw, maxW);
            }
            logoAt.setEmpty();
            pillAt.setEmpty();
            settle(w, h);
        }

        /** The space a player takes: his ring and number above, his name below. */
        private RectF box(int i) {
            float half = Math.max(px(19), tws[i] / 2 + px(5));
            return new RectF(xs[i] - half, ys[i] - px(17), xs[i] + half, ys[i] + px(29));
        }

        /** Players eased apart up or down until none touches another, the logo or the pill. */
        private void settle(int w, int h) {
            float top = px(18), bottom = h - px(30);
            for (int round = 0; round < 60; round++) {
                boolean moved = false;
                for (int i = 0; i < xs.length; i++) {
                    for (RectF block : new RectF[]{logoAt, pillAt}) {
                        if (block.isEmpty()) continue;
                        RectF b = box(i);
                        if (!RectF.intersects(b, block)) continue;
                        // Away from it: down from the pill at the top, up from the logo at the foot.
                        ys[i] += block == pillAt ? block.bottom - b.top + 1 : -(b.bottom - block.top + 1);
                        moved = true;
                    }
                    for (int j = i + 1; j < xs.length; j++) {
                        RectF a = box(i), b = box(j);
                        if (!RectF.intersects(a, b)) continue;
                        float overlap = Math.min(a.bottom - b.top, b.bottom - a.top) / 2f + 1;
                        if (ys[i] <= ys[j]) { ys[i] -= overlap; ys[j] += overlap; }
                        else { ys[i] += overlap; ys[j] -= overlap; }
                        moved = true;
                    }
                }
                for (int i = 0; i < ys.length; i++) ys[i] = Math.max(top, Math.min(bottom, ys[i]));
                if (!moved) break;
            }
        }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            Path round = new Path();
            round.addRoundRect(new RectF(0, 0, w, h), px(8), px(8), Path.Direction.CW);
            cv.save();
            cv.clipPath(round);
            int stripes = 12;
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < stripes; i++) {
                p.setColor(i % 2 == 0 ? 0xFF2F8D3C : 0xFF36994A);
                cv.drawRect(w * i / (float) stripes, 0, w * (i + 1) / (float) stripes, h, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, px(1.6f)));
            p.setColor(0x99FFFFFF);
            float in = px(6);
            cv.drawRect(in, in, w - in, h - in, p);
            cv.drawLine(w / 2f, in, w / 2f, h - in, p);
            cv.drawCircle(w / 2f, h / 2f, px(26), p);
            cv.drawRect(in, h / 2f - px(48), in + px(46), h / 2f + px(48), p);
            cv.drawRect(w - in - px(46), h / 2f - px(48), w - in, h / 2f + px(48), p);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < xs.length; i++) {
                // Each player pops up in his place as the reveal crosses the pitch.
                float t = Math.max(0f, Math.min(1f, (shown * 1.35f - xs[i] / w) / 0.3f));
                if (t <= 0f) continue;
                float k = 0.55f + 0.45f * (1f - (1f - t) * (1f - t));
                cv.save();
                cv.scale(k, k, xs[i], ys[i]);
                draw(cv, teams.get(i), who.get(i), xs[i], ys[i], tws[i], sizes[i]);
                cv.restore();
            }
            if (d.channelLogo != null && !logoAt.isEmpty()) {
                cv.drawBitmap(d.channelLogo, null, logoAt, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));
            }
            if (!pillAt.isEmpty()) pill(cv);
            cv.restore();
        }

        private void pill(Canvas cv) {
            p.setColor(0xF2FFFFFF);
            cv.drawRoundRect(pillAt, pillAt.height() / 2, pillAt.height() / 2, p);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            p.setTextSize(px(10.5f));
            p.setTextAlign(Paint.Align.LEFT);
            Paint.FontMetrics fm = p.getFontMetrics();
            float mid = pillAt.centerY() - (fm.ascent + fm.descent) / 2f, x = pillAt.left + px(9);
            p.setColor(0xFF111214);
            cv.drawText("Press", x, mid, p);
            x += p.measureText("Press") + px(3);
            float kr = px(8);
            cv.drawCircle(x + kr, pillAt.centerY(), kr, p);
            p.setColor(0xFFFFFFFF);
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            p.setTextSize(px(6.5f));
            p.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics km = p.getFontMetrics();
            cv.drawText("OK", x + kr, pillAt.centerY() - (km.ascent + km.descent) / 2f, p);
            x += kr * 2 + px(3);
            p.setColor(0xFF111214);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            p.setTextSize(px(10.5f));
            p.setTextAlign(Paint.Align.LEFT);
            cv.drawText("to watch", x, mid, p);
        }

        private void draw(Canvas cv, Team t, Player pl, float cx, float cy, float tw, float size) {
            float r = px(13);
            // The face in its ring: a cut-out zoomed to the head, a framed photo whole, else the kit's avatar.
            Bitmap face = pl.photo != null ? pl.photo : avatar(t.photoKit != 0 ? t.photoKit : t.kit, t.photoKit != 0 ? t.photoSleeves : t.sleeves, "");
            boolean cut = pl.photo != null && !pl.framed;
            float zoom = cut ? 1.9f : 1f;
            Matrix m = new Matrix();
            float s = 2 * r * zoom / face.getWidth();
            m.setScale(s, s);
            m.postTranslate(cx - face.getWidth() * s / 2f, cut ? cy - r - 2 * r * 0.04f : cy - r);
            BitmapShader shader = new BitmapShader(face, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            shader.setLocalMatrix(m);
            p.setColor(t.kit != 0 ? t.kit : NAVY);
            cv.drawCircle(cx, cy, r, p);
            p.setShader(shader);
            cv.drawCircle(cx, cy, r, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, px(1.6f)));
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.FILL);
            float nx = cx + px(12), ny = cy - px(9), nr = px(7);
            p.setColor(t.kit != 0 ? t.kit : NAVY);
            cv.drawCircle(nx, ny, nr, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, px(1.2f)));
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(nx, ny, nr, p);
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(px(8.5f));
            Paint.FontMetrics fm = p.getFontMetrics();
            cv.drawText(pl.number, nx, ny - (fm.ascent + fm.descent) / 2f, p);
            p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            p.setTextSize(size);
            fm = p.getFontMetrics();
            float ly = cy + r + px(2), lh = px(13);
            p.setColor(0xA0000000);
            cv.drawRoundRect(new RectF(cx - tw / 2 - px(4), ly, cx + tw / 2 + px(4), ly + lh), px(6), px(6), p);
            p.setColor(0xFFFFFFFF);
            cv.drawText(pl.last, cx, ly + lh / 2f - (fm.ascent + fm.descent) / 2f, p);
        }
    }

    // ---- the last page -------------------------------------------------------------------------------------------

    /** "Starts in 15 min", "Starts in 1 hr 5 min", or "Kick-off now", from the kick-off and now. */
    static String startsIn(long kickoff) {
        if (kickoff <= 0) return null;
        long min = Math.round((kickoff - System.currentTimeMillis()) / 60_000.0);
        if (min <= 0) return "Kick-off now";
        if (min < 60) return "Starts in " + min + (min == 1 ? " min" : " mins");
        long h = min / 60, m = min % 60;
        return "Starts in " + h + (h == 1 ? " hr" : " hrs") + (m > 0 ? " " + m + (m == 1 ? " min" : " mins") : "");
    }

    /**
     * The first page: the two crests big on either side, and between them, centred as one block, the competition and
     * the ground, "Starts in …" and the kick-off, and the channel showing it with "Press OK to watch" (when a guide
     * lists it). Little empty ground: the crests fill the height.
     */
    private static View first(Context c, Data d) {
        FrameLayout page = new FrameLayout(c);
        page.setBackground(new Streaks(0xFF2A35B8, 0xFF080B3A, 0x0EFFFFFF, px(60), px(4)));
        int side = px(150);
        View home = club(c, d.home, px(116));
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(side, -2, Gravity.START | Gravity.CENTER_VERTICAL);
        hl.leftMargin = px(8);
        page.addView(home, hl);
        View away = club(c, d.away, px(116));
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(side, -2, Gravity.END | Gravity.CENTER_VERTICAL);
        al.rightMargin = px(8);
        page.addView(away, al);

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        StringBuilder where = new StringBuilder();
        if (d.competition != null && !d.competition.isEmpty()) where.append(d.competition);
        if (d.venue != null && !d.venue.isEmpty()) where.append(where.length() > 0 ? "  ·  " : "").append(d.venue);
        // In Home's own type (Inter), quietly: "Premier League · Anfield".
        TextView top = text(c, where.toString(), 13.5f, 0xC7FFFFFF, inter(c, R.font.inter_semibold));
        top.setGravity(Gravity.CENTER);
        mid.addView(top, new LinearLayout.LayoutParams(-1, -2));
        String starts = startsIn(d.kickoffAt);
        TextView when = text(c, starts != null ? starts : "v", starts != null ? 25 : 34, 0xFFFFFFFF,
                inter(c, R.font.inter_display_semibold));
        when.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
        wl.topMargin = px(10);
        mid.addView(when, wl);
        // "Starts in 15 mins" fades in slowly once the crests are in, then the kick-off time under it.
        when.setAlpha(0f);
        when.animate().alpha(1f).setStartDelay(950).setDuration(1_400).setInterpolator(new DecelerateInterpolator(1.2f)).withLayer().start();
        if (d.kickoff != null) {
            TextView ko = text(c, "Kick-off " + d.kickoff, 13.5f, 0xB3FFFFFF, inter(c, R.font.inter_regular));
            ko.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams kol = new LinearLayout.LayoutParams(-1, -2);
            kol.topMargin = px(4);
            mid.addView(ko, kol);
            ko.setAlpha(0f);
            ko.animate().alpha(1f).setStartDelay(1_650).setDuration(1_200).setInterpolator(new DecelerateInterpolator(1.2f)).withLayer().start();
        }
        View ch = channel(c, d, 26);
        if (ch != null || d.watch != null) {
            LinearLayout foot = new LinearLayout(c);
            foot.setOrientation(LinearLayout.VERTICAL);
            foot.setGravity(Gravity.CENTER_HORIZONTAL);
            if (ch != null) foot.addView(ch, new LinearLayout.LayoutParams(-2, px(26)));
            if (d.watch != null) {
                LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, px(30));
                ol.topMargin = px(ch != null ? 9 : 0);
                foot.addView(okPill(c, 13, 22), ol);
            }
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
            fl.topMargin = px(14);
            mid.addView(foot, fl);
        }
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(px(W - BAND) - 2 * side - px(16), -2, Gravity.CENTER);
        page.addView(mid, ml);

        // The crests come in from either side, then the block between them, each drawn once into a layer that moves.
        home.setTranslationX(-px(60)); away.setTranslationX(px(60));
        home.setAlpha(0f); away.setAlpha(0f); mid.setAlpha(0f);
        mid.setScaleX(0.9f); mid.setScaleY(0.9f);
        home.animate().translationX(0).alpha(1f).setStartDelay(250).setDuration(520).setInterpolator(new DecelerateInterpolator(2.2f)).withLayer().start();
        away.animate().translationX(0).alpha(1f).setStartDelay(250).setDuration(520).setInterpolator(new DecelerateInterpolator(2.2f)).withLayer().start();
        mid.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(520).setDuration(420).setInterpolator(new DecelerateInterpolator(2f)).withLayer().start();
        return page;
    }

    /** A club: its crest big, its name under it (on two lines when long). */
    private static View club(Context c, Team t, int crestPx) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.addView(crest(c, t == null ? null : t.crest), new LinearLayout.LayoutParams(crestPx, crestPx));
        TextView n = text(c, t == null ? "" : t.name, 14.5f, 0xFFFFFFFF, inter(c, R.font.inter_semibold));
        n.setGravity(Gravity.CENTER);
        n.setSingleLine(false);
        n.setMaxLines(2);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = px(6);
        v.addView(n, nl);
        return v;
    }

    /** One of Home's own typefaces (Inter); the system's when it cannot be read. */
    private static Typeface inter(Context c, int font) {
        try {
            return c.getResources().getFont(font);
        } catch (RuntimeException e) {
            return Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }
    }

    /** "Press [OK] to watch", on a white pill. */
    private static View okPill(Context c, float textPx, int keyPx) {
        LinearLayout pill = new LinearLayout(c);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2FFFFFF);
        bg.setCornerRadius(px(keyPx));
        pill.setBackground(bg);
        pill.setPadding(px(keyPx * 0.55f), 0, px(keyPx * 0.55f), 0);
        Typeface bold = inter(c, R.font.inter_semibold);
        pill.addView(text(c, "Press", textPx, 0xFF111214, bold));
        TextView key = text(c, "OK", textPx * 0.62f, 0xFFFFFFFF, Typeface.create("sans-serif-black", Typeface.NORMAL));
        key.setGravity(Gravity.CENTER);
        GradientDrawable disc = new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);
        disc.setColor(0xFF111214);
        key.setBackground(disc);
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(px(keyPx), px(keyPx));
        kl.leftMargin = kl.rightMargin = px(keyPx * 0.22f);
        pill.addView(key, kl);
        pill.addView(text(c, "to watch", textPx, 0xFF111214, bold));
        return pill;
    }

    /** The channel's logo, transparent, as it is; its name in white when there is no logo; null without either. */
    private static View channel(Context c, Data d, int height) {
        if (d.channelLogo != null) {
            ImageView logo = new ImageView(c);
            logo.setImageBitmap(sharp(d.channelLogo));
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.setAdjustViewBounds(true);
            logo.setMaxWidth(px(height * 11f));
            return logo;
        }
        if (d.channel == null) return null;
        TextView name = text(c, d.channel, height * 0.62f, 0xFFFFFFFF, Typeface.create("sans-serif-black", Typeface.NORMAL));
        name.setShadowLayer(px(3), 0, px(1), 0x99000000);
        name.setGravity(Gravity.CENTER_VERTICAL);
        return name;
    }

    /** On a TV with no helper to pass OK here: the window takes the keys on the last page; OK watches, any other closes. */
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
            if (ok && d.watch != null) d.watch.run();
            return true;
        });
    }

    /** The card away: faded, or at once when another takes its place. */
    static void close(boolean fade) {
        main.removeCallbacksAndMessages(null);
        FrameLayout v = host;
        host = null;
        showing = null;
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
        v.animate().alpha(0f).translationX(-px(30)).setDuration(260).withEndAction(gone).start();
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

    // ---- pictures for a player without a cut-out ------------------------------------------------------------------

    private static int trim(int kit) {
        return lum(kit) > 0.62 ? NAVY : 0xFFFFFFFF;
    }

    /**
     * No photo anywhere: a blank figure, head and shoulders, in the team's kit as it is today (the shirt's body and
     * sleeve colours), lit from the top left so it has some depth, his number on the chest.
     */
    static Bitmap avatar(int kit, int sleeves, String number) {
        int body = kit != 0 ? kit : NAVY, arms = sleeves != 0 ? sleeves : body;
        int s = 300;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        // Drawn higher in the frame (the head near the top, as in the players' photos); the shirt runs to the foot.
        cv.translate(0, -26);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // The neck, shadowed under the chin.
        p.setShader(new LinearGradient(0, 140, 0, 182, 0xFF9AA0B2, 0xFFC4C9D6, Shader.TileMode.CLAMP));
        cv.drawRoundRect(new RectF(127, 136, 173, 186), 14, 14, p);
        // The head, a lit sphere.
        p.setShader(new android.graphics.RadialGradient(128, 72, 92, new int[]{0xFFF7F8FB, 0xFFDDE0E8, 0xFFB4BAC9},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        cv.drawOval(new RectF(98, 38, 202, 160), p);
        p.setShader(null);
        // The shirt: the body, the sleeves over the shoulders, shaded at the sides and lit along the top.
        Path shirt = shoulders();
        cv.save();
        cv.clipPath(shirt);
        p.setShader(new LinearGradient(0, 166, 0, 300, lighter(body), darker(body), Shader.TileMode.CLAMP));
        cv.drawRect(0, 150, s, s + 40, p);
        if (arms != body) {
            p.setShader(new LinearGradient(0, 166, 0, 300, lighter(arms), darker(arms), Shader.TileMode.CLAMP));
            Path left = new Path();
            left.moveTo(0, 340);
            left.lineTo(0, 196);
            left.quadTo(58, 180, 84, 206);
            left.lineTo(70, 340);
            left.close();
            cv.drawPath(left, p);
            Matrix mirror = new Matrix();
            mirror.setScale(-1, 1, 150, 0);
            Path right = new Path(left);
            right.transform(mirror);
            cv.drawPath(right, p);
        }
        p.setShader(new LinearGradient(0, 0, s, 0, new int[]{0x55000000, 0x00000000, 0x00000000, 0x55000000},
                new float[]{0.05f, 0.36f, 0.64f, 0.95f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 150, s, s + 40, p);
        p.setShader(new LinearGradient(0, 166, 0, 236, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 150, s, s + 40, p);
        cv.restore();
        p.setShader(null);
        // The collar, in the sleeves' colour where they differ, else white or navy against the shirt.
        int trim = arms != body ? arms : (lum(body) > 0.62 ? NAVY : 0xFFFFFFFF);
        p.setColor(trim);
        Path collar = new Path();
        collar.moveTo(116, 170);
        collar.quadTo(150, 210, 184, 170);
        collar.lineTo(175, 165);
        collar.quadTo(150, 196, 125, 165);
        collar.close();
        cv.drawPath(collar, p);
        // The number, with a soft shadow.
        if (number != null && !number.isEmpty()) {
            p.setColor(lum(body) > 0.62 ? NAVY : 0xFFFFFFFF);
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(62);
            p.setShadowLayer(5, 0, 3, 0x66000000);
            cv.drawText(number, 150, 268, p);
            p.clearShadowLayer();
        }
        return b;
    }

    private static int lighter(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, bl = c & 0xFF;
        return 0xFF000000 | ((r + (255 - r) / 5) << 16) | ((g + (255 - g) / 5) << 8) | (bl + (255 - bl) / 5);
    }

    /** A photo of his face, framed in a white ring on shoulders in the team's kit (an old club's shirt never shows). */
    static Bitmap framed(Bitmap face, int kit) {
        int colour = kit != 0 ? kit : NAVY;
        int s = 300;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawPath(shoulders(), shirtPaint(colour));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Matrix m = new Matrix();
        float sc = 160f / Math.min(face.getWidth(), face.getHeight());
        m.setScale(sc, sc);
        m.postTranslate(150 - face.getWidth() * sc / 2f, 104 - face.getHeight() * sc / 2f);
        BitmapShader shader = new BitmapShader(face, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setLocalMatrix(m);
        p.setShader(shader);
        cv.drawCircle(150, 104, 80, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6);
        p.setColor(0xFFFFFFFF);
        cv.drawCircle(150, 104, 80, p);
        return b;
    }

    private static Path shoulders() {
        Path body = new Path();
        body.moveTo(18, 340);
        body.lineTo(34, 222);
        body.quadTo(44, 186, 96, 172);
        body.lineTo(122, 164);
        body.quadTo(150, 186, 178, 164);
        body.lineTo(204, 172);
        body.quadTo(256, 186, 266, 222);
        body.lineTo(282, 340);
        body.close();
        return body;
    }

    private static Paint shirtPaint(int colour) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 164, 0, 300, colour, darker(colour), Shader.TileMode.CLAMP));
        return p;
    }

    private static int darker(int c) {
        return 0xFF000000 | (((c >> 16) & 0xFF) * 8 / 10 << 16) | (((c >> 8) & 0xFF) * 8 / 10 << 8) | ((c & 0xFF) * 8 / 10);
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

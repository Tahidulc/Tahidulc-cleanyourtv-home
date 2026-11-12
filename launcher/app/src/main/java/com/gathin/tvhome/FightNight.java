package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UFC's fight nights (ESPN's card: its fights in order, the last the main event), in the line-up card's look over the
 * apps (a navy band down the left, the pale streaked stage, its lines coming in from the right): the main event's card
 * as the main card begins, with both fighters, their records, the weight and rounds, and about when they walk out
 * (the main card's start and the fights before it, or the fights still to come and the one on now); again ten minutes
 * before that walk; and, while a main-card fight is on the channel watched, a small card of the round and the time left
 * in it, the result as it ends. UFC only: ESPN has no boxing card to go by.
 */
final class FightNight {
    private FightNight() { }

    static final String SCORES = "https://site.api.espn.com/apis/site/v2/sports/mma/ufc/scoreboard";
    /** Each fight still to come before the main event, and each before it on the main card. */
    static final long PER_FIGHT_MS = 25 * 60_000L, PER_MAIN_CARD_FIGHT_MS = 22 * 60_000L, PER_ROUND_MS = 6 * 60_000L;
    /** Its switch in Notifications (Sport): "Fight night". */
    static final String KIND = "fightnight";
    static final long REMIND_MS = 10 * 60_000L, CARD_MS = 20_000L, RESULT_MS = 60_000L, ROUND_MS = 5 * 60_000L;

    /** One fighter: name, ESPN's id (for the picture), record. */
    static final class Fighter {
        String name = "", id = "", record = "";
        boolean winner;
        Bitmap photo;
    }

    /** One fight: its weight, rounds, when its part of the card starts, how it stands. */
    static final class Fight {
        String id = "", weight = "", state = "pre", result = "";
        int rounds = 3, round;
        long start;
        String clock = "";
        Fighter red = new Fighter(), blue = new Fighter();

        boolean on() {
            return "in".equals(state);
        }

        boolean over() {
            return "post".equals(state);
        }
    }

    /** One event: its name and fights in order, the main event last. */
    static final class Event {
        String id = "", name = "";
        final List<Fight> fights = new ArrayList<>();

        Fight main() {
            return fights.isEmpty() ? null : fights.get(fights.size() - 1);
        }

        /** The main card's start: the main event's part of the card. */
        long mainCardStart() {
            Fight m = main();
            return m == null ? 0 : m.start;
        }
    }

    private static final java.util.regex.Pattern ISO = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2})(:\\d{2})?Z");

    /** Pure: ESPN's "2026-11-15T02:00Z" in ms; 0 for none. */
    static long time(String iso) {
        java.util.regex.Matcher m = ISO.matcher(iso == null ? "" : iso);
        if (!m.find()) return 0;
        try {
            return Instant.parse(m.group(1) + (m.group(2) == null ? ":00" : m.group(2)) + "Z").toEpochMilli();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Pure: the events on ESPN's UFC scoreboard, each with its fights in order. */
    static List<Event> events(JSONObject board) {
        List<Event> out = new ArrayList<>();
        JSONArray events = board == null ? null : board.optJSONArray("events");
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            JSONArray comps = e == null ? null : e.optJSONArray("competitions");
            if (comps == null || comps.length() == 0) continue;
            Event ev = new Event();
            ev.id = e.optString("id");
            ev.name = e.optString("name", e.optString("shortName"));
            for (int k = 0; k < comps.length(); k++) {
                JSONObject c = comps.optJSONObject(k);
                if (c == null) continue;
                Fight f = new Fight();
                f.id = c.optString("id");
                f.start = time(c.optString("date"));
                JSONObject type = c.optJSONObject("type");
                f.weight = type == null ? "" : type.optString("abbreviation");
                JSONObject format = c.optJSONObject("format");
                JSONObject reg = format == null ? null : format.optJSONObject("regulation");
                if (reg != null && reg.optInt("periods") > 0) f.rounds = reg.optInt("periods");
                JSONObject st = c.optJSONObject("status");
                JSONObject stType = st == null ? null : st.optJSONObject("type");
                f.state = stType == null ? "pre" : stType.optString("state", "pre");
                f.round = st == null ? 0 : st.optInt("period");
                f.clock = st == null ? "" : st.optString("displayClock");
                JSONObject res = st == null ? null : st.optJSONObject("result");
                // How it was won, where ESPN says (KO/TKO, Decision); its status itself says only "Final".
                f.result = res != null ? res.optString("displayName", res.optString("shortDisplayName")) : "";
                JSONArray sides = c.optJSONArray("competitors");
                List<Fighter> fs = new ArrayList<>();
                int[] order = new int[2];
                for (int s = 0; sides != null && s < sides.length() && s < 2; s++) {
                    JSONObject x = sides.optJSONObject(s);
                    if (x == null) continue;
                    Fighter ft = new Fighter();
                    JSONObject a = x.optJSONObject("athlete");
                    ft.name = a == null ? "" : a.optString("displayName");
                    ft.id = x.optString("id");
                    ft.winner = x.optBoolean("winner");
                    JSONArray recs = x.optJSONArray("records");
                    JSONObject r0 = recs == null ? null : recs.optJSONObject(0);
                    ft.record = r0 == null ? "" : r0.optString("summary");
                    order[fs.size()] = x.optInt("order", s + 1);
                    fs.add(ft);
                }
                if (fs.size() == 2) {
                    boolean swap = order[1] < order[0];
                    f.red = swap ? fs.get(1) : fs.get(0);
                    f.blue = swap ? fs.get(0) : fs.get(1);
                }
                ev.fights.add(f);
            }
            if (!ev.fights.isEmpty()) out.add(ev);
        }
        return out;
    }

    /**
     * Pure: about when the main event's fighters walk out (ms), now: the later of the main card's start with the fights
     * before it on the main card, and now with the fights still to come before it and what is left of the one on; 0 once
     * the main event is on or over.
     */
    static long ringWalk(Event e, long now) {
        Fight main = e == null ? null : e.main();
        if (main == null || !"pre".equals(main.state)) return 0;
        long cardStart = main.start;
        int beforeOnMain = 0;
        long toCome = 0;
        for (int i = 0; i < e.fights.size() - 1; i++) {
            Fight f = e.fights.get(i);
            if (f.start == cardStart) beforeOnMain++;
            if (f.on()) toCome += Math.max(0, f.rounds - Math.max(1, f.round)) * PER_ROUND_MS + 5 * 60_000L;
            else if (!f.over()) toCome += PER_FIGHT_MS;
        }
        return Math.max(cardStart + beforeOnMain * PER_MAIN_CARD_FIGHT_MS, now + toCome);
    }

    /** Pure: "in 1 h 05", "in 12 min", "now". */
    static String until(long ms) {
        long m = Math.max(0, ms) / 60_000L;
        if (m <= 0) return "now";
        return m >= 60 ? String.format(Locale.UK, "in %d h %02d", m / 60, m % 60) : "in " + m + " min";
    }

    /** Pure: the seconds into its round ESPN's clock says ("2:27": 147; it counts up, as a result's time does); -1 for none. */
    static int elapsed(String clock) {
        String c = clock == null ? "" : clock.trim();
        int colon = c.indexOf(':');
        if (colon <= 0) return -1;
        try {
            int s = Integer.parseInt(c.substring(0, colon).trim()) * 60 + Integer.parseInt(c.substring(colon + 1).trim());
            return Math.max(0, Math.min(300, s));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Pure: the share of a five-minute round left so many seconds into it (3:13 in: 0.36). */
    static float roundLeft(int elapsed) {
        return elapsed < 0 ? -1f : Math.max(0f, Math.min(1f, (300 - elapsed) / 300f));
    }

    /**
     * Pure: the seconds into the round now, from where ESPN's clock last moved (anchored at anchorAt), counting on between
     * its reads: ESPN's clock may sit still between them. Never past the round's five minutes.
     */
    static int elapsedNow(int anchored, long anchorAt, long now) {
        if (anchored < 0) return -1;
        return (int) Math.min(300, anchored + Math.max(0, now - anchorAt) / 1000);
    }

    /** Pure: the time left in a round so far into it ("1:47 left"). */
    static String leftWords(int elapsed) {
        int left = Math.max(0, 300 - Math.max(0, elapsed));
        return String.format(Locale.UK, "%d:%02d left", left / 60, left % 60);
    }

    /**
     * Pure: the result's line: "Archer wins · KO/TKO R4", or "Archer wins · R3 2:27" where ESPN gives no method (the
     * winner's surname; the round, and the time in it it ended, when known); "Fight over" with no winner (a draw).
     */
    static String resultLine(Fight f) {
        Fighter w = f.red.winner ? f.red : f.blue.winner ? f.blue : null;
        String how = f.result == null || "final".equalsIgnoreCase(f.result.trim()) ? "" : f.result.trim();
        String clock = f.clock == null || f.clock.trim().isEmpty() || "0:00".equals(f.clock.trim()) || "-".equals(f.clock.trim()) ? "" : f.clock.trim();
        String when = f.round > 0 ? "R" + f.round + (how.isEmpty() && !clock.isEmpty() ? " " + clock : "") : "";
        String rest = (how + " " + when).trim();
        if (w == null) return rest.isEmpty() ? "Fight over" : "Fight over · " + rest;
        return GoalWatchService.surname(w.name) + " wins" + (rest.isEmpty() ? "" : " · " + rest);
    }

    /** The main-card fight on now (null none). */
    static Fight onNow(Event e) {
        if (e == null) return null;
        long card = e.mainCardStart();
        for (Fight f : e.fights) if (f.start == card && f.on()) return f;
        return null;
    }

    // ---- each scores poll ------------------------------------------------------------------------------------------------

    private static Handler mainHandler;

    private static synchronized Handler main() {
        if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
        return mainHandler;
    }
    private static ExecutorService io;
    private static Event event;
    private static long readAt;
    private static boolean reading;
    private static final Set<String> shown = new HashSet<>();
    private static Context app;
    private static Object[] lastProg;
    private static long mainShownAt;

    private static synchronized ExecutorService io() {
        if (io == null) io = Executors.newSingleThreadExecutor();
        return io;
    }

    /**
     * Each scores poll (main thread): UFC's card read (each minute on fight nights, every fifteen seconds while the round's
     * card is up), then the main event's card, its reminder and the round's card as they fall due. prog: the guide's
     * programme on the channel tuned ({title, start}), or null.
     */
    static void tick(Context c, Object[] prog) {
        app = c.getApplicationContext();
        if (prog != null) lastProg = prog;
        long now = System.currentTimeMillis();
        boolean onChannel = ufcOnChannel(app, lastProg);
        Event e = event;
        boolean near = near(e, now);
        // The round's card up: every fifteen seconds; a fight night (or UFC on): each minute; else every three hours.
        long every = roundWin != null ? 15_000L : near || onChannel ? 60_000L : 3 * 3_600_000L;
        if (reading || now - readAt < every) return;
        reading = true;
        readAt = now;
        io().execute(() -> {
            Event got = null;
            try {
                got = next(events(new JSONObject(ScoresTicker.get(SCORES, ScoresTicker.GENERIC_UA))), System.currentTimeMillis());
                if (got != null && near(got, System.currentTimeMillis())) photos(app, got);
            } catch (Exception ignored) {
                // read again next time
            }
            Event read = got;
            main().post(() -> {
                reading = false;
                if (read == null) return;
                event = read;
                if (near(read, System.currentTimeMillis())) due(app, read, ufcOnChannel(app, lastProg));
            });
        });
    }

    /** Pure: whether it is the event's fight night: from three hours before its main card to six after. */
    static boolean near(Event e, long now) {
        long start = e == null ? 0 : e.mainCardStart();
        return start > 0 && now > start - 3 * 3_600_000L && now < start + 6 * 3_600_000L;
    }

    /** Pure: the event on now or next: the first whose main card began less than eight hours ago. */
    static Event next(List<Event> all, long now) {
        Event best = null;
        for (Event x : all) {
            long s = x.mainCardStart();
            if (s <= 0 || s < now - 8 * 3_600_000L) continue;
            if (best == null || s < best.mainCardStart()) best = x;
        }
        return best;
    }

    /** Whether UFC is on the channel watched: the guide's programme, or the channel's own name. */
    private static boolean ufcOnChannel(Context c, Object[] prog) {
        String title = prog == null ? null : (String) prog[0];
        if ("ufc".equals(com.gathin.broadcast.Moments.sportOf(title))) return BoardCover.playerUp(c);
        for (String n : InStep.tunedNames(c)) if (n.toLowerCase(Locale.ROOT).contains("ufc")) return BoardCover.playerUp(c);
        return false;
    }

    /** The fighters' pictures (off the main thread), ESPN's cut-outs. */
    private static void photos(Context c, Event e) {
        Fight m = e.main();
        Fight on = onNow(e);
        for (Fight f : new Fight[]{m, on}) {
            if (f == null) continue;
            for (Fighter x : new Fighter[]{f.red, f.blue}) {
                if (x.photo != null || x.id.isEmpty()) continue;
                x.photo = ScoresTicker.cachedLogo(c, "https://a.espncdn.com/i/headshots/mma/players/full/" + x.id + ".png", 260);
            }
        }
    }

    /** What falls due now: the main event's card as the main card begins, its reminder, the round's card. */
    private static void due(Context c, Event e, boolean onChannel) {
        long now = System.currentTimeMillis();
        Fight m = e.main();
        if (m == null) return;
        long walk = ringWalk(e, now);
        boolean cardBegun = now >= e.mainCardStart() || onNow(e) != null;
        boolean recent = now - mainShownAt < 30 * 60_000L;
        // Once as UFC comes on the channel watched, once as the main card begins (not twice in half an hour), and ten
        // minutes before the walk.
        if ("pre".equals(m.state) && onChannel && !shown.contains("chan|" + e.id) && !recent) {
            shown.add("chan|" + e.id);
            showMain(c, e, false, false);
        } else if (cardBegun && "pre".equals(m.state) && shown.add("card|" + e.id) && !recent) {
            showMain(c, e, false, false);
        } else if (walk > 0 && walk - now <= REMIND_MS && walk - now > 0 && shown.add("remind|" + e.id)) {
            showMain(c, e, true, false);
        }
        Fight on = onNow(e);
        if (onChannel && on != null && (roundWin != null || NotifyPrefs.allowed(c, KIND, false))) showRound(c, on);
        else if (roundFor != null) {
            Fight was = null;
            for (Fight f : e.fights) if (f.id.equals(roundFor)) was = f;
            if (was != null && was.over()) roundResult(was);
            else if (!onChannel) closeRound();
        }
    }

    // ---- the cards, in the line-up card's look ----------------------------------------------------------------------------

    private static FrameLayout mainWin, roundWin;
    private static final Runnable closeMainRun = FightNight::closeMain, closeRoundRun = FightNight::closeRound;
    private static String roundFor;
    private static int roundShown = -1;
    private static float k = 1f;

    private static int px(float v) {
        return Math.round(v * k);
    }

    private static TextView words(Context c, String s, float size, int colour, boolean heavy) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(colour);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTypeface(heavy ? Typeface.create("sans-serif-black", Typeface.NORMAL) : Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** A card: the navy band down the left (its caption), the pale streaked stage beside it. */
    private static FrameLayout card(Context c, int w, int h, int band, String caption, FrameLayout[] stageOut) {
        FrameLayout card = new FrameLayout(c);
        float r = px(14);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        card.setClipToOutline(true);
        card.setBackgroundColor(0xFF0D1360);
        FrameLayout b = new FrameLayout(c);
        b.setBackground(new SkyStyle.Streaks(0xFF1B2591, 0xFF0D1360, 0x12FFFFFF, px(21), px(3)));
        card.addView(b, new FrameLayout.LayoutParams(band, h));
        LinearLayout bl = new LinearLayout(c);
        bl.setOrientation(LinearLayout.VERTICAL);
        bl.setGravity(Gravity.CENTER);
        bl.addView(new Gloves(c), new LinearLayout.LayoutParams(px(44), px(30)));
        TextView cap = words(c, caption, 17, 0xF2FFFFFF, true);
        cap.setSingleLine(false);
        cap.setGravity(Gravity.CENTER);
        cap.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams capl = new LinearLayout.LayoutParams(-2, -2);
        capl.topMargin = px(10);
        bl.addView(cap, capl);
        b.addView(bl, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout stage = new FrameLayout(c);
        stage.setBackground(new SkyStyle.Streaks(0xFFF4F5FD, 0xFFC2C5EE, 0x8CFFFFFF, px(75), px(32)));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(w - band, h);
        sl.leftMargin = band;
        card.addView(stage, sl);
        stageOut[0] = stage;
        return card;
    }

    /** A window for a card at (x, y) from the top left (or the bottom left), not taking the remote. */
    private static FrameLayout window(Context c, View card, int w, int h, int x, int y, boolean top, String title) {
        int margin = px(10);
        FrameLayout win = new FrameLayout(c);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(w, h);
        cl.setMargins(margin, margin, margin, margin);
        win.addView(card, cl);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w + 2 * margin, h + 2 * margin,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = (top ? Gravity.TOP : Gravity.BOTTOM) | Gravity.START;
        lp.x = x;
        lp.y = y;
        lp.setTitle(title);
        try {
            c.getSystemService(WindowManager.class).addView(win, lp);
        } catch (RuntimeException e) {
            return null;
        }
        return win;
    }

    private static void remove(FrameLayout win) {
        if (win == null) return;
        try {
            win.getContext().getSystemService(WindowManager.class).removeView(win);
        } catch (RuntimeException ignored) {
            // gone already
        }
    }

    /** The card in from the left as the line-up card comes, its lines from the right one after another. */
    private static void slideIn(View card, List<View> lines) {
        card.setTranslationX(-card.getLayoutParams().width - px(40));
        card.animate().translationX(0).setDuration(520).setInterpolator(new DecelerateInterpolator(2.2f)).start();
        for (int i = 0; i < lines.size(); i++) {
            View v = lines.get(i);
            v.setAlpha(0f);
            v.setTranslationX(px(60));
            v.animate().alpha(1f).translationX(0).setStartDelay(260L + 90L * i).setDuration(460).setInterpolator(new DecelerateInterpolator(2.2f)).start();
        }
    }

    /** One fighter's side of the main event's card: the picture, the name, the record, the corner's colour along the foot. */
    private static LinearLayout side(Context c, Fighter f, boolean red) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView pic = new ImageView(c);
        pic.setScaleType(ImageView.ScaleType.FIT_END);
        if (f.photo != null) pic.setImageBitmap(f.photo);
        else pic.setImageDrawable(new GradientDrawable() {{ setShape(OVAL); setColor(red ? 0xFFC8102E : 0xFF1B458F); }});
        col.addView(pic, new LinearLayout.LayoutParams(px(118), px(108)));
        View bar = new View(c);
        bar.setBackgroundColor(red ? 0xFFD42B2B : 0xFF2E5FD6);
        col.addView(bar, new LinearLayout.LayoutParams(px(150), px(5)));
        TextView n = words(c, f.name, 24, 0xFF151A63, true);
        n.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(px(230), -2);
        nl.topMargin = px(8);
        col.addView(n, nl);
        TextView r = words(c, f.record.isEmpty() ? (red ? "Red corner" : "Blue corner") : f.record + (red ? " · red corner" : " · blue corner"), 16, 0xFF5A6094, false);
        r.setGravity(Gravity.CENTER);
        col.addView(r, new LinearLayout.LayoutParams(px(230), -2));
        return col;
    }

    /** The main event's card (reminder: ten minutes to the walk), over the apps or on Home, for twenty seconds. */
    static void showMain(Context ctx, Event e, boolean reminder, boolean test) {
        Context c = ctx.getApplicationContext();
        boolean home = NotifyPrefs.homeNow();
        if (!test && !NotifyPrefs.allowed(c, KIND, home)) {
            NotifyPrefs.held(c, KIND, home, null, "fight night: " + e.name);
            return;
        }
        mainShownAt = System.currentTimeMillis();
        Fight m = e.main();
        if (m == null) return;
        closeMain();
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f * 0.88f;
        int w = px(820), h = px(268), band = px(150);
        FrameLayout[] st = new FrameLayout[1];
        FrameLayout card = card(c, w, h, band, reminder ? "WALK\nIN 10" : "MAIN\nEVENT", st);
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(px(18), px(12), px(18), px(12));
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout redSide = side(c, m.red, true), blueSide = side(c, m.blue, false);
        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        mid.addView(words(c, "v", 34, 0xFF151A63, true));
        TextView wt = words(c, m.weight, 15, 0xFF5A6094, true);
        wt.setGravity(Gravity.CENTER);
        mid.addView(wt, new LinearLayout.LayoutParams(px(150), -2));
        mid.addView(words(c, m.rounds + " ROUNDS", 15, 0xFF5A6094, true));
        row.addView(redSide, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(mid, new LinearLayout.LayoutParams(px(150), -2));
        row.addView(blueSide, new LinearLayout.LayoutParams(0, -2, 1f));
        body.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        long now = System.currentTimeMillis(), walk = ringWalk(e, now);
        String when = walk > 0 ? Instant.ofEpochMilli(walk).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) : "";
        TextView foot = words(c, walk > 0 ? "RING WALK ABOUT " + when + " · " + until(walk - now) : e.name, 19, 0xFF151A63, true);
        foot.setLetterSpacing(0.04f);
        foot.setGravity(Gravity.CENTER);
        body.addView(foot, new LinearLayout.LayoutParams(-1, -2));
        st[0].addView(body, new FrameLayout.LayoutParams(-1, -1));
        mainWin = window(c, card, w, h, px(34), px(AmbientScene.showing() ? AmbientScene.ALERT_FOOT : 34), false, "FightNight");
        if (mainWin == null) return;
        AlertLog.note(c, (reminder ? "ring-walk reminder: " : "main event card: ") + m.red.name + " v " + m.blue.name);
        List<View> lines = new ArrayList<>();
        lines.add(redSide);
        lines.add(mid);
        lines.add(blueSide);
        lines.add(foot);
        slideIn(card, lines);
        main().postDelayed(closeMainRun, CARD_MS);
    }

    private static void closeMain() {
        main().removeCallbacks(closeMainRun);
        FrameLayout w = mainWin;
        mainWin = null;
        if (w == null) return;
        View card = w.getChildAt(0);
        if (card == null) {
            remove(w);
            return;
        }
        card.animate().translationX(-card.getWidth() - px(40)).setDuration(380).withEndAction(() -> remove(w)).start();
    }

    private static TextView roundWords, timeWords, resultWords;
    private static View timeBar;

    /** The round's card near the top left: both fighters in their corners' colours, the round, the time left in it. */
    static void showRound(Context ctx, Fight f) {
        Context c = ctx.getApplicationContext();
        if (roundWin != null && f.id.equals(roundFor)) {
            updateRound(f);
            return;
        }
        closeRound();
        k = c.getResources().getDisplayMetrics().widthPixels / 1920f * 0.88f;
        int w = px(560), h = px(118), band = px(118);
        FrameLayout[] st = new FrameLayout[1];
        FrameLayout card = card(c, w, h, band, "ROUND", st);
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(px(16), px(10), px(16), px(10));
        LinearLayout names = new LinearLayout(c);
        names.setGravity(Gravity.CENTER_VERTICAL);
        View rb = new View(c);
        rb.setBackgroundColor(0xFFD42B2B);
        names.addView(rb, new LinearLayout.LayoutParams(px(6), px(26)));
        TextView rn = words(c, GoalWatchService.surname(f.red.name).toUpperCase(Locale.UK), 21, 0xFF151A63, true);
        LinearLayout.LayoutParams rnl = new LinearLayout.LayoutParams(0, -2, 1f);
        rnl.leftMargin = px(8);
        names.addView(rn, rnl);
        roundWords = words(c, "", 21, 0xFF151A63, true);
        names.addView(roundWords);
        TextView bn = words(c, GoalWatchService.surname(f.blue.name).toUpperCase(Locale.UK), 21, 0xFF151A63, true);
        bn.setGravity(Gravity.END);
        LinearLayout.LayoutParams bnl = new LinearLayout.LayoutParams(0, -2, 1f);
        bnl.rightMargin = px(8);
        names.addView(bn, bnl);
        View bb = new View(c);
        bb.setBackgroundColor(0xFF2E5FD6);
        names.addView(bb, new LinearLayout.LayoutParams(px(6), px(26)));
        body.addView(names, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout clock = new LinearLayout(c);
        clock.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout track = new FrameLayout(c);
        track.setBackground(fill(0x26151A63, px(4)));
        timeBar = new View(c);
        timeBar.setBackground(fill(0xFF151A63, px(4)));
        timeBar.setPivotX(0);
        track.addView(timeBar, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, px(8), 1f);
        clock.addView(track, tl);
        timeWords = words(c, "", 17, 0xFF151A63, true);
        LinearLayout.LayoutParams twl = new LinearLayout.LayoutParams(-2, -2);
        twl.leftMargin = px(12);
        clock.addView(timeWords, twl);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.topMargin = px(14);
        body.addView(clock, cl);
        resultWords = words(c, "", 19, 0xFF151A63, true);
        resultWords.setVisibility(View.GONE);
        body.addView(resultWords, new LinearLayout.LayoutParams(-1, -2));
        st[0].addView(body, new FrameLayout.LayoutParams(-1, -1));
        roundWin = window(c, card, w, h, px(60), px(48), true, "FightRound");
        if (roundWin == null) return;
        roundFor = f.id;
        roundShown = -1;
        updateRound(f);
        List<View> lines = new ArrayList<>();
        lines.add(names);
        lines.add(clock);
        slideIn(card, lines);
        main().removeCallbacks(roundLoop);
        main().postDelayed(roundLoop, 15_000L);
    }

    private static final Runnable roundLoop = () -> {
        if (roundWin == null || app == null) return;
        readAt = 0;
        tick(app, null);
        FightNight.main().postDelayed(FightNight.roundLoop, 15_000L);
    };

    private static GradientDrawable fill(int colour, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colour);
        g.setCornerRadius(r);
        return g;
    }

    /** Where the round's clock was last seen to move: its round, its seconds in, when. */
    private static int anchorRound = -1, anchorElapsed = -1;
    private static long anchorAt;

    /** The round and the time left, as ESPN has them now (its clock counted on between reads). */
    private static void updateRound(Fight f) {
        if (roundWords == null) return;
        roundWords.setText(f.round > 0 ? f.round + " / " + f.rounds : "– / " + f.rounds);
        int e = elapsed(f.clock);
        long now = System.currentTimeMillis();
        // A new round, or ESPN's clock moved: counted from here; the same reading again: counted on from before.
        if (f.round != anchorRound || e >= 0 && e != anchorElapsed) {
            anchorRound = f.round;
            anchorElapsed = e;
            anchorAt = now;
        }
        main().removeCallbacks(clockRun);
        clockRun.run();
        if (f.round != roundShown && roundShown >= 0) {
            roundWords.setScaleX(1.3f);
            roundWords.setScaleY(1.3f);
            roundWords.animate().scaleX(1f).scaleY(1f).setDuration(380).start();
        }
        roundShown = f.round;
    }

    /** The time left in the round, its words and its bar, each second while the round's card is up. */
    private static final Runnable clockRun = new Runnable() {
        @Override public void run() {
            if (timeWords == null || roundWin == null) return;
            int e = elapsedNow(anchorElapsed, anchorAt, System.currentTimeMillis());
            timeWords.setText(e >= 0 ? leftWords(e) : "");
            if (timeBar != null) timeBar.animate().scaleX(Math.max(0f, roundLeft(e))).setDuration(900)
                    .setInterpolator(new android.view.animation.LinearInterpolator()).start();
            if (e >= 0 && e < 300) main().postDelayed(this, 1_000L);
        }
    };

    /** The fight over: its result where the clock was, the card gone a minute later. */
    static void roundResult(Fight f) {
        if (resultWords == null || roundWin == null) return;
        if (resultWords.getVisibility() == View.VISIBLE) return;
        main().removeCallbacks(clockRun);
        ((View) timeWords.getParent()).setVisibility(View.GONE);
        resultWords.setText(resultLine(f));
        resultWords.setTextColor(f.red.winner ? 0xFFB01E2A : f.blue.winner ? 0xFF1E46B0 : 0xFF151A63);
        resultWords.setVisibility(View.VISIBLE);
        resultWords.setAlpha(0f);
        resultWords.setTranslationX(px(60));
        resultWords.animate().alpha(1f).translationX(0).setDuration(460).start();
        main().removeCallbacks(roundLoop);
        main().postDelayed(closeRoundRun, RESULT_MS);
    }

    static void closeRound() {
        main().removeCallbacks(roundLoop);
        main().removeCallbacks(closeRoundRun);
        main().removeCallbacks(clockRun);
        anchorRound = anchorElapsed = -1;
        FrameLayout w = roundWin;
        roundWin = null;
        roundFor = null;
        roundWords = timeWords = resultWords = null;
        timeBar = null;
        if (w == null) return;
        View card = w.getChildAt(0);
        if (card == null) {
            remove(w);
            return;
        }
        card.animate().translationX(-card.getWidth() - px(40)).setDuration(380).withEndAction(() -> remove(w)).start();
    }

    /** Red and blue gloves, side by side: the band's mark. */
    private static final class Gloves extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Gloves(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight(), r = Math.min(w / 2f, h) * 0.46f;
            glove(cv, w * 0.32f, h * 0.48f, r, 0xFFD42B2B);
            glove(cv, w * 0.68f, h * 0.48f, r, 0xFF3D7BFF);
        }

        private void glove(Canvas cv, float cx, float cy, float r, int colour) {
            p.setColor(colour);
            cv.drawCircle(cx, cy, r, p);
            path.reset();
            path.addRoundRect(cx - r * 0.7f, cy + r * 0.6f, cx + r * 0.7f, cy + r * 1.15f, r * 0.2f, r * 0.2f, Path.Direction.CW);
            cv.drawPath(path, p);
            p.setColor(0x55FFFFFF);
            cv.drawCircle(cx - r * 0.3f, cy - r * 0.3f, r * 0.28f, p);
        }
    }

    // ---- the test (adb) -------------------------------------------------------------------------------------------------

    /** adb: a made-up fight night: the main event's card, then the round's card ticking down, then its result. */
    static void test(Context ctx) {
        Context c = ctx.getApplicationContext();
        app = c;
        long now = System.currentTimeMillis();
        Event e = new Event();
        e.id = "test";
        e.name = "Fight Night: Archer vs Ruiz";
        for (int i = 0; i < 5; i++) {
            Fight f = new Fight();
            f.id = "t" + i;
            f.start = i < 2 ? now - 3 * 3_600_000L : now - 20 * 60_000L;
            f.state = i < 3 ? "post" : i == 3 ? "in" : "pre";
            f.round = i == 3 ? 2 : 0;
            f.rounds = i == 4 ? 5 : 3;
            f.weight = i == 4 ? "Heavyweight" : "Lightweight";
            f.red.name = i == 4 ? "Kane Archer" : "Red Fighter " + i;
            f.blue.name = i == 4 ? "Mateo Ruiz" : "Blue Fighter " + i;
            f.red.record = "24-1-0";
            f.blue.record = "19-0-0";
            e.fights.add(f);
        }
        showMain(c, e, false, true);
        Fight m = e.main();
        main().postDelayed(() -> {
            m.state = "in";
            m.round = 4;
            m.clock = "3:13";
            showRound(c, m);
        }, CARD_MS + 1_000L);
        main().postDelayed(() -> {
            m.clock = "4:08";
            updateRound(m);
        }, CARD_MS + 9_000L);
        main().postDelayed(() -> {
            m.state = "post";
            m.red.winner = true;
            m.result = "KO/TKO";
            m.clock = "4:31";
            roundResult(m);
        }, CARD_MS + 18_000L);
    }
}

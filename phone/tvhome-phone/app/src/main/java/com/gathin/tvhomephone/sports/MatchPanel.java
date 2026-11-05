package com.gathin.tvhomephone.sports;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The match centre over the stream: a dark panel up from the foot of the picture (rounded at the top, a handle to pull it
 * down by) with the game's Timeline, Line-ups, Stats (two across when it is wide) and Table, drawn as the game's own page
 * draws them (GameTabs) and read again every 30 seconds while it is on screen and the game is on. The table is the
 * competition's as it stands, with the live score counted in and both clubs marked. Pull the handle down, or ✕, and it
 * goes (setOnClose tells the player). ESPN is read off the main thread.
 */
public final class MatchPanel extends LinearLayout {
    /** The tabs: the game's own three, then the table. */
    static final int TABLE = 3;
    private static final String[] NAMES = {GameTabs.NAMES[0], GameTabs.NAMES[1], GameTabs.NAMES[2], "Table"};
    private static final long REFRESH_MS = 30_000L;
    /** How much of the screen's height it takes. */
    static final float SHARE = 0.58f;

    private final GameTabs parts;
    private final LinearLayout tabs, body;
    private final TextView score;
    private final NestedScrollView scroll;
    private Game game;
    private Espn.Summary summary;
    private List<String[]> table;
    private int tab = -1;
    /** The table's first row for one of the game's clubs, drawn last. */
    private View marked;
    private boolean attached, closing;
    private Runnable onClose;
    private final Runnable refresh = this::load;

    public MatchPanel(Context c) {
        super(c);
        setOrientation(VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF00B0C21);
        float r = Ui.dp(c, 22);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        setBackground(bg);
        setClickable(true);
        parts = new GameTabs(c, () -> attached);

        FrameLayout handle = new FrameLayout(c);
        View pill = new View(c);
        pill.setBackground(Ui.fill(c, 0x59FFFFFF, 2));
        handle.addView(pill, new FrameLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 4), Gravity.CENTER));
        handle.setContentDescription("Pull down to close the match centre");
        drag(handle);
        addView(handle, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 22)));

        LinearLayout head = Ui.row(c);
        int side = Ui.dp(c, 18);
        head.setPadding(side, 0, Ui.dp(c, 6), 0);
        tabs = Ui.row(c);
        head.addView(tabs, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        score = Ui.text(c, "", 13, c.getColor(R.color.text_soft), true);
        score.setMaxLines(1);
        score.setEllipsize(TextUtils.TruncateAt.END);
        score.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 6), 0);
        head.addView(score);
        ImageButton close = new ImageButton(c);
        close.setImageResource(R.drawable.live_ic_close);
        close.setColorFilter(Color.WHITE);
        close.setBackground(null);
        close.setContentDescription("Close the match centre");
        close.setOnClickListener(v -> close());
        head.addView(close, new LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        drag(head);
        addView(head, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 44)));
        View line = new View(c);
        line.setBackgroundColor(0x1FFFFFFF);
        addView(line, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(c, 1)));

        scroll = new NestedScrollView(c);
        scroll.setClipToPadding(false);
        body = Ui.column(c);
        scroll.addView(body, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
    }

    /** The panel's height on this screen: a little over half of it. */
    public static int height(Context c) {
        return Math.round(c.getResources().getDisplayMetrics().heightPixels * SHARE);
    }

    /** Laid over the picture at its foot, at height(). */
    public static FrameLayout.LayoutParams layoutParams(Context c) {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height(c), Gravity.BOTTOM);
    }

    public void setOnClose(Runnable r) {
        onClose = r;
    }

    /** The game to show: drawn at once from what is known, then read from ESPN (and again every 30 seconds while on). */
    public void show(Game g) {
        if (g == null) return;
        boolean other = game == null || !g.id.equals(game.id);
        game = g;
        if (other) {
            summary = null;
            table = null;
            tab = -1;
        }
        paint();
        removeCallbacks(refresh);
        load();
    }

    /** Up from the foot of the screen. */
    public void slideIn() {
        setTranslationY(height(getContext()));
        animate().translationY(0).setDuration(220).start();
    }

    /** Down and away; the player hears (setOnClose). */
    public void close() {
        if (closing) return;
        closing = true;
        removeCallbacks(refresh);
        animate().translationY(Math.max(getHeight(), height(getContext()))).setDuration(180).withEndAction(() -> {
            if (onClose != null) onClose.run();
        }).start();
    }

    /** Pulled down by the handle (or the tab row): it goes when pulled a quarter of the way, or flicked. */
    @SuppressLint("ClickableViewAccessibility")
    private void drag(View grip) {
        float[] from = new float[1];
        VelocityTracker[] vt = new VelocityTracker[1];
        grip.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    from[0] = e.getRawY();
                    vt[0] = VelocityTracker.obtain();
                    vt[0].addMovement(e);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (vt[0] != null) vt[0].addMovement(e);
                    setTranslationY(Math.max(0, e.getRawY() - from[0]));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float speed = 0;
                    if (vt[0] != null) {
                        vt[0].addMovement(e);
                        vt[0].computeCurrentVelocity(1000);
                        speed = vt[0].getYVelocity();
                        vt[0].recycle();
                        vt[0] = null;
                    }
                    float moved = e.getRawY() - from[0];
                    if (e.getActionMasked() == MotionEvent.ACTION_UP && shouldClose(moved, speed, getHeight(), Ui.dp(getContext(), 8))) {
                        close();
                    } else {
                        animate().translationY(0).setDuration(160).start();
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    /** Whether a pull of moved pixels at speed (px/s) on a panel this tall closes it. Pure. */
    static boolean shouldClose(float moved, float speed, int height, int slop) {
        if (moved < slop) return false;
        return moved > height * 0.25f || speed > 1200;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (game != null) {
            removeCallbacks(refresh);
            postDelayed(refresh, REFRESH_MS);
        }
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        removeCallbacks(refresh);
        super.onDetachedFromWindow();
    }

    /** The game, its summary and (when the table is shown, or there is nothing else) the table, read again. */
    private void load() {
        Game g = game;
        if (g == null || g.slug == null || g.id == null) return;
        boolean wantTable = table == null;
        App.io.execute(() -> {
            Game fresh = null;
            LocalDate day = java.time.Instant.ofEpochMilli(g.kickoff).atZone(ZoneId.systemDefault()).toLocalDate();
            for (Game x : Espn.scoreboard(g.slug, day, 15_000L)) if (x.id.equals(g.id)) fresh = x;
            Espn.Summary s = Espn.summary(g.slug, g.id, 20_000L);
            List<String[]> t = wantTable ? Espn.table(g.slug) : null;
            Game got = fresh;
            post(() -> {
                if (game == null || !game.id.equals(g.id)) return;
                if (got != null) game = got;
                summary = s;
                if (t != null) table = t;
                int y = scroll.getScrollY();
                paint();
                scroll.post(() -> scroll.scrollTo(0, y));
                if (attached && !closing && game.state != Game.POST) {
                    removeCallbacks(refresh);
                    postDelayed(refresh, REFRESH_MS);
                }
            });
        });
    }

    /** The tabs there is something in: the game's own, then the table when the competition has one. */
    private List<Integer> available() {
        parts.set(game, summary);
        List<Integer> out = new ArrayList<>(parts.available());
        if (table != null && !table.isEmpty()) out.add(TABLE);
        return out;
    }

    private void paint() {
        Context c = getContext();
        if (game == null) return;
        score.setText(line(game));
        List<Integer> have = available();
        if (!have.contains(tab)) tab = have.isEmpty() ? -1 : have.contains(GameTabs.STATS) && game.state == Game.LIVE
                ? GameTabs.STATS : have.get(0);
        tabs.removeAllViews();
        for (int t : have) {
            boolean on = t == tab;
            TextView v = Ui.text(c, NAMES[t], 14, on ? Color.WHITE : c.getColor(R.color.text_soft), on);
            v.setGravity(Gravity.CENTER);
            v.setMaxLines(1);
            v.setSelected(on);
            // The line under the chosen one (its inset would be taken as padding: the padding is set after it).
            if (on) v.setBackground(underline(c));
            v.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 12), 0);
            v.setOnClickListener(x -> {
                tab = t;
                paint();
                scroll.scrollTo(0, 0);
                if (t == TABLE) toClubs();
            });
            tabs.addView(v, new LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(c, 44)));
        }
        body.removeAllViews();
        // Not wider than a phone's page, in the middle of a wide panel.
        int w = getWidth() > 0 ? getWidth() : c.getResources().getDisplayMetrics().widthPixels;
        int side = Math.max(Ui.dp(c, 18), (w - Ui.dp(c, 720)) / 2);
        body.setPadding(side, Ui.dp(c, 4), side, Ui.dp(c, 18));
        boolean wide = w >= Ui.dp(c, 600);
        parts.columns(wide ? 2 : 1);
        parts.compact(wide);
        if (summary == null && table == null) {
            Ui.empty(body, "Reading the game…");
        } else if (tab == TABLE) {
            table(c);
        } else if (tab >= 0) {
            parts.paint(tab, body);
        } else {
            Ui.empty(body, game.state == Game.PRE ? "The line-ups come about an hour before kick-off." : "Nothing yet.");
        }
    }

    /** The chosen tab's white line along its foot. */
    private static android.graphics.drawable.Drawable underline(Context c) {
        android.graphics.drawable.ShapeDrawable d = new android.graphics.drawable.ShapeDrawable();
        d.getPaint().setColor(Color.WHITE);
        return new android.graphics.drawable.InsetDrawable(d, 0, Ui.dp(c, 41), 0, 0);
    }

    /** "Everton 1–0 Brentford · 63'" (or the kick-off time). */
    static String line(Game g) {
        if (g.state == Game.PRE) {
            return g.homeName + " v " + g.awayName + " · " + java.time.Instant.ofEpochMilli(g.kickoff).atZone(ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        }
        String when = g.state == Game.POST ? "FT" : g.halftime ? "HT" : g.detail == null ? "" : g.detail;
        return g.homeName + " " + g.score() + " " + g.awayName + (when.isEmpty() ? "" : " · " + when);
    }

    // ---- the table -------------------------------------------------------------------------------------------------

    /** The competition's table as it stands: the live score counted in while the game is on, both clubs marked. */
    private void table(Context c) {
        boolean live = game.state == Game.LIVE;
        List<String[]> rows = live ? standing(table, game) : table;
        marked = null;
        TextView title = Ui.heading(c, live ? "As it stands" : "Table", c.getColor(R.color.text_soft));
        title.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 8));
        body.addView(title);
        LinearLayout head = SportsFragment.tableRow(c, new String[]{"#", null, "Club", "P", "W", "D", "L", "GD", "Pts"}, false, true);
        head.setAlpha(0.7f);
        body.addView(head);
        Set<String> mine = new java.util.HashSet<>();
        mine.add(game.homeId);
        mine.add(game.awayId);
        // A cup's groups: only the group these clubs are in.
        String group = groupOf(rows, mine);
        if (!group.isEmpty()) body.addView(Ui.heading(c, group, c.getColor(R.color.text_soft)));
        for (String[] r : rows) {
            if (!r[7].equals(group)) continue;
            String w = r.length > 10 ? r[8] : "", d = r.length > 10 ? r[9] : "", l = r.length > 10 ? r[10] : "";
            LinearLayout row = SportsFragment.tableRow(c, new String[]{r[0], r[3], r[2], r[4], w, d, l, r[5], r[6]}, mine.contains(r[1]), false);
            if (marked == null && mine.contains(r[1])) marked = row;
            body.addView(row);
        }
    }

    /** The table scrolled to the first of the game's two clubs (a long one would start far from them). */
    private void toClubs() {
        View first = marked;
        if (first == null) return;
        scroll.post(() -> scroll.scrollTo(0, Math.max(0, first.getTop() - Ui.dp(getContext(), 56))));
    }

    /** The group the game's clubs are in ("" for a league). */
    private static String groupOf(List<String[]> rows, Set<String> ids) {
        for (String[] r : rows) if (ids.contains(r[1])) return r[7];
        return "";
    }

    /**
     * The table with a live game's score counted in (pure, for the tests): each club's played, won, drawn, lost, goal
     * difference and points moved on by the score now, then sorted again by points, goal difference and the old order,
     * the places numbered afresh. Rows: {rank, id, name, logo, played, gd, points, group, won, drawn, lost}.
     */
    static List<String[]> standing(List<String[]> rows, Game g) {
        List<String[]> out = new ArrayList<>(rows.size());
        Map<String, Integer> was = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i).clone();
            was.put(r[1], i);
            if (r[1].equals(g.homeId)) moved(r, g.homeScore, g.awayScore);
            else if (r[1].equals(g.awayId)) moved(r, g.awayScore, g.homeScore);
            out.add(r);
        }
        out.sort((a, b) -> {
            if (!a[7].equals(b[7])) return a[7].compareTo(b[7]);
            int p = Integer.compare(Espn.parseInt(b[6]), Espn.parseInt(a[6]));
            if (p != 0) return p;
            int d = Integer.compare(gd(b[5]), gd(a[5]));
            return d != 0 ? d : Integer.compare(was.get(a[1]), was.get(b[1]));
        });
        String group = null;
        int n = 0;
        for (String[] r : out) {
            if (!r[7].equals(group)) {
                group = r[7];
                n = 0;
            }
            r[0] = String.valueOf(++n);
        }
        return out;
    }

    private static void moved(String[] r, int scored, int against) {
        r[4] = String.valueOf(Espn.parseInt(r[4]) + 1);
        int d = gd(r[5]) + scored - against;
        r[5] = d > 0 ? "+" + d : String.valueOf(d);
        int pts = scored > against ? 3 : scored == against ? 1 : 0;
        r[6] = String.valueOf(Espn.parseInt(r[6]) + pts);
        if (r.length > 10) {
            int k = scored > against ? 8 : scored == against ? 9 : 10;
            if (!r[k].isEmpty()) r[k] = String.valueOf(Espn.parseInt(r[k]) + 1);
        }
    }

    private static int gd(String s) {
        return Espn.parseInt(s == null ? "" : s.replace("+", "").trim());
    }
}

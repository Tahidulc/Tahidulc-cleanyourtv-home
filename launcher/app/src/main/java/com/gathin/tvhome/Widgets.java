package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * The ad-break screen's widgets (AdBreak), each from what was fetched for the game (AdBreak.Pack), in the line-up
 * card's look: navy lettering on its pale streaked panel, a crest by every club, a picture by every player. A widget is
 * made only when its data is there; nothing is filled in by guess.
 */
final class Widgets {
    private Widgets() { }

    static final int INK = 0xFF151A63, MUTE = 0xFF5A6094, WIN = 0xFF22A447, LOSS = 0xFFD42B2B;

    /** A widget's lines, made fresh each time it comes round. */
    interface Lines {
        List<View> make(Context c);
    }

    private static float s;

    private static int px(float v) {
        return Math.round(v * s);
    }

    private static TextView t(Context c, String text, float size, int colour, boolean heavy) {
        return AdBreak.text(c, text, size, colour, heavy ? Typeface.create("sans-serif-black", Typeface.NORMAL) : Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    private static TextView foot(Context c, String text) {
        return t(c, text, 15, MUTE, false);
    }

    private static LinearLayout row(Context c, float gap, View... views) {
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < views.length; i++) {
            if (views[i] == null) continue;
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
            if (r.getChildCount() > 0) l.leftMargin = px(gap);
            r.addView(views[i], l);
        }
        return r;
    }

    private static View crest(Context c, AdBreak.Pack p, String id, float size) {
        Bitmap b = id == null ? null : p.crests.get(id);
        ImageView v = new ImageView(c);
        if (b != null) v.setImageBitmap(b);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(px(size), px(size)));
        v.setMinimumWidth(px(size));
        v.setMinimumHeight(px(size));
        return sized(v, size, size);
    }

    private static View sized(View v, float w, float h) {
        FrameLayout f = new FrameLayout(v.getContext());
        f.addView(v, new FrameLayout.LayoutParams(px(w), px(h)));
        f.setMinimumWidth(px(w));
        f.setMinimumHeight(px(h));
        return f;
    }

    /** A club with its crest, its name in heavy navy, w wide when given (to line up rows). */
    private static View club(Context c, AdBreak.Pack p, String id, String name, float w) {
        LinearLayout r = row(c, 9, crest(c, p, id, 25), t(c, name, 18.75f, INK, true));
        if (w > 0) r.setLayoutParams(new LinearLayout.LayoutParams(px(w), -2));
        return w > 0 ? sized(r, w, 30) : r;
    }

    private static View box(Context c, char r) {
        TextView v = t(c, String.valueOf(r), 16.5f, r == 'D' ? INK : Color.WHITE, true);
        v.setGravity(Gravity.CENTER);
        GradientDrawable g = AdBreak.fill(r == 'W' ? WIN : r == 'L' ? LOSS : Color.WHITE, px(6));
        if (r == 'D') g.setStroke(Math.max(1, px(2.25f)), 0x59151A63);
        v.setBackground(g);
        return sized(v, 28.5f, 28.5f);
    }

    /** A picture in a white ring (a player's photo, a poster's picture), else initials on its colour. */
    static View ringed(Context c, Bitmap photo, String initials, int colour, int size) {
        View v = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override protected void onDraw(Canvas cv) {
                float r = getWidth() / 2f, ring = Math.max(2f, getWidth() * 0.05f);
                p.setShader(null);
                p.setColor(colour);
                cv.drawCircle(r, r, r - ring / 2, p);
                if (photo != null && !photo.isRecycled()) {
                    BitmapShader sh = new BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    Matrix m = new Matrix();
                    float k = Math.max(getWidth() / (float) photo.getWidth(), getHeight() / (float) photo.getHeight());
                    m.setScale(k, k);
                    m.postTranslate((getWidth() - photo.getWidth() * k) / 2f, 0);
                    sh.setLocalMatrix(m);
                    p.setShader(sh);
                    cv.drawCircle(r, r, r - ring / 2, p);
                    p.setShader(null);
                } else if (initials != null && !initials.isEmpty()) {
                    p.setColor(Color.WHITE);
                    p.setTextAlign(Paint.Align.CENTER);
                    p.setTextSize(getWidth() * 0.36f);
                    p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                    Paint.FontMetrics fm = p.getFontMetrics();
                    cv.drawText(initials, r, r - (fm.ascent + fm.descent) / 2f, p);
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(ring);
                p.setColor(Color.WHITE);
                cv.drawCircle(r, r, r - ring / 2, p);
                p.setStyle(Paint.Style.FILL);
            }
        };
        v.setMinimumWidth(size);
        v.setMinimumHeight(size);
        FrameLayout f = new FrameLayout(c);
        f.addView(v, new FrameLayout.LayoutParams(size, size));
        return f;
    }

    /** A shirt in a kit's colours (its body and sleeves). */
    private static View shirt(Context c, int body, int sleeves) {
        View v = new View(c) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override protected void onDraw(Canvas cv) {
                float k = getWidth() / 38f;
                Path b = new Path();
                b.moveTo(12 * k, 2 * k); b.lineTo(8 * k, 6 * k); b.lineTo(8 * k, 33 * k); b.lineTo(30 * k, 33 * k); b.lineTo(30 * k, 6 * k);
                b.lineTo(26 * k, 2 * k); b.quadTo(19 * k, 7 * k, 12 * k, 2 * k); b.close();
                Path l = new Path();
                l.moveTo(12 * k, 2 * k); l.lineTo(4 * k, 6 * k); l.lineTo(0, 14 * k); l.lineTo(6 * k, 17 * k); l.lineTo(8 * k, 13 * k); l.lineTo(8 * k, 6 * k); l.close();
                Path rr = new Path();
                rr.moveTo(26 * k, 2 * k); rr.lineTo(34 * k, 6 * k); rr.lineTo(38 * k, 14 * k); rr.lineTo(32 * k, 17 * k); rr.lineTo(30 * k, 13 * k); rr.lineTo(30 * k, 6 * k); rr.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(body | 0xFF000000);
                cv.drawPath(b, p);
                p.setColor((sleeves != 0 ? sleeves : body) | 0xFF000000);
                cv.drawPath(l, p);
                cv.drawPath(rr, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.4f * k);
                p.setColor(0x66151A63);
                cv.drawPath(b, p);
                cv.drawPath(l, p);
                cv.drawPath(rr, p);
            }
        };
        return sized(v, 51, 46);
    }

    private static String compName(ScoresTicker.Game g) {
        String n = g.league == null && g.slug == null ? null : ScoresTicker.competition(g);
        return n == null || n.isEmpty() ? "This competition's" : n;
    }

    private static String shortName(AdBreak.Pack p, boolean home) {
        String n = AdBreak.name(p.g, home);
        return n.replace("Manchester ", "Man ").replace(" Hotspur", "").replace("Wolverhampton Wanderers", "Wolves")
                .replace("Brighton & Hove Albion", "Brighton").replace("AFC ", "").replace(" United", n.startsWith("Manchester") ? " United" : "");
    }

    // ---- the three cards' widgets -------------------------------------------------------------------------------------

    /** Each card's widgets: the match, the league, the house and the day. */
    static List<List<AdBreak.Widget>> build(Context ctx, AdBreak.Pack p, float scale) {
        s = scale;
        List<List<AdBreak.Widget>> out = new ArrayList<>();
        List<AdBreak.Widget> a = new ArrayList<>(), b = new ArrayList<>(), h = new ArrayList<>();
        out.add(a);
        out.add(b);
        out.add(h);
        ScoresTicker.Game g = p.g;
        Bitmap mark = p.mark;
        String home = shortName(p, true), away = shortName(p, false);

        // At full time: the whole game (the final score, every scorer, possession, shots).
        if (p.full != null) a.add(new AdBreak.Widget(mark, "FULL\nTIME", c -> gameLines(c, p, p.full, home, away), null, null, null));
        // At half-time: the first half, as the half-time card has it (the scorers, possession, shots).
        if (p.half != null && p.full == null) {
            MoreAlerts.Stats st = p.half;
            a.add(new AdBreak.Widget(mark, "FIRST\nHALF", c -> {
                List<View> l = new ArrayList<>();
                TextView score = t(c, st.home + "–" + st.away, 22.5f, INK, true);
                score.setPadding(px(9), px(1), px(9), px(1));
                score.setBackground(AdBreak.fill(0x1A151A63, px(6)));
                l.add(row(c, 9, crest(c, p, g.homeId, 27), t(c, home, 18, INK, true), score, t(c, away, 18, INK, true), crest(c, p, g.awayId, 27)));
                for (int side = 0; side < 2; side++) {
                    List<String> goals = side == 0 ? st.homeGoals : st.awayGoals;
                    if (goals.isEmpty()) continue;
                    l.add(row(c, 7, crest(c, p, side == 0 ? g.homeId : g.awayId, 19), t(c, String.join(" · ", goals), 15, INK, false)));
                }
                StringBuilder line = new StringBuilder();
                if (st.homePoss >= 0) line.append("Possession ").append(st.homePoss).append("–").append(st.awayPoss).append('%');
                if (st.homeShots >= 0) line.append(line.length() > 0 ? " · " : "").append("Shots ").append(st.homeShots).append("–").append(st.awayShots);
                if (line.length() > 0) l.add(foot(c, line.toString()));
                return l;
            }, null, null, null));
        }

        // Form, this competition only.
        if (!p.homeForm.isEmpty() || !p.awayForm.isEmpty()) {
            a.add(new AdBreak.Widget(mark, "FORM", c -> {
                List<View> l = new ArrayList<>();
                for (int side = 0; side < 2; side++) {
                    String f = side == 0 ? p.homeForm : p.awayForm;
                    if (f.isEmpty()) continue;
                    LinearLayout r = row(c, 6, club(c, p, side == 0 ? g.homeId : g.awayId, side == 0 ? home : away, 180));
                    for (char ch : f.toCharArray()) {
                        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-2, -2);
                        bl.leftMargin = px(6);
                        r.addView(box(c, ch), bl);
                    }
                    l.add(r);
                }
                l.add(foot(c, compName(g) + " games only · oldest first"));
                return l;
            }, null, null, null));
        }
        // The last meetings.
        if (!p.h2h.isEmpty()) {
            a.add(new AdBreak.Widget(mark, "HEAD\nTO HEAD", c -> {
                List<View> l = new ArrayList<>();
                LinearLayout chips = new LinearLayout(c);
                for (String[] m : p.h2h) {
                    LinearLayout col = new LinearLayout(c);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.setGravity(Gravity.CENTER_HORIZONTAL);
                    TextView sc = t(c, m[0], 18, "D".equals(m[1]) ? INK : Color.WHITE, true);
                    sc.setGravity(Gravity.CENTER);
                    GradientDrawable bg = AdBreak.fill("W".equals(m[1]) ? WIN : "L".equals(m[1]) ? LOSS : Color.WHITE, px(6));
                    if ("D".equals(m[1])) bg.setStroke(Math.max(1, px(2.25f)), 0x59151A63);
                    sc.setBackground(bg);
                    sc.setPadding(0, px(4), 0, px(4));
                    col.addView(sc, new LinearLayout.LayoutParams(-1, -2));
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(Long.parseLong(m[2]));
                    TextView d = t(c, new java.text.SimpleDateFormat("MMM yy", Locale.UK).format(cal.getTime()), 13.5f, MUTE, false);
                    d.setGravity(Gravity.CENTER);
                    col.addView(d, new LinearLayout.LayoutParams(-1, -2));
                    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
                    if (chips.getChildCount() > 0) cl.leftMargin = px(6);
                    chips.addView(col, cl);
                }
                l.add(chips);
                int[] tl = AdBreakLogic.tally(p.h2h);
                l.add(row(c, 7, crest(c, p, g.homeId, 22), t(c, tl[0] + (tl[0] == 1 ? " win" : " wins"), 18, INK, true), t(c, "·", 18, MUTE, false),
                        crest(c, p, g.awayId, 22), t(c, tl[1] + (tl[1] == 1 ? " win" : " wins"), 18, INK, true), t(c, "·", 18, MUTE, false),
                        t(c, tl[2] + (tl[2] == 1 ? " draw" : " draws"), 18, INK, true)));
                l.add(foot(c, home + "'s side first · oldest on the left"));
                return l;
            }, null, null, null));
        }
        // The season so far, from the table.
        AdBreakLogic.Row hr = AdBreakLogic.row(p.table, g.homeId), ar = AdBreakLogic.row(p.table, g.awayId);
        if (hr != null && ar != null) {
            a.add(new AdBreak.Widget(mark, "SEASON\nSO FAR", c -> {
                List<View> l = new ArrayList<>();
                LinearLayout head = row(c, 0, sized(new View(c), 180, 4));
                for (String k : new String[]{"P", "W", "D", "L", "PTS"}) head.addView(sized(right(t(c, k, 13.5f, MUTE, true)), 51, 18));
                l.add(head);
                for (AdBreakLogic.Row r : new AdBreakLogic.Row[]{hr, ar}) {
                    LinearLayout line = row(c, 0, club(c, p, r.id, r == hr ? home : away, 180));
                    for (int v : new int[]{r.played, r.wins, r.draws, r.losses, r.points}) line.addView(sized(right(t(c, String.valueOf(v), 21, INK, true)), 51, 27));
                    l.add(line);
                }
                return l;
            }, null, null, null));
        }
        // Each side's leading scorer, with his picture.
        if (p.leaders[0] != null || p.leaders[1] != null) {
            a.add(new AdBreak.Widget(mark, "PLAYERS\nTO WATCH", c -> {
                List<View> l = new ArrayList<>();
                for (String[] x : p.leaders) {
                    if (x == null) continue;
                    LinearLayout who = new LinearLayout(c);
                    who.setOrientation(LinearLayout.VERTICAL);
                    who.addView(t(c, x[0], 18.75f, INK, true));
                    who.addView(t(c, x[1] + " in the " + compName(g), 15, MUTE, false));
                    l.add(row(c, 12, ringed(c, p.faces.get(x[0]), AdBreak.initials(x[0]), INK, px(45)), crest(c, p, x[2], 25), who));
                }
                return l;
            }, null, null, null));
        }
        // Their kits today.
        if (p.homeKit != null && p.homeKit.known() && p.awayKit != null && p.awayKit.known()) {
            a.add(new AdBreak.Widget(mark, "KITS\nTODAY", c -> {
                List<View> l = new ArrayList<>();
                LinearLayout r = new LinearLayout(c);
                r.setGravity(Gravity.CENTER_VERTICAL);
                for (int side = 0; side < 2; side++) {
                    Kits.Kit k = side == 0 ? p.homeKit : p.awayKit;
                    LinearLayout words = new LinearLayout(c);
                    words.setOrientation(LinearLayout.VERTICAL);
                    words.addView(t(c, side == 0 ? home : away, 18, INK, true));
                    String kn = side == 0 ? p.homeKitName : p.awayKitName;
                    if (kn != null) words.addView(t(c, kn, 15, MUTE, false));
                    LinearLayout one = row(c, 8, crest(c, p, side == 0 ? g.homeId : g.awayId, 30), shirt(c, k.body, k.sleeves), words);
                    LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(0, -2, 1f);
                    r.addView(one, ol);
                }
                l.add(r);
                l.add(foot(c, "As the line-ups show them"));
                return l;
            }, null, null, null));
        }
        if (p.referee != null) {
            String ref = p.referee;
            a.add(new AdBreak.Widget(mark, "THE\nREFEREE", c -> {
                List<View> l = new ArrayList<>();
                l.add(row(c, 12, ringed(c, null, AdBreak.initials(ref), 0xFF2B2D33, px(45)), t(c, ref, 21, INK, true)));
                l.add(foot(c, "In charge today"));
                return l;
            }, null, null, null));
        }
        if (!p.absent.isEmpty()) {
            a.add(new AdBreak.Widget(mark, "OUT OF\nTHE GAME", c -> {
                List<View> l = new ArrayList<>();
                for (String id : new String[]{g.homeId, g.awayId}) {
                    List<String> names = new ArrayList<>();
                    for (String[] x : p.absent) if (x[0].equals(id) && names.size() < 3) names.add(x[1] + (x[2].isEmpty() ? "" : " (" + x[2].toLowerCase(Locale.UK) + ")"));
                    if (names.isEmpty()) continue;
                    TextView w = t(c, String.join(" · ", names), 16.5f, INK, false);
                    l.add(row(c, 9, crest(c, p, id, 25), w));
                }
                return l;
            }, null, null, null));
        }

        // The league.
        List<AdBreakLogic.Row> around = AdBreakLogic.around(p.table, g.homeId, g.awayId);
        if (!around.isEmpty()) {
            b.add(new AdBreak.Widget(mark, "THE\nTABLE", c -> {
                List<View> l = new ArrayList<>();
                for (AdBreakLogic.Row r : around) {
                    boolean ours = r.id.equals(g.homeId) || r.id.equals(g.awayId);
                    LinearLayout line = row(c, 9, sized(right(t(c, String.valueOf(r.rank), 15, MUTE, true)), 24, 22), crest(c, p, r.id, 21));
                    TextView n = t(c, r.name, 17, INK, ours);
                    line.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
                    line.addView(sized(right(t(c, (r.gd > 0 ? "+" : "") + r.gd, 15, MUTE, true)), 45, 22));
                    line.addView(sized(right(t(c, String.valueOf(r.points), 18, INK, true)), 39, 22));
                    if (ours) line.setBackground(AdBreak.fill(0x29D42B2B, px(6)));
                    line.setPadding(px(6), px(1), px(9), px(1));
                    l.add(line);
                }
                return l;
            }, null, null, null));
        }
        int[] win = AdBreakLogic.placesIf(p.table, g.homeId, g.awayId, 1, 0), draw = AdBreakLogic.placesIf(p.table, g.homeId, g.awayId, 0, 0),
                loss = AdBreakLogic.placesIf(p.table, g.homeId, g.awayId, 0, 1);
        if (win != null && draw != null && loss != null && hr != null && ar != null) {
            b.add(new AdBreak.Widget(mark, "IF IT\nFINISHED\nNOW", c -> {
                List<View> l = new ArrayList<>();
                LinearLayout head = row(c, 0, sized(new View(c), 150, 4));
                for (String k : new String[]{home.toUpperCase(Locale.UK) + " WIN", "DRAW", away.toUpperCase(Locale.UK) + " WIN"}) {
                    TextView x = t(c, k, 12.75f, MUTE, true);
                    x.setGravity(Gravity.CENTER);
                    head.addView(x, new LinearLayout.LayoutParams(0, -2, 1f));
                }
                l.add(head);
                for (int side = 0; side < 2; side++) {
                    int now = side == 0 ? hr.rank : ar.rank;
                    LinearLayout line = row(c, 0, club(c, p, side == 0 ? g.homeId : g.awayId, side == 0 ? home : away, 150));
                    for (int[] at : new int[][]{win, draw, loss}) {
                        int place = at[side];
                        String arrow = place < now ? " ▲" : place > now ? " ▼" : "";
                        TextView x = t(c, AdBreakLogic.ordinal(place) + arrow, 19.5f, INK, true);
                        x.setGravity(Gravity.CENTER);
                        line.addView(x, new LinearLayout.LayoutParams(0, -2, 1f));
                    }
                    l.add(line);
                }
                return l;
            }, null, null, null));
        }
        // The other games at the same time, OK to hop to the first one on a channel.
        if (!p.alsoNow.isEmpty()) {
            Runnable hop = null;
            for (Object[] o : p.alsoNow) {
                Object[] where = (Object[]) o[1];
                if (where != null && where.length > 2 && where[2] instanceof Runnable) {
                    hop = (Runnable) where[2];
                    break;
                }
                if (where != null && where[0] instanceof List && !((List<?>) where[0]).isEmpty()) {
                    @SuppressWarnings("unchecked") List<String> names = (List<String>) where[0];
                    Bitmap logo = (Bitmap) where[1];
                    hop = () -> ChannelOpen.open(ctx, ctx.getString(R.string.scores_open_package), names, logo);
                    break;
                }
            }
            String when = new java.text.SimpleDateFormat("HH:mm", Locale.UK).format(new java.util.Date(g.kickoff));
            boolean canHop = hop != null;
            b.add(new AdBreak.Widget(mark, "ALSO\nAT " + when, c -> {
                List<View> l = new ArrayList<>();
                for (Object[] o : p.alsoNow) {
                    ScoresTicker.Game x = (ScoresTicker.Game) o[0];
                    Object[] where = (Object[]) o[1];
                    String ch = where != null && where[0] instanceof List && !((List<?>) where[0]).isEmpty() ? String.valueOf(((List<?>) where[0]).get(0)) : "";
                    LinearLayout line = row(c, 7, crest(c, p, x.homeId, 21), t(c, AdBreak.name(x, true), 16.5f, INK, true), t(c, "v", 15, MUTE, false),
                            t(c, AdBreak.name(x, false), 16.5f, INK, true), crest(c, p, x.awayId, 21));
                    View fill = new View(c);
                    line.addView(fill, new LinearLayout.LayoutParams(0, 1, 1f));
                    line.addView(t(c, ch, 15, MUTE, true));
                    l.add(line);
                }
                if (canHop) l.add(foot(c, "OK: the first one, for the break"));
                return l;
            }, hop, null, null));
        }
        // Games on now elsewhere.
        List<ScoresTicker.Game> live = new ArrayList<>(), later = new ArrayList<>();
        Calendar today = Calendar.getInstance();
        for (ScoresTicker.Game x : latestGames()) {
            if (x == null || x.id == null || x.id.equals(g.id)) continue;
            if (x.state == 0 && live.size() < 3) live.add(x);
            if (x.state == 1 && x.kickoff > g.kickoff + 60_000L && later.size() < 3) {
                Calendar k = Calendar.getInstance();
                k.setTimeInMillis(x.kickoff);
                if (k.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) && k.get(Calendar.YEAR) == today.get(Calendar.YEAR)) later.add(x);
            }
        }
        // At half-time: the games still playing, the first one a hop away (HalfTimeHop's: back for the second half).
        List<ScoresTicker.Game> hops = p.half != null && HalfTimeHop.on(ctx) ? HalfTimeHop.choices(g, latestGames()) : new ArrayList<>();
        if (!hops.isEmpty()) {
            ScoresTicker.Game to = hops.get(0);
            b.add(new AdBreak.Widget(mark, "ALSO\nON NOW", c -> {
                List<View> l = new ArrayList<>();
                for (ScoresTicker.Game x : hops) l.add(scoreRow(c, p, x, x.detail));
                l.add(foot(c, "OK: the first one for the break, back for the second half"));
                return l;
            }, () -> HalfTimeHop.hop(ctx, g, to), null, null));
            live.clear();
        }
        if (!live.isEmpty()) {
            b.add(new AdBreak.Widget(mark, "ELSE-\nWHERE\nNOW", c -> {
                List<View> l = new ArrayList<>();
                for (ScoresTicker.Game x : live) l.add(scoreRow(c, p, x, x.detail));
                return l;
            }, null, null, null));
        }
        if (!p.homeNext.isEmpty() || !p.awayNext.isEmpty()) {
            b.add(new AdBreak.Widget(mark, "COMING\nUP", c -> {
                List<View> l = new ArrayList<>();
                for (int side = 0; side < 2; side++) {
                    List<String[]> next = side == 0 ? p.homeNext : p.awayNext;
                    if (next.isEmpty()) continue;
                    LinearLayout line = row(c, 7, crest(c, p, side == 0 ? g.homeId : g.awayId, 25));
                    for (String[] n : next) {
                        AdBreakLogic.Row r = AdBreakLogic.row(p.table, n[0]);
                        int colour = r == null ? 0xFFC98A12 : r.rank <= 6 ? LOSS : r.rank <= 14 ? 0xFFC98A12 : WIN;
                        LinearLayout chip = row(c, 6, crest(c, p, n[0], 19), t(c, n[3], 15, Color.WHITE, true));
                        chip.setGravity(Gravity.CENTER);
                        chip.setBackground(AdBreak.fill(colour, px(6)));
                        chip.setPadding(0, px(4), 0, px(4));
                        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
                        cl.leftMargin = px(7);
                        line.addView(chip, cl);
                    }
                    l.add(line);
                }
                l.add(foot(c, "Green easier · amber · red harder, by the table"));
                return l;
            }, null, null, null));
        }
        if (!later.isEmpty() || !p.f1Later.isEmpty()) {
            // The games and today's F1 sessions together, in time order, three at most.
            List<Object[]> rows = new ArrayList<>();
            for (ScoresTicker.Game x : later) rows.add(new Object[]{new java.text.SimpleDateFormat("HH:mm", Locale.UK).format(new java.util.Date(x.kickoff)), x});
            for (String[] f : p.f1Later) rows.add(new Object[]{f[0], f});
            rows.sort((x, y) -> String.valueOf(x[0]).compareTo(String.valueOf(y[0])));
            while (rows.size() > 3) rows.remove(rows.size() - 1);
            b.add(new AdBreak.Widget(mark, "LATER\nTODAY", c -> {
                List<View> l = new ArrayList<>();
                for (Object[] r : rows) l.add(laterRow(c, p, r));
                return l;
            }, null, null, null));
        }

        // The house and the day.
        if (p.weatherHead != null) {
            h.add(new AdBreak.Widget(null, "AT THE\nGROUND", c -> {
                List<View> l = new ArrayList<>();
                l.add(t(c, p.weatherHead, 27, INK, true));
                if (p.weatherLine != null) l.add(t(c, p.weatherLine, 15, MUTE, false));
                if (g.venue != null) l.add(foot(c, g.venue));
                return l;
            }, null, null, null));
        }
        boolean[] mode = {MatchMode.isGame(ctx, g.id)};
        h.add(new AdBreak.Widget(null, "MATCH\nMODE", c -> {
            List<View> l = new ArrayList<>();
            boolean on = MatchMode.isGame(c, g.id);
            TextView sw = t(c, on ? "ON" : "OFF", 15, on ? Color.WHITE : INK, true);
            sw.setGravity(Gravity.CENTER);
            sw.setBackground(AdBreak.fill(on ? WIN : 0x33151A63, px(18)));
            sw.setPadding(px(15), px(5), px(15), px(5));
            TextView words = t(c, "For this game: quiet alerts, lights in its colours at kick-off, flashing for a goal", 16.5f, INK, false);
            words.setSingleLine(false);
            words.setMaxLines(2);
            LinearLayout line = row(c, 12, crest(c, p, g.homeId, 30));
            line.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(-2, -2);
            swl.leftMargin = px(12);
            line.addView(sw, swl);
            l.add(line);
            l.add(foot(c, on ? "OK: switch off" : "OK: switch on"));
            return l;
        }, () -> {
            if (MatchMode.isGame(ctx, g.id)) MatchMode.stop(ctx); else MatchMode.start(ctx, g);
            mode[0] = MatchMode.isGame(ctx, g.id);
        }, null, null));
        if (!p.mine.isEmpty()) {
            // The players you follow whose clubs play today: their photo, club, when and against whom.
            h.add(new AdBreak.Widget(null, "YOUR\nPLAYERS", c -> {
                List<View> l = new ArrayList<>();
                for (String[] m : p.mine) {
                    l.add(row(c, 9, ringed(c, p.faces.get(m[0]), AdBreak.initials(m[0]), INK, px(40)), crest(c, p, m[1], 21),
                            t(c, Players.surname(m[0]), 17, INK, true), t(c, "· " + m[3], 15, MUTE, false),
                            t(c, "v", 14, MUTE, false), crest(c, p, m[2], 21)));
                }
                l.add(foot(c, "Players you follow, playing today"));
                return l;
            }, null, null, null));
        }
        if (!p.headlines.isEmpty()) {
            h.add(new AdBreak.Widget(null, "HEAD-\nLINES", c -> {
                List<View> l = new ArrayList<>();
                for (String x : p.headlines) {
                    View bar = new View(c);
                    bar.setBackground(AdBreak.fill(LOSS, px(2)));
                    l.add(row(c, 10, sized(bar, 4.5f, 21), t(c, x, 16.5f, INK, false)));
                }
                return l;
            }, null, null, null));
        }
        if (p.clip != null) {
            String[] clip = p.clip;
            h.add(new AdBreak.Widget(null, "THE LAST\nMEETING", c -> {
                List<View> l = new ArrayList<>();
                TextView score = t(c, clip[2], 22.5f, INK, true);
                score.setPadding(px(9), px(1), px(9), px(1));
                score.setBackground(AdBreak.fill(0x1A151A63, px(6)));
                l.add(row(c, 9, crest(c, p, g.homeId, 27), score, crest(c, p, g.awayId, 27)));
                l.add(t(c, home + " v " + away, 16.5f, INK, true));
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(Long.parseLong(clip[3]));
                l.add(foot(c, new java.text.SimpleDateFormat("MMM yyyy", Locale.UK).format(cal.getTime()) + " · no sound"));
                return l;
            }, null, clip[0], p.clipStill));
        }
        return out;
    }

    /** A whole game's lines (FULL TIME): the score between both crests, each side's scorers, possession and shots. */
    private static List<View> gameLines(Context c, AdBreak.Pack p, MoreAlerts.Stats st, String home, String away) {
        ScoresTicker.Game g = p.g;
        List<View> l = new ArrayList<>();
        TextView score = t(c, st.home + "–" + st.away, 22.5f, INK, true);
        score.setPadding(px(9), px(1), px(9), px(1));
        score.setBackground(AdBreak.fill(0x1A151A63, px(6)));
        l.add(row(c, 9, crest(c, p, g.homeId, 27), t(c, home, 18, INK, true), score, t(c, away, 18, INK, true), crest(c, p, g.awayId, 27)));
        for (int side = 0; side < 2; side++) {
            List<String> goals = side == 0 ? st.homeGoals : st.awayGoals;
            if (goals.isEmpty()) continue;
            TextView who = t(c, String.join(" · ", goals), 15, INK, false);
            who.setSingleLine(true);
            who.setEllipsize(android.text.TextUtils.TruncateAt.END);
            l.add(row(c, 7, crest(c, p, side == 0 ? g.homeId : g.awayId, 19), who));
        }
        StringBuilder line = new StringBuilder();
        if (st.homePoss >= 0) line.append("Possession ").append(st.homePoss).append("–").append(st.awayPoss).append('%');
        if (st.homeShots >= 0) line.append(line.length() > 0 ? " · " : "").append("Shots ").append(st.homeShots).append("–").append(st.awayShots);
        if (line.length() > 0) l.add(foot(c, line.toString()));
        return l;
    }

    // ---- the general screen (a match on, no game known) -----------------------------------------------------------------

    /**
     * The general screen's cards: the scores now (the clubs you follow first) and today's results; the games and F1
     * later today and your players today; BBC Sport's football headlines. A card with nothing behind it is left empty.
     */
    static List<List<AdBreak.Widget>> general(Context ctx, AdBreak.Pack p, float scale) {
        s = scale;
        List<List<AdBreak.Widget>> out = new ArrayList<>();
        List<AdBreak.Widget> a = new ArrayList<>(), b = new ArrayList<>(), h = new ArrayList<>();
        out.add(a);
        out.add(b);
        out.add(h);
        for (List<ScoresTicker.Game> part : threes(p.liveNow)) {
            a.add(new AdBreak.Widget(null, "SCORES\nNOW", c -> {
                List<View> l = new ArrayList<>();
                for (ScoresTicker.Game x : part) l.add(scoreRow(c, p, x, x.halftime ? "HT" : ScoresTicker.liveClock(x)));
                return l;
            }, null, null, null));
        }
        for (List<ScoresTicker.Game> part : threes(p.doneToday)) {
            a.add(new AdBreak.Widget(null, "RESULTS\nTODAY", c -> {
                List<View> l = new ArrayList<>();
                for (ScoresTicker.Game x : part) l.add(scoreRow(c, p, x, "FT"));
                return l;
            }, null, null, null));
        }
        // The games and today's F1 sessions together, in time order, three a widget.
        List<Object[]> rows = new ArrayList<>();
        for (ScoresTicker.Game x : p.laterToday) rows.add(new Object[]{new java.text.SimpleDateFormat("HH:mm", Locale.UK).format(new java.util.Date(x.kickoff)), x});
        for (String[] f : p.f1Later) rows.add(new Object[]{f[0], f});
        rows.sort((x, y) -> String.valueOf(x[0]).compareTo(String.valueOf(y[0])));
        while (rows.size() > 6) rows.remove(rows.size() - 1);
        for (int i = 0; i < rows.size(); i += 3) {
            List<Object[]> part = rows.subList(i, Math.min(rows.size(), i + 3));
            b.add(new AdBreak.Widget(null, "LATER\nTODAY", c -> {
                List<View> l = new ArrayList<>();
                for (Object[] r : part) l.add(laterRow(c, p, r));
                return l;
            }, null, null, null));
        }
        if (!p.mine.isEmpty()) {
            b.add(new AdBreak.Widget(null, "YOUR\nPLAYERS", c -> {
                List<View> l = new ArrayList<>();
                for (String[] m : p.mine) {
                    l.add(row(c, 9, ringed(c, p.faces.get(m[0]), AdBreak.initials(m[0]), INK, px(40)), crest(c, p, m[1], 21),
                            t(c, Players.surname(m[0]), 17, INK, true), t(c, "· " + m[3], 15, MUTE, false),
                            t(c, "v", 14, MUTE, false), crest(c, p, m[2], 21)));
                }
                l.add(foot(c, "Players you follow, playing today"));
                return l;
            }, null, null, null));
        }
        for (int i = 0; i < p.headlines.size(); i += 3) {
            List<String> part = p.headlines.subList(i, Math.min(p.headlines.size(), i + 3));
            h.add(new AdBreak.Widget(null, "HEAD-\nLINES", c -> {
                List<View> l = new ArrayList<>();
                for (String x : part) {
                    View bar = new View(c);
                    bar.setBackground(AdBreak.fill(LOSS, px(2)));
                    l.add(row(c, 10, sized(bar, 4.5f, 21), t(c, x, 16.5f, INK, false)));
                }
                return l;
            }, null, null, null));
        }
        return out;
    }

    private static List<List<ScoresTicker.Game>> threes(List<ScoresTicker.Game> games) {
        List<List<ScoresTicker.Game>> out = new ArrayList<>();
        for (int i = 0; i < games.size(); i += 3) out.add(new ArrayList<>(games.subList(i, Math.min(games.size(), i + 3))));
        return out;
    }

    /** A game's line: both crests and names round the score, and its minute (or HT, FT) at the end. */
    private static LinearLayout scoreRow(Context c, AdBreak.Pack p, ScoresTicker.Game x, String end) {
        TextView score = t(c, (x.homeScore == null ? "0" : x.homeScore) + "–" + (x.awayScore == null ? "0" : x.awayScore), 18, INK, true);
        score.setPadding(px(9), px(1), px(9), px(1));
        score.setBackground(AdBreak.fill(0x1A151A63, px(6)));
        LinearLayout line = row(c, 7, crest(c, p, x.homeId, 21), t(c, AdBreak.name(x, true), 16.5f, INK, true), score,
                t(c, AdBreak.name(x, false), 16.5f, INK, true), crest(c, p, x.awayId, 21));
        line.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        line.addView(t(c, end == null ? "" : end, 15, MUTE, true));
        return line;
    }

    /** A later-today line: {"HH:mm", a game} or {"HH:mm", an F1 session {time, session, Grand Prix}}. */
    private static LinearLayout laterRow(Context c, AdBreak.Pack p, Object[] r) {
        View when = sized(t(c, (String) r[0], 18, INK, true), 60, 24);
        if (r[1] instanceof ScoresTicker.Game) {
            ScoresTicker.Game x = (ScoresTicker.Game) r[1];
            return row(c, 7, when, crest(c, p, x.homeId, 21), t(c, AdBreak.name(x, true), 16.5f, INK, true),
                    t(c, "v", 15, MUTE, false), t(c, AdBreak.name(x, false), 16.5f, INK, true), crest(c, p, x.awayId, 21));
        }
        String[] f = (String[]) r[1];
        return row(c, 7, when, crest(c, p, "f1", 21), t(c, f[1], 16.5f, INK, true), t(c, f[2], 15, MUTE, false));
    }

    private static List<ScoresTicker.Game> latestGames() {
        return AdBreak.latestGames();
    }

    private static TextView right(TextView v) {
        v.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        return v;
    }

    // ---- where they stand (above the line-up card, its size) ---------------------------------------------------------

    /** The table's two lines for the game in the line-up card's look, 616 x 204; null without the table. */
    static View tableCard(Context c, AdBreak.Pack p, float scale) {
        s = scale;
        ScoresTicker.Game g = p.g;
        AdBreakLogic.Row hr = AdBreakLogic.row(p.table, g.homeId), ar = AdBreakLogic.row(p.table, g.awayId);
        if (hr == null || ar == null) return null;
        FrameLayout card = new FrameLayout(c);
        float r = px(12);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        });
        card.setClipToOutline(true);
        FrameLayout band = new FrameLayout(c);
        band.setBackground(SkyStyle.band(SkyStyle.NAVY, SkyStyle.NAVY_DEEP, px(18), px(3)));
        LinearLayout bl = new LinearLayout(c);
        bl.setOrientation(LinearLayout.VERTICAL);
        bl.setGravity(Gravity.CENTER_HORIZONTAL);
        bl.setPadding(0, px(24), 0, 0);
        if (p.mark != null) {
            ImageView m = new ImageView(c);
            m.setImageBitmap(p.mark);
            m.setScaleType(ImageView.ScaleType.FIT_CENTER);
            bl.addView(m, new LinearLayout.LayoutParams(px(36), px(36)));
        }
        TextView cap = AdBreak.text(c, "WHERE\nTHEY\nSTAND", 12.75f, 0xE6FFFFFF, Typeface.create("sans-serif-condensed", Typeface.BOLD));
        cap.setSingleLine(false);
        cap.setMaxLines(3);
        cap.setGravity(Gravity.CENTER);
        cap.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams capl = new LinearLayout.LayoutParams(-1, -2);
        capl.topMargin = px(12);
        bl.addView(cap, capl);
        band.addView(bl, new FrameLayout.LayoutParams(-1, -1));
        card.addView(band, new FrameLayout.LayoutParams(px(84), -1));
        FrameLayout stage = new FrameLayout(c);
        stage.setBackground(new SkyStyle.Streaks(0xFFF4F5FD, 0xFFC2C5EE, 0x8CFFFFFF, px(75), px(32)));
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-1, -1);
        sl.leftMargin = px(84);
        card.addView(stage, sl);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(px(21), px(15), px(21), px(15));
        LinearLayout head = row(c, 0, sized(right(t(c, "POS", 13.5f, MUTE, true)), 36, 18));
        TextView clubH = t(c, "   CLUB", 13.5f, MUTE, true);
        head.addView(clubH, new LinearLayout.LayoutParams(0, -2, 1f));
        for (String k : new String[]{"P", "GD", "PTS"}) head.addView(sized(right(t(c, k, 13.5f, MUTE, true)), 54, 18));
        col.addView(head);
        for (AdBreakLogic.Row x : new AdBreakLogic.Row[]{hr, ar}) {
            LinearLayout line = row(c, 0, sized(right(t(c, String.valueOf(x.rank), 21, INK, true)), 36, 30));
            LinearLayout who = row(c, 9, crest(c, p, x.id, 27), t(c, x.name, 21, INK, true));
            who.setPadding(px(12), 0, 0, 0);
            line.addView(who, new LinearLayout.LayoutParams(0, -2, 1f));
            line.addView(sized(right(t(c, String.valueOf(x.played), 19.5f, INK, false)), 54, 30));
            line.addView(sized(right(t(c, (x.gd > 0 ? "+" : "") + x.gd, 19.5f, INK, false)), 54, 30));
            line.addView(sized(right(t(c, String.valueOf(x.points), 22.5f, INK, true)), 54, 30));
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
            ll.topMargin = px(9);
            col.addView(line, ll);
        }
        int[] win = AdBreakLogic.placesIf(p.table, g.homeId, g.awayId, 1, 0);
        if (win != null) {
            String n = shortName(p, true);
            String line = win[0] < hr.rank ? "A win today takes " + n + " up to " + AdBreakLogic.ordinal(win[0])
                    : "A win today keeps " + n + " " + AdBreakLogic.ordinal(win[0]);
            TextView f = t(c, line, 16.5f, MUTE, false);
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, -2);
            fl.topMargin = px(12);
            col.addView(f, fl);
        }
        stage.addView(col, new FrameLayout.LayoutParams(-1, -1));
        return card;
    }
}

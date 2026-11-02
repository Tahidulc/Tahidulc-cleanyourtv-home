package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Previously on: before a series watched here goes on after a break (three weeks or more; Your shows opens it), or from
 * its panel. The last three episodes seen, with their stills and what happened (TMDB's synopses, nothing after the
 * last one seen), who's who in that season (faces and characters), and the next episode, with Play, the official recap
 * (YouTube's search) and Skip. Left and Right along the buttons, OK acts, Back closes.
 */
final class Recap {
    private Recap() { }

    private static final int GREEN = 0xFF3DDC84, NAVY = 0xFF10157E;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    static final long GAP_DAYS = 21;

    private static Recap shown;
    private HomeV2 h;
    private FrameLayout overlay;
    private final List<TextView> buttons = new ArrayList<>();
    private final List<Runnable> acts = new ArrayList<>();
    private int at;
    private boolean waitUp;

    /** The newest entry for a series watched here with its episode known, or null. */
    static WatchHistory.Entry last(Context c, int id) {
        for (WatchHistory.Entry e : WatchHistory.load(c)) if (e.tv && e.tmdbId == id && YourShows.position(e) != null) return e;
        return null;
    }

    static boolean can(Context c, int id) {
        return last(c, id) != null;
    }

    /** Days since the series was last watched here (Long.MAX_VALUE when never). */
    static long gap(Context c, int id) {
        WatchHistory.Entry e = last(c, id);
        return e == null ? Long.MAX_VALUE : (System.currentTimeMillis() - e.at) / 86_400_000L;
    }

    static boolean isOpen() {
        return shown != null;
    }

    static boolean key(KeyEvent e) {
        return shown != null && shown.onKey(e);
    }

    static void close() {
        if (shown != null) shown.hide();
    }

    /** Opens it for series t; play is what goes on afterwards (Play, Skip). */
    static void open(HomeV2 h, PagesV2.Title t, Runnable play) {
        WatchHistory.Entry e = last(h.a, t.id);
        if (e == null) { play.run(); return; }
        close();
        Recap r = new Recap();
        r.h = h;
        shown = r;
        r.overlay = new FrameLayout(h.a);
        r.overlay.setBackgroundColor(0xFF060928);
        r.overlay.setClickable(true);
        h.root.addView(r.overlay, new FrameLayout.LayoutParams(-1, -1));
        r.overlay.setAlpha(0f);
        r.overlay.animate().alpha(1f).setDuration(220).start();
        r.waitUp = false;   // opened on an OK already let go
        int[] pos = YourShows.position(e);
        boolean finished = e.progress() >= 0.95f;
        IO.execute(() -> {
            Object[] got = read(h, t, pos[0], pos[1], finished);
            MAIN.post(() -> { if (shown == r) r.build(t, e, got, play); });
        });
    }

    private void hide() {
        if (shown == this) shown = null;
        View o = overlay;
        o.animate().alpha(0f).setDuration(180).withEndAction(() -> h.root.removeView(o)).start();
        h.paint();
    }

    // ---- reading ------------------------------------------------------------------------------------------------

    /** {details, backdrop, logo, seen [{label, name, overview, still}], who [{name, character, face}], next {label, name, still} or null}. */
    private static Object[] read(HomeV2 h, PagesV2.Title t, int s, int e, boolean finished) {
        Context c = h.a;
        String key = h.tmdbKey;
        JSONObject d = YourShows.details(key, t.id);
        Bitmap back = null, logo = null;
        if (d != null && !d.optString("backdrop_path").isEmpty() && !"null".equals(d.optString("backdrop_path"))) {
            back = ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w1280" + d.optString("backdrop_path"), h.px(1920));
        }
        try {
            JSONArray logos = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/tv/" + t.id + "/images?include_image_language=en,null&api_key=" + key))
                    .optJSONArray("logos");
            for (int i = 0; logos != null && i < logos.length(); i++) {
                JSONObject l = logos.optJSONObject(i);
                if (!"en".equals(l.optString("iso_639_1")) || !l.optString("file_path").endsWith(".png")) continue;
                logo = ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w500" + l.optString("file_path"), h.px(460));
                if (logo != null) logo = HomeV2.trim(logo);
                break;
            }
        } catch (Exception ignored) {
            // the name in words
        }
        // The episodes seen: up to the last one finished (the one under way, when it was more than half done).
        int lastS = s, lastE = finished ? e : e - 1;
        List<JSONObject> eps = new ArrayList<>();
        JSONArray here = season(key, t.id, lastS);
        if (lastE < 1 && lastS > 1) { here = season(key, t.id, --lastS); lastE = here == null ? 0 : here.length(); }
        for (int i = 0; here != null && i < here.length(); i++) {
            JSONObject ep = here.optJSONObject(i);
            if (ep.optInt("episode_number") <= lastE) eps.add(ep);
        }
        if (eps.size() < 3 && lastS > 1) {
            JSONArray before = season(key, t.id, lastS - 1);
            List<JSONObject> more = new ArrayList<>();
            for (int i = 0; before != null && i < before.length(); i++) more.add(before.optJSONObject(i));
            more.addAll(eps);
            eps = more;
        }
        List<Object[]> seen = new ArrayList<>();
        for (int i = Math.max(0, eps.size() - 3); i < eps.size(); i++) {
            JSONObject ep = eps.get(i);
            String still = ep.optString("still_path", "");
            seen.add(new Object[]{"S" + ep.optInt("season_number") + " E" + ep.optInt("episode_number"), ep.optString("name"),
                    ep.optString("overview"), still.isEmpty() || "null".equals(still) ? null
                    : ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w780" + still, h.px(400))});
        }
        // Who's who in the last season seen.
        List<Object[]> who = new ArrayList<>();
        try {
            JSONArray cast = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/tv/" + t.id + "/season/" + lastS + "/aggregate_credits?api_key=" + key))
                    .optJSONArray("cast");
            for (int i = 0; cast != null && i < cast.length() && who.size() < 5; i++) {
                JSONObject p = cast.optJSONObject(i);
                JSONArray roles = p.optJSONArray("roles");
                String character = roles != null && roles.length() > 0 ? roles.optJSONObject(0).optString("character") : "";
                String photo = p.optString("profile_path", "");
                who.add(new Object[]{p.optString("name"), character, photo.isEmpty() || "null".equals(photo) ? null
                        : ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w185" + photo, h.px(120))});
            }
        } catch (Exception ignored) {
            // no who's who
        }
        // The next one: the one under way, or the one after the last seen.
        int ns = s, ne = finished ? e + 1 : e;
        JSONArray ns1 = season(key, t.id, ns);
        JSONObject next = null;
        for (int i = 0; ns1 != null && i < ns1.length(); i++) if (ns1.optJSONObject(i).optInt("episode_number") == ne) next = ns1.optJSONObject(i);
        if (next == null) {
            JSONArray ns2 = season(key, t.id, ns + 1);
            if (ns2 != null && ns2.length() > 0) next = ns2.optJSONObject(0);
        }
        Object[] up = null;
        if (next != null) {
            String still = next.optString("still_path", "");
            up = new Object[]{"S" + next.optInt("season_number") + " E" + next.optInt("episode_number"), next.optString("name"),
                    still.isEmpty() || "null".equals(still) ? null : ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w780" + still, h.px(560)),
                    next.optInt("runtime")};
        }
        return new Object[]{d, back, logo, seen, who, up};
    }

    private static JSONArray season(String key, int id, int s) {
        if (s < 1) return null;
        try {
            return new JSONObject(HomeV2.get("https://api.themoviedb.org/3/tv/" + id + "/season/" + s + "?language=en-GB&api_key=" + key)).optJSONArray("episodes");
        } catch (Exception e) {
            return null;
        }
    }

    // ---- showing ------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void build(PagesV2.Title t, WatchHistory.Entry e, Object[] got, Runnable play) {
        Context c = h.a;
        Typeface heavy = c.getResources().getFont(R.font.outfit_extrabold), semi = c.getResources().getFont(R.font.inter_semibold),
                regular = c.getResources().getFont(R.font.inter_regular);
        if (got[1] != null) {
            ImageView bg = new ImageView(c);
            bg.setImageBitmap((Bitmap) got[1]);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bg.setAlpha(0.5f);
            overlay.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        }
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xF5060928, 0xDB060928, 0x59060928}));
        overlay.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        if (got[2] != null) {
            ImageView l = new ImageView(c);
            l.setImageBitmap((Bitmap) got[2]);
            l.setAdjustViewBounds(true);
            l.setScaleType(ImageView.ScaleType.FIT_START);
            l.setMaxWidth(h.px(460));
            l.setMaxHeight(h.px(130));
            place(l, 120, 70, 460, 130);
        } else {
            TextView n = h.text(48, heavy, Color.WHITE);
            n.setText(t.title);
            place(n, 120, 80, 1000, 70);
        }
        int[] pos = YourShows.position(e);
        long days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now());
        LinearLayout line = new LinearLayout(c);
        line.setGravity(Gravity.CENTER_VERTICAL);
        Object[] up = (Object[]) got[5];
        boolean newSeason = up != null && ((String) up[0]).endsWith(" E1") && !((String) up[0]).startsWith("S" + pos[0] + " ");
        TextView chip = h.text(15, semi, newSeason ? 0xFF06331B : NAVY);
        chip.setText(newSeason ? "SEASON " + ((String) up[0]).split(" ")[0].substring(1) + " IS HERE" : "WELCOME BACK");
        chip.setLetterSpacing(0.12f);
        chip.setPadding(h.px(12), h.px(6), h.px(12), h.px(6));
        chip.setBackground(h.fill(6, newSeason ? GREEN : 0xFFFFD04D));
        line.addView(chip);
        TextView when = h.text(22, regular, 0xE6FFFFFF);
        when.setText("You last watched S" + pos[0] + " E" + pos[1] + " · " + ago(days));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.leftMargin = h.px(16);
        line.addView(when, wl);
        place(line, 120, 222, 1300, 44);
        TextView head = h.text(46, heavy, Color.WHITE);
        head.setText("Previously on " + t.title);
        place(head, 120, 290, 1100, 64);
        TextView sub = h.text(20, regular, 0xBFFFFFFF);
        sub.setText("The last episodes you saw, in two minutes. Nothing after S" + pos[0] + " E" + pos[1] + ".");
        place(sub, 120, 360, 1100, 30);
        List<Object[]> seen = (List<Object[]>) got[3];
        for (int i = 0; i < seen.size(); i++) {
            Object[] ep = seen.get(i);
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            FrameLayout pic = new FrameLayout(c);
            pic.setBackground(h.fill(16, 0xFF141A5C));
            pic.setClipToOutline(true);
            pic.setOutlineProvider(h.round(16));
            ImageView st = new ImageView(c);
            st.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (ep[3] != null) st.setImageBitmap((Bitmap) ep[3]);
            pic.addView(st, new FrameLayout.LayoutParams(-1, -1));
            TextView lab = h.text(12, semi, Color.WHITE);
            lab.setText(ep[0] + (i == seen.size() - 1 ? " · LAST SEEN" : ""));
            lab.setLetterSpacing(0.08f);
            lab.setPadding(h.px(8), h.px(4), h.px(8), h.px(4));
            lab.setBackground(h.fill(5, 0xB3000000));
            FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, -2);
            ll.leftMargin = h.px(10);
            ll.topMargin = h.px(10);
            pic.addView(lab, ll);
            card.addView(pic, new LinearLayout.LayoutParams(h.px(352), h.px(198)));
            TextView nm = h.text(20, semi, Color.WHITE);
            nm.setText((String) ep[1]);
            nm.setSingleLine(true);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
            nl.topMargin = h.px(10);
            card.addView(nm, nl);
            TextView ov = h.text(16, regular, 0xD1FFFFFF);
            ov.setText((String) ep[2]);
            ov.setMaxLines(3);
            ov.setLineSpacing(0, 1.12f);
            card.addView(ov);
            place(card, 120 + i * 372, 412, 352, 330);
        }
        List<Object[]> who = (List<Object[]>) got[4];
        if (!who.isEmpty()) {
            TextView wh = h.text(16, semi, 0xD9FFFFFF);
            wh.setText("WHO'S WHO");
            wh.setLetterSpacing(0.16f);
            place(wh, 120, 770, 400, 26);
            LinearLayout faces = new LinearLayout(c);
            for (Object[] p : who) {
                LinearLayout one = new LinearLayout(c);
                one.setOrientation(LinearLayout.VERTICAL);
                one.setGravity(Gravity.CENTER_HORIZONTAL);
                ImageView f = new ImageView(c);
                f.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (p[2] != null) f.setImageBitmap((Bitmap) p[2]);
                f.setBackground(h.fill(52, 0xFF141A5C));
                f.setClipToOutline(true);
                f.setOutlineProvider(h.round(52));
                one.addView(f, new LinearLayout.LayoutParams(h.px(104), h.px(104)));
                TextView ch = h.text(17, semi, Color.WHITE);
                ch.setText((String) p[1]);
                ch.setGravity(Gravity.CENTER);
                ch.setMaxLines(2);
                one.addView(ch, new LinearLayout.LayoutParams(h.px(150), -2));
                TextView nm = h.text(13, regular, 0xB3FFFFFF);
                nm.setText((String) p[0]);
                nm.setGravity(Gravity.CENTER);
                nm.setSingleLine(true);
                one.addView(nm, new LinearLayout.LayoutParams(h.px(150), -2));
                faces.addView(one, new LinearLayout.LayoutParams(h.px(170), -2));
            }
            place(faces, 100, 804, 900, 220);
        }
        // Up next, and the buttons.
        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        if (up != null) {
            FrameLayout pic = new FrameLayout(c);
            pic.setBackground(h.fill(22, 0xFF141A5C));
            pic.setClipToOutline(true);
            pic.setOutlineProvider(h.round(22));
            ImageView st = new ImageView(c);
            st.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (up[2] != null) st.setImageBitmap((Bitmap) up[2]);
            pic.addView(st, new FrameLayout.LayoutParams(-1, -1));
            View sh = new View(c);
            sh.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0xD9000000}));
            pic.addView(sh, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            TextView a = h.text(15, semi, 0xD9FFFFFF);
            a.setText("UP NEXT · " + up[0] + ((Integer) up[3] > 0 ? " · " + up[3] + " mins" : ""));
            words.addView(a);
            TextView b = h.text(28, heavy, Color.WHITE);
            b.setText((String) up[1]);
            b.setSingleLine(true);
            words.addView(b);
            FrameLayout.LayoutParams wl2 = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            wl2.leftMargin = h.px(20);
            wl2.bottomMargin = h.px(16);
            pic.addView(words, wl2);
            right.addView(pic, new LinearLayout.LayoutParams(h.px(560), h.px(315)));
        }
        LinearLayout row = new LinearLayout(c);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = h.px(18);
        right.addView(row, rl);
        button(row, up != null ? "▶  Play " + up[0] : "▶  Play", () -> { hide(); play.run(); }, semi);
        button(row, "Official recap", () -> {
            hide();
            String q = t.title + " season " + pos[0] + " recap";
            h.searchTitle("com.google.android.youtube.tv", q, false, 0, null);
        }, semi);
        button(row, "Skip", () -> { hide(); play.run(); }, semi);
        place(right, 1240, 412, 600, 460);
        at = 0;
        paint();
    }

    private void button(LinearLayout row, String words, Runnable act, Typeface face) {
        TextView b = h.text(19, face, Color.WHITE);
        b.setText(words);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setPadding(h.px(22), 0, h.px(22), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(58));
        lp.rightMargin = h.px(12);
        row.addView(b, lp);
        buttons.add(b);
        acts.add(act);
    }

    private static String ago(long days) {
        if (days < 1) return "today";
        if (days < 14) return days + (days == 1 ? " day ago" : " days ago");
        if (days < 60) return days / 7 + " weeks ago";
        if (days < 365) return days / 30 + " months ago";
        long y = days / 365;
        return y + (y == 1 ? " year ago" : " years ago");
    }

    private boolean onKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (waitUp && ok) { if (e.getAction() == KeyEvent.ACTION_UP) waitUp = false; return true; }
        waitUp = false;
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) hide();
            return true;
        }
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled() && at < acts.size()) acts.get(at).run();
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && at > 0) at--;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && at < buttons.size() - 1) at++;
        paint();
        return true;
    }

    private void paint() {
        for (int i = 0; i < buttons.size(); i++) {
            boolean on = i == at;
            GradientDrawable g = h.fill(29, on ? Color.WHITE : 0x1FFFFFFF);
            if (!on) g.setStroke(Math.max(1, h.px(1.5f)), 0x73FFFFFF);
            buttons.get(i).setBackground(g);
            buttons.get(i).setTextColor(on ? NAVY : Color.WHITE);
        }
    }

    private void place(View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(h.px(w), h.px(hgt));
        p.leftMargin = h.px(x);
        p.topMargin = h.px(y);
        overlay.addView(v, p);
    }
}

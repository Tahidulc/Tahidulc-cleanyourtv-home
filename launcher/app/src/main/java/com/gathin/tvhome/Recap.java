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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Previously on: before a series watched here goes on after a break (three weeks or more; Your shows opens it), or from
 * its panel. The last three episodes seen, with their stills and what happened (TMDB's synopses, nothing after the
 * last one seen), who's who in the season coming (faces and characters, those back from the one seen marked), and the
 * next episode, with Play, the official recap (YouTube's search) and Skip. Play on top, the other two under it: Up,
 * Down, Left and Right between them, OK acts, Back closes.
 */
final class Recap {
    private Recap() { }

    private static final int GREEN = 0xFF3DDC84, NAVY = MockKit.NAVY;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    static final long GAP_DAYS = 21;

    private static Recap shown;
    private HomeV2 h;
    private FrameLayout overlay;
    private final List<LinearLayout> buttons = new ArrayList<>();
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
        r.overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF141C92, 0xFF1F29A6, 0xFF2A35B8}));
        r.overlay.setClickable(true);
        h.root.addView(r.overlay, new FrameLayout.LayoutParams(-1, -1));
        r.overlay.setAlpha(0f);
        r.overlay.animate().alpha(1f).setDuration(220).start();
        r.waitUp = false;   // opened on an OK already let go
        int[] pos = YourShows.position(e);
        boolean finished = e.progress() >= 0.95f;
        IO.execute(() -> {
            // Opened again (or closed) before this one was read: nothing fetched for it.
            if (shown != r) return;
            Object[] got;
            try {
                got = read(h, t, pos[0], pos[1], finished, () -> shown == r);
            } catch (OutOfMemoryError | RuntimeException x) {
                // No room for it: the caches let go of half of what they hold, and the series plays on without the recap.
                ByteLru.trimAll(0.5f);
                MAIN.post(() -> { if (shown == r) { r.hide(); play.run(); } });
                return;
            }
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

    /**
     * {details, backdrop, logo, seen [{label, name, overview, still, finale}], who [{name, character, face, in the
     * season before}], next {label, name, still, runtime} or null, the season the faces are from, the last season seen}.
     */
    private static Object[] read(HomeV2 h, PagesV2.Title t, int s, int e, boolean finished, java.util.function.BooleanSupplier open) {
        Context c = h.a;
        String key = h.tmdbKey;
        JSONObject d = YourShows.details(key, t.id);
        Bitmap back = null, logo = null;
        // Each picture at the size it is shown (the backdrop at TMDB's 1280, behind a deep shade), the small photos in 16-bit
        // colour, and none at all once the recap has closed or been opened again.
        if (open.getAsBoolean() && d != null && !d.optString("backdrop_path").isEmpty() && !"null".equals(d.optString("backdrop_path"))) {
            back = ScoresTicker.cachedPicture(c, "https://image.tmdb.org/t/p/w1280" + d.optString("backdrop_path"), h.px(1280), false);
        }
        try {
            JSONArray logos = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/tv/" + t.id + "/images?include_image_language=en,null&api_key=" + key))
                    .optJSONArray("logos");
            for (int i = 0; logos != null && i < logos.length(); i++) {
                JSONObject l = logos.optJSONObject(i);
                if (!"en".equals(l.optString("iso_639_1")) || !l.optString("file_path").endsWith(".png")) continue;
                if (!open.getAsBoolean()) break;
                logo = ScoresTicker.cachedPicture(c, "https://image.tmdb.org/t/p/w500" + l.optString("file_path"), h.px(560), false);
                if (logo != null) {
                    Bitmap cut = HomeV2.trim(logo);
                    if (cut != logo) logo.recycle();
                    logo = cut;
                }
                break;
            }
        } catch (Exception ignored) {
            // the name in words
        }
        // The episodes seen: up to the last one finished (the one under way, when it was more than half done).
        int lastS = s, lastE = finished ? e : e - 1;
        List<JSONObject> eps = new ArrayList<>();
        Map<Integer, Integer> lastOf = new HashMap<>();
        JSONArray here = season(key, t.id, lastS);
        if (lastE < 1 && lastS > 1) { here = season(key, t.id, --lastS); lastE = here == null ? 0 : here.length(); }
        note(lastOf, here);
        for (int i = 0; here != null && i < here.length(); i++) {
            JSONObject ep = here.optJSONObject(i);
            if (ep.optInt("episode_number") <= lastE) eps.add(ep);
        }
        if (eps.size() < 3 && lastS > 1) {
            JSONArray before = season(key, t.id, lastS - 1);
            note(lastOf, before);
            List<JSONObject> more = new ArrayList<>();
            for (int i = 0; before != null && i < before.length(); i++) more.add(before.optJSONObject(i));
            more.addAll(eps);
            eps = more;
        }
        List<Object[]> seen = new ArrayList<>();
        for (int i = Math.max(0, eps.size() - 3); i < eps.size(); i++) {
            JSONObject ep = eps.get(i);
            String still = ep.optString("still_path", "");
            Integer end = lastOf.get(ep.optInt("season_number"));
            seen.add(new Object[]{"S" + ep.optInt("season_number") + " E" + ep.optInt("episode_number"), ep.optString("name"),
                    ep.optString("overview"), still.isEmpty() || "null".equals(still) || !open.getAsBoolean() ? null
                    : ScoresTicker.cachedPicture(c, "https://image.tmdb.org/t/p/w780" + still, h.px(352), true),
                    end != null && end == ep.optInt("episode_number") && end > 1});
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
                    still.isEmpty() || "null".equals(still) || !open.getAsBoolean() ? null
                            : ScoresTicker.cachedPicture(c, "https://image.tmdb.org/t/p/w780" + still, h.px(560), true),
                    next.optInt("runtime")};
        }
        // Who's who: in the new season when one is next (each face marked when they were in the one seen), else in
        // the last season seen.
        int whoS = next != null && next.optInt("season_number") > lastS ? next.optInt("season_number") : lastS;
        Set<Integer> before = new HashSet<>();
        if (whoS != lastS) {
            JSONArray old = cast(key, t.id, lastS);
            for (int i = 0; old != null && i < old.length(); i++) before.add(old.optJSONObject(i).optInt("id"));
        }
        List<Object[]> who = new ArrayList<>();
        JSONArray cast = cast(key, t.id, whoS);
        if (cast == null && whoS != lastS) { cast = cast(key, t.id, lastS); whoS = lastS; before.clear(); }
        for (int i = 0; cast != null && i < cast.length() && who.size() < 5; i++) {
            JSONObject p = cast.optJSONObject(i);
            JSONArray roles = p.optJSONArray("roles");
            String character = roles != null && roles.length() > 0 ? roles.optJSONObject(0).optString("character") : "";
            String photo = p.optString("profile_path", "");
            // A face fills a 104 circle from a portrait: 104 across, so about 160 down.
            who.add(new Object[]{p.optString("name"), character, photo.isEmpty() || "null".equals(photo) || !open.getAsBoolean() ? null
                    : ScoresTicker.cachedPicture(c, "https://image.tmdb.org/t/p/w185" + photo, h.px(160), true), before.contains(p.optInt("id"))});
        }
        return new Object[]{d, back, logo, seen, who, up, whoS, lastS};
    }

    /** The highest episode number in each season listed. */
    private static void note(Map<Integer, Integer> lastOf, JSONArray eps) {
        for (int i = 0; eps != null && i < eps.length(); i++) {
            JSONObject ep = eps.optJSONObject(i);
            if (ep != null) lastOf.merge(ep.optInt("season_number"), ep.optInt("episode_number"), Math::max);
        }
    }

    /** A season's cast (TMDB's aggregate credits), or null. */
    private static JSONArray cast(String key, int id, int s) {
        if (s < 1) return null;
        try {
            JSONArray a = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/tv/" + id + "/season/" + s + "/aggregate_credits?api_key=" + key))
                    .optJSONArray("cast");
            return a == null || a.length() == 0 ? null : a;
        } catch (Exception e) {
            return null;
        }
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

    /**
     * As the mock-up: the series' picture darkened behind a deep blue that fades from the left; its logo; a green
     * "SEASON 2 IS HERE" (or a yellow "WELCOME BACK") with when it was last watched and the app's logo; the three
     * episodes seen, the last ringed in white; who's who as round faces (those back from the season seen marked); and
     * on the right the next episode, ringed, over a white "OK Play season 2, episode 1" with Official recap and Skip.
     */
    @SuppressWarnings("unchecked")
    private void build(PagesV2.Title t, WatchHistory.Entry e, Object[] got, Runnable play) {
        Context c = h.a;
        Typeface heavy = Fonts.get(c, R.font.outfit_extrabold), semi = Fonts.get(c, R.font.inter_semibold),
                regular = Fonts.get(c, R.font.inter_regular);
        overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF141C92, 0xFF1F29A6, 0xFF2A35B8}));
        if (got[1] != null) {
            ImageView bg = new ImageView(c);
            bg.setImageBitmap((Bitmap) got[1]);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bg.setColorFilter(new android.graphics.LightingColorFilter(0xFF8C8C8C, 0));   // brightness 55%
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
            l.setMaxWidth(h.px(560));
            l.setMaxHeight(h.px(130));
            place(l, 120, 84, 560, 130);
        } else {
            TextView n = h.text(56, heavy, Color.WHITE);
            n.setText(t.title);
            n.setSingleLine(true);
            place(n, 120, 110, 1100, 80);
        }
        int[] pos = YourShows.position(e);
        int whoS = (Integer) got[6], lastS = (Integer) got[7];
        LocalDate when = Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()).toLocalDate();
        long days = ChronoUnit.DAYS.between(when, LocalDate.now());
        LinearLayout line = new LinearLayout(c);
        line.setGravity(Gravity.CENTER_VERTICAL);
        Object[] up = (Object[]) got[5];
        int upS = up == null ? 0 : Integer.parseInt(((String) up[0]).split(" ")[0].substring(1));
        boolean newSeason = up != null && ((String) up[0]).endsWith(" E1") && upS > pos[0];
        TextView chip = h.text(15, heavy, newSeason ? 0xFF06331B : NAVY);
        chip.setText(newSeason ? "SEASON " + upS + " IS HERE" : "WELCOME BACK");
        chip.setLetterSpacing(0.13f);
        chip.setPadding(h.px(12), h.px(6), h.px(12), h.px(6));
        chip.setBackground(h.fill(6, newSeason ? GREEN : 0xFFFFD04D));
        line.addView(chip);
        String date = when.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK));
        String ago = ago(days);
        TextView said = h.text(22, regular, 0xE6FFFFFF);
        said.setText((newSeason ? "You finished season " + pos[0] + " on " + date : "You last watched S" + pos[0] + " E" + pos[1] + " on " + date)
                + (days < 1 ? "" : " · that's " + ago));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2);
        wl.leftMargin = h.px(14);
        line.addView(said, wl);
        // The app it was watched in, by its own logo (a cut-out one only: a banner card would be too big here).
        Object[] mark = e.pkg == null ? null : AppLogos.logo(c, e.pkg);
        if (mark != null && mark[0] != null && !Boolean.TRUE.equals(mark[1])) {
            ImageView m = new ImageView(c);
            m.setImageBitmap((Bitmap) mark[0]);
            m.setAdjustViewBounds(true);
            m.setScaleType(ImageView.ScaleType.FIT_CENTER);
            m.setMaxWidth(h.px(160));
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, h.px(30));
            ml.leftMargin = h.px(14);
            line.addView(m, ml);
        }
        place(line, 120, 238, 1500, 44);
        TextView head = h.text(46, heavy, Color.WHITE);
        head.setText("Previously on " + t.title);
        head.setSingleLine(true);
        head.setEllipsize(android.text.TextUtils.TruncateAt.END);
        place(head, 120, 300, 1100, 60);
        List<Object[]> seen = (List<Object[]>) got[3];
        TextView sub = h.text(20, regular, 0xBFFFFFFF);
        sub.setText("The last " + (seen.size() == 3 ? "three episodes" : seen.size() == 1 ? "episode" : "episodes") + " you saw, in two minutes. "
                + (newSeason ? "Nothing from season " + upS + "." : "Nothing after S" + pos[0] + " E" + pos[1] + "."));
        place(sub, 120, 362, 1100, 30);
        for (int i = 0; i < seen.size(); i++) {
            Object[] ep = seen.get(i);
            boolean lastOne = i == seen.size() - 1;
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
            TextView lab = h.text(12, heavy, Color.WHITE);
            lab.setText(ep[0] + (Boolean.TRUE.equals(ep[4]) ? " · FINALE" : ""));
            lab.setLetterSpacing(0.08f);
            lab.setPadding(h.px(8), h.px(4), h.px(8), h.px(4));
            lab.setBackground(h.fill(5, 0xB3000000));
            FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, -2);
            ll.leftMargin = h.px(10);
            ll.topMargin = h.px(10);
            pic.addView(lab, ll);
            if (lastOne) MockKit.ring(h, pic, 16, 4, Color.WHITE);
            card.addView(pic, new LinearLayout.LayoutParams(h.px(352), h.px(198)));
            TextView nm = h.text(20, semi, Color.WHITE);
            nm.setText((String) ep[1]);
            nm.setSingleLine(true);
            nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
            nl.topMargin = h.px(10);
            card.addView(nm, nl);
            TextView ov = h.text(16, regular, 0xD1FFFFFF);
            ov.setText((String) ep[2]);
            ov.setMaxLines(3);
            ov.setEllipsize(android.text.TextUtils.TruncateAt.END);
            ov.setLineSpacing(0, 1.18f);
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-1, -2);
            ol.topMargin = h.px(10);
            card.addView(ov, ol);
            place(card, 120 + i * 372, 414, 352, 340);
        }
        List<Object[]> who = (List<Object[]>) got[4];
        if (!who.isEmpty()) {
            TextView wh = h.text(16, heavy, 0xD9FFFFFF);
            wh.setText("WHO'S WHO IN SEASON " + whoS);
            wh.setLetterSpacing(0.16f);
            place(wh, 120, 774, 600, 26);
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
                MockKit.ring(h, f, 52, 3, 0xD9FFFFFF);
                one.addView(f, new LinearLayout.LayoutParams(h.px(104), h.px(104)));
                TextView ch = h.text(18, semi, Color.WHITE);
                ch.setText((String) p[1]);
                ch.setGravity(Gravity.CENTER);
                ch.setMaxLines(2);
                ch.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(h.px(150), -2);
                cl.topMargin = h.px(6);
                one.addView(ch, cl);
                TextView nm = h.text(14, regular, 0xB3FFFFFF);
                nm.setText((String) p[0]);
                nm.setGravity(Gravity.CENTER);
                nm.setSingleLine(true);
                nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(h.px(150), -2);
                al.topMargin = h.px(4);
                one.addView(nm, al);
                if (Boolean.TRUE.equals(p[3])) {
                    TextView back = h.text(11, heavy, 0xFFFFD04D);
                    back.setText("IN SEASON " + lastS);
                    back.setLetterSpacing(0.09f);
                    back.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(h.px(150), -2);
                    bl.topMargin = h.px(5);
                    one.addView(back, bl);
                }
                faces.addView(one, new LinearLayout.LayoutParams(h.px(168), -2));
            }
            place(faces, 100, 808, 900, 240);
        }
        // Up next, and the buttons.
        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        if (up != null) {
            FrameLayout pic = new FrameLayout(c);
            pic.setBackground(h.fill(22, 0xFF141A5C));
            pic.setClipToOutline(true);
            pic.setOutlineProvider(h.round(22));
            pic.setElevation(h.px(18));
            ImageView st = new ImageView(c);
            st.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (up[2] != null) st.setImageBitmap((Bitmap) up[2]);
            pic.addView(st, new FrameLayout.LayoutParams(-1, -1));
            View sh = new View(c);
            sh.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0x00000000, 0x33000000, 0xD9000000}));
            pic.addView(sh, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            TextView a = h.text(15, semi, 0xD9FFFFFF);
            a.setText("UP NEXT · " + up[0] + ((Integer) up[3] > 0 ? " · " + up[3] + " mins" : ""));
            words.addView(a);
            TextView b = h.text(28, heavy, Color.WHITE);
            b.setText((String) up[1]);
            b.setSingleLine(true);
            b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
            bl.topMargin = h.px(8);
            words.addView(b, bl);
            FrameLayout.LayoutParams wl2 = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            wl2.leftMargin = h.px(20);
            wl2.rightMargin = h.px(20);
            wl2.bottomMargin = h.px(18);
            pic.addView(words, wl2);
            MockKit.ring(h, pic, 22, 5, Color.WHITE);
            right.addView(pic, new LinearLayout.LayoutParams(h.px(560), h.px(315)));
        }
        String playWords = up == null ? "Play" : "Play season " + upS + ", episode " + ((String) up[0]).split(" E")[1];
        LinearLayout first = MockKit.pill(h, playWords, 22, semi, 70, true, null);
        first.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(-1, h.px(70));
        fl.topMargin = h.px(16);
        right.addView(first, fl);
        buttons.add(first);
        acts.add(() -> { hide(); play.run(); });
        LinearLayout row = new LinearLayout(c);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, h.px(58));
        rl.topMargin = h.px(16);
        right.addView(row, rl);
        android.graphics.drawable.Drawable yt = null;
        try {
            yt = c.getPackageManager().getApplicationIcon("com.google.android.youtube.tv");
        } catch (Exception ignored) {
            // the words alone
        }
        button(row, "Official recap", yt, () -> {
            hide();
            String q = t.title + " season " + lastS + " recap";
            h.searchTitle("com.google.android.youtube.tv", q, false, 0, null);
        }, semi, true);
        button(row, "Skip, I remember", null, () -> { hide(); play.run(); }, semi, false);
        place(right, 1250, 414, 560, 560);
        at = 0;
        paint();
    }

    private void button(LinearLayout row, String words, android.graphics.drawable.Drawable icon, Runnable act, Typeface face, boolean gap) {
        LinearLayout b = MockKit.pill(h, words, 19, face, 58, false, icon);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        if (gap) lp.rightMargin = h.px(12);
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

    /** Play on its own row at the top; Official recap and Skip side by side under it. */
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
        if (e.getAction() != KeyEvent.ACTION_DOWN || buttons.isEmpty()) return true;
        if (code == KeyEvent.KEYCODE_DPAD_UP) at = 0;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN && at == 0) at = Math.min(1, buttons.size() - 1);
        else if (code == KeyEvent.KEYCODE_DPAD_LEFT && at == 2) at = 1;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && at < buttons.size() - 1) at++;
        else if (code == KeyEvent.KEYCODE_DPAD_LEFT && at == 1) at = 0;
        paint();
        return true;
    }

    private void paint() {
        for (int i = 0; i < buttons.size(); i++) MockKit.paint(h, buttons.get(i), i == at, i == 0 ? 70 : 58);
    }

    private void place(View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(h.px(w), h.px(hgt));
        p.leftMargin = h.px(x);
        p.topMargin = h.px(y);
        overlay.addView(v, p);
    }
}

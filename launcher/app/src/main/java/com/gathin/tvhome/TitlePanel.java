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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A title's details, opened on the page itself under its hero (Up from the tabs, "up on the hero", or OK held on a
 * poster in Discover, Movies or TV shows): the hero keeps its picture, logo and line, its description and the page's
 * tabs and posters make way, and under the hero come Play, My list and, for a series watched before, Previously on;
 * for a film in a series of films, the saga so far in a box (how many seen, the films with the ones watched on this
 * TV ticked); and the cast in rings, each with how many things of theirs were watched here, the one focused opening
 * into a white card with their picture and where you've seen them. Up and Down between the rows (Up past the buttons
 * to the top bar, Down past the cast back to the page), Left and Right along them, OK acts, Back closes.
 */
final class TitlePanel {
    private static final int YELLOW = 0xFFFFD04D, GREEN = 0xFF3DDC84, NAVY = 0xFF10157E;
    private static final int BUTTONS = 0, SAGA = 1, CAST = 2;

    private final HomeV2 h;
    private final Context c;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Typeface heavy, semi, regular;
    private FrameLayout overlay;
    private boolean open, waitUp;
    /** The saga box is shown as well: a tighter layout, and the cast's card this tall, so it ends above the foot. */
    private boolean compact;
    private int CARD_H = 300;
    private int gen, row, col;
    private PagesV2.Title title;
    private Runnable play;
    private final List<View> buttons = new ArrayList<>(), saga = new ArrayList<>(), cast = new ArrayList<>();
    private final List<Runnable> buttonActs = new ArrayList<>();
    private final List<PagesV2.Title> sagaTitles = new ArrayList<>();
    private final List<Object[]> people = new ArrayList<>();
    private final List<View> faces = new ArrayList<>(), cards = new ArrayList<>();
    private LinearLayout listButton;

    TitlePanel(HomeV2 home) {
        h = home;
        c = home.a;
        heavy = c.getResources().getFont(R.font.outfit_extrabold);
        semi = c.getResources().getFont(R.font.inter_semibold);
        regular = c.getResources().getFont(R.font.inter_regular);
    }

    boolean isOpen() {
        return open;
    }

    /** Opens on a title; play does what OK on its poster does. */
    void show(PagesV2.Title t, Runnable play) {
        show(t, play, true);
    }

    /** held: opened by holding OK, so that OK's release is not a press (Up on the hero opens it with no OK to wait for). */
    void show(PagesV2.Title t, Runnable play, boolean held) {
        if (open) close(false);
        open = true;
        waitUp = held;
        title = t;
        this.play = play;
        int my = ++gen;
        // On the page, under its hero: no screen of its own. The description and the posters make way.
        overlay = new FrameLayout(c);
        overlay.setClipChildren(false);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(260).setStartDelay(120).start();
        h.heroMakeWay(true);
        io.execute(() -> {
            Object[] got = read(t);
            main.post(() -> { if (my == gen && open) build(t, got); });
        });
    }

    void hide() {
        close(true);
    }

    private void close(boolean fade) {
        if (!open) return;
        open = false;
        gen++;
        View o = overlay;
        overlay = null;
        if (fade) o.animate().alpha(0f).setDuration(160).withEndAction(() -> h.root.removeView(o)).start();
        else h.root.removeView(o);
        if (fade) {
            h.heroMakeWay(false);
            h.paint();
        }
    }

    // ---- reading ------------------------------------------------------------------------------------------------

    /** {details, backdrop, logo, people [{name, character, photo, seen [{title, poster, when}]}], saga [{Title, Bitmap, watched, when}]}. */
    private Object[] read(PagesV2.Title t) {
        String key = h.tmdbKey;
        JSONObject d = null;
        try {
            d = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/" + (t.tv ? "tv/" : "movie/") + t.id + "?language=en-GB&api_key=" + key
                    + "&append_to_response=" + (t.tv ? "aggregate_credits" : "credits") + ",watch/providers,images&include_image_language=en,null"));
        } catch (Exception ignored) {
            // the panel shows what the poster had
        }
        // The hero above already shows its picture and logo.
        Bitmap back = null, logo = null;
        Map<String, LocalDate> seen = ViewingLog.watched(c);
        // The cast: each with what of theirs was watched here.
        List<Object[]> who = new ArrayList<>();
        JSONObject credits = d == null ? null : d.optJSONObject(t.tv ? "aggregate_credits" : "credits");
        JSONArray castList = credits == null ? null : credits.optJSONArray("cast");
        for (int i = 0; castList != null && i < castList.length() && who.size() < 8; i++) {
            JSONObject p = castList.optJSONObject(i);
            String character = p.optString("character", "");
            JSONArray roles = p.optJSONArray("roles");
            if (character.isEmpty() && roles != null && roles.length() > 0) character = roles.optJSONObject(0).optString("character", "");
            String photo = p.optString("profile_path", "");
            Bitmap face = photo.isEmpty() || "null".equals(photo) ? null : ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w185" + photo, h.px(180));
            List<Object[]> in = new ArrayList<>();
            try {
                JSONArray cc = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/person/" + p.optInt("id") + "/combined_credits?api_key=" + key))
                        .optJSONArray("cast");
                java.util.Set<String> keys = new java.util.HashSet<>();
                for (int k = 0; cc != null && k < cc.length(); k++) {
                    JSONObject w = cc.optJSONObject(k);
                    boolean tv = "tv".equals(w.optString("media_type"));
                    String wk = (tv ? "tv:" : "mv:") + w.optInt("id");
                    if (w.optInt("id") == t.id && tv == t.tv || !seen.containsKey(wk) || !keys.add(wk)) continue;
                    String poster = w.optString("poster_path", "");
                    in.add(new Object[]{tv ? w.optString("name") : w.optString("title"), poster.isEmpty() || "null".equals(poster) ? null
                            : "https://image.tmdb.org/t/p/w185" + poster, seen.get(wk)});
                }
            } catch (Exception ignored) {
                // nothing known for them
            }
            in.sort((x, y) -> ((LocalDate) y[2]).compareTo((LocalDate) x[2]));
            List<Object[]> shown = new ArrayList<>();
            for (int k = 0; k < Math.min(2, in.size()); k++) {
                Object[] w = in.get(k);
                shown.add(new Object[]{w[0], w[1] == null ? null : ScoresTicker.cachedLogo(c, (String) w[1], h.px(110)), w[2]});
            }
            List<String> names = new ArrayList<>();
            for (Object[] w : in) names.add((String) w[0]);
            who.add(new Object[]{p.optString("name"), character, face, shown, names});
        }
        // A film in a series of films: the films so far.
        List<Object[]> parts = new ArrayList<>();
        JSONObject coll = d == null || t.tv ? null : d.optJSONObject("belongs_to_collection");
        String sagaName = coll == null ? null : coll.optString("name");
        if (coll != null) {
            try {
                JSONArray ps = new JSONObject(HomeV2.get("https://api.themoviedb.org/3/collection/" + coll.optInt("id") + "?language=en-GB&api_key=" + key))
                        .optJSONArray("parts");
                List<JSONObject> list = new ArrayList<>();
                for (int i = 0; ps != null && i < ps.length(); i++) list.add(ps.getJSONObject(i));
                list.sort((x, y) -> x.optString("release_date", "9999").compareTo(y.optString("release_date", "9999")));
                for (JSONObject f : list) {
                    if (f.optString("release_date", "").isEmpty() && parts.size() > 0) continue;
                    PagesV2.Title ft = new PagesV2.Title();
                    ft.id = f.optInt("id");
                    ft.tv = false;
                    ft.title = f.optString("title");
                    String pp = f.optString("poster_path", ""), bp = f.optString("backdrop_path", "");
                    ft.poster = pp.isEmpty() || "null".equals(pp) ? null : "https://image.tmdb.org/t/p/w342" + pp;
                    ft.backdrop = bp.isEmpty() || "null".equals(bp) ? null : "https://image.tmdb.org/t/p/w1280" + bp;
                    ft.year = f.optString("release_date", "").length() >= 4 ? f.optString("release_date").substring(0, 4) : "";
                    ft.overview = f.optString("overview", "");
                    ft.what = "";
                    Bitmap pic = ft.poster == null ? null : ScoresTicker.cachedLogo(c, ft.poster.replace("/w342/", "/w185/"), h.px(120));
                    parts.add(new Object[]{ft, pic, seen.get("mv:" + ft.id), f.optString("release_date", "")});
                    if (parts.size() >= 6) break;
                }
            } catch (Exception ignored) {
                // no saga shown
            }
        }
        return new Object[]{d, back, logo, who, parts, sagaName, YourShows.gb(d == null ? new JSONObject() : d)};
    }

    // ---- building -----------------------------------------------------------------------------------------------

    /** Where the details start under the hero (its line ends about here), and their left edge. */
    private static final int TOP = 468;

    private int left() {
        return h.dockMode ? 56 : 216;
    }

    @SuppressWarnings("unchecked")
    private void build(PagesV2.Title t, Object[] got) {
        overlay.removeAllViews();
        buttons.clear();
        buttonActs.clear();
        saga.clear();
        sagaTitles.clear();
        cast.clear();
        faces.clear();
        cards.clear();
        people.clear();
        int x0 = left(), y = TOP;

        // Play, My list (and Previously on): the mock-ups' round pills, Play with the remote's OK.
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        place(overlay, bar, x0, y, 1400, 56);
        addButton(bar, "Play", true, () -> { hide(); if (play != null) play.run(); });
        boolean listed = MyList.has(c, t.id, t.tv);
        listButton = addButton(bar, listed ? "\u2713  On My list" : "+  My list", false, () -> {
            boolean now = MyList.toggle(c, title, HomeV2.onYourApps(h, (JSONObject) got[6]) != null);
            MockKit.setWords(listButton, now ? "\u2713  On My list" : "+  My list");
        });
        if (t.tv && Recap.can(c, t.id)) addButton(bar, "\u21BA  Previously on", false, () -> { hide(); Recap.open(h, t, play); });
        // With the saga box as well, everything a little tighter, so the cast's card ends above the screen's foot
        // (it ran 40 past it): the box's posters smaller, the cast's rings and card shorter.
        List<Object[]> sagaParts = (List<Object[]>) got[4];
        compact = sagaParts.size() > 1;
        CARD_H = compact ? 276 : 300;
        y += compact ? 70 : 78;

        // The saga so far: a box with how many are seen, a bar for it, and the films.
        List<Object[]> parts = (List<Object[]>) got[4];
        if (parts.size() > 1) {
            int watched = 0;
            for (Object[] p : parts) if (p[2] != null) watched++;
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(h.px(22), h.px(14), h.px(22), h.px(14));
            GradientDrawable bg = h.fill(18, 0x8C0A0E50);
            bg.setStroke(Math.max(1, h.px(1)), 0x38FFFFFF);
            box.setBackground(bg);
            LinearLayout head = new LinearLayout(c);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = h.text(14, semi, Color.WHITE);
            name.setText(((String) got[5]).toUpperCase(Locale.UK));
            name.setLetterSpacing(0.14f);
            head.addView(name);
            TextView count = h.text(14, regular, 0xBFFFFFFF);
            count.setText(watched + " of " + parts.size() + " seen");
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.leftMargin = h.px(14);
            head.addView(count, cl);
            box.addView(head);
            FrameLayout track = new FrameLayout(c);
            track.setBackground(h.fill(3, 0x33FFFFFF));
            View done = new View(c);
            done.setBackground(h.fill(3, GREEN));
            track.addView(done, new FrameLayout.LayoutParams(Math.round(h.px(736) * watched / (float) parts.size()), -1));
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(h.px(736), h.px(6));
            tl.topMargin = h.px(8);
            tl.bottomMargin = h.px(10);
            box.addView(track, tl);
            LinearLayout strip = new LinearLayout(c);
            for (Object[] p : parts) {
                PagesV2.Title ft = (PagesV2.Title) p[0];
                LinearLayout one = new LinearLayout(c);
                one.setGravity(Gravity.CENTER_VERTICAL);
                one.setPadding(h.px(4), h.px(4), h.px(12), h.px(4));
                ImageView pic = new ImageView(c);
                pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (p[1] != null) pic.setImageBitmap((Bitmap) p[1]);
                pic.setBackground(h.fill(9, 0xFF1A1F6B));
                pic.setClipToOutline(true);
                pic.setOutlineProvider(h.round(9));
                boolean coming = p[2] == null && ft.id != t.id && status(ft, null, (String) p[3], t).startsWith("Out");
                pic.setAlpha(p[2] != null || ft.id == t.id ? 1f : 0.55f);
                if (coming) {
                    GradientDrawable dash = new GradientDrawable();
                    dash.setCornerRadius(h.px(9));
                    dash.setStroke(Math.max(1, h.px(2)), 0xB3FFFFFF, h.px(6), h.px(4));
                    pic.setForeground(dash);
                }
                one.addView(pic, new LinearLayout.LayoutParams(h.px(64), h.px(96)));
                LinearLayout words = new LinearLayout(c);
                words.setOrientation(LinearLayout.VERTICAL);
                TextView st = h.text(15, semi, p[2] != null ? GREEN : YELLOW);
                st.setText(status(ft, (LocalDate) p[2], (String) p[3], t));
                st.setSingleLine(true);
                words.addView(st);
                TextView nm = h.text(16, semi, Color.WHITE);
                nm.setText(ft.title);
                nm.setMaxLines(2);
                words.addView(nm);
                if (ft.year != null && !ft.year.isEmpty()) {
                    TextView yr = h.text(13, regular, 0xBFFFFFFF);
                    yr.setText(ft.year);
                    words.addView(yr);
                }
                LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(h.px(150), -2);
                wl.leftMargin = h.px(12);
                one.addView(words, wl);
                LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, -2);
                ol.rightMargin = h.px(8);
                strip.addView(one, ol);
                saga.add(one);
                sagaTitles.add(ft);
            }
            box.addView(strip);
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2);
            bl.leftMargin = h.px(x0);
            bl.topMargin = h.px(y);
            overlay.addView(box, bl);
            y += 188;
        }

        // The cast in rings: how many of theirs were watched here on a green badge; the one focused opens into a card.
        List<Object[]> who = (List<Object[]>) got[3];
        if (!who.isEmpty()) {
            TextView head = h.text(24, semi, Color.WHITE);
            head.setText("Cast");
            place(overlay, head, x0, y, 90, 34);
            TextView note = h.text(17, regular, 0xBFFFFFFF);
            note.setText("\u00B7  where you've seen them, from what you've watched on this TV");
            place(overlay, note, x0 + 80, y + 6, 1100, 28);
            LinearLayout strip = new LinearLayout(c);
            strip.setClipChildren(false);
            place(overlay, strip, x0 - 6, y + 40, 1880 - x0, CARD_H + 20);
            for (Object[] p : who) {
                FrameLayout slot = new FrameLayout(c);
                slot.setClipChildren(false);
                View face = face(p);
                View card = card(p);
                card.setVisibility(View.GONE);
                slot.addView(face, new FrameLayout.LayoutParams(h.px(178), -2));
                slot.addView(card, new FrameLayout.LayoutParams(h.px(430), h.px(CARD_H)));
                strip.addView(slot, new LinearLayout.LayoutParams(-2, -2));
                cast.add(slot);
                faces.add(face);
                cards.add(card);
                people.add(p);
            }
        }
        row = BUTTONS;
        col = 0;
        paint();
    }

    /** A cast member at rest: the ring, the badge, the name, the part and where they were seen. */
    @SuppressWarnings("unchecked")
    private View face(Object[] p) {
        LinearLayout one = new LinearLayout(c);
        one.setOrientation(LinearLayout.VERTICAL);
        one.setGravity(Gravity.CENTER_HORIZONTAL);
        one.setPadding(h.px(4), h.px(4), h.px(4), h.px(4));
        FrameLayout ring = new FrameLayout(c);
        ImageView f = new ImageView(c);
        f.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (p[2] != null) f.setImageBitmap((Bitmap) p[2]);
        int ringSize = compact ? 116 : 132;
        f.setBackground(h.fill(ringSize / 2f, 0xFF1A1F6B));
        f.setClipToOutline(true);
        f.setOutlineProvider(h.round(ringSize / 2f));
        MockKit.ring(h, f, ringSize / 2f, 3, 0xCCFFFFFF);
        ring.addView(f, new FrameLayout.LayoutParams(h.px(ringSize), h.px(ringSize)));
        List<String> names = (List<String>) p[4];
        if (!names.isEmpty()) {
            TextView n = h.text(14, semi, 0xFF06331B);
            n.setText(String.valueOf(names.size()));
            n.setGravity(Gravity.CENTER);
            GradientDrawable g = h.fill(17, GREEN);
            g.setStroke(Math.max(1, h.px(2.5f)), 0xFF0A0E4A);
            n.setBackground(g);
            FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(h.px(34), h.px(34), Gravity.BOTTOM | Gravity.END);
            ring.addView(n, nl);
        }
        one.addView(ring, new LinearLayout.LayoutParams(h.px(ringSize), h.px(ringSize)));
        TextView nm = h.text(17, semi, Color.WHITE);
        nm.setText((String) p[0]);
        nm.setGravity(Gravity.CENTER);
        nm.setMaxLines(2);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(h.px(170), -2);
        nl.topMargin = h.px(8);
        one.addView(nm, nl);
        TextView ch = h.text(14, regular, 0xB3FFFFFF);
        ch.setText((String) p[1]);
        ch.setGravity(Gravity.CENTER);
        ch.setSingleLine(true);
        ch.setEllipsize(android.text.TextUtils.TruncateAt.END);
        one.addView(ch, new LinearLayout.LayoutParams(h.px(170), -2));
        if (!names.isEmpty()) {
            TextView s = h.text(13, semi, 0xFF9FE7C0);
            s.setText(String.join(", ", names.subList(0, Math.min(2, names.size()))) + (names.size() > 2 ? " +" + (names.size() - 2) : ""));
            s.setGravity(Gravity.CENTER);
            s.setMaxLines(2);
            one.addView(s, new LinearLayout.LayoutParams(h.px(170), -2));
        }
        return one;
    }

    /** The focused cast member: a white card, their picture tall at the left, and where you've seen them. */
    @SuppressWarnings("unchecked")
    private View card(Object[] p) {
        LinearLayout card = new LinearLayout(c);
        card.setPadding(h.px(16), h.px(16), h.px(18), h.px(16));
        card.setBackground(h.fill(20, Color.WHITE));
        card.setElevation(h.px(10));
        ImageView pic = new ImageView(c);
        pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (p[2] != null) pic.setImageBitmap((Bitmap) p[2]);
        pic.setBackground(h.fill(14, 0xFFDDE0F5));
        pic.setClipToOutline(true);
        pic.setOutlineProvider(h.round(14));
        card.addView(pic, new LinearLayout.LayoutParams(h.px(compact ? 136 : 150), -1));
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView nm = h.text(22, heavy, NAVY);
        nm.setText((String) p[0]);
        nm.setMaxLines(2);
        words.addView(nm);
        if (!((String) p[1]).isEmpty()) {
            TextView ch = h.text(15, regular, 0xBF10157E);
            ch.setText("as " + p[1]);
            ch.setSingleLine(true);
            ch.setEllipsize(android.text.TextUtils.TruncateAt.END);
            words.addView(ch);
        }
        List<Object[]> shown = (List<Object[]>) p[3];
        TextView head = h.text(13, semi, 0xCC10157E);
        head.setText(shown.isEmpty() ? "NOTHING ELSE OF THEIRS WATCHED HERE YET"
                : "WHERE YOU'VE SEEN " + ((String) p[0]).split(" ")[0].toUpperCase(Locale.UK));
        head.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.topMargin = h.px(12);
        words.addView(head, hl);
        for (Object[] w : shown) {
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            ImageView poster = new ImageView(c);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (w[1] != null) poster.setImageBitmap((Bitmap) w[1]);
            poster.setBackground(h.fill(6, 0xFFDDE0F5));
            poster.setClipToOutline(true);
            poster.setOutlineProvider(h.round(6));
            r.addView(poster, new LinearLayout.LayoutParams(h.px(52), h.px(78)));
            LinearLayout ww = new LinearLayout(c);
            ww.setOrientation(LinearLayout.VERTICAL);
            TextView n = h.text(15, semi, NAVY);
            n.setText((String) w[0]);
            n.setMaxLines(2);
            ww.addView(n);
            TextView when = h.text(12, regular, 0xB310157E);
            when.setText("Watched " + ((LocalDate) w[2]).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.UK)));
            ww.addView(when);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
            wl.leftMargin = h.px(10);
            r.addView(ww, wl);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.topMargin = h.px(8);
            words.addView(r, rl);
        }
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(0, -1, 1f);
        wl.leftMargin = h.px(16);
        card.addView(words, wl);
        return card;
    }

    private LinearLayout addButton(LinearLayout bar, String words, boolean main, Runnable act) {
        LinearLayout b = MockKit.pill(h, words, 20, semi, 56, main, null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(56));
        lp.rightMargin = h.px(14);
        bar.addView(b, lp);
        buttons.add(b);
        buttonActs.add(act);
        return b;
    }

    private String meta(PagesV2.Title t, JSONObject d) {
        List<String> bits = new ArrayList<>();
        if (d != null) {
            String date = d.optString(t.tv ? "first_air_date" : "release_date", "");
            if (date.length() >= 4) bits.add(date.substring(0, 4));
            JSONArray g = d.optJSONArray("genres");
            for (int i = 0; g != null && i < Math.min(2, g.length()); i++) bits.add(g.optJSONObject(i).optString("name"));
            if (t.tv && d.optInt("number_of_seasons") > 0) bits.add(d.optInt("number_of_seasons") + (d.optInt("number_of_seasons") == 1 ? " season" : " seasons"));
            if (!t.tv && d.optInt("runtime") > 0) bits.add(d.optInt("runtime") / 60 + "h " + d.optInt("runtime") % 60 + "m");
            if (d.optDouble("vote_average") > 0) bits.add(String.format(Locale.UK, "★ %.1f", d.optDouble("vote_average")));
        } else if (t.year != null && !t.year.isEmpty()) bits.add(t.year);
        bits.add(t.tv ? "Series" : "Film");
        return String.join("  ·  ", bits);
    }

    private static String status(PagesV2.Title ft, LocalDate watched, String release, PagesV2.Title current) {
        if (watched != null) return "✓ Watched " + watched.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.UK));
        if (ft.id == current.id) return "This one";
        try {
            LocalDate r = LocalDate.parse(release);
            if (r.isAfter(LocalDate.now())) return "Out " + r.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK));
        } catch (Exception ignored) {
            // no date
        }
        return "Not seen yet";
    }

    // ---- the remote -----------------------------------------------------------------------------------------------

    boolean key(KeyEvent e) {
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        // The OK held to open it: its own release is not a press.
        if (waitUp && ok) { if (e.getAction() == KeyEvent.ACTION_UP) waitUp = false; return true; }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            if (e.getAction() == KeyEvent.ACTION_UP) hide();
            return true;
        }
        if (ok) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) press();
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        List<View> here = list(row);
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && col > 0) col--;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && col < here.size() - 1) col++;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
            // Down past the last row: back to the page's tabs and posters.
            if (!move(1)) { hide(); return true; }
        } else if (code == KeyEvent.KEYCODE_DPAD_UP) {
            // Up past the buttons: on to the top bar, as Up from the page's tabs did.
            if (!move(-1)) { hide(); h.upFromPage(); return true; }
        }
        paint();
        return true;
    }

    private boolean move(int by) {
        for (int r = row + by; r >= BUTTONS && r <= CAST; r += by) {
            if (list(r).isEmpty()) continue;
            row = r;
            col = Math.min(col, list(r).size() - 1);
            return true;
        }
        return false;
    }

    private List<View> list(int r) {
        return r == BUTTONS ? buttons : r == SAGA ? saga : cast;
    }

    private void press() {
        if (row == BUTTONS && col < buttonActs.size()) buttonActs.get(col).run();
        else if (row == SAGA && col < sagaTitles.size()) {
            PagesV2.Title ft = sagaTitles.get(col);
            if (ft.id == title.id) return;
            // Another film of the saga: opened where it can be watched (its streaming app, else Debrid Stream's page).
            hide();
            h.openTitle(ft);
        }
    }

    private void paint() {
        for (int i = 0; i < buttons.size(); i++) MockKit.paint(h, (LinearLayout) buttons.get(i), row == BUTTONS && i == col, 56);
        for (int i = 0; i < saga.size(); i++) {
            boolean on = row == SAGA && i == col;
            GradientDrawable g = h.fill(14, on ? 0x33FFFFFF : 0);
            if (on) g.setStroke(h.px(3), Color.WHITE);
            saga.get(i).setBackground(g);
        }
        for (int i = 0; i < cast.size(); i++) {
            boolean on = row == CAST && i == col;
            faces.get(i).setVisibility(on ? View.INVISIBLE : View.VISIBLE);
            View card = cards.get(i);
            // The card takes the place of the ring, so those after it move along to make room.
            ((FrameLayout.LayoutParams) faces.get(i).getLayoutParams()).width = h.px(178);
            cast.get(i).getLayoutParams().width = on ? h.px(442) : h.px(178);
            cast.get(i).requestLayout();
            if (on && card.getVisibility() != View.VISIBLE) {
                card.setVisibility(View.VISIBLE);
                card.setAlpha(0f);
                card.setScaleX(0.92f);
                card.setScaleY(0.92f);
                card.setPivotX(h.px(66));
                card.setPivotY(h.px(66));
                card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(HomeV2.SKY).start();
                card.post(() -> fit(card));
            } else if (!on) {
                card.animate().cancel();
                card.setVisibility(View.GONE);
            }
        }
        // Off the cast: back where it was, if it had lifted.
        if (row != CAST && overlay != null && overlay.getTranslationY() != 0) overlay.animate().translationY(0).setDuration(200).start();
    }

    /** Should a card still run past the screen's foot (a long name, a bigger font), the details lift just enough. */
    private void fit(View card) {
        if (overlay == null || card.getVisibility() != View.VISIBLE) return;
        int[] at = new int[2];
        card.getLocationOnScreen(at);
        float over = at[1] - overlay.getTranslationY() + card.getHeight() - h.px(1064);
        overlay.animate().translationY(over > 0 ? -over : 0).setDuration(200).start();
    }

    private void place(FrameLayout parent, View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(h.px(w), h.px(hgt));
        p.leftMargin = h.px(x);
        p.topMargin = h.px(y);
        parent.addView(v, p);
    }
}

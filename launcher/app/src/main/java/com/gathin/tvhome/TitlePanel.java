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
 * A title's panel (hold OK on a poster in Discover, Movies or TV shows): its picture and logo, Play, My list and, for a
 * series watched before, Previously on; for a film in a series of films, the films so far with the ones watched on
 * this TV ticked; and the cast, each with the films and series they are in that were watched on this TV ("where you've
 * seen them"). Up and Down between the rows, Left and Right along them, OK acts, Back closes.
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
    private int gen, row, col;
    private PagesV2.Title title;
    private Runnable play;
    private final List<View> buttons = new ArrayList<>(), saga = new ArrayList<>(), cast = new ArrayList<>();
    private final List<Runnable> buttonActs = new ArrayList<>();
    private final List<PagesV2.Title> sagaTitles = new ArrayList<>();
    private final List<Object[]> people = new ArrayList<>();
    private FrameLayout castCard;
    private TextView listButton;

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
        if (open) close(false);
        open = true;
        waitUp = true;
        title = t;
        this.play = play;
        int my = ++gen;
        overlay = new FrameLayout(c);
        overlay.setBackgroundColor(0xFF0A0E4A);
        overlay.setClickable(true);
        h.root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).start();
        TextView wait = h.text(22, regular, 0xB3FFFFFF);
        wait.setText(t.title);
        place(overlay, wait, 120, 120, 1200, 40);
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
        if (fade) o.animate().alpha(0f).setDuration(180).withEndAction(() -> h.root.removeView(o)).start();
        else h.root.removeView(o);
        if (fade) h.paint();
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
        Bitmap back = null, logo = null;
        String b = t.backdrop;
        if (d != null && !d.optString("backdrop_path").isEmpty() && !"null".equals(d.optString("backdrop_path"))) b = "https://image.tmdb.org/t/p/w1280" + d.optString("backdrop_path");
        if (b != null) back = ScoresTicker.cachedLogo(c, b, h.px(1400));
        JSONArray logos = d == null || d.optJSONObject("images") == null ? null : d.optJSONObject("images").optJSONArray("logos");
        for (int i = 0; logos != null && i < logos.length(); i++) {
            JSONObject l = logos.optJSONObject(i);
            if (!"en".equals(l.optString("iso_639_1")) || !l.optString("file_path").endsWith(".png")) continue;
            logo = ScoresTicker.cachedLogo(c, "https://image.tmdb.org/t/p/w500" + l.optString("file_path"), h.px(560));
            if (logo != null) logo = HomeV2.trim(logo);
            break;
        }
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

    @SuppressWarnings("unchecked")
    private void build(PagesV2.Title t, Object[] got) {
        overlay.removeAllViews();
        buttons.clear();
        buttonActs.clear();
        saga.clear();
        sagaTitles.clear();
        cast.clear();
        people.clear();
        JSONObject d = (JSONObject) got[0];
        Bitmap back = (Bitmap) got[1], logo = (Bitmap) got[2];
        if (back != null) {
            ImageView bg = new ImageView(c);
            bg.setImageBitmap(back);
            bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
            place(overlay, bg, 620, 0, 1300, 731);
        }
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF0A0E4A, 0xF20A0E4A, 0x660A0E4A, 0x000A0E4A}));
        place(overlay, shade, 600, 0, 1000, 731);
        View foot = new View(c);
        foot.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x000A0E4A, 0xFF0A0E4A}));
        place(overlay, foot, 600, 430, 1320, 302);

        if (logo != null) {
            ImageView l = new ImageView(c);
            l.setImageBitmap(logo);
            l.setAdjustViewBounds(true);
            l.setScaleType(ImageView.ScaleType.FIT_START);
            l.setMaxWidth(h.px(560));
            l.setMaxHeight(h.px(140));
            place(overlay, l, 120, 96, 560, 140);
        } else {
            TextView n = h.text(54, heavy, Color.WHITE);
            n.setText(t.title);
            n.setMaxLines(2);
            place(overlay, n, 120, 96, 900, 140);
        }
        TextView meta = h.text(20, regular, 0xE6FFFFFF);
        meta.setText(meta(t, d));
        place(overlay, meta, 120, 252, 1100, 32);
        TextView over = h.text(20, regular, 0xD9FFFFFF);
        String ov = d != null && !d.optString("overview").isEmpty() ? d.optString("overview") : t.overview;
        over.setText(ov == null ? "" : ov);
        over.setMaxLines(3);
        over.setLineSpacing(0, 1.15f);
        place(overlay, over, 120, 292, 880, 100);

        // Play, My list (and Previously on).
        LinearLayout bar = new LinearLayout(c);
        place(overlay, bar, 120, 410, 1100, 64);
        addButton(bar, "▶  Play", () -> { hide(); if (play != null) play.run(); });
        boolean listed = MyList.has(c, t.id, t.tv);
        listButton = addButton(bar, listed ? "✓  On My list" : "＋  My list", () -> {
            boolean now = MyList.toggle(c, title, HomeV2.onYourApps(h, (JSONObject) got[6]) != null);
            listButton.setText(now ? "✓  On My list" : "＋  My list");
        });
        if (t.tv && Recap.can(c, t.id)) addButton(bar, "↺  Previously on", () -> { hide(); Recap.open(h, t, play); });

        int y = 500;
        // The films so far.
        List<Object[]> parts = (List<Object[]>) got[4];
        if (parts.size() > 1) {
            int watched = 0;
            for (Object[] p : parts) if (p[2] != null) watched++;
            TextView head = h.text(15, semi, Color.WHITE);
            head.setText(((String) got[5]).toUpperCase(Locale.UK) + "   ·   " + watched + " of " + parts.size() + " seen on this TV");
            head.setLetterSpacing(0.12f);
            place(overlay, head, 120, y, 1300, 26);
            LinearLayout strip = new LinearLayout(c);
            place(overlay, strip, 110, y + 32, 1700, 180);
            for (Object[] p : parts) {
                PagesV2.Title ft = (PagesV2.Title) p[0];
                LinearLayout one = new LinearLayout(c);
                one.setGravity(Gravity.CENTER_VERTICAL);
                one.setPadding(h.px(10), h.px(10), h.px(16), h.px(10));
                ImageView pic = new ImageView(c);
                pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (p[1] != null) pic.setImageBitmap((Bitmap) p[1]);
                pic.setBackground(h.fill(8, 0xFF1A1F6B));
                pic.setClipToOutline(true);
                pic.setOutlineProvider(h.round(8));
                pic.setAlpha(p[2] != null || ft.id == t.id ? 1f : 0.6f);
                one.addView(pic, new LinearLayout.LayoutParams(h.px(86), h.px(129)));
                LinearLayout words = new LinearLayout(c);
                words.setOrientation(LinearLayout.VERTICAL);
                TextView st = h.text(14, semi, p[2] != null ? GREEN : YELLOW);
                st.setText(status(ft, (LocalDate) p[2], (String) p[3], t));
                words.addView(st);
                TextView nm = h.text(16, semi, Color.WHITE);
                nm.setText(ft.title);
                nm.setMaxLines(2);
                words.addView(nm);
                LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(h.px(150), -2);
                wl.leftMargin = h.px(12);
                one.addView(words, wl);
                strip.addView(one, new LinearLayout.LayoutParams(-2, -2));
                saga.add(one);
                sagaTitles.add(ft);
            }
            y += 230;
        }

        // The cast.
        List<Object[]> who = (List<Object[]>) got[3];
        if (!who.isEmpty()) {
            TextView head = h.text(24, semi, Color.WHITE);
            head.setText("Cast");
            place(overlay, head, 120, y, 200, 34);
            TextView note = h.text(17, regular, 0xBFFFFFFF);
            note.setText("· where you've seen them, from what's been watched on this TV");
            place(overlay, note, 200, y + 6, 1000, 28);
            LinearLayout strip = new LinearLayout(c);
            place(overlay, strip, 110, y + 44, 1720, 280);
            for (Object[] p : who) {
                LinearLayout one = new LinearLayout(c);
                one.setOrientation(LinearLayout.VERTICAL);
                one.setGravity(Gravity.CENTER_HORIZONTAL);
                one.setPadding(h.px(6), h.px(6), h.px(6), h.px(6));
                FrameLayout face = new FrameLayout(c);
                ImageView f = new ImageView(c);
                f.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (p[2] != null) f.setImageBitmap((Bitmap) p[2]);
                f.setBackground(h.fill(64, 0xFF1A1F6B));
                f.setClipToOutline(true);
                f.setOutlineProvider(h.round(64));
                face.addView(f, new FrameLayout.LayoutParams(h.px(128), h.px(128)));
                List<String> names = (List<String>) p[4];
                if (!names.isEmpty()) {
                    TextView n = h.text(14, semi, 0xFF06331B);
                    n.setText(String.valueOf(names.size()));
                    n.setGravity(Gravity.CENTER);
                    n.setBackground(h.fill(17, GREEN));
                    face.addView(n, new FrameLayout.LayoutParams(h.px(34), h.px(34), Gravity.BOTTOM | Gravity.END));
                }
                one.addView(face, new LinearLayout.LayoutParams(h.px(128), h.px(128)));
                TextView nm = h.text(16, semi, Color.WHITE);
                nm.setText((String) p[0]);
                nm.setGravity(Gravity.CENTER);
                nm.setMaxLines(2);
                one.addView(nm, new LinearLayout.LayoutParams(h.px(170), -2));
                TextView ch = h.text(13, regular, 0xB3FFFFFF);
                ch.setText((String) p[1]);
                ch.setGravity(Gravity.CENTER);
                ch.setSingleLine(true);
                one.addView(ch, new LinearLayout.LayoutParams(h.px(170), -2));
                if (!names.isEmpty()) {
                    TextView s = h.text(13, semi, 0xFF9FE7C0);
                    s.setText(String.join(", ", names.subList(0, Math.min(2, names.size()))) + (names.size() > 2 ? " +" + (names.size() - 2) : ""));
                    s.setGravity(Gravity.CENTER);
                    s.setMaxLines(2);
                    one.addView(s, new LinearLayout.LayoutParams(h.px(170), -2));
                }
                strip.addView(one, new LinearLayout.LayoutParams(h.px(196), -2));
                cast.add(one);
                people.add(p);
            }
        }
        row = BUTTONS;
        col = 0;
        paint();
    }

    private TextView addButton(LinearLayout bar, String words, Runnable act) {
        TextView b = h.text(21, semi, Color.WHITE);
        b.setText(words);
        b.setGravity(Gravity.CENTER);
        b.setPadding(h.px(28), 0, h.px(28), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, h.px(60));
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
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN) move(1);
        else if (code == KeyEvent.KEYCODE_DPAD_UP) move(-1);
        paint();
        return true;
    }

    private void move(int by) {
        for (int r = row + by; r >= BUTTONS && r <= CAST; r += by) {
            if (list(r).isEmpty()) continue;
            row = r;
            col = Math.min(col, list(r).size() - 1);
            return;
        }
    }

    private List<View> list(int r) {
        return r == BUTTONS ? buttons : r == SAGA ? saga : cast;
    }

    private void press() {
        if (row == BUTTONS && col < buttonActs.size()) buttonActs.get(col).run();
        else if (row == SAGA && col < sagaTitles.size()) {
            PagesV2.Title ft = sagaTitles.get(col);
            if (ft.id == title.id) return;
            show(ft, () -> h.openTitle(ft));
        }
    }

    private void paint() {
        for (int i = 0; i < buttons.size(); i++) {
            boolean on = row == BUTTONS && i == col;
            TextView b = (TextView) buttons.get(i);
            GradientDrawable g = h.fill(30, on ? Color.WHITE : 0x24FFFFFF);
            if (!on) g.setStroke(Math.max(1, h.px(1.5f)), 0x59FFFFFF);
            b.setBackground(g);
            b.setTextColor(on ? NAVY : Color.WHITE);
        }
        for (int i = 0; i < saga.size(); i++) {
            boolean on = row == SAGA && i == col;
            GradientDrawable g = h.fill(16, on ? 0x33FFFFFF : 0);
            if (on) g.setStroke(h.px(3), Color.WHITE);
            saga.get(i).setBackground(g);
        }
        for (int i = 0; i < cast.size(); i++) {
            boolean on = row == CAST && i == col;
            GradientDrawable g = h.fill(18, on ? 0x33FFFFFF : 0);
            if (on) g.setStroke(h.px(3), Color.WHITE);
            cast.get(i).setBackground(g);
        }
        showCastCard();
    }

    /** The focused cast member's card: where you've seen them, with the posters. */
    @SuppressWarnings("unchecked")
    private void showCastCard() {
        if (castCard != null) { overlay.removeView(castCard); castCard = null; }
        if (row != CAST || col >= people.size()) return;
        Object[] p = people.get(col);
        List<Object[]> shown = (List<Object[]>) p[3];
        if (shown.isEmpty()) return;
        castCard = new FrameLayout(c);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(h.px(22), h.px(18), h.px(22), h.px(18));
        box.setBackground(h.fill(20, 0xFAFFFFFF));
        TextView head = h.text(14, semi, NAVY);
        head.setText("WHERE YOU'VE SEEN " + ((String) p[0]).split(" ")[0].toUpperCase(Locale.UK));
        head.setLetterSpacing(0.12f);
        box.addView(head);
        for (Object[] w : shown) {
            LinearLayout r = new LinearLayout(c);
            r.setGravity(Gravity.CENTER_VERTICAL);
            ImageView pic = new ImageView(c);
            pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            if (w[1] != null) pic.setImageBitmap((Bitmap) w[1]);
            pic.setBackground(h.fill(6, 0xFFDDE0F5));
            pic.setClipToOutline(true);
            pic.setOutlineProvider(h.round(6));
            r.addView(pic, new LinearLayout.LayoutParams(h.px(52), h.px(78)));
            LinearLayout words = new LinearLayout(c);
            words.setOrientation(LinearLayout.VERTICAL);
            TextView n = h.text(16, semi, NAVY);
            n.setText((String) w[0]);
            n.setMaxLines(2);
            words.addView(n);
            TextView when = h.text(13, regular, 0xB310157E);
            when.setText("Watched " + ((LocalDate) w[2]).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.UK)));
            words.addView(when);
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(h.px(250), -2);
            wl.leftMargin = h.px(12);
            r.addView(words, wl);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
            rl.topMargin = h.px(10);
            box.addView(r, rl);
        }
        castCard.addView(box, new FrameLayout.LayoutParams(h.px(380), -2));
        View focused = cast.get(col);
        int[] at = new int[2], base = new int[2];
        focused.getLocationOnScreen(at);
        overlay.getLocationOnScreen(base);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(h.px(380), -2);
        int x = at[0] - base[0], y = at[1] - base[1];
        if (y + focused.getHeight() + h.px(12 + 260) <= h.px(1070)) {
            // Under the cast, clear of the story and the buttons.
            lp.leftMargin = Math.min(x, h.px(1920 - 420));
            lp.topMargin = y + focused.getHeight() + h.px(12);
        } else {
            // No room under it (a film with its series above): beside the one focused.
            boolean right = x + focused.getWidth() + h.px(400) <= h.px(1900);
            lp.leftMargin = right ? x + focused.getWidth() + h.px(10) : x - h.px(390);
            lp.topMargin = Math.min(y, h.px(1070 - 260));
        }
        overlay.addView(castCard, lp);
    }

    private void place(FrameLayout parent, View v, int x, int y, int w, int hgt) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(h.px(w), h.px(hgt));
        p.leftMargin = h.px(x);
        p.topMargin = h.px(y);
        parent.addView(v, p);
    }
}

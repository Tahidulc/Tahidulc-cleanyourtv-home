package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Sources;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A title's page, drawn by one view: the backdrop behind it all; the top part (the title large, Play and the round
 * buttons, the story and facts, the cast's names); then, the page sliding up as the remote goes down, a series' seasons
 * and its episodes in one row (the season's pill following the row), the trailer, the cast in circles, and more like
 * it. Each part is reached with Up and Down, and the page slides to keep it in sight.
 */
final class DetailPage extends View {
    interface Host {
        void play(Catalog.Episode ep);

        void open(Catalog.Title t);

        void trailerChosen(String id);

        /** Left from the first button: the page closes. */
        void left();

        /** How far through an episode is (0 to 1), below 0 when not started. */
        float progress(Catalog.Episode ep);
    }

    /** One of the buttons at the foot of the top part: a pill with words (icon -1) or a round one with an icon. */
    static final class Button {
        final String label;
        final int icon;
        final Runnable run;

        Button(String label, int icon, Runnable run) {
            this.label = label;
            this.icon = icon;
            this.run = run;
        }
    }

    static final int BUTTONS = 0, SEASONS = 1, EPISODES = 2, VIDEOS = 3, MORE = 4;
    private static final float X = 150f, EP_W = 400f, EP_H = 225f, EP_GAP = 30f, EP_TEXT = 168f;

    private final Host host;
    private Catalog.Title title;
    private Catalog.Details details;
    private final List<Button> buttons = new ArrayList<>();
    private int button, area = BUTTONS;
    private final List<Catalog.Episode> eps = new ArrayList<>();
    private final List<Catalog.Season> seasons = new ArrayList<>();
    private int ep, season;
    private float epX;
    private String trailer = "";
    private boolean trailerOn;
    private final List<String> cast = new ArrayList<>();
    /** The cast and the director as the circles show them: {name, role}. */
    private final List<String[]> people = new ArrayList<>();
    private String director = "";
    private final List<PosterArt.Item> more = new ArrayList<>();
    private int moreFocus;
    private float moreX;
    private float offset, offsetTo;
    private long last;
    private String playlist = "";

    private final PosterArt art;
    private final TextPaint bigP = Dark.text(92, 700, Dark.TEXT), centreP = Dark.text(54, 700, Dark.TEXT);
    private final TextPaint btnP = Dark.text(28, 600, 0xFF111114), tagP = Dark.text(18, 700, Dark.SOFT);
    private final TextPaint nameP = Dark.text(28, 600, Dark.TEXT), plotP = Dark.text(25, 400, 0xE6FFFFFF);
    private final TextPaint factP = Dark.text(23, 500, Dark.SOFT), sideP = Dark.text(22, 400, Dark.SOFT), sideKey = Dark.text(22, 600, Dark.TEXT);
    private final TextPaint seasonP = Dark.text(29, 600, Dark.MUTED), seasonOn = Dark.text(29, 700, Dark.TEXT), seasonPill = Dark.text(29, 700, 0xFF111114);
    private final TextPaint epNumP = Dark.text(20, 500, Dark.MUTED), epTitleP = Dark.text(26, 600, Dark.TEXT);
    private final TextPaint epPlotP = Dark.text(21, 400, 0xB3FFFFFF), epFactP = Dark.text(19, 400, Dark.MUTED);
    private final TextPaint headP = Dark.text(30, 600, Dark.TEXT), labelP = Dark.text(21, 500, Dark.MUTED);
    private final TextPaint castP = Dark.text(21, 600, Dark.TEXT), roleP = Dark.text(18, 400, Dark.MUTED), faceP = Dark.text(46, 600, Dark.TEXT);
    private final TextPaint hintP = Dark.text(20, 500, Dark.SOFT);
    private final Paint pill = Dark.fill(0xEBFFFFFF), pillOn = Dark.fill(Dark.ACCENT), round = Dark.fill(0x33FFFFFF), roundOn = Dark.fill(0xFFFFFFFF);
    private final Paint iconLine = Dark.line(Dark.TEXT, 2.6f), iconSolid = Dark.fill(Dark.TEXT);
    private final Paint iconLineDark = Dark.line(0xFF111114, 2.6f), iconSolidDark = Dark.fill(0xFF111114);
    private final Paint tagBox = Dark.fill(0x33FFFFFF), ring = Dark.line(0xFFFFFFFF, 3f), textBox = Dark.fill(0x24FFFFFF);
    private final Paint track = Dark.fill(0x59FFFFFF), bar = Dark.fill(0xFFFFFFFF), card = Dark.fill(Dark.CARD), disc = Dark.fill(0);
    private final Paint dim = new Paint(), band = new Paint(), bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint playDisc = Dark.fill(0x99000000);
    private final android.graphics.Path clip = new android.graphics.Path();
    private final RectF r = new RectF();
    private final Runnable redraw = this::invalidate;
    private StaticLayout titleL, plotL, starringL;
    private final List<StaticLayout> epTitles = new ArrayList<>(), epPlots = new ArrayList<>();

    DetailPage(Context c, Host host) {
        super(c);
        this.host = host;
        art = new PosterArt(Ui.f(Shelves.POSTER_W));
        bigP.setShadowLayer(Ui.f(12), 0, Ui.f(2), 0x99000000);
    }

    // ---- what it shows ------------------------------------------------------------------------------------------

    /** The title, its details (null until read), its buttons, its trailer ("" none), more like it, the episode to start on. */
    void set(Catalog.Title t, Catalog.Details d, List<Button> list, String trailerId, List<PosterArt.Item> moreLike, String resumeId) {
        boolean first = title == null || details == null && d != null;
        title = t;
        details = d;
        buttons.clear();
        buttons.addAll(list);
        button = Math.max(0, Math.min(button, buttons.size() - 1));
        Sources.Entry e = Sources.get(getContext(), t.playlist());
        playlist = e == null ? "" : e.name;
        eps.clear();
        seasons.clear();
        epTitles.clear();
        epPlots.clear();
        if (d != null && t.series) {
            seasons.addAll(d.seasons);
            eps.addAll(d.episodes());
        }
        if (first && !resumeId.isEmpty()) {
            for (int i = 0; i < eps.size(); i++) if (eps.get(i).id.equals(resumeId)) ep = i;
            epX = ep;
        }
        ep = Math.max(0, Math.min(ep, eps.size() - 1));
        season = seasonOf(ep);
        trailer = trailerId == null ? "" : trailerId;
        cast.clear();
        cast.addAll(Billboard.names(d == null ? "" : d.cast, 12));
        director = d == null ? "" : d.director;
        people.clear();
        for (String n : cast) people.add(new String[]{n, ""});
        if (!director.isEmpty()) people.add(new String[]{director.split(",")[0].trim(), "Director"});
        more.clear();
        more.addAll(moreLike);
        moreFocus = Math.max(0, Math.min(moreFocus, more.size() - 1));
        String plot = d != null && !d.plot.isEmpty() ? d.plot : t.plot;
        titleL = Ui.layout(t.name, bigP, Ui.px(1100), 2);
        plotL = plot.isEmpty() ? null : Ui.layout(plot, plotP, Ui.px(700), 3);
        String people = (cast.isEmpty() ? "" : "Starring: " + String.join(", ", Billboard.names(d == null ? "" : d.cast, 4)))
                + (director.isEmpty() ? "" : (cast.isEmpty() ? "" : "\n") + "Director: " + director);
        starringL = people.isEmpty() ? null : Ui.layout(people, sideP, Ui.px(400), 5);
        if (!available(area)) area = BUTTONS;
        settle(true);
        invalidate();
    }

    private boolean available(int a) {
        switch (a) {
            case SEASONS: return title != null && title.series && seasons.size() > 1;
            case EPISODES: return !eps.isEmpty();
            case VIDEOS: return !trailer.isEmpty();
            case MORE: return !more.isEmpty();
            default: return true;
        }
    }

    private int seasonOf(int index) {
        if (index < 0 || index >= eps.size()) return 0;
        int n = eps.get(index).season;
        for (int i = 0; i < seasons.size(); i++) if (seasons.get(i).number == n) return i;
        return 0;
    }

    private int firstOf(int seasonIndex) {
        if (seasonIndex < 0 || seasonIndex >= seasons.size()) return 0;
        int n = seasons.get(seasonIndex).number;
        for (int i = 0; i < eps.size(); i++) if (eps.get(i).season == n) return i;
        return 0;
    }

    // ---- the parts' places (design pixels, from the page's top) ---------------------------------------------------

    private float episodesY() {
        return 1080f;
    }

    private float videosY() {
        return episodesY() + (eps.isEmpty() ? 0f : 660f);
    }

    private float castY() {
        return videosY() + (trailer.isEmpty() ? 0f : 400f);
    }

    private float moreY() {
        return castY() + (cast.isEmpty() && director.isEmpty() ? 0f : 330f);
    }

    private float bottomY() {
        return moreY() + (more.isEmpty() ? 0f : 540f);
    }

    private void settle(boolean now) {
        float to;
        switch (area) {
            case SEASONS:
            case EPISODES:
                to = episodesY();
                break;
            case VIDEOS:
                to = videosY() - 150f;
                break;
            case MORE:
                to = Math.max(castY() - 150f, moreY() - 1080f + 560f);
                break;
            default:
                to = 0f;
                break;
        }
        offsetTo = Math.max(0f, Math.min(to, Math.max(0f, bottomY() - 1080f)));
        if (area == BUTTONS) offsetTo = 0f;
        if (now || Exec.inline) offset = offsetTo;
    }

    // ---- the remote ---------------------------------------------------------------------------------------------

    private void go(int a) {
        area = a;
        if (a == SEASONS) season = seasonOf(ep);
        settle(false);
        invalidate();
    }

    /** The next part down (or up) that has something. */
    private int next(int from, int dir) {
        for (int a = from + dir; a >= BUTTONS && a <= MORE; a += dir) if (available(a)) return a;
        return -1;
    }

    boolean key(int code) {
        if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_UP) {
            int n = next(area, code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
            if (n >= 0) go(n);
            return true;
        }
        boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT, right = code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!left && !right) return false;
        switch (area) {
            case BUTTONS:
                if (left && button == 0) host.left();
                else button = Math.max(0, Math.min(buttons.size() - 1, button + (left ? -1 : 1)));
                break;
            case SEASONS:
                season = Math.max(0, Math.min(seasons.size() - 1, season + (left ? -1 : 1)));
                ep = firstOf(season);
                break;
            case EPISODES:
                ep = Math.max(0, Math.min(eps.size() - 1, ep + (left ? -1 : 1)));
                season = seasonOf(ep);
                break;
            case MORE:
                if (left && moreFocus == 0) {
                    go(next(MORE, -1));
                    return true;
                }
                moreFocus = Math.max(0, Math.min(more.size() - 1, moreFocus + (left ? -1 : 1)));
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    void ok() {
        switch (area) {
            case BUTTONS:
                if (button < buttons.size()) buttons.get(button).run.run();
                break;
            case SEASONS:
                go(EPISODES);
                break;
            case EPISODES:
                if (ep < eps.size()) host.play(eps.get(ep));
                break;
            case VIDEOS:
                host.trailerChosen(trailer);
                break;
            case MORE:
                if (moreFocus < more.size()) host.open(more.get(moreFocus).title);
                break;
            default:
                break;
        }
    }

    boolean back() {
        if (area != BUTTONS) {
            go(BUTTONS);
            return true;
        }
        return false;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        if (title == null) return;
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 16f : Math.min(48f, now - last);
        last = now;
        float k = Exec.inline ? 1f : 1f - (float) Math.exp(-dt / 80.0);
        boolean moving = false;
        if (Math.abs(offsetTo - offset) > 0.5f) {
            offset += (offsetTo - offset) * k;
            moving = true;
        } else {
            offset = offsetTo;
        }
        if (Math.abs(ep - epX) > 0.002f) {
            epX += (ep - epX) * k;
            moving = true;
        } else {
            epX = ep;
        }
        if (Math.abs(moreFocus - moreX) > 0.002f) {
            moreX += (moreFocus - moreX) * k;
            moving = true;
        } else {
            moreX = moreFocus;
        }
        if (!moving) last = 0;

        drawBackdrop(c);
        float o = offset;
        if (o < 1080f) drawTop(c, -o);
        if (!eps.isEmpty()) drawEpisodes(c, episodesY() - o);
        if (!trailer.isEmpty()) drawVideos(c, videosY() - o);
        if (!cast.isEmpty() || !director.isEmpty()) drawCast(c, castY() - o);
        if (!more.isEmpty()) drawMore(c, moreY() - o);
        // Below the top part: the title centred over the page as it scrolls (the series' row has its own).
        boolean ownHeader = !eps.isEmpty() && o >= episodesY() - 60f && o < videosY() - 300f;
        if (o > 700f && !ownHeader) {
            if (band.getShader() == null) band.setShader(new LinearGradient(0, 0, 0, Ui.f(170), 0xF20E0E11, 0x000E0E11, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, getWidth(), Ui.f(170), band);
            String s = Ui.fit(title.name, centreP, Ui.f(1400));
            c.drawText(s, (getWidth() - centreP.measureText(s)) / 2f, Ui.f(92), centreP);
        }
        if (moving) postInvalidateOnAnimation();
    }

    private void drawBackdrop(Canvas c) {
        String pic = details != null && !details.backdrop.isEmpty() ? details.backdrop : title.backdrop;
        if (!pic.isEmpty()) {
            Bitmap b = Pics.get(pic, getWidth(), getHeight(), Pics.DARK_FULL, redraw);
            if (b != null) c.drawBitmap(b, 0, 0, bmp);
        } else if (!title.poster.isEmpty()) {
            // A poster only: on the right, faded the same way.
            int ph = getHeight(), pw = Math.round(ph * 2f / 3f);
            Bitmap b = Pics.get(title.poster, pw, ph, Pics.DARK, redraw);
            if (b != null) c.drawBitmap(b, getWidth() - pw, 0, bmp);
        }
        // Darker as the page goes down, so the rows stand out from it.
        int a = (int) Math.min(200f, 60f + offset / 1080f * 150f);
        dim.setColor(a << 24 | 0x0E0E11);
        c.drawRect(0, 0, getWidth(), getHeight(), dim);
    }

    private void drawTop(Canvas c, float dy) {
        float x = Ui.f(X), y0 = Ui.f(dy);
        if (titleL != null) Ui.draw(c, titleL, x, y0 + Ui.f(120));
        // The buttons: Play's pill, then the round ones.
        float by = y0 + Ui.f(770);
        boolean on = area == BUTTONS;
        float bx = x;
        float firstEnd = x;
        for (int i = 0; i < buttons.size(); i++) {
            Button b = buttons.get(i);
            boolean here = on && i == button;
            if (b.icon < 0) {
                float w = Math.max(Ui.f(420), btnP.measureText(b.label) + Ui.f(72));
                r.set(bx, by, bx + w, by + Ui.f(68));
                if (here) {
                    c.drawRoundRect(r, r.height() / 2, r.height() / 2, pillOn);
                } else {
                    c.drawRoundRect(r, r.height() / 2, r.height() / 2, pill);
                }
                Dark.icon(c, Dark.PLAY, r.left + Ui.f(40), r.centerY(), Ui.f(22), iconLineDark, iconSolidDark);
                c.drawText(Ui.fit(b.label, btnP, w - Ui.f(90)), r.left + Ui.f(64), r.centerY() + Ui.f(10), btnP);
                firstEnd = r.right;
                bx = x;
                by += Ui.f(88);
            } else {
                r.set(bx, by, bx + Ui.f(84), by + Ui.f(64));
                c.drawRoundRect(r, Ui.f(18), Ui.f(18), here ? roundOn : round);
                Dark.icon(c, b.icon, r.centerX(), r.centerY(), Ui.f(28), here ? iconLineDark : iconLine, here ? iconSolidDark : iconSolid);
                if (here) c.drawText(b.label, x, by + Ui.f(100), hintP);
                bx += Ui.f(100);
            }
        }
        // The middle: where it comes from, its name and year, its story, its facts.
        float mx = Math.max(x + Ui.f(480), firstEnd + Ui.f(50)), my = y0 + Ui.f(770);
        float tx = mx;
        if (!playlist.isEmpty()) {
            String p = playlist.toUpperCase(Locale.UK);
            float tw = tagP.measureText(p) + Ui.f(16);
            r.set(mx, my, mx + tw, my + Ui.f(28));
            c.drawRoundRect(r, Ui.f(5), Ui.f(5), tagBox);
            c.drawText(p, mx + Ui.f(8), my + Ui.f(20), tagP);
            tx = r.right + Ui.f(12);
        }
        c.drawText(Ui.fit(title.name + (title.year.isEmpty() ? "" : " (" + title.year + ")"), nameP, Ui.f(1400) - tx),
                tx, my + Ui.f(24), nameP);
        float py = my + Ui.f(46);
        if (plotL != null) {
            Ui.draw(c, plotL, mx, py);
            py += plotL.getHeight() + Ui.f(14);
        }
        String facts = facts();
        if (!facts.isEmpty()) c.drawText(Ui.fit(facts, factP, Ui.f(700)), mx, py + Ui.f(20), factP);
        // The right: who is in it.
        if (starringL != null) Ui.draw(c, starringL, Ui.f(1460), my);
    }

    /** "2024 · 2h 42m · ★ 6.9 · Drama · 12A". */
    private String facts() {
        List<String> parts = new ArrayList<>();
        if (!title.year.isEmpty()) parts.add(title.year);
        if (details != null && details.minutes > 0 && !title.series) parts.add(Ui.duration(details.minutes));
        if (title.series && !seasons.isEmpty()) parts.add(seasons.size() == 1 ? "1 season" : seasons.size() + " seasons");
        float rating = Billboard.rating(details != null && !details.rating.isEmpty() ? details.rating : title.rating);
        if (rating > 0) parts.add("★ " + String.format(Locale.UK, "%.1f", rating));
        String g = details != null ? PosterArt.firstGenre(details.genre) : "";
        if (g.isEmpty()) g = PosterArt.genreOf(title.category);
        if (!g.isEmpty()) parts.add(g);
        if (details != null && !details.age.isEmpty()) parts.add(details.age);
        return String.join(" · ", parts);
    }

    private void drawEpisodes(Canvas c, float dy) {
        float y0 = Ui.f(dy);
        if (y0 > getHeight() || y0 + Ui.f(660) < 0) return;
        // The title, centred, then the seasons.
        String s = Ui.fit(title.name, centreP, Ui.f(1400));
        c.drawText(s, (getWidth() - centreP.measureText(s)) / 2f, y0 + Ui.f(96), centreP);
        float sx = Ui.f(X), sy = y0 + Ui.f(176);
        if (seasons.size() > 1 || title.series) {
            for (int i = 0; i < seasons.size(); i++) {
                String name = seasonName(seasons.get(i));
                boolean cur = i == season;
                TextPaint p = cur ? (area == SEASONS ? seasonPill : seasonOn) : seasonP;
                float tw = p.measureText(name);
                if (cur && area == SEASONS) {
                    r.set(sx - Ui.f(22), sy - Ui.f(38), sx + tw + Ui.f(22), sy + Ui.f(14));
                    c.drawRoundRect(r, r.height() / 2, r.height() / 2, roundOn);
                }
                c.drawText(name, sx, sy, p);
                sx += tw + Ui.f(58);
                if (sx > getWidth()) break;
            }
        }
        // The row: every episode, season after season, the one the remote is on lifted.
        float x0 = Ui.f(X), ty = y0 + Ui.f(232), pitch = Ui.f(EP_W + EP_GAP);
        int n = eps.size();
        int from = Math.max(0, (int) Math.floor(epX) - 2);
        int to = Math.min(n - 1, from + (int) Math.ceil(getWidth() / pitch) + 3);
        boolean here = area == EPISODES;
        for (int i = from; i <= to; i++) {
            if (here && i == ep) continue;
            drawEpisode(c, i, x0 + (i - epX) * pitch, ty, false);
        }
        if (n > 0) drawEpisode(c, ep, x0 + (ep - epX) * pitch, ty, here);
    }

    private static String seasonName(Catalog.Season s) {
        return s.name.matches("(?i)series \\d+") ? "Season " + s.number : s.name;
    }

    private void drawEpisode(Canvas c, int i, float x, float y, boolean focused) {
        Catalog.Episode e = eps.get(i);
        float w = Ui.f(EP_W), h = Ui.f(EP_H);
        if (x > getWidth() || x + w < -Ui.f(40)) return;
        while (epTitles.size() <= i) {
            epTitles.add(null);
            epPlots.add(null);
        }
        c.save();
        if (focused) {
            // The words' own box, lit, under the still.
            r.set(x - Ui.f(14), y + h + Ui.f(10), x + w + Ui.f(14), y + h + Ui.f(EP_TEXT) + Ui.f(4));
            c.drawRoundRect(r, Ui.f(16), Ui.f(16), textBox);
            c.scale(1.05f, 1.05f, x + w / 2f, y + h / 2f);
        }
        r.set(x, y, x + w, y + h);
        String still = e.still.isEmpty() ? (details != null && !details.backdrop.isEmpty() ? details.backdrop : title.backdrop) : e.still;
        Bitmap b = still.isEmpty() ? null : Pics.get(still, Math.round(w), Math.round(h), Pics.CROP, redraw);
        if (b != null) {
            rounded(c, b, x, y);
        } else {
            card.setColor(PosterArt.blank(title.name + e.number));
            c.drawRoundRect(r, Ui.f(14), Ui.f(14), card);
        }
        float p = host.progress(e);
        if (p >= 0f) {
            float bh = Ui.f(5), bx = x + Ui.f(14), bw = w - Ui.f(28), byy = y + h - Ui.f(14);
            c.drawRoundRect(bx, byy - bh, bx + bw, byy, bh / 2, bh / 2, track);
            c.drawRoundRect(bx, byy - bh, bx + bw * Math.max(0.03f, Math.min(1f, p)), byy, bh / 2, bh / 2, bar);
        }
        if (focused) Dark.ring(c, r, Ui.f(14), ring);
        c.restore();
        // The words under it.
        float ty = y + h + Ui.f(36);
        c.drawText("S" + e.season + " E" + e.number, x, ty, epNumP);
        if (epTitles.get(i) == null) {
            String t = e.title.isEmpty() ? "Episode " + e.number : e.title;
            epTitles.set(i, Ui.layout(t, epTitleP, Math.round(w), 1));
            epPlots.set(i, e.plot.isEmpty() ? null : Ui.layout(e.plot, epPlotP, Math.round(w), 2));
        }
        Ui.draw(c, epTitles.get(i), x, ty + Ui.f(10));
        float py = ty + Ui.f(46);
        StaticLayout pl = epPlots.get(i);
        if (pl != null) {
            Ui.draw(c, pl, x, py);
            py += pl.getHeight();
        }
        String facts = episodeFacts(e.minutes, e.aired);
        if (!facts.isEmpty()) c.drawText(facts, x, py + Ui.f(28), epFactP);
    }

    /** A still drawn in r (set to its place) with its corners rounded. */
    private void rounded(Canvas c, Bitmap b, float x, float y) {
        c.save();
        clip.reset();
        clip.addRoundRect(r, Ui.f(14), Ui.f(14), android.graphics.Path.Direction.CW);
        c.clipPath(clip);
        c.drawBitmap(b, x, y, bmp);
        c.restore();
    }

    /** Pure: "1h 7m, 9 Oct 2022", either half alone, or "". */
    static String episodeFacts(int minutes, String aired) {
        String d = "";
        if (aired != null && aired.length() >= 10) {
            try {
                d = LocalDate.parse(aired.substring(0, 10)).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK));
            } catch (RuntimeException ignored) {
                d = "";
            }
        }
        String m = Ui.duration(minutes);
        if (m.isEmpty()) return d;
        return d.isEmpty() ? m : m + ", " + d;
    }

    private void drawVideos(Canvas c, float dy) {
        float y0 = Ui.f(dy);
        if (y0 > getHeight() || y0 + Ui.f(400) < 0) return;
        float x = Ui.f(X);
        c.drawText("Videos", x, y0 + Ui.f(44), headP);
        float w = Ui.f(EP_W), h = Ui.f(EP_H), y = y0 + Ui.f(72);
        boolean here = area == VIDEOS;
        c.save();
        if (here) c.scale(1.05f, 1.05f, x + w / 2f, y + h / 2f);
        r.set(x, y, x + w, y + h);
        Bitmap b = Pics.get("https://i.ytimg.com/vi/" + trailer + "/hqdefault.jpg", Math.round(w), Math.round(h), Pics.CROP, redraw);
        if (b != null) {
            rounded(c, b, x, y);
        } else {
            card.setColor(Dark.CARD_HI);
            c.drawRoundRect(r, Ui.f(14), Ui.f(14), card);
        }
        c.drawCircle(r.centerX(), r.centerY(), Ui.f(34), playDisc);
        Dark.icon(c, Dark.PLAY, r.centerX() + Ui.f(3), r.centerY(), Ui.f(30), iconLine, iconSolid);
        if (here) Dark.ring(c, r, Ui.f(14), ring);
        c.restore();
        c.drawText("Trailer", x, y + h + Ui.f(38), labelP);
    }

    private void drawCast(Canvas c, float dy) {
        float y0 = Ui.f(dy);
        if (y0 > getHeight() || y0 + Ui.f(330) < 0) return;
        float x = Ui.f(X);
        c.drawText("Cast", x, y0 + Ui.f(44), headP);
        float rad = Ui.f(72), cy = y0 + Ui.f(72) + rad, step = Ui.f(196);
        for (int i = 0; i < people.size(); i++) {
            float cx = x + rad + i * step;
            if (cx - rad > getWidth()) break;
            Dark.avatar(c, cx, cy, rad, people.get(i)[0], disc, faceP);
            String n = Ui.fit(people.get(i)[0], castP, step - Ui.f(16));
            c.drawText(n, cx - castP.measureText(n) / 2f, cy + rad + Ui.f(34), castP);
            if (!people.get(i)[1].isEmpty()) {
                String role = people.get(i)[1];
                c.drawText(role, cx - roleP.measureText(role) / 2f, cy + rad + Ui.f(60), roleP);
            }
        }
    }

    private void drawMore(Canvas c, float dy) {
        float y0 = Ui.f(dy);
        if (y0 > getHeight() || y0 + Ui.f(540) < 0) return;
        float x0 = Ui.f(X);
        c.drawText("More like this", x0, y0 + Ui.f(44), headP);
        float ty = y0 + Ui.f(76), pitch = art.w + Ui.f(Shelves.GAP);
        int n = more.size();
        int from = Math.max(0, (int) Math.floor(moreX) - 1);
        int to = Math.min(n - 1, from + (int) Math.ceil(getWidth() / pitch) + 2);
        boolean here = area == MORE;
        // The poster the remote is on grows into its lift and ring as the one it left settles back; both drawn last.
        boolean moving = moreLift.step(here && n > 0 ? more.get(moreFocus) : null);
        Object leaving = moreLift.leaving();
        int settling = -1;
        for (int i = from; i <= to; i++) {
            PosterArt.Item it = more.get(i);
            if (moreLift.of(it) > 0f) {
                if (it == leaving) settling = i;
                continue;
            }
            art.draw(c, it, x0 + (i - moreX) * pitch, ty, 0f, 255, redraw);
        }
        if (settling >= 0) art.draw(c, more.get(settling), x0 + (settling - moreX) * pitch, ty, moreLift.of(more.get(settling)), 255, redraw);
        if (n > 0 && here && more.get(moreFocus) != leaving) {
            art.draw(c, more.get(moreFocus), x0 + (moreFocus - moreX) * pitch, ty, moreLift.of(more.get(moreFocus)), 255, redraw);
        }
        if (moving) postInvalidateOnAnimation();
    }

    private final Lift moreLift = new Lift();
}

package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.sports.LeaguePhotos;
import com.gathin.homefieldtv.sports.Squad;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A player's page (or the manager's) in 07 (the approved mock-ups 20, 21 and 22), under the shell's "‹ back" line (the
 * club's name): the club's ground; on the left his card of frosted glass with his real picture (PlayerArt: the league's
 * own cut-out or ESPN's head and shoulders rising out of its top, a face alone as a portrait inside it, else the club's
 * crest and his initials; never a drawn person) and his number in the club's coin at its shoulder; on the right his
 * first name and his surname huge, his position, his country with its flag and his age, a rule, what the figures are
 * for ("PREMIER LEAGUE · 2026/2027", a manager's "AT EVERTON"), then this season's figures as glass tiles four across
 * (appearances, goals, assists, clean sheets for a goalkeeper, cards, minutes, rating; a manager's matches, won, drawn
 * and lost at the club), each left out when the source lacks it. The round arrows at the sides when there is someone
 * that way. Left and Right go to the previous or next of the squad as the club's page showed them (the same pills),
 * sliding across; Up goes to the tab bar; Back goes back to the club's page, its row on him.
 *
 * <p>His season is one read of FotMob's page for him, once the remote rests on him; the players either side are read
 * after it, nothing further.
 */
final class PlayerScreen extends Screen {
    static final long SLIDE_MS = 300, REST_MS = 250;
    /**
     * The card (design px, the mock-ups' .pl-card: its glass from (150, 440), 500 by 560, the picture rising 380 over
     * it), the words' left edge, and how far a player's page slides.
     */
    static final float CARD_X = 150f, CARD_Y = 440f, CARD_W = 500f, CARD_H = 560f, CARD_RISE = 380f, TEXT_X = 760f, TEXT_R = 1810f,
            SLIDE = 640f;

    private final TeamScreen team;
    final List<Squad.Person> list;
    int at;
    private Page page;
    final Glide pos = new Glide(SLIDE_MS, SLIDE);
    /** Each one's season as read, by FotMob's id. */
    final Map<String, Squad.Season> seasons = new HashMap<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable load = this::load;

    PlayerScreen(TvActivity tv, TeamScreen team, List<Squad.Person> list, int at) {
        super(tv);
        this.team = team;
        this.list = list;
        this.at = Math.max(0, Math.min(list.size() - 1, at));
        pos.jump(this.at);
    }

    Squad.Person person() {
        return at >= 0 && at < list.size() ? list.get(at) : null;
    }

    /** For previews and tests: a season as if it had been read. */
    void preview(Squad.Person p, Squad.Season s) {
        seasons.put(p.id, s);
        if (page != null) page.forget(p);
    }

    /** Its page draws an opaque ground over the whole screen (the window's own isn't drawn under it). */
    @Override boolean opaque() {
        return true;
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        page = new Page();
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        h.removeCallbacks(load);
        if (!Exec.inline) h.postDelayed(load, 0);
        if (page != null) page.ahead();
    }

    @Override void hidden() {
        h.removeCallbacks(load);
    }

    // ---- 07's shell -------------------------------------------------------------------------------------------------

    @Override boolean shell() {
        return true;
    }

    /** Where Back goes: the club's page. */
    @Override String backLine() {
        return clubName().isEmpty() ? "Squad" : clubName();
    }

    private String clubName() {
        if (team == null) return "";
        if (team.squad != null && !team.squad.name.isEmpty()) return team.squad.name;
        return team.club.fullName.isEmpty() ? team.club.name : team.club.fullName;
    }

    /** His season, read once the remote rests on him; then the ones either side, read ahead. */
    private void load() {
        Squad.Person p = person();
        if (p == null || !p.fotmob) return;
        if (!seasons.containsKey(p.id)) {
            Squad.Season had = Squad.keptSeason(p.id);
            if (had != null) {
                took(p, had);
            } else {
                Exec.io(() -> {
                    Squad.Season s = Squad.season(p.id);
                    Exec.main(() -> {
                        if (s != null) took(p, s);
                        ahead();
                    });
                });
                return;
            }
        }
        ahead();
    }

    private void took(Squad.Person p, Squad.Season s) {
        seasons.put(p.id, s);
        if (page != null) {
            page.forget(p);
            page.invalidate();
        }
    }

    /** The seasons of the ones either side, read ahead (only those). */
    private void ahead() {
        for (int i = at - 1; i <= at + 1; i += 2) {
            if (i < 0 || i >= list.size()) continue;
            Squad.Person p = list.get(i);
            if (!p.fotmob || seasons.containsKey(p.id) || Squad.keptSeason(p.id) != null) continue;
            Exec.io(() -> Squad.season(p.id));
        }
    }

    @Override boolean key(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            if (tv.tabs() != null) tv.tabs().enter();
            return true;
        }
        if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) {
            return code == KeyEvent.KEYCODE_DPAD_DOWN;
        }
        int to = Math.max(0, Math.min(list.size() - 1, at + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
        if (to != at) {
            at = to;
            pos.to(to);
            if (team != null) team.follow(list.get(to));
            h.removeCallbacks(load);
            if (!Exec.inline) h.postDelayed(load, REST_MS);
            page.ahead();
            page.invalidate();
        }
        return true;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    /** One page's words, worked out once for a person (and again when his season arrives). */
    private static final class Sheet {
        String first = "", last = "", position = "", country = "", age = "", flag = "", number = "", what = "";
        float lastSize = 1f;
        List<String[]> figures;
    }

    final class Page extends View {
        private final Map<Squad.Person, Sheet> sheets = new HashMap<>();
        private final Map<Squad.Person, PlayerArt.Pic> pics = new IdentityHashMap<>();
        private final ClubLook.Glass glass = new ClubLook.Glass();
        private final ClubLook.Coin coin = new ClubLook.Coin(64f);
        private final Paint ground = new Paint(), grain = ClubLook.grain(), vignette = new Paint(Paint.DITHER_FLAG), line = new Paint(),
                flagP = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), dotP = new Paint(Paint.ANTI_ALIAS_FLAG),
                tint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint firstP = Type.paint(46f, Type.MEDIUM, 0xFFFFFFFF), lastP = Type.paint(132f, Type.EXTRABOLD, 0xFFFFFFFF),
                factP = Type.paint(25f, Type.SEMIBOLD, 0xFFFFFFFF), whatP = Type.paint(17f, Type.BOLD, 0xBFFFFFFF),
                valueP = Type.paint(62f, Type.EXTRABOLD, 0xFFFFFFFF), labelP = Type.paint(19f, Type.SEMIBOLD, 0xBFFFFFFF);
        private final RectF r = new RectF(), box = new RectF();
        private final Matrix fm = new Matrix();
        private final Runnable redraw = this::invalidate;
        private PlayerArt art;
        private int colour;
        /** The flag's rounded shader, made when its picture comes. */
        private Bitmap flagFor;
        private BitmapShader flagShader;

        Page() {
            super(tv);
            lastP.setLetterSpacing(-0.035f);
            whatP.setLetterSpacing(0.14f);
            valueP.setLetterSpacing(-0.03f);
            colour = team == null ? 0 : team.colour;
            coin.colour(colour, x(63f));
            line.setColor(0x38FFFFFF);
            dotP.setColor(0x99FFFFFF);
            tint.setColor(ClubLook.glassTint(colour));
            Shadows.prepare(Look07.SH_GLASS, 26f);
        }

        private float x(float d) {
            return Ui.f(d);
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            ClubLook.ground(ground, colour, w, h);
            ClubLook.vignette(vignette, w, h);
            art = new PlayerArt(x(CARD_W), x(CARD_RISE), x(CARD_H), x(30f));
            sheets.clear();
            ahead();
        }

        /** A person's words to work out again (his season arrived). */
        void forget(Squad.Person p) {
            sheets.remove(p);
        }

        private PlayerArt.Pic picOf(Squad.Person p) {
            PlayerArt.Pic pic = pics.get(p);
            if (pic == null) {
                pic = new PlayerArt.Pic(p);
                pics.put(p, pic);
            }
            return pic;
        }

        /** The pictures either side, read ahead (when the remote moves), their league's own looked up first. */
        void ahead() {
            int league = team == null ? LeaguePhotos.NONE : team.league;
            for (int i = Math.max(0, at - 1); i <= Math.min(list.size() - 1, at + 1); i++) {
                Squad.Person p = list.get(i);
                PlayerArt.Pic pic = picOf(p);
                if (LeaguePhotos.pending(league, p) && team != null) {
                    Squad.Club club = team.club;
                    LeaguePhotos.lookUp(tv, league, club.fullName.isEmpty() ? club.name : club.fullName, club.name, p, () -> {
                        pic.again();
                        invalidate();
                    });
                    continue;
                }
                String u = pic.url();
                if (u != null && art != null) Pics.prefetch(u, art.width(Squad.kind(u)), art.height(Squad.kind(u)), Pics.FIT);
            }
        }

        private Sheet sheet(Squad.Person p) {
            Sheet s = sheets.get(p);
            if (s != null) return s;
            s = new Sheet();
            s.first = p.first;
            s.last = p.last;
            float lw = lastP.measureText(p.last), room = x(TEXT_R - TEXT_X);
            s.lastSize = lw > room ? room / lw : 1f;
            Squad.Season season = seasons.get(p.id);
            s.position = season != null && !season.position.isEmpty() && p.line != Squad.STAFF ? title(season.position) : p.position;
            s.country = p.country;
            s.flag = p.flag;
            s.age = p.age > 0 ? "Age " + p.age : "";
            s.number = p.number;
            if (season != null && !season.figures.isEmpty()) {
                s.figures = season.figures;
                s.what = (season.season.isEmpty() ? season.competition : season.competition + " · " + season.season).toUpperCase(Locale.UK);
            } else {
                s.figures = p.figures;
                s.what = p.figures.isEmpty() ? "" : "THIS SEASON";
            }
            sheets.put(p, s);
            return s;
        }

        /** "Attacking Midfielder" as "Attacking midfielder". */
        private String title(String s) {
            String[] w = s.trim().split("\\s+");
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < w.length; i++) {
                if (i > 0) b.append(' ');
                b.append(i == 0 ? w[i] : w[i].toLowerCase(Locale.UK));
            }
            return b.toString();
        }

        /** The ground, its grain and its vignette: three screens of fill that don't move, kept as one picture (Cached). */
        private final Cached still = new Cached(true);
        private final Cached.Painter paintStill = this::paintStill;

        private void paintStill(Canvas c) {
            int w = getWidth(), h = getHeight();
            c.drawRect(0, 0, w, h, ground);
            c.drawRect(0, 0, w, h, grain);
            c.drawRect(0, 0, w, h, vignette);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            still.forget();
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = pos.step();
            int w = getWidth(), h = getHeight();
            still.draw(c, w, h, ground.getShader(), Math.round(Ui.s * 1000f), paintStill, 1f, 0f);
            float p = pos.value();
            int a = Math.max(0, Math.min(list.size() - 1, (int) Math.floor(p))), b = Math.min(list.size() - 1, a + 1);
            if (art != null) {
                if (b != a && Math.abs(b - p) < 1f) drawOne(c, list.get(b), b - p);
                if (Math.abs(a - p) < 1f) drawOne(c, list.get(a), a - p);
            }
            // The way to the ones either side: 07's round glass buttons.
            float ay = x(592f);
            if (at > 0) arrow(c, x(72f), ay, Icons07.LEFT);
            if (at < list.size() - 1) arrow(c, w - x(72f), ay, Icons07.RIGHT);
            if (moving) postInvalidateOnAnimation();
        }

        private void arrow(Canvas c, float cx, float cy, int icon) {
            float rad = x(32f);
            r.set(cx - rad, cy - rad, cx + rad, cy + rad);
            tint.setAlpha(255);
            c.drawCircle(cx, cy, rad, tint);
            Frost.glass(c, r, rad, 1f);
            Icons07.draw(c, icon, cx, cy, x(30f), 0xFFFFFFFF);
        }

        /** One person's page at d (0 in place; -1 gone left, 1 coming from the right), sliding and fading with it. */
        private void drawOne(Canvas c, Squad.Person person, float d) {
            float alpha = Math.max(0f, 1f - Math.abs(d));
            if (alpha <= 0.01f) return;
            Sheet s = sheet(person);
            c.save();
            c.translate(x(d * SLIDE), 0);
            // His card: the glass, his real picture rising out of it, the coin at its shoulder.
            c.save();
            c.translate(x(CARD_X), x(CARD_Y - CARD_RISE));
            box.set(0, art.top, art.w, art.top + art.h);
            glass.draw(c, box, art.radius, 1f, alpha, 1f);
            picture(c, person, alpha);
            if (!s.number.isEmpty()) coin.draw(c, art.w - x(27f), art.top - x(30f) + x(63f), x(63f), s.number, alpha, 1f, 0f);
            c.restore();
            // The words: the names, his position, country and age, then the season's figures. They go in the first half
            // of a slide and come in the second, so two players' words never lie over each other.
            float words = Math.max(0f, 1f - 2f * Math.abs(d));
            if (words > 0.01f) drawWords(c, s, words);
            c.restore();
        }

        private void picture(Canvas c, Squad.Person person, float alpha) {
            int league = team == null ? LeaguePhotos.NONE : team.league;
            if (LeaguePhotos.pending(league, person)) return;
            PlayerArt.Pic pic = picOf(person);
            String url = pic.url();
            if (url == null) {
                String crestUrl = team == null ? "" : team.club.crest;
                int cs = art.crestSize();
                Bitmap crest = crestUrl.isEmpty() ? null : Pics.get(crestUrl, cs, cs, Pics.FIT, redraw);
                art.mark(c, crest, pic.initials, alpha);
                return;
            }
            int kind = Squad.kind(url);
            Bitmap bm = Pics.get(url, art.width(kind), art.height(kind), Pics.FIT, redraw);
            if (bm != null) art.draw(c, pic, bm, kind, alpha);
        }

        private void drawWords(Canvas c, Sheet s, float words) {
            int wa = Math.round(255 * words);
            float tx = x(TEXT_X);
            if (!s.first.isEmpty()) {
                firstP.setAlpha(wa);
                c.drawText(s.first, tx, x(295f), firstP);
            }
            lastP.setAlpha(wa);
            c.save();
            c.translate(tx, x(416f));
            c.scale(s.lastSize, s.lastSize);
            c.drawText(s.last, 0, 0, lastP);
            c.restore();
            drawFacts(c, s, tx, x(466f), wa);
            line.setAlpha(Math.round(0x38 * words));
            c.drawRect(tx, x(511f), x(TEXT_R), x(511f) + Math.max(1f, x(1f)), line);
            if (s.figures.isEmpty()) return;
            whatP.setAlpha(Math.round(0xBF * words));
            c.drawText(s.what, tx, Type.centred(whatP, x(548f)), whatP);
            float colW = (TEXT_R - TEXT_X - 3 * 20f) / 4f;
            for (int i = 0; i < s.figures.size() && i < 8; i++) {
                String[] f = s.figures.get(i);
                float fx = TEXT_X + (colW + 20f) * (i % 4), fy = i < 4 ? 580f : 735f;
                r.set(x(fx), x(fy), x(fx + colW), x(fy + 129f));
                tint.setAlpha(Math.round(255 * words));
                c.drawRoundRect(r, x(26f), x(26f), tint);
                Frost.glass(c, r, x(26f), words);
                valueP.setAlpha(wa);
                c.drawText(f[1], x(fx + 22f), x(fy + 18f + 62f * 0.8f), valueP);
                labelP.setAlpha(Math.round(0xBF * words));
                c.drawText(f[0], x(fx + 22f), x(fy + 18f + 62f + 8f + 18f), labelP);
            }
        }

        /** "Striker  ·  [flag] France  ·  Age 24", each part left out when not known. */
        private void drawFacts(Canvas c, Sheet s, float x0, float cy, int a) {
            float xx = x0, gap = x(14f), base = Type.centred(factP, cy);
            factP.setAlpha(a);
            boolean any = false;
            if (!s.position.isEmpty()) {
                c.drawText(s.position, xx, base, factP);
                xx += factP.measureText(s.position);
                any = true;
            }
            if (!s.country.isEmpty() || !s.flag.isEmpty()) {
                if (any) xx = dot(c, xx, cy, gap, a);
                Bitmap flag = s.flag.isEmpty() ? null : Pics.get(s.flag, Ui.px(40f), Ui.px(27f), Pics.CROP, redraw);
                if (flag != null) {
                    flag(c, flag, xx, cy, a);
                    xx += x(40f) + gap;
                }
                if (!s.country.isEmpty()) {
                    c.drawText(s.country, xx, base, factP);
                    xx += factP.measureText(s.country);
                }
                any = true;
            }
            if (!s.age.isEmpty()) {
                if (any) xx = dot(c, xx, cy, gap, a);
                c.drawText(s.age, xx, base, factP);
            }
        }

        /** The country's flag, 40 by 27 with its corners rounded (4). */
        private void flag(Canvas c, Bitmap flag, float left, float cy, int a) {
            if (flag != flagFor) {
                flagFor = flag;
                flagShader = new BitmapShader(flag, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            }
            r.set(left, cy - x(13.5f), left + x(40f), cy + x(13.5f));
            fm.setScale(r.width() / flag.getWidth(), r.height() / flag.getHeight());
            fm.postTranslate(r.left, r.top);
            flagShader.setLocalMatrix(fm);
            flagP.setShader(flagShader);
            flagP.setAlpha(a);
            c.drawRoundRect(r, x(4f), x(4f), flagP);
        }

        private float dot(Canvas c, float xx, float cy, float gap, int a) {
            dotP.setAlpha(Math.round(0x99 * a / 255f));
            c.drawCircle(xx + gap + x(3f), cy, x(2.5f), dotP);
            return xx + gap * 2f + x(6f);
        }
    }
}

package com.gathin.homefieldtv;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.sports.LeaguePhotos;
import com.gathin.homefieldtv.sports.Squad;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A club's page in 07 (the approved mock-ups 19, 19b and 19c, clip 4), under the shell's "‹ back" line: the club's own
 * colour behind, brightest a little above the middle and deepening to the edges, with a fine grain and a soft vignette;
 * the crest and the PLAYERS | MANAGEMENT pills at the left, the lines' pills (ALL, GOALKEEPERS, DEFENDERS, MIDFIELDERS,
 * FORWARDS) at the right; then the squad as a carousel of frosted-glass cards: the remote's in the middle, larger and
 * brighter, two each side smaller and fainter, each with his real picture (PlayerArt: the league's own cut-out or ESPN's
 * head and shoulders rising out of its top, a face alone as a portrait inside it, else the club's crest and his
 * initials; never a drawn person), his number in the club's coin at its top right corner and his name at its foot (the
 * middle card's first name); his line written down beside the middle card and his surname huge under it, rising in as
 * the remote moves. The hints along the foot.
 *
 * <p>Left and Right glide the row a player on 07's ease (a held key glides on); Up goes to the pills, where Left and
 * Right (or OK) pick one and Down comes back to the cards, Up again to the tab bar; OK on a card opens the player's page
 * (PlayerScreen), Back closes. Management shows the manager and any staff the source lists, as cards in the same row. The
 * squad is read once as the page opens (Squad.read: FotMob's, else ESPN's roster, ESPN's head and shoulders matched to
 * FotMob's players); a Premier League or La Liga club's players' own league pictures are looked up as their cards come
 * near (LeaguePhotos, as the phone does).
 */
final class TeamScreen extends Screen {
    /** A step of the row (07's ease, a little over the mock-ups' 0.5 s with its overshoot), the cards' fade in when the
     *  pills change who is shown, and the surname's rise. */
    static final long SLIDE_MS = 420, FADE_MS = 260, SWAP_MS = 400;
    /** The pills: Players and Management at the left, then All and each line at the right. */
    static final int P_PLAYERS = 0, P_STAFF = 1, P_ALL = 2, PILLS = 7;
    static final String[] PILL_WORDS = {"PLAYERS", "MANAGEMENT", "ALL", "GOALKEEPERS", "DEFENDERS", "MIDFIELDERS", "FORWARDS"};
    /**
     * The middle card (design px, the mock-ups' .pc): its width and its glass panel's height; a step of the row; the
     * row's middle; the pills' line (under the shell's top 136 px).
     */
    static final float W0 = 340f, H0 = 390f, STEP = 430f, MID = 960f, TOP_Y = 166f;

    final Squad.Club club;
    Squad squad;
    boolean failed;
    /** Management chosen; else the players of line (-1: every line). */
    boolean staff;
    int line = -1;
    /** Who the row shows, the one the remote is on, and whether the remote is up on the pills (and which). */
    final List<Squad.Person> shown = new ArrayList<>();
    int focus;
    boolean onPills;
    int pill = P_ALL;
    /** The club's colour as chosen (ClubLook.pick); 0 until known. */
    int colour;
    /** Whose own pictures the club's players have (LeaguePhotos.league), once the squad is read. */
    int league = LeaguePhotos.NONE;
    private Page page;
    /** Where the row is (a player's index, gliding on 07's ease); the cards' fade in; the surname rising in. */
    final Glide pos = new Glide(SLIDE_MS, STEP).springy(Look07.BOUNCE, Look07.BOUNCE_KICK), fade = new Glide(FADE_MS, 200f),
            swap = new Glide(SWAP_MS, 14f).springy(Look07.BOUNCE, Look07.BOUNCE_KICK);
    /** What the page says where there are no cards (reading, nothing listed). */
    String note = "Reading the squad…";

    TeamScreen(TvActivity tv, Squad.Club club) {
        super(tv);
        this.club = club;
        colour = ClubLook.pick(Squad.colour(club.colour), Squad.colour(club.alt), 0);
        fade.jump(1f);
        swap.jump(1f);
    }

    /** For previews and tests: the page as if this squad had been read. */
    void preview(Squad s) {
        took(s);
    }

    @Override View build() {
        FrameLayout f = new FrameLayout(tv);
        page = new Page();
        f.addView(page, new FrameLayout.LayoutParams(-1, -1));
        Squad had = Squad.kept(club);
        if (had != null) took(had);
        return f;
    }

    @Override void shown() {
        tv.live.stop();
        tv.live.place(null);
        if (squad == null && !failed && !Exec.inline) {
            android.content.Context app = tv.getApplicationContext();
            Exec.io(() -> {
                Squad s = Squad.read(app, club);
                Exec.main(() -> took(s));
            });
        }
        if (page != null) page.invalidate();
    }

    // ---- 07's shell -------------------------------------------------------------------------------------------------

    @Override boolean shell() {
        return true;
    }

    /** Where Back goes: the hub's section, or the game the club was opened from. */
    @Override String backLine() {
        Screen under = tv.below(this);
        if (under instanceof SportsScreen) return ((SportsScreen) under).backWords();
        if (under instanceof GameScreen) return ((GameScreen) under).backWords();
        return "Sports";
    }

    /** Down from the tab bar: back on the pills. */
    @Override void fromTabs() {
        onPills = true;
        pill = chosenPill();
        if (page != null) page.invalidate();
    }

    void took(Squad s) {
        if (s == null || s.players.isEmpty() && s.staff.isEmpty()) {
            failed = squad == null;
            if (failed) note = "The squad isn't available just now";
            if (page != null) page.invalidate();
            return;
        }
        squad = s;
        league = LeaguePhotos.league(club.slug, s.fotmobLeague);
        int c = ClubLook.pick(Squad.colour(club.colour), Squad.colour(club.alt), s.colour);
        if (c != colour || page != null && !page.coloured()) {
            colour = c;
            if (page != null) page.colours();
        }
        if (s.players.isEmpty()) staff = true;
        refilter();
    }

    /** The pill the page's choice is: where Up from the cards lands. */
    int chosenPill() {
        return staff ? P_STAFF : line < 0 ? P_ALL : P_ALL + 1 + line;
    }

    /** A pill picked: Players (the line as it was), Management, or All or a line (its players). */
    void pick(int p) {
        boolean wasStaff = staff;
        int wasLine = line;
        if (p == P_PLAYERS) staff = false;
        else if (p == P_STAFF) staff = true;
        else {
            staff = false;
            line = p == P_ALL ? -1 : p - P_ALL - 1;
        }
        if (staff != wasStaff || line != wasLine) refilter();
    }

    /** The row for the pills chosen, the remote kept on the same person while he is still in it (else the first). */
    void refilter() {
        Squad.Person was = focus >= 0 && focus < shown.size() ? shown.get(focus) : null;
        shown.clear();
        if (squad != null) shown.addAll(squad.shown(staff, line));
        int at = was == null ? -1 : shown.indexOf(was);
        focus = Math.max(0, at);
        pos.jump(focus);
        fade.jump(0f);
        fade.to(1f);
        note = staff ? "No management listed" : "No " + (line < 0 ? "players" : Squad.LINES[line].toLowerCase(Locale.UK)) + " listed";
        if (page != null) {
            page.prepare();
            page.prefetch();
            page.invalidate();
        }
    }

    /** The player's page moved on to another: the row is on him when it comes back. */
    void follow(Squad.Person p) {
        int i = shown.indexOf(p);
        if (i >= 0) {
            focus = i;
            pos.jump(i);
            if (page != null) {
                page.prefetch();
                page.invalidate();
            }
        }
    }

    Squad.Person focused() {
        return focus >= 0 && focus < shown.size() ? shown.get(focus) : null;
    }

    @Override boolean key(int code, KeyEvent e) {
        if (onPills) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (pill > 0) pick(--pill);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (pill < PILLS - 1) pick(++pill);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    onPills = false;
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    // Up past the pills: the remote up to 07's tab bar.
                    if (tv.tabs() != null) {
                        onPills = false;
                        tv.tabs().enter();
                    }
                    break;
                default:
                    return false;
            }
            page.invalidate();
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                int to = Math.max(0, Math.min(shown.size() - 1, focus + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
                if (to != focus) {
                    focus = to;
                    pos.to(to);
                    page.prefetch();
                }
                break;
            }
            case KeyEvent.KEYCODE_DPAD_UP:
                onPills = true;
                pill = chosenPill();
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                break;
            default:
                return false;
        }
        page.invalidate();
        return true;
    }

    @Override void ok() {
        if (onPills) {
            pick(pill);
            onPills = false;
            page.invalidate();
            return;
        }
        Squad.Person p = focused();
        if (p != null) tv.push(new PlayerScreen(tv, this, new ArrayList<>(shown), focus));
    }

    @Override boolean back() {
        if (onPills) {
            onPills = false;
            page.invalidate();
            return true;
        }
        return false;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    /** One card's words and picture, worked out once (when who is shown changes), so a frame makes nothing. */
    private static final class Card {
        Squad.Person person;
        PlayerArt.Pic pic;
        /** The side card's whole name, cut to fit; the middle card's first name; the surname and its size (a share). */
        String name = "", first = "", last = "", label = "";
        float lastSize = 1f;
    }

    /** The page, drawn in design pixels of a 1920 x 1080 screen (Ui.f). */
    final class Page extends View {
        /**
         * By distance from the middle (0, 1, 2, 3 places; the mock-ups' geo()): the card's middle at the left and at the
         * right (the right gap a little narrower, the line's label in it), its width, its panel's top, its opacity and
         * the size its whole name is written at.
         */
        private final float[] leftX = {0f, -450f, -770f, -1330f}, rightX = {0f, 410f, 750f, 1170f}, slotW = {340f, 240f, 200f, 180f},
                slotTop = {330f, 420f, 455f, 470f}, slotAlpha = {1f, 0.62f, 0.38f, 0.38f}, slotName = {20f, 20f, 17f, 15f};
        private final List<Card> cards = new ArrayList<>();
        private final java.util.Map<Squad.Person, PlayerArt.Pic> pics = new java.util.IdentityHashMap<>();
        private final ClubLook.Pills pills = new ClubLook.Pills(PILL_WORDS, 50f, 18f, 22f, 0.08f);
        private final ClubLook.Glass glass = new ClubLook.Glass();
        private final ClubLook.Coin coin = new ClubLook.Coin(46f);
        private final Paint ground = new Paint(), grain = ClubLook.grain(), vignette = new Paint(Paint.DITHER_FLAG),
                bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), keyFill = new Paint(Paint.ANTI_ALIAS_FLAG),
                keyLine = new Paint(Paint.ANTI_ALIAS_FLAG), keyInk = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint firstP = Type.paint(34f, Type.SEMIBOLD, 0xFFFFFFFF), nameP = Type.paint(20f, Type.SEMIBOLD, 0xFFFFFFFF),
                lastP = Type.paint(132f, Type.EXTRABOLD, 0xFFFFFFFF), labelP = Type.paint(17f, Type.BOLD, 0xBFFFFFFF),
                noteP = Type.paint(34f, Type.MEDIUM, 0xE6FFFFFF), keyP = Type.paint(13f, Type.BOLD, 0xFFFFFFFF),
                hintP = Type.paint(19f, Type.MEDIUM, Look07.L2);
        private final RectF r = new RectF(), box = new RectF();
        private final Path tri = new Path();
        private final Runnable redraw = this::invalidate;
        private boolean coloured;
        /** The middle card at full size (screen px): its art, and its panel in its own units. */
        private PlayerArt art;
        /** The surname and line shown, and for whom (they rise in when the remote moves on). */
        private Card swapped;

        Page() {
            super(tv);
            lastP.setLetterSpacing(-0.03f);
            lastP.setShadowLayer(Ui.f(24f), 0, Ui.f(8f), 0x66000028);
            labelP.setLetterSpacing(0.5f);
            firstP.setShadowLayer(Ui.f(6f), 0, Ui.f(2f), 0x40000000);
            nameP.setShadowLayer(Ui.f(6f), 0, Ui.f(2f), 0x40000000);
            keyFill.setColor(0x2EFFFFFF);
            keyLine.setStyle(Paint.Style.STROKE);
            keyLine.setStrokeWidth(Math.max(1f, Ui.f(1f)));
            keyLine.setColor(0x4DFFFFFF);
            keyInk.setColor(0xFFFFFFFF);
            colours();
        }

        boolean coloured() {
            return coloured;
        }

        private float x(float d) {
            return Ui.f(d);
        }

        /** The colours for the club's: the ground and the coins. */
        void colours() {
            coloured = colour != 0;
            if (getWidth() > 0) ClubLook.ground(ground, colour, getWidth(), getHeight());
            coin.colour(colour, x(46f));
            pills.tint = ClubLook.glassTint(colour);
            invalidate();
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            ClubLook.ground(ground, colour, w, h);
            ClubLook.vignette(vignette, w, h);
            float pillsRight = pills.from(x(Look07.MARGIN + 58f + 24f), x(14f), P_PLAYERS, P_STAFF);
            pills.to(Math.max(pillsRight + x(40f), w - x(Look07.MARGIN)), x(8f), P_ALL, PILLS - 1);
            art = new PlayerArt(x(W0), x(W0 * 0.62f), x(H0), x(30f));
            prepare();
            invalidate();
        }

        /** The picture state kept for a person (made the first time). */
        PlayerArt.Pic picOf(Squad.Person p) {
            PlayerArt.Pic pic = pics.get(p);
            if (pic == null) {
                pic = new PlayerArt.Pic(p);
                pics.put(p, pic);
            }
            return pic;
        }

        /** Each card's words, cut and sized to fit, for who is shown now. */
        void prepare() {
            cards.clear();
            float side = x(240f - 24f), big = x(1700f);
            nameP.setTextSize(x(20f));
            for (Squad.Person p : shown) {
                Card c = new Card();
                c.person = p;
                c.pic = picOf(p);
                c.name = Ui.fit(p.first.isEmpty() ? p.last : p.first + " " + p.last, nameP, side);
                c.first = Ui.fit(p.first.isEmpty() ? p.last : p.first, firstP, x(W0 - 32f));
                c.last = p.last;
                float lw = lastP.measureText(p.last);
                c.lastSize = lw > big ? big / lw : 1f;
                c.label = (p.line == Squad.STAFF ? p.position : Squad.LINES[p.line]).toUpperCase(Locale.UK);
                cards.add(c);
            }
            swapped = null;
        }

        /** The remote moved on (or came back on another): the surname and the line rise in for him. */
        private void swapIn() {
            swap.jump(0f);
            swap.to(1f);
        }

        @Override protected void onDraw(Canvas c) {
            boolean moving = pos.step();
            moving |= fade.step();
            moving |= swap.step();
            int w = getWidth(), h = getHeight();
            c.drawRect(0, 0, w, h, ground);
            c.drawRect(0, 0, w, h, grain);
            c.drawRect(0, 0, w, h, vignette);
            if (!cards.isEmpty() && art != null) {
                float p = pos.value(), k = fade.value();
                // The furthest first, so the nearer cards lie over them.
                int lo = Math.max(0, (int) Math.ceil(p - 3f + 1e-3f)), hi = Math.min(cards.size() - 1, (int) Math.floor(p + 3f - 1e-3f));
                while (lo <= hi) {
                    if (p - lo >= hi - p) {
                        drawCard(c, cards.get(lo), lo - p, k);
                        lo++;
                    } else {
                        drawCard(c, cards.get(hi), hi - p, k);
                        hi--;
                    }
                }
                Card now = cards.get(Math.max(0, Math.min(cards.size() - 1, focus)));
                if (swapped != now) {
                    if (swapped != null && !Exec.inline) swapIn();
                    swapped = now;
                }
                drawNames(c, now, k);
            } else {
                c.drawText(note, w / 2f - noteP.measureText(note) / 2f, x(560f), noteP);
            }
            drawTop(c);
            drawHints(c);
            if (moving) postInvalidateOnAnimation();
        }

        /** The crest and the pills, under the shell's top line. */
        private void drawTop(Canvas c) {
            float cy = x(TOP_Y), size = x(58f);
            Bitmap crest = club.crest.isEmpty() ? null : Pics.get(club.crest, Math.round(size), Math.round(size), Pics.FIT, redraw);
            if (crest != null) c.drawBitmap(crest, x(Look07.MARGIN + 29f) - crest.getWidth() / 2f, cy - crest.getHeight() / 2f, bmp);
            boolean any = squad != null;
            for (int i = 0; i < PILLS; i++) {
                boolean chosen = i == P_PLAYERS ? !staff : i == P_STAFF ? staff : !staff && (i == P_ALL ? line < 0 : line == i - P_ALL - 1);
                float alpha = !any ? 0.5f : i >= P_ALL && staff ? 0.38f : 1f;
                pills.draw(c, i, cy, chosen, onPills && i == pill, alpha);
            }
        }

        /** The hints along the foot: the keys and what they do here. */
        private void drawHints(Canvas c) {
            float cy = x(1035f), hx = x(Look07.MARGIN);
            hx = key(c, hx, cy, 0) + x(8f);
            c.drawText("The squad", hx, Type.centred(hintP, cy), hintP);
            hx += hintP.measureText("The squad") + x(24f);
            hx = key(c, hx, cy, 1) + x(8f);
            c.drawText("Pills", hx, Type.centred(hintP, cy), hintP);
            hx += hintP.measureText("Pills") + x(24f);
            hx = key(c, hx, cy, 2) + x(8f);
            c.drawText("His page", hx, Type.centred(hintP, cy), hintP);
        }

        /** A remote key's chip (07's .k): 0 Left and Right, 1 Up, 2 OK; its right edge. */
        private float key(Canvas c, float left, float cy, int which) {
            float kh = x(30f), kw = which == 0 ? x(46f) : x(38f);
            r.set(left, cy - kh / 2f, left + kw, cy + kh / 2f);
            c.drawRoundRect(r, kh / 2f, kh / 2f, keyFill);
            c.drawRoundRect(r, kh / 2f, kh / 2f, keyLine);
            float t = x(5f);
            if (which == 0) {
                triangle(c, r.centerX() - x(6f), cy, t, -1);
                triangle(c, r.centerX() + x(6f), cy, t, 1);
            } else if (which == 1) {
                tri.reset();
                tri.moveTo(r.centerX() - t, cy + t * 0.6f);
                tri.lineTo(r.centerX() + t, cy + t * 0.6f);
                tri.lineTo(r.centerX(), cy - t * 0.8f);
                tri.close();
                c.drawPath(tri, keyInk);
            } else {
                c.drawText("OK", r.centerX() - keyP.measureText("OK") / 2f, Type.centred(keyP, cy), keyP);
            }
            return r.right;
        }

        private void triangle(Canvas c, float cx, float cy, float t, int dir) {
            tri.reset();
            tri.moveTo(cx - dir * t * 0.6f, cy - t);
            tri.lineTo(cx - dir * t * 0.6f, cy + t);
            tri.lineTo(cx + dir * t * 0.8f, cy);
            tri.close();
            c.drawPath(tri, keyInk);
        }

        /** The surname under the middle card and its line down its side, rising in as the remote moves on. */
        private void drawNames(Canvas c, Card card, float k) {
            float s = Math.max(0f, Math.min(1f, swap.value())), rise = (1f - swap.value()) * 14f;
            int a = Math.round(255 * k * s);
            if (a <= 2) return;
            lastP.setAlpha(a);
            c.save();
            c.translate(x(MID), x(850f + rise));
            c.scale(card.lastSize, card.lastSize);
            c.drawText(card.last, -(lastP.measureText(card.last) - lastP.getLetterSpacing() * lastP.getTextSize()) / 2f, 0, lastP);
            c.restore();
            if (card.label.isEmpty()) return;
            float lx = x(MID + W0 / 2f + 28f), ly = x(360f);
            labelP.setAlpha(Math.round(0xBF * k * s));
            c.save();
            c.rotate(90f, lx, ly);
            c.drawText(card.label, lx, ly, labelP);
            c.restore();
        }

        private float slot(float[] v, float ad) {
            int i = Math.min(2, (int) ad);
            float t = Math.min(1f, ad - i);
            return v[i] + (v[i + 1] - v[i]) * t;
        }

        /**
         * One card at d places from the middle (fractions while gliding), drawn at the middle card's size and scaled:
         * its glass, his picture, his coin and his name (the middle card's first name, a side card's whole name).
         */
        private void drawCard(Canvas c, Card card, float d, float k) {
            float ad = Math.abs(d);
            if (ad >= 3f) return;
            float alpha = slot(slotAlpha, ad) * k;
            if (alpha <= 0.01f) return;
            float cw = slot(slotW, ad), panelTop = slot(slotTop, ad);
            float cx = MID + (d < 0 ? slot(leftX, ad) : slot(rightX, ad));
            float s = cw / W0, f = Math.max(0f, 1f - ad);
            c.save();
            c.translate(x(cx - cw / 2f), x(panelTop - W0 * 0.62f * s));
            c.scale(s, s);
            box.set(0, art.top, art.w, art.top + art.h);
            glass.draw(c, box, art.radius, f, alpha, s);
            picture(c, card, alpha);
            Squad.Person person = card.person;
            if (!person.number.isEmpty()) coin.draw(c, art.w - x(28f), art.top + x(28f), x(46f), person.number, alpha, s, 0f);
            // The name: the whole name at the side, the first name in the middle; one goes before the other comes.
            float small = Math.max(0f, 1f - f / 0.4f), large = Math.max(0f, (f - 0.6f) / 0.4f);
            float foot = art.top + art.h;
            if (small > 0.01f) {
                float size = slot(slotName, Math.max(1f, ad));
                nameP.setTextSize(x(size) / s);
                nameP.setAlpha(Math.round(255 * alpha * small));
                c.drawText(card.name, art.w / 2f - nameP.measureText(card.name) / 2f, foot - x(16f + size * 0.3f) / s, nameP);
            }
            if (large > 0.01f && !card.first.isEmpty()) {
                firstP.setAlpha(Math.round(255 * alpha * large));
                c.drawText(card.first, art.w / 2f - firstP.measureText(card.first) / 2f, foot - x(26f + 34f * 0.26f) / s, firstP);
            }
            c.restore();
        }

        /** His real picture on the card: the best one that loads, else the club's crest and his initials (never drawn). */
        private void picture(Canvas c, Card card, float alpha) {
            PlayerArt.Pic pic = card.pic;
            if (LeaguePhotos.pending(league, card.person)) return;
            String url = pic.url();
            if (url == null) {
                int cs = art.crestSize();
                Bitmap crest = club.crest.isEmpty() ? null : Pics.get(club.crest, cs, cs, Pics.FIT, redraw);
                art.mark(c, crest, pic.initials, alpha);
                return;
            }
            int kind = Squad.kind(url);
            Bitmap b = Pics.get(url, art.width(kind), art.height(kind), Pics.FIT, redraw);
            if (b != null) art.draw(c, pic, b, kind, alpha);
        }

        /**
         * The pictures of the cards just beyond those in sight, read ahead (when the remote moves, not per frame), and
         * their league's own looked up first where the club's league has them.
         */
        void prefetch() {
            for (int i = Math.max(0, focus - 4); i <= Math.min(shown.size() - 1, focus + 4); i++) {
                Squad.Person p = shown.get(i);
                PlayerArt.Pic pic = picOf(p);
                if (LeaguePhotos.pending(league, p)) {
                    LeaguePhotos.lookUp(tv, league, club.fullName.isEmpty() ? club.name : club.fullName, club.name, p, () -> {
                        pic.again();
                        readAhead(pic);
                        invalidate();
                    });
                    continue;
                }
                readAhead(pic);
            }
        }

        /** The picture he will be drawn with, read ahead at the card's size (once the card's size is known). */
        private void readAhead(PlayerArt.Pic pic) {
            String u = pic.url();
            if (u != null && art != null) Pics.prefetch(u, art.width(Squad.kind(u)), art.height(Squad.kind(u)), Pics.FIT);
        }
    }
}

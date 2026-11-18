package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.LogoMatch;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.prefs.ChannelEdits;

import java.util.ArrayList;
import java.util.List;

/**
 * Change logo, from a channel's menu in the TV guide: the new logos that may be the channel's, the best first, each on
 * its tile as the guide draws it, with its name, its country and kind ("United Kingdom  ·  HD") and how sure the match
 * is, then the playlist's own logo; the one shown now ticked. On a card as tall as the long-OK card and at its right
 * edge ([1192,32], 696 wide: wider, for the logos' names and kinds; the panel's colour) over the screen dimmed. Up and
 * Down move, OK keeps one for this channel (ChannelEdits: the best match is kept as "automatic", so a newer list of
 * logos can still change it), Back leaves it as it was.
 */
final class LogoScreen extends Screen {
    /** The card: the long-OK card's top, height and right edge (GuideMenu), wider; its head, rows and tiles (design pixels). */
    static final float CARD_W = 696f, CARD_LEFT = GuideMenu.CARD_X + GuideMenu.CARD_W - CARD_W, CARD_TOP = GuideMenu.CARD_Y,
            CARD_H = GuideMenu.CARD_H;
    static final float HEAD = 96f, ROW = 112f, TILE_W = 120f, TILE_H = 80f;
    static final String PLAYLIST = ChannelEdits.LOGO_PLAYLIST;
    static final String NONE = "No new logo matches this channel's name. A channel renamed is matched by its new name, "
            + "then by the playlist's.";

    private final Channel ch;
    /** The logos found (null while they are looked for). */
    private List<LogoMatch.Found> found;
    private final List<String[]> rows = new ArrayList<>();
    private int focus;
    private float scroll;
    private Card card;

    LogoScreen(TvActivity tv, Channel ch) {
        super(tv);
        this.ch = ch;
    }

    @Override View build() {
        card = new Card(tv);
        return card;
    }

    @Override boolean overlay() {
        return true;
    }

    @Override boolean overVideo() {
        return true;
    }

    @Override void shown() {
        if (found != null) return;
        layOut();
        // Looked for off the main thread: it reads more than one channel's match does.
        Exec.io(() -> {
            List<LogoMatch.Found> f = LogoPack.candidates(ch, 12);
            Exec.main(() -> {
                found = f;
                layOut();
            });
        });
    }

    /** The rows: each logo found (its path), then the playlist's; the remote on the one shown now. */
    private void layOut() {
        rows.clear();
        String now = shownNow();
        int at = -1;
        if (found != null) {
            String auto = LogoPack.auto(ch);
            for (LogoMatch.Found f : found) {
                String sure = f.logo.path.equals(auto) ? "Best match" : f.sure >= LogoMatch.SURE ? "Good match" : "Possible match";
                rows.add(new String[]{f.logo.path, f.logo.label(), f.logo.about() + "  ·  " + sure});
                if (f.logo.path.equals(now)) at = rows.size() - 1;
            }
        }
        rows.add(new String[]{PLAYLIST, "Playlist's logo", ch.logo.isEmpty() ? "The playlist gives none" : "As the playlist gives it"});
        if (PLAYLIST.equals(now)) at = rows.size() - 1;
        focus = Math.max(0, at);
        settle();
        if (card != null) card.invalidate();
    }

    /** What the channel shows now: a new logo's path, or the playlist's. */
    private String shownNow() {
        String choice = ChannelEdits.logo(ch.key);
        if (!choice.isEmpty()) return choice;
        String auto = LogoPack.on() ? LogoPack.auto(ch) : null;
        return auto == null ? PLAYLIST : auto;
    }

    // ---- the remote -------------------------------------------------------------------------------------------------

    @Override boolean key(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_UP && focus > 0) focus--;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN && focus < rows.size() - 1) focus++;
        else return true;
        settle();
        card.invalidate();
        return true;
    }

    @Override void ok() {
        if (focus < 0 || focus >= rows.size()) return;
        choose(rows.get(focus)[0]);
    }

    /** One kept for the channel: the best match as automatic, the playlist's as itself unless it would show anyway. */
    void choose(String path) {
        String auto = LogoPack.auto(ch);
        String keep;
        if (PLAYLIST.equals(path)) keep = LogoPack.on() && auto != null ? PLAYLIST : "";
        else keep = LogoPack.on() && path.equals(auto) ? "" : path;
        ChannelEdits.setLogo(tv, ch.key, keep);
        tv.pop();
    }

    /** The list slides so the remote's row is in sight. */
    private void settle() {
        float room = CARD_H - HEAD - 16f, y0 = focus * ROW, y1 = y0 + ROW, total = rows.size() * ROW;
        scroll = RowList.scrollFor(scroll, y0, y1, room, total);
    }

    // ---- what the tests and previews look at ------------------------------------------------------------------------

    int count() {
        return rows.size();
    }

    int focus() {
        return focus;
    }

    String path(int i) {
        return rows.get(i)[0];
    }

    // ---- the card ---------------------------------------------------------------------------------------------------

    private final class Card extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final TextPaint headP = Type.paint(Type.SMALL, Type.MEDIUM, Palette.ACCENT), nameP = Type.paint(Type.ROW, Type.MEDIUM, Palette.TEXT1);
        private final TextPaint titleP = Type.paint(Type.ROW, Type.REGULAR, Palette.TEXT1), aboutP = Type.paint(Type.SMALL, Type.REGULAR, Palette.TEXT2);
        private final RectF r = new RectF();
        private final Runnable redraw = this::invalidate;
        private StaticLayout none;

        Card(Context c) {
            super(c);
        }

        @Override protected void onDraw(Canvas c) {
            fill.setColor(Palette.SCRIM);
            c.drawRect(0, 0, getWidth(), getHeight(), fill);
            r.set(Ui.f(CARD_LEFT), Ui.f(CARD_TOP), Ui.f(CARD_LEFT + CARD_W), Ui.f(CARD_TOP + CARD_H));
            fill.setColor(Palette.PANEL);
            c.drawRoundRect(r, Ui.f(GuideMenu.RADIUS), Ui.f(GuideMenu.RADIUS), fill);
            float tx = CARD_LEFT + 40f;
            headP.setColor(Palette.ACCENT);
            c.drawText("Change logo", Ui.f(tx), Ui.f(CARD_TOP + 44f), headP);
            nameP.setColor(Palette.TEXT1);
            c.drawText(Ui.fit(ChannelEdits.name(ch), nameP, Ui.f(CARD_W - 80f)), Ui.f(tx), Ui.f(CARD_TOP + 80f), nameP);
            c.save();
            c.clipRect(Ui.f(CARD_LEFT), Ui.f(CARD_TOP + HEAD), Ui.f(CARD_LEFT + CARD_W), Ui.f(CARD_TOP + CARD_H - 8f));
            float top = CARD_TOP + HEAD + 8f - scroll;
            for (int i = 0; i < rows.size(); i++) {
                float y = top + i * ROW;
                if (y > CARD_TOP + CARD_H || y + ROW < CARD_TOP + HEAD) continue;
                drawRow(c, rows.get(i), y, i == focus);
            }
            if (found == null) {
                aboutP.setColor(Palette.TEXT2);
                c.drawText("Looking for logos…", Ui.f(tx), Ui.f(top + rows.size() * ROW + 40f), aboutP);
            } else if (found.isEmpty()) {
                aboutP.setColor(Palette.TEXT2);
                if (none == null) none = Ui.layout(NONE, aboutP, Ui.px(CARD_W - 80f), 3);
                Ui.draw(c, none, Ui.f(tx), Ui.f(top + rows.size() * ROW + 12f));
            }
            c.restore();
        }

        private void drawRow(Canvas c, String[] row, float y, boolean lit) {
            if (lit) {
                r.set(Ui.f(CARD_LEFT + 16f), Ui.f(y), Ui.f(CARD_LEFT + CARD_W - 16f), Ui.f(y + ROW - 8f));
                Focus.pill(c, r);
            }
            float tileX = CARD_LEFT + 32f, tileY = y + (ROW - 8f - TILE_H) / 2f;
            String url = PLAYLIST.equals(row[0]) ? ch.logo : LogoPack.urlOf(row[0]);
            Bitmap logo = url.isEmpty() ? null : Pics.get(url, Ui.px(TILE_W), Ui.px(TILE_H), Pics.logoMode(), redraw);
            if (logo != null) {
                c.drawBitmap(logo, Ui.f(tileX) + (Ui.f(TILE_W) - logo.getWidth()) / 2f, Ui.f(tileY) + (Ui.f(TILE_H) - logo.getHeight()) / 2f, bmp);
            } else {
                r.set(Ui.f(tileX), Ui.f(tileY), Ui.f(tileX + TILE_W), Ui.f(tileY + TILE_H));
                fill.setColor(Pics.tiles ? 0xFF000000 : Palette.SELECTED);
                c.drawRoundRect(r, Ui.f(8f), Ui.f(8f), fill);
                if (url.isEmpty() || Pics.failed(url)) RoundButton.glyph(c, Dark.TV, r.centerX(), r.centerY(), Ui.f(44f), Palette.TEXT3);
            }
            float textX = tileX + TILE_W + 24f, room = CARD_LEFT + CARD_W - 32f - textX - (row[0].equals(shownNow()) ? 44f : 0f);
            titleP.setColor(Palette.TEXT1);
            float cy = y + (ROW - 8f) / 2f;
            c.drawText(Ui.fit(row[1], titleP, Ui.f(room)), Ui.f(textX), Ui.f(cy - 6f), titleP);
            aboutP.setColor(lit ? Palette.TEXT1 : Palette.TEXT2);
            c.drawText(Ui.fit(row[2], aboutP, Ui.f(room)), Ui.f(textX), Ui.f(cy + 26f), aboutP);
            if (row[0].equals(shownNow())) RoundButton.glyph(c, Dark.CHECK, Ui.f(CARD_LEFT + CARD_W - 52f), Ui.f(cy), Ui.f(32f), Palette.ACCENT_LIGHT);
        }
    }
}

package com.gathin.homefieldtv;

import static org.junit.Assume.assumeTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import com.gathin.broadcast.CoverLooks;
import com.gathin.broadcast.LogoExtras;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.Library;
import com.gathin.homefieldtv.engine.MadeUpChannels;
import com.gathin.homefieldtv.sports.BoardClock;
import com.gathin.homefieldtv.sports.Game;
import com.gathin.homefieldtv.sports.InStep;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pictures of Homefield TV's scoreboard and logo as the launcher's over TiviMate, for a look before a real TV: the plain
 * full-screen picture with the board and the cover, another game's goal coming out of the board (frame by frame too, for
 * a short film), the same moments as the launcher draws them over TiviMate (the same board and drop code, the launcher's
 * Glass cover), the OK overlay and the guide with no board, Event with its extras, and every logo choice over a
 * stand-in picture. Saved in the folder PREVIEW_OUT names; skipped unless it is set; never committed.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class BoardLikeTiviMatePreviewTest {
    private File out;

    private static void idleFor(long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void press(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idleFor(600);
    }

    /** The screen as it is, at 1920 x 1080 (or scaled), saved as name.png. */
    private Bitmap shot(TvActivity a, String name, float scale) throws Exception {
        View root = a.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1920, 1080);
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        Bitmap s = scale == 1f ? b : Bitmap.createScaledBitmap(b, Math.round(1920 * scale), Math.round(1080 * scale), true);
        save(s, name);
        return s;
    }

    private void save(Bitmap b, String name) throws Exception {
        File f = new File(out, name + ".png");
        //noinspection ResultOfMethodCallIgnored
        f.getParentFile().mkdirs();
        try (FileOutputStream o = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
    }

    /**
     * A stand-in for a match on the channel: a pitch in the sun, a few figures, and the broadcaster's own scoreboard top left
     * and its logo top right (made up, no real broadcaster's), for the board and the cover to be seen over.
     */
    static Bitmap picture(int w, int h, String channel) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFF1F5E27, 0xFF3F9446, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        for (int i = 0; i < 10; i += 2) {
            p.setColor(0x14FFFFFF);
            c.drawRect(i * w / 10f, 0, (i + 1) * w / 10f, h, p);
        }
        // The stands across the top, the touchline and the centre circle.
        p.setShader(new LinearGradient(0, 0, 0, h * 0.22f, 0xFF20242E, 0xFF3A3F4C, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.22f, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(h * 0.006f);
        p.setColor(0xCCFFFFFF);
        c.drawLine(0, h * 0.24f, w, h * 0.24f, p);
        c.drawCircle(w * 0.5f, h * 0.68f, h * 0.16f, p);
        c.drawLine(w * 0.5f, h * 0.24f, w * 0.5f, h, p);
        p.setStyle(Paint.Style.FILL);
        // A few players.
        int[] kits = {0xFF0F8B8D, 0xFF7A1F3D};
        float[][] at = {{0.32f, 0.52f}, {0.41f, 0.7f}, {0.58f, 0.6f}, {0.66f, 0.82f}, {0.24f, 0.76f}, {0.73f, 0.47f}};
        for (int i = 0; i < at.length; i++) {
            float x = at[i][0] * w, y = at[i][1] * h, s = h * 0.05f;
            p.setColor(kits[i % 2]);
            c.drawRoundRect(new RectF(x - s * 0.35f, y - s, x + s * 0.35f, y), s * 0.2f, s * 0.2f, p);
            p.setColor(0xFFE8C9A8);
            c.drawCircle(x, y - s * 1.2f, s * 0.22f, p);
        }
        p.setColor(0xFFFFFFFF);
        c.drawCircle(w * 0.47f, h * 0.73f, h * 0.012f, p);
        // The broadcaster's own scoreboard (top left) and logo (top right): what the board and the cover go over.
        p.setColor(0xE6101420);
        c.drawRoundRect(new RectF(w * 0.045f, h * 0.045f, w * 0.27f, h * 0.095f), 8, 8, p);
        p.setColor(0xFFFFFFFF);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(h * 0.032f);
        c.drawText("NOR 1-0 RIV  67:41", w * 0.055f, h * 0.083f, p);
        p.setColor(0xCC1A2A7A);
        c.drawRoundRect(new RectF(w * 0.86f, h * 0.045f, w * 0.955f, h * 0.11f), 10, 10, p);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(h * 0.026f);
        c.drawText(channel, w * 0.868f, h * 0.088f, p);
        return b;
    }

    private static BoardLayer board(LiveScreen s) {
        View v = ((ViewGroup) s.view()).getChildAt(0);
        return (BoardLayer) v;
    }

    @Test public void previews() throws Exception {
        String dir = System.getProperty("preview.out", "");
        assumeTrue(!dir.isEmpty());
        out = new File(dir);
        //noinspection ResultOfMethodCallIgnored
        out.mkdirs();
        Exec.inline = true;
        Context app = RuntimeEnvironment.getApplication();
        for (String p : new String[]{"scoreboard", "in_step", "logo_extras"}) app.getSharedPreferences(p, Context.MODE_PRIVATE).edit().clear().commit();
        AlertSwitches.reset();
        AlertSwitches.launcher = (c, q) -> null;
        LogoHere.reset();
        LogoHere.launcher = (c, q) -> null;
        List<Channel> ch = MadeUpChannels.load(app);
        List<String> recent = new ArrayList<>();
        for (Channel c : ch) recent.add(c.key);
        Library.get(app).setPicks(new ArrayList<>(), recent);
        LiveTv.previewStill = picture(1920, 1080, "SPORTS ONE");
        TvActivity a = Robolectric.buildActivity(TvActivity.class).setup().get();
        idleFor(600);
        a.watch(ch.get(2));
        idleFor(1200);
        LiveScreen live = (LiveScreen) a.top();
        press(a, KeyEvent.KEYCODE_BACK);
        BoardLayer b = board(live);
        InStep.setDelay(app, ch.get(2).key, 0);
        long now = System.currentTimeMillis();
        b.found(BoardLikeTiviMateTest.match("preview-1"),
                Collections.singletonList(new BoardClock.Reading(now - 3_000L, 1, 0, Game.LIVE, false, 67 * 60, "68'")));
        idleFor(1500);

        // 1. Full screen, live: the board top left over the broadcaster's, the cover top right (the channel's own logo).
        shot(a, "01-homefield-full-screen-board-and-logo", 1f);
        BoardPrefs.setLogoLook(app, "GLASS");
        idleFor(1100);
        shot(a, "01b-homefield-full-screen-glass-logo", 1f);

        // 2. Another game's goal, handed over by the launcher, out of the board: a short film of it, then its still.
        Bundle goal = BoardLikeTiviMateTest.otherGoal("preview-other", "2–1", "Alex Player 67'");
        BoardInbox.offer(goal);
        File frames = new File(out, "frames-other-goal");
        //noinspection ResultOfMethodCallIgnored
        frames.mkdirs();
        for (int i = 0; i < 70; i++) {
            shot(a, "frames-other-goal/f" + String.format(java.util.Locale.ROOT, "%03d", i), 0.5f);
            idleFor(40);
        }
        shot(a, "02-other-game-goal-from-the-board", 1f);
        idleFor(9_000);

        // 3. The same moments as the launcher draws them over TiviMate (its standard places, its board and drop code, its
        // default Glass cover), over a TiviMate stand-in (the channel full screen, nothing of TiviMate's over it).
        BoardInbox.offer(BoardLikeTiviMateTest.otherGoal("preview-other-2", "2–2", "Ben Example 71'"));
        idleFor(1200);
        shot(a, "03-launcher-over-tivimate-standin-other-goal", 1f);
        idleFor(9_000);

        // 4. The OK overlay over the picture: no board, no cover; and the guide with the channel in its preview: none.
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        shot(a, "04-ok-overlay-no-board", 1f);
        press(a, KeyEvent.KEYCODE_BACK);
        press(a, KeyEvent.KEYCODE_INFO);
        shot(a, "04b-info-card-no-board", 1f);
        press(a, KeyEvent.KEYCODE_BACK);

        // 5. Event with its extras: the picture and sound marks, Up next to the left of its name's box, the prayer pill.
        // (Event names a sports broadcaster's channel: one named as theirs are, for the picture.)
        BoardLayer.previewChannelName = "UK: Sky Sports Main Event UHD HDR10+ ATMOS";
        BoardPrefs.setLogoLook(app, "EVENT_NAVY");
        idleFor(1100);
        LogoExtras.showNow("Match of the Day", "22:30", System.currentTimeMillis() + 4 * 60_000L, "Maghrib in 5 min", "17:02");
        idleFor(700);
        shot(a, "05-event-logo-with-up-next-and-prayer", 1f);
        BoardLayer.previewChannelName = null;

        // 6. The hold-OK menu on the logo's look: the cover as chosen, the looks either side.
        BoardPrefs.setLogoLook(app, "BROADCAST");
        idleFor(1100);
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + TvActivity.LONG_MS + 100, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1));
        a.dispatchKeyEvent(new KeyEvent(t, t + TvActivity.LONG_MS + 120, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0));
        idleFor(600);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        shot(a, "06-hold-ok-menu-logo-look", 1f);
        press(a, KeyEvent.KEYCODE_BACK);

        // 7. The guide: the live screen away, no board.
        a.toGuide();
        idleFor(1500);
        shot(a, "07-guide-no-board", 1f);

        grid(app);
    }

    /** Every logo choice over the stand-in picture, a cell each, with its name under it. */
    private void grid(Context app) throws Exception {
        List<String[]> cells = new ArrayList<>();
        // The looks, the channel's own logo first (Homefield TV's as it came, drawn as the cover draws it: see 01).
        for (CoverLooks.Look l : CoverLooks.Look.values()) cells.add(new String[]{l.name(), l.label, "" + CoverLooks.PLATE, "UHD"});
        // The looks drawn whole on each background.
        for (CoverLooks.Look l : new CoverLooks.Look[]{CoverLooks.Look.EVENT_NAVY, CoverLooks.Look.SPORTS, CoverLooks.Look.STYLES,
                CoverLooks.Look.FLOODLIGHT}) {
            for (int g : CoverLooks.GROUNDS) if (g != CoverLooks.PLATE) cells.add(new String[]{l.name(), l.label + " · " + CoverLooks.groundWords(g), "" + g, "UHD"});
        }
        // The picture tags on Broadcast.
        for (String tag : CoverLooks.TAGS) {
            String shown = CoverLooks.tagShown(tag, "Sports One UHD");
            cells.add(new String[]{"BROADCAST", "Picture tag: " + CoverLooks.tagWords(tag, "Sports One UHD"), "" + CoverLooks.PLATE, shown});
        }
        int cols = 6, cw = 360, chh = 190, rows = (cells.size() + 5 + cols - 1) / cols;
        Bitmap sheet = Bitmap.createBitmap(cols * cw, rows * chh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(sheet);
        c.drawColor(0xFF0B0E16);
        Bitmap pic = picture(cw, chh - 30, "SPORTS ONE");
        CoverLooks art = new CoverLooks(app, BoardPrefs.outfit(app, 700), BoardPrefs.outfit(app, 800));
        Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        label.setColor(0xFFFFFFFF);
        label.setTextSize(15f);
        label.setTypeface(BoardPrefs.outfit(app, 600));
        int i = 0;
        for (String[] cell : cells) {
            int x = (i % cols) * cw, y = (i / cols) * chh;
            c.drawBitmap(pic, x, y, null);
            CoverLooks.Parts p = new CoverLooks.Parts();
            p.tag = cell[3];
            p.live = true;
            p.inset = 2;
            p.sub = "main event";
            p.ground = Integer.parseInt(cell[2]);
            p.marks = LogoExtras.marks("Sports One UHD HDR10+ ATMOS");
            p.board = new CoverLooks.Board(0xF70B1033, 0xFF2443B4, 0xFFE6E9F2, false);
            int save = c.save();
            c.translate(x + 40, y + 22);
            art.draw(c, CoverLooks.Look.valueOf(cell[0]), cw - 80, 112, p);
            c.restoreToCount(save);
            c.drawText(cell[1], x + 8, y + chh - 10, label);
            i++;
        }
        // The styles, five of the 54 as samples, and Event turned over to its clock.
        for (int s : new int[]{0, 13, 27, 40, 53}) {
            int x = (i % cols) * cw, y = (i / cols) * chh;
            c.drawBitmap(pic, x, y, null);
            CoverLooks.Parts p = new CoverLooks.Parts();
            p.tag = "HD";
            p.style = s;
            p.inset = 2;
            int save = c.save();
            c.translate(x + 40, y + 22);
            art.draw(c, CoverLooks.Look.STYLES, cw - 80, 112, p);
            c.restoreToCount(save);
            c.drawText("Style " + (s + 1) + " of 54 · " + com.gathin.broadcast.BrandStyles.name(s), x + 8, y + chh - 10, label);
            i++;
        }
        save(sheet, "08-every-logo-choice-grid");
        // Every one of the 54 styles on a sheet of its own.
        int sc = 6, srows = (com.gathin.broadcast.BrandStyles.COUNT + sc - 1) / sc;
        Bitmap styles = Bitmap.createBitmap(sc * cw, srows * chh, Bitmap.Config.ARGB_8888);
        Canvas sv = new Canvas(styles);
        sv.drawColor(0xFF0B0E16);
        for (int s = 0; s < com.gathin.broadcast.BrandStyles.COUNT; s++) {
            int x = (s % sc) * cw, y = (s / sc) * chh;
            sv.drawBitmap(pic, x, y, null);
            CoverLooks.Parts p = new CoverLooks.Parts();
            p.tag = "HD";
            p.style = s;
            p.inset = 2;
            int save = sv.save();
            sv.translate(x + 40, y + 22);
            art.draw(sv, CoverLooks.Look.STYLES, cw - 80, 112, p);
            sv.restoreToCount(save);
            sv.drawText((s + 1) + " · " + com.gathin.broadcast.BrandStyles.name(s), x + 8, y + chh - 10, label);
        }
        save(styles, "09-all-54-styles");
    }
}

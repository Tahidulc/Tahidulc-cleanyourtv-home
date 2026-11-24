package com.gathin.homefieldtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;

import com.gathin.homefieldtv.prefs.PlayerPrefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The player's menu, over the picture (the MENU key or the remote's settings key, or a long OK when Settings → Remote
 * say so): the picture's shape (each channel remembers its own; films have one), the sound's delay against the picture,
 * and which sound track plays. Up and Down choose a line, Left and Right change it at once; OK or Back close it, and it
 * goes by itself after a few seconds untouched.
 */
final class PlayerMenu extends View {
    /** What the menu changes: the player, and the picture's shape where it is kept. */
    interface Target {
        Player player();

        int aspect();

        void aspect(int mode);
    }

    private static final long IDLE_MS = 9000;
    private static final int PICTURE = 0, DELAY = 1, TRACK = 2, SUBS = 3, ROWS = 4;
    private static final String[] LABELS = {"Picture", "Sound delay", "Sound", "Subtitles"};
    /** The sound with no track chosen: the one the copy itself plays first. */
    static final String AS_IT_COMES = "The film's sound, as it comes";
    static final String NO_SUBS = "This copy has no subtitles of its own";

    private final Target target;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable idle = this::close;
    private final Paint panel = Dark.fill(Dark.alpha(Dark.PANEL, 0xF2)), hi = Dark.fill(Dark.CARD_HI);
    private final Paint ring = Dark.line(Dark.alpha(Dark.RING, 0xE6), 3f), edge = Dark.line(0x26FFFFFF, 1.5f);
    private final TextPaint title = Dark.text(34, 700, Dark.TEXT), label = Dark.text(28, 600, Dark.SOFT);
    private final TextPaint value = Dark.text(28, 600, Dark.TEXT), hint = Dark.text(22, 500, Dark.MUTED), arrow = Dark.text(30, 700, Dark.ACCENT);
    private final RectF r = new RectF();
    private boolean open;
    private int row;

    PlayerMenu(Context c, Target target) {
        super(c);
        this.target = target;
        setVisibility(GONE);
        setFocusable(false);
    }

    boolean isOpen() {
        return open;
    }

    void open() {
        open = true;
        row = PICTURE;
        setVisibility(VISIBLE);
        setAlpha(1f);
        poke();
    }

    void close() {
        h.removeCallbacks(idle);
        if (!open) return;
        open = false;
        setVisibility(GONE);
    }

    private void poke() {
        h.removeCallbacks(idle);
        h.postDelayed(idle, IDLE_MS);
        invalidate();
    }

    /** A key from an activity (the film player): the D-pad changes it; OK, Back, MENU close it. Every key used while open. */
    boolean key(KeyEvent e) {
        if (!open) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int code = e.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_MENU
                || code == KeyEvent.KEYCODE_SETTINGS) {
            if (e.getRepeatCount() == 0) close();
            return true;
        }
        key(code);
        return true;
    }

    /** A D-pad key from a screen (the live player): true when used (OK and Back come as close()). */
    boolean key(int code) {
        if (!open) return false;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                row = (row + ROWS - 1) % ROWS;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                row = (row + 1) % ROWS;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                change(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                change(1);
                break;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
                close();
                return true;
            default:
                return true;
        }
        poke();
        return true;
    }

    private void change(int dir) {
        Context c = getContext();
        switch (row) {
            case PICTURE: {
                int n = PlayerPrefs.ASPECT_NAMES.length;
                target.aspect(((target.aspect() + dir) % n + n) % n);
                break;
            }
            case DELAY:
                PlayerPrefs.setDelayMs(c, PlayerPrefs.delayMs(c) + dir * PlayerPrefs.DELAY_STEP);
                break;
            case SUBS:
                stepSubs(dir);
                break;
            default:
                stepTrack(dir);
        }
    }

    // ---- the sound tracks -----------------------------------------------------------------------------------------

    /** The sound tracks the stream has, as groups the player can be told to play. */
    private List<Tracks.Group> audio() {
        List<Tracks.Group> out = new ArrayList<>();
        Player p = target.player();
        if (p == null) return out;
        for (Tracks.Group g : p.getCurrentTracks().getGroups()) {
            if (g.getType() == C.TRACK_TYPE_AUDIO && g.isSupported()) out.add(g);
        }
        return out;
    }

    private int playing(List<Tracks.Group> groups) {
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).isSelected()) return i;
        return -1;
    }

    /**
     * The sound stepped through: the film's sound as it comes (no track chosen: the copy's own first), then each track. A
     * copy with one track has nothing to step.
     */
    private void stepTrack(int dir) {
        Player p = target.player();
        List<Tracks.Group> groups = audio();
        if (p == null || groups.size() < 2) return;
        int at = chosenAudio(p) ? playing(groups) + 1 : 0, n = groups.size() + 1;
        int next = ((at + dir) % n + n) % n;
        androidx.media3.common.TrackSelectionParameters.Builder b = p.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO);
        if (next > 0) b.setOverrideForType(new TrackSelectionOverride(groups.get(next - 1).getMediaTrackGroup(), 0));
        p.setTrackSelectionParameters(b.build());
    }

    /** Whether a sound track was chosen here (not the copy's own first). */
    private static boolean chosenAudio(Player p) {
        for (TrackSelectionOverride o : p.getTrackSelectionParameters().overrides.values()) {
            if (o.getType() == C.TRACK_TYPE_AUDIO) return true;
        }
        return false;
    }

    /** The copy's own subtitles: Off, then each; none to step through for a copy with none. */
    private List<Tracks.Group> subs() {
        List<Tracks.Group> out = new ArrayList<>();
        Player p = target.player();
        if (p == null) return out;
        for (Tracks.Group g : p.getCurrentTracks().getGroups()) {
            if (g.getType() == C.TRACK_TYPE_TEXT && g.isSupported()) out.add(g);
        }
        return out;
    }

    private void stepSubs(int dir) {
        Player p = target.player();
        List<Tracks.Group> groups = subs();
        if (p == null || groups.isEmpty()) return;
        int at = p.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) ? 0 : playing(groups) + 1;
        int n = groups.size() + 1, next = ((at + dir) % n + n) % n;
        androidx.media3.common.TrackSelectionParameters.Builder b = p.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT);
        if (next == 0) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true);
        } else {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(new TrackSelectionOverride(groups.get(next - 1).getMediaTrackGroup(), 0));
        }
        p.setTrackSelectionParameters(b.build());
    }

    private String subsValue() {
        Player p = target.player();
        List<Tracks.Group> groups = subs();
        if (p == null || groups.isEmpty()) return NO_SUBS;
        int at = playing(groups);
        if (at < 0 || p.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) return "Off";
        Format f = groups.get(at).getTrackFormat(0);
        String lang = f.label != null && !f.label.isEmpty() ? f.label
                : f.language != null && !f.language.isEmpty() && !"und".equals(f.language)
                ? new Locale(f.language).getDisplayLanguage(Locale.UK) : "Subtitles " + (at + 1);
        return groups.size() > 1 ? lang + "  (" + (at + 1) + " of " + groups.size() + ")" : lang;
    }

    /** Pure: a sound track in words ("English · 5.1", "Track 2 · Stereo"). */
    static String trackWords(Format f, int index) {
        String lang = f.label != null && !f.label.isEmpty() ? f.label
                : f.language != null && !f.language.isEmpty() && !"und".equals(f.language)
                ? new Locale(f.language).getDisplayLanguage(Locale.UK) : "Track " + (index + 1);
        String ch = f.channelCount >= 8 ? "7.1" : f.channelCount >= 6 ? "5.1" : f.channelCount == 2 ? "Stereo"
                : f.channelCount == 1 ? "Mono" : "";
        return ch.isEmpty() ? lang : lang + " · " + ch;
    }

    private String trackValue() {
        List<Tracks.Group> groups = audio();
        if (groups.isEmpty()) return "None";
        Player p = target.player();
        if (groups.size() > 1 && p != null && !chosenAudio(p)) return AS_IT_COMES;
        int at = Math.max(0, playing(groups));
        String w = trackWords(groups.get(at).getTrackFormat(0), at);
        return groups.size() > 1 ? w + "  (" + (at + 1) + " of " + groups.size() + ")" : w;
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        if (!open) return;
        Context ctx = getContext();
        float x = Ui.f(1180), y = Ui.f(560), w = Ui.f(680), lineH = Ui.f(92);
        r.set(x, y, x + w, y + Ui.f(104) + ROWS * lineH + Ui.f(70));
        c.drawRoundRect(r, Ui.f(26), Ui.f(26), panel);
        c.drawRoundRect(r, Ui.f(26), Ui.f(26), edge);
        c.drawText("Picture and sound", x + Ui.f(36), y + Ui.f(64), title);
        String[] values = {PlayerPrefs.ASPECT_NAMES[target.aspect()], PlayerPrefs.delayWords(PlayerPrefs.delayMs(ctx)), trackValue(),
                subsValue()};
        float ly = y + Ui.f(100);
        for (int i = 0; i < ROWS; i++) {
            r.set(x + Ui.f(18), ly, x + w - Ui.f(18), ly + lineH - Ui.f(12));
            if (i == row) {
                c.drawRoundRect(r, Ui.f(18), Ui.f(18), hi);
                c.drawRoundRect(r, Ui.f(18), Ui.f(18), ring);
            }
            float base = ly + (lineH - Ui.f(12)) / 2f + Ui.f(10);
            c.drawText(LABELS[i], x + Ui.f(40), base, label);
            String v = Ui.fit(values[i], value, Ui.f(i == SUBS || i == TRACK ? 420 : 330));
            float vw = value.measureText(v);
            float right = x + w - Ui.f(64);
            c.drawText(v, right - vw, base, value);
            if (i == row) {
                c.drawText("‹", right - vw - Ui.f(30), base, arrow);
                c.drawText("›", right + Ui.f(14), base, arrow);
            }
            ly += lineH;
        }
        c.drawText("Left and Right change it  ·  OK closes", x + Ui.f(40), ly + Ui.f(34), hint);
    }
}

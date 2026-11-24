package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Events;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.LiveClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The full screen's newer parts, kept apart from PlayerActivity: the rewind's replay bar (back and on 10 s, the bar to
 * drag along what was kept, Back to live), the line saying an alert waits for the stream, the banner for a goal in
 * another game (Switch), the touch lock, the scores box in the small window, the cover while the channel plays on a TV
 * (Continue on phone), and their options: the picture's quality, the source, alerts matching the stream, the rewind,
 * night sound, the touch lock and Watch on TV.
 */
final class PlayerExtras implements LiveAlerts.Listener {
    /** What the full screen tells these parts. */
    interface Host {
        Channel current();

        /** A channel played here (from the banner, Continue on phone). */
        void play(Channel ch);

        /** The game the channel shows now, from its tile (null when none). */
        Game game();

        void closeSheet();

        /** Tests: the info tile in this style now (the guide panel for PANEL), the style chosen kept. */
        default void testTile(TileKind.Style style) {}
    }

    private final Activity a;
    private final FrameLayout root;
    private final Playback pb;
    private final Host host;
    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout replayBar, banner, pipBug, lockBox;
    private TextView replayBadge, heldLine, bannerText, pipText, ripple;
    private SeekBar seek;
    private TextView playPause;
    private FrameLayout lockCover;
    private boolean locked, dragging, inPip;
    private Events.Alert bannerAlert;
    private final Runnable everySecond = new Runnable() {
        @Override public void run() {
            h.removeCallbacks(this);
            refreshReplay();
            refreshHeld();
            refreshPip();
            h.postDelayed(this, 1000);
        }
    };
    private final Runnable hideBanner = () -> fadeOut(banner);
    private final Runnable hideRipple = () -> fadeOut(ripple);

    PlayerExtras(Activity a, FrameLayout root, Playback pb, Host host) {
        this.a = a;
        this.root = root;
        this.pb = pb;
        this.host = host;
        build();
    }

    void start() {
        LiveAlerts.addListener(this);
        h.post(everySecond);
        if (overlay != null) overlay.start();
    }

    void stop() {
        LiveAlerts.removeListener(this);
        h.removeCallbacksAndMessages(null);
        if (overlay != null) overlay.stop();
    }

    /** The scoreboard over the picture, with its alerts (ScoreOverlay). */
    private ScoreOverlay overlay;

    /** The scoreboard over the picture (null before it is made). */
    ScoreOverlay overlay() {
        return overlay;
    }

    void overlay(ScoreOverlay o) {
        overlay = o;
    }

    /** The insets of the screen's cutout and bars, for the parts along its edges. */
    void inset(int left, int topInset, int right, int bottom) {
        replayBar.setPadding(Ui.dp(a, 16) + left, Ui.dp(a, 10), Ui.dp(a, 16) + right, Ui.dp(a, 12) + bottom);
        ((FrameLayout.LayoutParams) heldLine.getLayoutParams()).topMargin = Ui.dp(a, 64) + topInset;
        ((FrameLayout.LayoutParams) banner.getLayoutParams()).topMargin = Ui.dp(a, 12) + topInset;
        ((FrameLayout.LayoutParams) lockBox.getLayoutParams()).leftMargin = Ui.dp(a, 18) + left;
        ((FrameLayout.LayoutParams) lockBox.getLayoutParams()).bottomMargin = Ui.dp(a, 18) + bottom;
        if (overlay != null) overlay.inset(left, topInset);
    }

    // ---- building ---------------------------------------------------------------------------------------------

    private void build() {
        Context c = a;
        // The replay bar, along the foot.
        replayBar = new LinearLayout(c);
        replayBar.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable shade = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xE6000000, 0x00000000});
        replayBar.setBackground(shade);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        replayBadge = Ui.text(c, "", 12, Ui.NAVY_INK, true);
        replayBadge.setBackground(Ui.round(c, 0xFFFFC93C, 12));
        replayBadge.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
        row.addView(replayBadge);
        row.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView live = button(c, a.getString(R.string.live3_back_to_live), true);
        live.setOnClickListener(v -> pb.backToLive());
        row.addView(live);
        replayBar.addView(row);
        seek = new SeekBar(c);
        seek.setProgressTintList(ColorStateList.valueOf(0xFF8FC3FF));
        seek.setThumbTintList(ColorStateList.valueOf(Ui.TEXT));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {}

            @Override public void onStartTrackingTouch(SeekBar s) {
                dragging = true;
            }

            @Override public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                long from = pb.rewindFrom();
                if (from > 0) pb.replay(from + s.getProgress() * 1000L);
            }
        });
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 40));
        replayBar.addView(seek, sp);
        LinearLayout keys = new LinearLayout(c);
        keys.setGravity(Gravity.CENTER);
        TextView back = round(c, "−10");
        back.setContentDescription("Back 10 seconds");
        back.setOnClickListener(v -> jump(-10_000));
        playPause = round(c, "❚❚");
        playPause.setContentDescription("Pause");
        playPause.setOnClickListener(v -> {
            if (pb.player() == null) return;
            if (pb.player().getPlayWhenReady()) pb.player().pause();
            else pb.player().play();
            refreshReplay();
        });
        TextView on = round(c, "+10");
        on.setContentDescription("On 10 seconds");
        on.setOnClickListener(v -> jump(10_000));
        for (TextView t : new TextView[]{back, playPause, on}) {
            LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52));
            kp.leftMargin = kp.rightMargin = Ui.dp(c, 10);
            keys.addView(t, kp);
        }
        replayBar.addView(keys);
        replayBar.setVisibility(View.GONE);
        replayBar.setClickable(true);
        root.addView(replayBar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        // The double tap's sign, either side.
        ripple = Ui.text(c, "", 15, Ui.TEXT, true);
        ripple.setGravity(Gravity.CENTER);
        ripple.setBackground(Ui.round(c, 0x33FFFFFF, 60));
        ripple.setVisibility(View.GONE);
        root.addView(ripple, new FrameLayout.LayoutParams(Ui.dp(c, 120), Ui.dp(c, 120), Gravity.CENTER_VERTICAL | Gravity.START));

        // An alert waiting for the stream.
        heldLine = Ui.text(c, "", 13, Ui.TEXT, true);
        heldLine.setBackground(Ui.round(c, 0xD10B0C21, 18));
        heldLine.setPadding(Ui.dp(c, 12), Ui.dp(c, 7), Ui.dp(c, 12), Ui.dp(c, 7));
        heldLine.setVisibility(View.GONE);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hp.topMargin = Ui.dp(c, 64);
        root.addView(heldLine, hp);

        // A goal elsewhere.
        banner = new LinearLayout(c);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setBackground(Ui.round(c, 0xEB0B0C21, 28));
        banner.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6));
        TextView goal = Ui.text(c, "GOAL", 12, Ui.TEXT, true);
        goal.setBackground(Ui.round(c, 0xFFE0213A, 14));
        goal.setPadding(Ui.dp(c, 9), Ui.dp(c, 4), Ui.dp(c, 9), Ui.dp(c, 4));
        banner.addView(goal);
        bannerText = Ui.text(c, "", 14, Ui.TEXT, true);
        bannerText.setMaxLines(2);
        bannerText.setPadding(Ui.dp(c, 10), 0, Ui.dp(c, 10), 0);
        banner.addView(bannerText, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView sw = button(c, a.getString(R.string.live3_switch), true);
        sw.setOnClickListener(v -> switchToBanner());
        banner.addView(sw);
        TextView x = Ui.text(c, "✕", 16, Ui.TEXT, true);
        x.setGravity(Gravity.CENTER);
        x.setContentDescription(a.getString(R.string.live3_dismiss));
        x.setOnClickListener(v -> fadeOut(banner));
        banner.addView(x, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        banner.setVisibility(View.GONE);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Math.min(Ui.dp(c, 520), a.getResources().getDisplayMetrics().widthPixels - Ui.dp(c, 24)),
                -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        bp.topMargin = Ui.dp(c, 12);
        root.addView(banner, bp);

        // The scores box in the small window.
        pipBug = new LinearLayout(c);
        pipBug.setGravity(Gravity.CENTER_VERTICAL);
        pipText = Ui.text(c, "", 11, Ui.TEXT, true);
        pipText.setBackground(Ui.round(c, 0xE61450B8, 4));
        pipText.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2));
        pipBug.addView(pipText);
        pipBug.setVisibility(View.GONE);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        pp.leftMargin = pp.topMargin = Ui.dp(c, 6);
        root.addView(pipBug, pp);

        // The touch lock: everything taken but the lock itself, held to undo.
        lockCover = new FrameLayout(c);
        lockCover.setClickable(true);
        lockCover.setOnClickListener(v -> Toast.makeText(a, R.string.live3_lock_hint, Toast.LENGTH_SHORT).show());
        lockBox = new LinearLayout(c);
        lockBox.setGravity(Gravity.CENTER);
        lockBox.setBackground(Ui.round(c, 0x99000000, 26));
        ImageView lock = new ImageView(c);
        lock.setImageResource(R.drawable.live_ic_lock);
        lockBox.addView(lock, new LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26)));
        lockBox.setContentDescription(a.getString(R.string.live3_lock_hint));
        lockBox.setOnClickListener(v -> Toast.makeText(a, R.string.live3_lock_hint, Toast.LENGTH_SHORT).show());
        lockBox.setOnLongClickListener(v -> {
            setLocked(false);
            Toast.makeText(a, R.string.live3_unlocked, Toast.LENGTH_SHORT).show();
            return true;
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(c, 52), Ui.dp(c, 52), Gravity.BOTTOM | Gravity.START);
        lp.leftMargin = lp.bottomMargin = Ui.dp(c, 18);
        lockCover.addView(lockBox, lp);
        lockCover.setVisibility(View.GONE);
        root.addView(lockCover, new FrameLayout.LayoutParams(-1, -1));
    }

    private static TextView button(Context c, String words, boolean light) {
        TextView t = Ui.text(c, words, 14, light ? Ui.NAVY_INK : Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(Ui.dp(c, 40));
        t.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 16), 0);
        t.setBackground(Ui.touch(c, false, light ? Ui.TEXT : 0x26FFFFFF, 20));
        return t;
    }

    private static TextView round(Context c, String words) {
        TextView t = Ui.text(c, words, 15, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.touch(c, false, 0x26FFFFFF, 26));
        return t;
    }

    private static void fadeOut(View v) {
        if (v == null || v.getVisibility() != View.VISIBLE) return;
        v.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            v.setVisibility(View.GONE);
            v.setAlpha(1f);
        }).start();
    }

    private static void fadeIn(View v) {
        if (v.getVisibility() == View.VISIBLE) return;
        v.setAlpha(0f);
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).setDuration(200).start();
    }

    // ---- the rewind ------------------------------------------------------------------------------------------

    /** The replay began or ended. */
    void onReplay(boolean on) {
        if (on) fadeIn(replayBar);
        else fadeOut(replayBar);
        refreshReplay();
    }

    /** Back (negative) or on, from the moment on screen: into the replay, along it, or back to live at its end. */
    void jump(long ms) {
        if (Timeshift.get(a).minutes() == 0) {
            Toast.makeText(a, R.string.live3_rewind_off, Toast.LENGTH_SHORT).show();
            return;
        }
        if (ms > 0 && !pb.replaying()) return;
        long to = pb.shownAt() + ms;
        if (!pb.replay(to)) Toast.makeText(a, R.string.live3_rewind_none, Toast.LENGTH_SHORT).show();
    }

    /** A double tap on the left (back 10 s) or the right (on 10 s): handled, with its sign. */
    void doubleTap(boolean left) {
        if (left || pb.replaying()) {
            showRipple(left);
            jump(left ? -10_000 : 10_000);
        }
    }

    private void showRipple(boolean left) {
        ripple.setText(left ? "«  10 s" : "10 s  »");
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) ripple.getLayoutParams();
        lp.gravity = Gravity.CENTER_VERTICAL | (left ? Gravity.START : Gravity.END);
        lp.leftMargin = lp.rightMargin = Ui.dp(a, 24);
        ripple.setLayoutParams(lp);
        ripple.setVisibility(View.VISIBLE);
        ripple.setAlpha(1f);
        h.removeCallbacks(hideRipple);
        h.postDelayed(hideRipple, 600);
    }

    private void refreshReplay() {
        if (!pb.replaying() || replayBar.getVisibility() != View.VISIBLE) return;
        long from = pb.rewindFrom(), end = pb.replayEnd(), at = pb.shownAt(), now = System.currentTimeMillis();
        if (from <= 0 || end <= from) return;
        if (!dragging) {
            seek.setMax((int) ((end - from) / 1000));
            seek.setProgress((int) Math.max(0, (at - from) / 1000));
        }
        replayBadge.setText(a.getString(R.string.live3_rewind).toUpperCase(Locale.UK) + " · " + clock(now - at) + " behind live");
        boolean playing = pb.player() != null && pb.player().getPlayWhenReady();
        playPause.setText(playing ? "❚❚" : "▶");
        playPause.setContentDescription(playing ? "Pause" : "Play");
    }

    static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.UK, "%d:%02d", s / 60, s % 60);
    }

    // ---- alerts --------------------------------------------------------------------------------------------------

    /** The game shown now (from the tile): its alerts wait for this stream. */
    void game(Game g) {
        Channel ch = host.current();
        LiveAlerts.watching(LiveAlerts.id(g), ch == null ? null : ch.key);
        if (overlay != null) overlay.game(g);
    }

    @Override public void onHeldChanged() {
        refreshHeld();
    }

    private void refreshHeld() {
        List<LiveAlerts.Held> held = LiveAlerts.held();
        if (held.isEmpty() || inPip || locked) {
            heldLine.setVisibility(View.GONE);
            return;
        }
        long in = held.get(0).showAt - System.currentTimeMillis();
        heldLine.setText((held.size() == 1 ? "1 alert waiting" : held.size() + " alerts waiting") + " · shows in " + clock(in + 999));
        heldLine.setVisibility(View.VISIBLE);
    }

    @Override public void onGoalElsewhere(Events.Alert alert) {
        // The scoreboard over the picture drops it instead (tap it to switch).
        if (overlay != null && overlay.on()) return;
        banner(alert);
    }

    /** The banner for a goal in another game, with Switch (a test shows it whether the scoreboard is on or not). */
    void banner(Events.Alert alert) {
        if (inPip || a.isFinishing() || alert == null) return;
        bannerAlert = alert;
        bannerText.setText(alert.title + (alert.text == null || alert.text.isEmpty() ? "" : "\n" + alert.text.split("\n")[0]));
        fadeIn(banner);
        h.removeCallbacks(hideBanner);
        h.postDelayed(hideBanner, 12_000);
    }

    private void switchToBanner() {
        Events.Alert alert = bannerAlert;
        fadeOut(banner);
        if (alert == null || alert.game == null) return;
        Context app = a.getApplicationContext();
        App.io.execute(() -> {
            Channel ch = AlsoShowing.best(app, alert.game);
            App.main.post(() -> {
                if (a.isFinishing()) return;
                if (ch == null) Toast.makeText(a, R.string.live3_not_in_playlist, Toast.LENGTH_SHORT).show();
                else host.play(ch);
            });
        });
    }

    // ---- the small window --------------------------------------------------------------------------------------

    void pip(boolean in) {
        inPip = in;
        if (overlay != null) overlay.pip(in);
        if (in) {
            if (match != null) {
                root.removeView(match);
                match = null;
            }
            heldLine.setVisibility(View.GONE);
            banner.setVisibility(View.GONE);
            ripple.setVisibility(View.GONE);
            replayBar.setVisibility(View.GONE);
        } else if (pb.replaying()) {
            replayBar.setVisibility(View.VISIBLE);
        }
        refreshPip();
    }

    private void refreshPip() {
        Game g = inPip ? host.game() : null;
        // The board itself is in the small window, in its own look: the line isn't needed.
        if (g == null || (overlay != null && overlay.inSmallWindow())) {
            pipBug.setVisibility(View.GONE);
            return;
        }
        String home = g.home == null || g.home.isEmpty() ? g.homeName : g.home, away = g.away == null || g.away.isEmpty() ? g.awayName : g.away;
        String mid = g.state == Game.PRE ? "v" : g.score();
        // The clock in minutes and seconds as the picture shows it (this is drawn again every second).
        String clock = ScreenTime.words(g, System.currentTimeMillis());
        pipText.setText(home + "  " + mid + "  " + away + (clock == null || clock.isEmpty() ? "" : "  · " + clock));
        pipBug.setVisibility(View.VISIBLE);
    }

    // ---- the touch lock ----------------------------------------------------------------------------------------

    boolean locked() {
        return locked;
    }

    /** Whether Move and resize is open (the scoreboard and the mark being placed): a swipe never changes channel then. */
    boolean adjusting() {
        return overlay != null && overlay.editing();
    }

    void setLocked(boolean on) {
        locked = on;
        lockCover.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) lockCover.bringToFront();
        refreshHeld();
    }

    // ---- the match centre -------------------------------------------------------------------------------------

    private com.gathin.tvhomephone.sports.MatchPanel match;

    /** The game's Timeline, Line-ups, Stats and Table over the lower half of the picture (slide it down to close). */
    void openMatch() {
        Game g = host.game();
        if (g == null || inPip) return;
        if (match != null) {
            match.close();
            return;
        }
        com.gathin.tvhomephone.sports.MatchPanel p = new com.gathin.tvhomephone.sports.MatchPanel(a);
        root.addView(p, com.gathin.tvhomephone.sports.MatchPanel.layoutParams(a));
        p.setOnClose(() -> {
            root.removeView(p);
            if (match == p) match = null;
        });
        match = p;
        p.show(g);
        p.slideIn();
    }

    /** Back: the line-ups beside the scoreboard closed if they are out (true), before anything else Back does. */
    boolean closeLineups() {
        return overlay != null && overlay.closeLineups();
    }

    /** The ad-break screen's cards: a key for them (true when taken), Back letting a picked card go. */
    boolean adBreakKey(android.view.KeyEvent e) {
        return overlay != null && overlay.adBreakKey(e);
    }

    boolean adBreakBack() {
        return overlay != null && overlay.adBreakBack();
    }

    boolean matchShown() {
        return match != null;
    }

    /** Move and resize closed if it was open (kept as placed); whether it was. */
    boolean endAdjust() {
        if (overlay == null || !overlay.editing()) return false;
        overlay.endAdjust(true);
        return true;
    }

    void closeMatch() {
        if (match != null) match.close();
    }

    // ---- on a TV -----------------------------------------------------------------------------------------------

    /** Cast: the TVs and devices on the Wi-Fi (the paired TVs among them), the channel sent to the one picked. */
    private void watchOnTv() {
        Channel ch = host.current();
        if (ch != null) CastSheet.show(a, ch);
    }

    // ---- the options -----------------------------------------------------------------------------------------

    /** The options sheet's new sections, after the picture's own. */
    void options(LinearLayout box) {
        Context c = a;
        // Quality: adaptive or a fixed resolution; 4K let through on mobile data; where it plays from.
        LinearLayout q = chipRow(c, box, R.string.live3_quality);
        List<TextView> qs = new ArrayList<>();
        int choice = Quality.choice(c);
        for (int lv : Quality.CHOICES) {
            TextView t = Ui.guideChip(c, Quality.label(lv), lv == choice);
            t.setOnClickListener(v -> {
                Quality.setChoice(c, lv);
                for (TextView x : qs) Ui.styleChip(c, x, x == v);
                pb.requality(false);
            });
            qs.add(t);
            addChip(q, t);
        }
        Channel s = pb.stream();
        if (s != null && Quality.metered(c) && Quality.ask4k(c)) {
            boolean has4k = false;
            for (Channel v : Library.get(c).variants(host.current())) has4k |= Library.level(v) >= 4;
            if (has4k && Library.level(s) < 4) {
                TextView k = Ui.guideChip(c, a.getString(R.string.live3_play_4k), false);
                k.setOnClickListener(v -> {
                    host.closeSheet();
                    pb.requality(true);
                });
                addChip(q, k);
            }
        }
        note(c, box, a.getString(R.string.live3_quality_note));
        String line = pb.sourceLine();
        if (!line.isEmpty()) note(c, box, "Now: " + line);
        toggle(c, box, a.getString(R.string.live3_data_saver), Quality.saver(c), on -> {
            Quality.setSaver(c, on);
            pb.requality(false);
        });
        toggle(c, box, a.getString(R.string.live3_ask_4k), Quality.ask4k(c), on -> {
            Quality.setAsk4k(c, on);
            pb.requality(false);
        });

        // Alerts as the stream shows the game.
        LinearLayout al = chipRow(c, box, R.string.live3_alerts);
        Channel st = host.current();
        String key = st == null ? "" : st.key;
        TextView value = Ui.guideChip(c, LiveAlerts.delay(c, key) + " s behind", false);
        TextView minus = Ui.guideChip(c, "− 5 s", false), plus = Ui.guideChip(c, "+ 5 s", false);
        minus.setOnClickListener(v -> {
            LiveAlerts.setDelay(c, key, LiveAlerts.delay(c, key) - 5);
            value.setText(LiveAlerts.delay(c, key) + " s behind");
            if (overlay != null) overlay.delayChanged();
        });
        plus.setOnClickListener(v -> {
            LiveAlerts.setDelay(c, key, LiveAlerts.delay(c, key) + 5);
            value.setText(LiveAlerts.delay(c, key) + " s behind");
            if (overlay != null) overlay.delayChanged();
        });
        addChip(al, minus);
        addChip(al, value);
        addChip(al, plus);
        TextView just = Ui.guideChip(c, a.getString(R.string.live3_just_happened), false);
        just.setOnClickListener(v -> {
            int d = LiveAlerts.justHappened(c);
            if (d < 0) Toast.makeText(c, R.string.live3_just_happened_none, Toast.LENGTH_LONG).show();
            else value.setText(d + " s behind");
            if (overlay != null) overlay.delayChanged();
        });
        // On a line of its own: the one to find at a glance as the goal goes in.
        LinearLayout justRow = new LinearLayout(c);
        justRow.setPadding(0, Ui.dp(c, 8), 0, 0);
        justRow.addView(just, new LinearLayout.LayoutParams(-2, -2));
        box.addView(justRow);
        note(c, box, "Tap “It's just happened” as you see a goal, a card or the kick-off: this channel's alerts then wait until your stream shows them.");
        toggle(c, box, a.getString(R.string.live3_hold), LiveAlerts.holding(c), on -> LiveAlerts.setHolding(c, on));

        // The scoreboard over the picture: on, over the channel's own, which alerts drop from it, over other apps.
        if (overlay != null) {
            chipRow(c, box, R.string.live4_board);
            toggle(c, box, a.getString(R.string.live4_board_on), overlay.on(), overlay::set);
            handOptions(c, box);
            channelOptions(c, box);
            // Moved and sized by hand: the scoreboard and the mark over the logo, dragged into place on the picture.
            action(c, box, R.drawable.live_ic_fullscreen, a.getString(R.string.live6_adjust), () -> {
                host.closeSheet();
                overlay.adjust();
            });
            // The size, kept for the channel playing (its own from then on).
            LinearLayout sizes = chipRow(c, box, overlay.channelKnown() ? R.string.live50_size_here : R.string.live6_size);
            List<TextView> ss = new ArrayList<>();
            for (int i = 0; i < ScoreBoard.SIZES.length; i++) {
                float k = ScoreBoard.SIZES[i];
                TextView t = Ui.guideChip(c, ScoreBoard.SIZE_NAMES[i], Math.abs(overlay.sizeHere() - k) < 0.01f);
                t.setOnClickListener(v -> {
                    overlay.setHere("size", Float.toString(k));
                    for (TextView x : ss) Ui.styleChip(c, x, x == v);
                });
                ss.add(t);
                addChip(sizes, t);
            }
            toggle(c, box, a.getString(R.string.live6_still), ScoreBoard.still(c), on -> ScoreBoard.setStill(c, on));
            toggle(c, box, a.getString(R.string.live4_board_cover), ScoreBoard.cover(c), on -> {
                ScoreBoard.setCover(c, on);
                overlay.game(host.game());
            });
            LinearLayout drops = chipRow(c, box, R.string.live4_board_alerts);
            String[][] groups = {{ScoreBoard.FOOTBALL, a.getString(R.string.live4_alerts_football)},
                    {ScoreBoard.SPORT, a.getString(R.string.live4_alerts_sport)}, {ScoreBoard.NEWS, a.getString(R.string.live4_alerts_news)}};
            for (String[] g : groups) {
                TextView t = Ui.guideChip(c, g[1], ScoreBoard.shows(c, g[0]));
                t.setOnClickListener(v -> {
                    boolean now = !ScoreBoard.shows(c, g[0]);
                    ScoreBoard.setShows(c, g[0], now);
                    Ui.styleChip(c, t, now);
                });
                addChip(drops, t);
            }
            // Its look, and Homefield's mark over the channel's logo.
            LinearLayout looks = chipRow(c, box, R.string.live4_board_look);
            List<TextView> ls = new ArrayList<>();
            boolean custom = ScoreBoard.customId(c) != null;
            for (OverlayBug.Style look : OverlayBug.Style.values()) {
                TextView t = Ui.guideChip(c, look.label, !custom && ScoreBoard.style(c) == look);
                t.setOnClickListener(v -> {
                    ScoreBoard.setStyle(c, look);
                    for (TextView x : ls) Ui.styleChip(c, x, x == v);
                    overlay.restyle();
                });
                ls.add(t);
                addChip(looks, t);
            }
            // My scoreboards, their own group: each one to use now, and the editor (the board on screen changing as it is
            // edited, given back when it closes).
            LinearLayout mine = chipRow(c, box, R.string.live_my_boards);
            for (com.gathin.broadcast.MyBoards.Saved sv : MyBoardStore.all(c)) {
                TextView t = Ui.guideChip(c, sv.name, sv.id.equals(ScoreBoard.customId(c)));
                t.setOnClickListener(v -> {
                    ScoreBoard.setCustom(c, sv.id);
                    for (TextView x : ls) Ui.styleChip(c, x, x == v);
                    overlay.restyle();
                });
                ls.add(t);
                addChip(mine, t);
            }
            action(c, box, R.drawable.live_ic_edit, a.getString(R.string.live_board_editor), () -> {
                host.closeSheet();
                MyBoardsRow.edit(a, ScoreBoard.customId(c), host.game(), () -> new BoardEditor.Live() {
                    @Override public void show(com.gathin.broadcast.BoardSpec s) {
                        overlay.previewBoard(s);
                    }

                    @Override public void done() {
                        overlay.restyle();
                    }
                }, null);
            });
            // Beside the look and size: whether what comes out of the channel's own scoreboard is covered (BugCover).
            toggle(c, box, a.getString(R.string.live_bug_pop_outs_short), ScoreBoard.coverPopOuts(c), on -> ScoreBoard.setCoverPopOuts(c, on));
            // The picture tag on the logo's cover.
            LinearLayout tags = chipRow(c, box, R.string.live20_logo_tag);
            List<TextView> tg = new ArrayList<>();
            for (ChannelMark.Tag tag : ChannelMark.Tag.values()) {
                TextView t = Ui.guideChip(c, tag == ChannelMark.Tag.AUTO ? "Auto" : tag.label, overlay.tagHere() == tag);
                t.setOnClickListener(v -> {
                    overlay.setHere("tag", tag.name());
                    for (TextView x : tg) Ui.styleChip(c, x, x == v);
                });
                tg.add(t);
                addChip(tags, t);
            }
            toggle(c, box, a.getString(R.string.live24_logo_live), overlay.liveHere(), on -> overlay.setHere("live", on ? "1" : "0"));
            toggle(c, box, a.getString(R.string.live21_logo_comp), overlay.compHere(), on -> overlay.setHere("comp", on ? "1" : "0"));
            // See-through (the channel's own logo hidden, the picture carried over it) and a general channel's name.
            toggle(c, box, a.getString(R.string.live50_see_through), overlay.seeThroughHere(), on -> overlay.setHere("see_through", on ? "1" : "0"));
            // What it is doing over this channel now: learning its logo, taken out, or on its plate (a solid logo).
            String seeing = overlay.seeThroughStatus();
            if (seeing != null) note(c, box, seeing);
            toggle(c, box, a.getString(R.string.live50_name_only), overlay.nameOnlyHere(), on -> overlay.setHere("name_only", on ? "1" : "0"));
            // The ad-break screen at the adverts (the build-up, half time, after the game): on or off, one to see now, and
            // one as at half time (the quiz, fun facts and prayer times).
            toggle(c, box, a.getString(R.string.live58_ad_break), ScoreBoard.adBreak(c), on -> ScoreBoard.setAdBreak(c, on));
            action(c, box, R.drawable.live_ic_play, a.getString(R.string.live58_ad_break_try), () -> {
                host.closeSheet();
                overlay.tryAdBreak();
            });
            action(c, box, R.drawable.live_ic_play, a.getString(R.string.live63_ad_break_half), () -> {
                host.closeSheet();
                overlay.tryAdBreak(true);
            });
            toggle(c, box, a.getString(R.string.live5_burn), BurnGuard.on(c), on -> BurnGuard.setOn(c, on));
            toggle(c, box, a.getString(R.string.live4_read), ScoreBoard.reads(c), on -> {
                ScoreBoard.setReads(c, on);
                overlay.game(host.game());
            });
            overlay.options(c, box, host::closeSheet);
            action(c, box, R.drawable.live_ic_pip, a.getString(FloatService.on(c) ? R.string.live4_float_stop : R.string.live4_float_start), () -> {
                host.closeSheet();
                FloatService.toggle(a);
            });
            note(c, box, a.getString(R.string.live4_board_note));
        }

        // The rewind.
        LinearLayout rw = chipRow(c, box, R.string.live3_rewind);
        List<TextView> rs = new ArrayList<>();
        int mins = Timeshift.get(c).minutes();
        for (int m : Timeshift.CHOICES) {
            TextView t = Ui.guideChip(c, m == 0 ? a.getString(R.string.live3_off) : a.getString(R.string.live3_minutes, m), m == mins);
            t.setOnClickListener(v -> {
                Timeshift.get(c).setMinutes(m);
                for (TextView x : rs) Ui.styleChip(c, x, x == v);
            });
            rs.add(t);
            addChip(rw, t);
        }
        TextView back = Ui.guideChip(c, a.getString(R.string.live3_rewind_back), false);
        back.setOnClickListener(v -> {
            host.closeSheet();
            jump(-30_000);
        });
        addChip(rw, back);
        note(c, box, "Keeps the last minutes of what you watch on this phone (about 1 GB at most), so you can go back; double-tap the left or right of the picture for 10 seconds.");

        // The game's match centre over the picture, two games at once.
        if (host.game() != null) {
            action(c, box, R.drawable.live_ic_stats, a.getString(R.string.live3_match_centre), () -> {
                host.closeSheet();
                openMatch();
            });
        }
        Channel now = host.current();
        if (now != null) {
            action(c, box, R.drawable.live_ic_two, a.getString(R.string.live3_two_games),
                    () -> {
                        host.closeSheet();
                        a.startActivity(TwoGamesActivity.intent(a, now.key, null));
                    });
        }

        // Test notifications, everything, grouped: each as it really comes, on this game (or a made-up one), now.
        PlayerTests.add(a, box, host, overlay, this);

        // Night sound, the touch lock, on a TV.
        if (NightSound.available()) {
            toggle(c, box, a.getString(R.string.live3_night), NightSound.on(c), on -> NightSound.set(c, on));
        }
        action(c, box, R.drawable.live_ic_lock, a.getString(R.string.live3_lock), () -> {
            host.closeSheet();
            setLocked(true);
        });
        action(c, box, R.drawable.live_ic_cast, a.getString(R.string.cast_on_tv), () -> {
            host.closeSheet();
            watchOnTv();
        });
    }

    private interface Flip {
        void set(boolean on);
    }

    /**
     * Up by hand: the scoreboard and the mark over the logo over the picture now, whatever is found on the channel:
     * off, the channel's own game, one of today's games (read off the main thread), or the mark alone.
     */
    private void handOptions(Context c, LinearLayout box) {
        LinearLayout row = chipRow(c, box, R.string.live49_hand);
        List<TextView> all = new ArrayList<>();
        boolean on = overlay.handOn();
        String id = on ? ScoreBoard.handGame(c) : null;
        TextView off = Ui.guideChip(c, a.getString(R.string.live49_hand_off), !on);
        TextView own = Ui.guideChip(c, a.getString(R.string.live49_hand_channel), on && "".equals(id));
        TextView only = Ui.guideChip(c, a.getString(R.string.live49_hand_logo), on && ScoreBoard.NO_GAME.equals(id));
        off.setOnClickListener(v -> {
            overlay.handOff();
            for (TextView x : all) Ui.styleChip(c, x, x == v);
        });
        own.setOnClickListener(v -> {
            overlay.hand(null, false);
            for (TextView x : all) Ui.styleChip(c, x, x == v);
        });
        only.setOnClickListener(v -> {
            overlay.hand(null, true);
            for (TextView x : all) Ui.styleChip(c, x, x == v);
        });
        all.add(off);
        all.add(own);
        all.add(only);
        addChip(row, off);
        addChip(row, own);
        addChip(row, only);
        // Today's games not over (live first), put in before "The logo only" once read.
        App.io.execute(() -> {
            List<Game> games = new ArrayList<>();
            try {
                for (Game g : com.gathin.tvhomephone.sports.OnAir.today(60_000L)) {
                    if (g != null && g.id != null && g.state != Game.POST && games.size() < 16) games.add(g);
                }
            } catch (RuntimeException ignored) {
                // the chips as they are
            }
            games.sort((x, y) -> x.state != y.state ? (x.state == Game.LIVE ? -1 : y.state == Game.LIVE ? 1 : 0) : Long.compare(x.kickoff, y.kickoff));
            App.main.post(() -> {
                if (row.getParent() == null) return;
                for (Game g : games) {
                    TextView t = Ui.guideChip(c, g.home + " v " + g.away + (g.state == Game.LIVE ? " · live" : ""), on && g.id.equals(id));
                    t.setOnClickListener(v -> {
                        overlay.hand(g, false);
                        for (TextView x : all) Ui.styleChip(c, x, x == v);
                    });
                    all.add(t);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                    lp.rightMargin = Ui.dp(c, 8);
                    row.addView(t, row.indexOfChild(only), lp);
                }
            });
        });
        note(c, box, a.getString(R.string.live49_hand_note));
    }

    /** This channel's own: as the rest, always up, or never; and its own logo look and style, else the general ones. */
    private void channelOptions(Context c, LinearLayout box) {
        Channel ch = host.current();
        if (ch == null || ch.key == null) return;
        String key = ch.key;
        LinearLayout modes = chipRow(c, box, R.string.live49_channel);
        List<TextView> ms = new ArrayList<>();
        String[][] choices = {{ChannelOverlays.SAME, a.getString(R.string.live49_channel_same)},
                {ChannelOverlays.ALWAYS, a.getString(R.string.live49_channel_always)}, {ChannelOverlays.NEVER, a.getString(R.string.live49_channel_never)}};
        for (String[] m : choices) {
            TextView t = Ui.guideChip(c, m[1], ChannelOverlays.mode(c, key).equals(m[0]));
            t.setOnClickListener(v -> {
                ChannelOverlays.setMode(c, key, m[0]);
                for (TextView x : ms) Ui.styleChip(c, x, x == v);
                overlay.channelChanged();
            });
            ms.add(t);
            addChip(modes, t);
        }
        LinearLayout looks = chipRow(c, box, R.string.live49_channel_logo);
        List<TextView> ls = new ArrayList<>();
        LogoCover.Look own = ChannelOverlays.look(c, key);
        TextView same = Ui.guideChip(c, a.getString(R.string.live49_channel_logo_same, ScoreBoard.logoCover(c).label), own == null);
        same.setOnClickListener(v -> {
            overlay.setLookFromOptions(null);
            for (TextView x : ls) Ui.styleChip(c, x, x == v);
        });
        ls.add(same);
        addChip(looks, same);
        for (LogoCover.Look lk : LogoCover.Look.values()) {
            TextView t = Ui.guideChip(c, lk.label, own == lk);
            t.setOnClickListener(v -> {
                overlay.setLookFromOptions(lk);
                for (TextView x : ls) Ui.styleChip(c, x, x == v);
            });
            ls.add(t);
            addChip(looks, t);
        }
        // The Whitebrook Road style on this channel (for the look of that name).
        LinearLayout styles = chipRow(c, box, R.string.live50_style_here);
        List<TextView> sts = new ArrayList<>();
        for (int i = 0; i < BrandStyles.COUNT; i++) {
            int n = i;
            TextView t = Ui.guideChip(c, (i + 1) + ". " + BrandStyles.name(i), overlay.styleHere() == i);
            t.setOnClickListener(v -> {
                overlay.setStyleHere(n);
                for (TextView x : sts) Ui.styleChip(c, x, x == v);
            });
            sts.add(t);
            addChip(styles, t);
        }
        note(c, box, a.getString(R.string.live49_channel_note, ch.name == null ? "this channel" : ch.name));
    }

    static LinearLayout chipRow(Context c, LinearLayout box, int label) {
        TextView t = Ui.text(c, c.getString(label).toUpperCase(Locale.UK), 12, Ui.G_SELECT, true);
        t.setLetterSpacing(0.1f);
        t.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 2));
        box.addView(t);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        hs.addView(row);
        box.addView(hs, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    static void addChip(LinearLayout row, TextView chip) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(row.getContext(), 8);
        row.addView(chip, lp);
    }

    static void note(Context c, LinearLayout box, String words) {
        TextView n = Ui.text(c, words, 12, Ui.DIM, false);
        n.setPadding(0, Ui.dp(c, 6), 0, 0);
        box.addView(n);
    }

    private static void toggle(Context c, LinearLayout box, String label, boolean on, Flip flip) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(c, label, 15, Ui.TEXT, false);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView chip = Ui.guideChip(c, on ? c.getString(R.string.live3_on) : c.getString(R.string.live3_off), on);
        boolean[] state = {on};
        chip.setOnClickListener(v -> {
            state[0] = !state[0];
            chip.setText(state[0] ? c.getString(R.string.live3_on) : c.getString(R.string.live3_off));
            Ui.styleChip(c, chip, state[0]);
            flip.set(state[0]);
        });
        row.addView(chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(c, 6);
        box.addView(row, lp);
    }

    private static void action(Context c, LinearLayout box, int icon, String label, Runnable r) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        row.setBackground(Ui.touch(c, false, 0, 12));
        ImageView i = new ImageView(c);
        i.setImageResource(icon);
        i.setImageTintList(ColorStateList.valueOf(Ui.G_SELECT));
        row.addView(i, new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24)));
        TextView t = Ui.text(c, label, 16, Ui.TEXT, false);
        t.setPadding(Ui.dp(c, 16), 0, 0, 0);
        row.addView(t);
        row.setOnClickListener(v -> r.run());
        box.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(c, 52)));
    }
}

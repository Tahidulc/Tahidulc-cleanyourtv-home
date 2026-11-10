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

import androidx.appcompat.app.AlertDialog;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Events;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.LiveClock;
import com.gathin.tvhomephone.sync.Tvs;

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
    private FrameLayout cover, lockCover;
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

    private void watchOnTv() {
        List<String> tvs = Tvs.names(a);
        if (tvs.isEmpty()) return;
        if (tvs.size() == 1) {
            sendTo(tvs.get(0));
            return;
        }
        new AlertDialog.Builder(a).setTitle(R.string.live3_on_tv_which)
                .setItems(tvs.toArray(new String[0]), (d, w) -> sendTo(tvs.get(w))).show();
    }

    private void sendTo(String tv) {
        Channel ch = host.current();
        if (ch == null) return;
        List<String> names = new ArrayList<>();
        names.add(ch.name);
        String plain = Names.plain(ch.name);
        if (!plain.equals(ch.name)) names.add(plain);
        Tvs.tuneOnTv(a, tv, names, (ok, err) -> {
            if (a.isFinishing()) return;
            if (!ok) {
                Toast.makeText(a, err, Toast.LENGTH_LONG).show();
                return;
            }
            // One stream at a time on most accounts: the phone's stops now the TV has it.
            pb.stop();
            showCover(tv, ch);
        });
    }

    private void showCover(String tv, Channel ch) {
        if (cover != null) root.removeView(cover);
        Context c = a;
        cover = new FrameLayout(c);
        cover.setBackgroundColor(0xF20B0C21);
        cover.setClickable(true);
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView icon = new ImageView(c);
        icon.setImageResource(R.drawable.live_ic_tv);
        box.addView(icon, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        TextView t = Ui.text(c, "Playing on " + tv, 18, Ui.TEXT, true);
        t.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 4));
        box.addView(t);
        box.addView(Ui.text(c, ch.name, 14, Ui.SOFT, false));
        TextView back = button(c, a.getString(R.string.live3_continue_phone), true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 48));
        bp.topMargin = Ui.dp(c, 20);
        box.addView(back, bp);
        back.setOnClickListener(v -> {
            root.removeView(cover);
            cover = null;
            host.play(ch);
        });
        cover.addView(box, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        root.addView(cover, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Whether the channel is on a TV now (the phone's cover up). */
    boolean onTv() {
        return cover != null;
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
            // Moved and sized by hand: the scoreboard and the mark over the logo, dragged into place on the picture.
            action(c, box, R.drawable.live_ic_fullscreen, a.getString(R.string.live6_adjust), () -> {
                host.closeSheet();
                overlay.adjust();
            });
            LinearLayout sizes = chipRow(c, box, R.string.live6_size);
            List<TextView> ss = new ArrayList<>();
            for (int i = 0; i < ScoreBoard.SIZES.length; i++) {
                float k = ScoreBoard.SIZES[i];
                TextView t = Ui.guideChip(c, ScoreBoard.SIZE_NAMES[i], Math.abs(ScoreBoard.size(c) - k) < 0.01f);
                t.setOnClickListener(v -> {
                    ScoreBoard.setSize(c, k);
                    for (TextView x : ss) Ui.styleChip(c, x, x == v);
                    overlay.restyle();
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
            for (OverlayBug.Style look : OverlayBug.Style.values()) {
                TextView t = Ui.guideChip(c, look.label, ScoreBoard.style(c) == look);
                t.setOnClickListener(v -> {
                    ScoreBoard.setStyle(c, look);
                    for (TextView x : ls) Ui.styleChip(c, x, x == v);
                    overlay.restyle();
                });
                ls.add(t);
                addChip(looks, t);
            }
            LinearLayout logos = chipRow(c, box, R.string.live4_logo);
            List<TextView> lg = new ArrayList<>();
            for (LogoCover.Look lk : LogoCover.Look.values()) {
                TextView t = Ui.guideChip(c, lk.label, ScoreBoard.logoCover(c) == lk);
                t.setOnClickListener(v -> {
                    ScoreBoard.setLogoCover(c, lk);
                    for (TextView x : lg) Ui.styleChip(c, x, x == v);
                    overlay.restyle();
                });
                lg.add(t);
                addChip(logos, t);
            }
            // The picture tag on the logo's cover.
            LinearLayout tags = chipRow(c, box, R.string.live20_logo_tag);
            List<TextView> tg = new ArrayList<>();
            for (ChannelMark.Tag tag : ChannelMark.Tag.values()) {
                TextView t = Ui.guideChip(c, tag == ChannelMark.Tag.AUTO ? "Auto" : tag.label, ScoreBoard.logoTag(c) == tag);
                t.setOnClickListener(v -> {
                    ScoreBoard.setLogoTag(c, tag);
                    for (TextView x : tg) Ui.styleChip(c, x, x == v);
                    overlay.restyle();
                });
                tg.add(t);
                addChip(tags, t);
            }
            toggle(c, box, a.getString(R.string.live24_logo_live), ScoreBoard.logoLive(c), on -> {
                ScoreBoard.setLogoLive(c, on);
                overlay.restyle();
            });
            toggle(c, box, a.getString(R.string.live21_logo_comp), ScoreBoard.logoComp(c), on -> {
                ScoreBoard.setLogoComp(c, on);
                overlay.restyle();
            });
            // Broadcast mode: Homefield's break over the channel's adverts; one to see now.
            toggle(c, box, a.getString(R.string.live5_broadcast), ScoreBoard.broadcast(c), on -> ScoreBoard.setBroadcast(c, on));
            action(c, box, R.drawable.live_ic_play, a.getString(R.string.live5_broadcast_try), () -> {
                host.closeSheet();
                overlay.startBreak(true);
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
        if (Tvs.paired(c)) {
            action(c, box, R.drawable.live_ic_tv, a.getString(R.string.live3_on_tv), () -> {
                host.closeSheet();
                watchOnTv();
            });
        }
    }

    private interface Flip {
        void set(boolean on);
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

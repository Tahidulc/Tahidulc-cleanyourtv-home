package com.gathin.tvhomephone.live;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Rational;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.GameActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Live TV's player, full screen: one of the family's own channels (HLS or MPEG-TS), edge to edge and under the camera's
 * cutout, with the system bars hidden (a swipe from the edge brings them back for a moment). It follows the phone round:
 * on its side the picture fills the screen (unless Fit or another shape was picked for the channel); upright it is fitted
 * to the width, centred, with the guide panel at the foot. A tap brings up the info tile (InfoTile: Match night for a
 * game, Cinematic for a film, Card for a series' episode, Glass ribbon for the rest, or one style for everything as
 * chosen in the options), and so do changing channel and a new programme starting; a swipe from the right brings the
 * guide panel (the channel's number, logo and name, what is on now with its times and how far in, what's next, and
 * the mini guide of the channels either side: tap one to change channel); they go after a few seconds. The ⋯ opens
 * the options: the info tile's style, the picture's shape (remembered per
 * channel), audio, subtitles, quality, similar channels, the favourite, a sleep timer, picture in picture, restarting the
 * stream and the programme's details. Swipe left or right for the next or previous channel in the category (ChannelSwipe:
 * only a clear fling, hidden ones passed over), with a quick channel card and the picture sliding across; swipe up for
 * the guide panel (its mini guide, or Recent with "Back to" the channel before) and down to put it away. With the swipe
 * switched off (the options, Settings), up and down change channel, left brings the mini guide and right puts it away,
 * as before. Double tap or pinch between Fit and Fill; slide on the left edge for brightness and on the right for
 * volume. The panel, its cards and the buttons are 07's frosted glass (TvLook), and keep clear of the cutout.
 * It plays through the one shared player (Playback): opened from the guide's own picture it takes that stream over as
 * it plays and hands it back on the way out (and, when turning the phone on its side opened it, turning it upright again
 * goes back). Picture in picture on leaving, the phone's audio focus, the screen kept on, and a few retries when the
 * stream drops. The stream's address is never logged (it can hold the login).
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {
    static final String EXTRA_KEY = "live_key", EXTRA_GROUP = "live_group", EXTRA_GUIDE = "live_from_guide",
            EXTRA_AUTO = "live_auto", EXTRA_PIP = "live_pip", EXTRA_REPLAY = "live_replay", EXTRA_UPRIGHT = "live_upright";
    /** The player's own switch: into picture in picture as the app is left while a channel plays (on unless turned off). */
    static final String AUTO_PIP = "auto_pip";
    /** Whether turning the phone upright leaves a full screen that turning it on its side opened ("1"; off unless set). */
    static final String UPRIGHT_BACK = "upright_back";
    private boolean pipNow;
    private static final long OVERLAY_MS = 6000, TILE_MS = 8000, TICK_MS = 30_000;
    private static final int[] SLEEP = {0, 15, 30, 60, 90};

    private final Handler h = new Handler(Looper.getMainLooper());
    /** The newer parts: the rewind's bar, the alert waiting, a goal elsewhere, the touch lock, on a TV (PlayerExtras). */
    private PlayerExtras extras;
    /** The match centre's button on the top bar, while the channel shows a game. */
    private ImageButton matchButton;
    private Library lib;
    private Playback pb;
    private Aspect.Store store;
    private Video video;
    private LinearLayout panel, top;
    private FrameLayout root;
    private TextView number, initials, name, group, nowTitle, nowTime, nextLine, sleepLine, message, zoom;
    private ImageView logo;
    /** The banner's box round the channel's logo (nothing behind a logo, or its black tile: LogoTiles). */
    private FrameLayout logoBox;
    private ProgressBar spinner, progress;
    private ImageButton star;
    private RecyclerView strip;
    private final Neighbours neighbours = new Neighbours();
    private LinearLayout level;
    private ImageView levelIcon;
    private TextView levelText;
    private ProgressBar levelBar;
    private View hint;
    /** The info tile over the picture (the Full-screen TV Info Tiles canvas), its programme, and the style chosen. */
    private FrameLayout tiles;
    private TileData.Info info;
    /**
     * On Automatic (Settings' "Scoreboard over matches"): the match the player found on this channel itself
     * (OverMatches), for the board where the tile has no game; whether a look is under way, and when the last began.
     */
    private Game found;
    private boolean finding;
    private long lookedAt;
    private TileKind.Style style = TileKind.Style.AUTO;
    /** The programme last shown, so a new one starting brings its tile up. */
    private String seen;
    private BottomSheetDialog sheet;
    /** The automatic subtitles over the picture, and what makes them (LiveCaptions). */
    private com.gathin.tvhomephone.captions.CaptionView captionView;
    private LiveCaptions captions;
    /** Over the picture while the channel is cast to a TV or a device. */
    private CastViews.Cover castCover;
    private final com.gathin.tvhomephone.cast.Casting.Listener castWatch = this::castChanged;
    private List<Channel> order = new ArrayList<>();
    private int pos;
    /** The quick channel card a swipe brings (ChannelSwipe), and the veil the picture slides under as it changes. */
    private ChannelCard card;
    private View veil;
    /** A swipe is changing channel: its card shows, not the info tile. */
    private boolean swiped;
    /** The guide panel's strip: the channels either side (false) or Recently watched (Recents). */
    private boolean stripRecent;
    private List<Channel> recentList = new ArrayList<>();
    private TextView tabChannels, tabRecent, backTo;
    private Channel backChannel;
    private Channel cur;
    private Aspect aspect = Aspect.FIT;
    private long sleepAt;
    private int sleepMinutes;
    private String centred;
    /** Opened from the guide (its stream goes back there on the way out); opened by turning the phone on its side. */
    private boolean fromGuide, auto, handBack, landscape;
    /**
     * Opened with the guide's full screen button, the phone upright: full screen starts upright, and goes on its side
     * (and back) only when the phone is turned (Turn), whichever way the phone's own rotation is set.
     */
    private boolean upright, turnedSideways;
    private final Turn turn = new Turn();
    private android.view.OrientationEventListener turner;
    /**
     * The small window asked for with its button (not as the app is left): once the picture is in the small window the
     * app goes to the phone's home screen, so the picture shows over other apps without a swipe away.
     */
    private boolean leaveOnPip;
    private Insets cut = Insets.NONE;
    private final Runnable hide = this::hideOverlay;
    /**
     * Every half a minute: a new programme on this channel brings its tile up, as the TV's banner does; on Automatic,
     * a match on it looked for again now and then.
     */
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            h.removeCallbacks(this);
            lookForMatch();
            // F1, UFC and boxing: their intro as their programme begins on this channel
            if (cur != null && extras != null && extras.overlay() != null) {
                extras.overlay().programmes(OverMatches.guide(lib.schedule(cur.key), System.currentTimeMillis()));
            }
            if (cur != null && !isInPictureInPictureMode() && style != TileKind.Style.PANEL) {
                TileData.Info now = TileData.now(PlayerActivity.this, lib, cur);
                if (now.onNow && seen != null && seen.startsWith(cur.key + "|") && !seen.equals(now.id()) && !overlayShown()
                        && sheet == null) showOverlay();
            }
            h.postDelayed(this, TICK_MS);
        }
    };
    private final Runnable hideLevel = () -> fade(level, false);
    private final Runnable hideZoom = () -> fade(zoom, false);
    private final Runnable sleep = () -> {
        pb.stop();
        Toast.makeText(this, R.string.live_sleeping, Toast.LENGTH_LONG).show();
        finish();
    };

    /** Opens the player on a channel (by key), swiping through the given group's list (Library.ALL for all). */
    public static void start(Context c, String key, String group) {
        start(c, key, group, false, false);
    }

    /**
     * fromGuide: opened from the Live TV tab, which gets the stream back on the way out; auto: opened by turning the
     * phone on its side, so turning it upright again goes back.
     */
    static void start(Context c, String key, String group, boolean fromGuide, boolean auto) {
        start(c, key, group, fromGuide, auto, false);
    }

    /** upright: the guide's full screen button with the phone upright, so full screen starts upright. */
    static void start(Context c, String key, String group, boolean fromGuide, boolean auto, boolean upright) {
        Intent i = new Intent(c, PlayerActivity.class).putExtra(EXTRA_KEY, key).putExtra(EXTRA_GROUP, group == null ? Library.ALL : group)
                .putExtra(EXTRA_GUIDE, fromGuide).putExtra(EXTRA_AUTO, auto).putExtra(EXTRA_UPRIGHT, upright && !auto);
        if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    /** The player back to a moment the rewind holds (an alert's "See it again"), on the channel playing. */
    static Intent replayIntent(Context c, long at) {
        Channel ch = Playback.get(c).channel();
        return intent(c, ch == null ? "" : ch.key).putExtra(EXTRA_REPLAY, at);
    }

    /** The player on one channel, from outside the app (an alert's Watch). */
    public static Intent intent(Context c, String key) {
        return new Intent(c, PlayerActivity.class).putExtra(EXTRA_KEY, key).putExtra(EXTRA_GROUP, Library.ALL)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        lib = Library.get(this);
        pb = Playback.get(this);
        store = Aspect.prefs(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Whichever way the phone is held, upright too (opened by turning it on its side: on its side it stays, orient()).
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        // Under the camera's cutout as well, so the picture reaches the very edge.
        if (Build.VERSION.SDK_INT >= 30) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            getWindow().setAttributes(lp);
        } else if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(build());
        captions = new LiveCaptions(this, captionView, new LiveCaptions.Host() {
            @Override public boolean channelHasOwn() { return ownSubtitles() != null; }

            @Override public void showOwn() {
                Track t = ownSubtitles();
                if (t != null && !t.group.isTrackSelected(t.index)) choose(t);
            }

            @Override public boolean casting() { return com.gathin.tvhomephone.cast.Casting.active(); }
        });
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.hide(WindowInsetsCompat.Type.systemBars());
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        landscape = isLandscape(getResources().getConfiguration());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                // The match centre first, then the touch lock holds, then out.
                // Moving the scoreboard first (Back keeps where it was put), then the match centre, then the touch
                // lock holds, then out.
                if (extras.endAdjust()) return;
                // A card picked on the ad-break screen: let go first.
                if (extras.adBreakBack()) return;
                // The line-ups out beside the scoreboard: Back closes them, the channel playing on.
                if (extras.closeLineups()) return;
                if (extras.matchShown()) extras.closeMatch();
                else if (!extras.locked()) close();
            }
        });
        if (!take(getIntent())) {
            finish();
            return;
        }
        orient();
        SharedPreferences p = prefs();
        style = TileKind.Style.of(p.getString("tile_style", TileKind.Style.AUTO.name()));
        if (!p.getBoolean("hint_seen", false)) h.postDelayed(this::showHint, 700);
    }

    /** The ad-break screen's cards take the remote's arrows once Down has gone into them. */
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (extras != null && extras.adBreakKey(e)) return true;
        return super.dispatchKeyEvent(e);
    }

    private SharedPreferences prefs() {
        return getApplicationContext().getSharedPreferences("live_player", Context.MODE_PRIVATE);
    }

    private static boolean isLandscape(Configuration cfg) {
        return cfg.orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (take(intent) && pb.isFront(host)) watch(cur);
        orient();
        replayFrom(intent);
        // Left from the Live TV guide while this was open behind it: into the small window.
        if (pipNow && !isInPictureInPictureMode()) {
            pipNow = false;
            h.post(this::enterPip);
        }
    }

    /** The channel and its list from the intent; false when the channel is gone from the playlist. */
    private boolean take(Intent i) {
        Channel c = lib.byKey(i.getStringExtra(EXTRA_KEY));
        if (c == null) {
            Toast.makeText(this, R.string.live_gone, Toast.LENGTH_LONG).show();
            return false;
        }
        fromGuide = i.getBooleanExtra(EXTRA_GUIDE, false);
        auto = i.getBooleanExtra(EXTRA_AUTO, false);
        boolean wasUpright = upright;
        upright = !auto && i.getBooleanExtra(EXTRA_UPRIGHT, false);
        if (upright && !wasUpright) turnedSideways = false;
        pipNow = i.getBooleanExtra(EXTRA_PIP, false);
        String g = i.getStringExtra(EXTRA_GROUP);
        order = lib.list(g == null ? Library.ALL : g, "");
        if (!order.contains(c)) order = lib.list(Library.ALL, "");
        if (!order.contains(c)) {
            // A hidden channel opened by name (from Sports): it plays, the others either side.
            order = new ArrayList<>(order);
            order.add(0, c);
        }
        pos = Math.max(0, order.indexOf(c));
        cur = c;
        neighbours.notifyDataSetChangedSafe();
        return true;
    }

    @Override protected void onStart() {
        super.onStart();
        if (cur == null) return;
        // Takes the picture over (the same stream carries on when the guide was already playing this channel).
        pb.take(host);
        watch(cur);
        com.gathin.tvhomephone.cast.Casting.listen(castWatch);
        if (!isInPictureInPictureMode()) captions.resume();
        // Its shape for the small window now (a stream taken over has told its size already).
        if (pipSupported()) setPictureInPictureParams(pipParams());
        h.postDelayed(tick, TICK_MS);
        extras.start();
        replayFrom(getIntent());
        // Opened as the app was left from the Live TV guide: straight into the small window.
        if (pipNow) {
            pipNow = false;
            h.post(this::enterPip);
        }
    }

    /** "See it again": back to the moment, once the stream is this channel's. */
    private void replayFrom(Intent i) {
        long at = i == null ? 0 : i.getLongExtra(EXTRA_REPLAY, 0);
        if (at <= 0) return;
        i.removeExtra(EXTRA_REPLAY);
        h.postDelayed(() -> {
            if (!pb.replay(at)) Toast.makeText(this, R.string.live3_rewind_none, Toast.LENGTH_SHORT).show();
        }, 300);
    }

    @Override protected void onResume() {
        super.onResume();
        watchTurns(true);
    }

    @Override protected void onPause() {
        // Also as it goes into the small window: no turning round there.
        watchTurns(false);
        super.onPause();
    }

    @Override protected void onStop() {
        super.onStop();
        com.gathin.tvhomephone.cast.Casting.unlisten(castWatch);
        captions.pause();
        h.removeCallbacks(tick);
        if (!isInPictureInPictureMode()) extras.stop();
        // Still playing in the small window.
        if (isInPictureInPictureMode()) return;
        // Back to the guide it came from: the stream plays on there. Otherwise (home, the screen off) it stops.
        pb.leave(host, isFinishing() && handBack);
    }

    @Override protected void onDestroy() {
        if (extras != null) extras.stop();
        LiveAlerts.watching(null, null);
        pb.leave(host, handBack);
        if (sheet != null) sheet.dismiss();
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /**
     * The information panel's "TV guide": the Live TV tab's guide, this channel going on in the picture at its top
     * (handed back as from full screen), wherever the player was opened from.
     */
    private void openGuide() {
        hideOverlay();
        handBack = true;
        Intent i = new Intent(this, com.gathin.tvhomephone.MainActivity.class)
                .putExtra(com.gathin.tvhomephone.MainActivity.EXTRA_OPEN, com.gathin.tvhomephone.MainActivity.OPEN_LIVE)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    /** Back, ✕ or turning upright again: to the guide, which keeps playing when it was playing before. */
    private void close() {
        handBack = fromGuide;
        finish();
    }

    @Override public void onConfigurationChanged(@NonNull Configuration cfg) {
        super.onConfigurationChanged(cfg);
        if (isInPictureInPictureMode()) return;
        turned(isLandscape(cfg));
    }

    /** Whether turning upright again leaves the full screen that turning on its side opened (off unless switched on). */
    static boolean uprightBack(Context c) {
        return "1".equals(Aspect.prefs(c).get(UPRIGHT_BACK));
    }

    /**
     * Opened by turning the phone on its side: it stays on its side (either way round) until left with back or the
     * cross, so lying down with it, or tilting it, never turns it upright and out to the guide. Upright too otherwise,
     * or when turning upright is to leave it (Options). Opened with the guide's full screen button, upright: it starts
     * upright and goes on its side when the phone is turned (and back when it is turned upright again). From anywhere
     * else it follows the phone.
     */
    private void orient() {
        if (auto) {
            setRequestedOrientation(uprightBack(this) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else if (upright) {
            setRequestedOrientation(turnedSideways ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }
    }

    /** Full screen opened upright: watching for the phone being turned on its side, and upright again. */
    private void watchTurns(boolean on) {
        if (!on || !upright) {
            if (turner != null) turner.disable();
            return;
        }
        if (turner == null) {
            turner = new android.view.OrientationEventListener(this, android.hardware.SensorManager.SENSOR_DELAY_UI) {
                @Override public void onOrientationChanged(int angle) {
                    int now = turn.feed(angle, android.os.SystemClock.elapsedRealtime());
                    if (now == Turn.NONE || !upright || isInPictureInPictureMode()) return;
                    boolean side = now == Turn.SIDEWAYS;
                    if (side == turnedSideways) return;
                    turnedSideways = side;
                    orient();
                }
            };
        }
        if (turner.canDetectOrientation()) turner.enable();
    }

    /** Turned round: back to the guide when turning it on its side opened this (and upright leaves); else the picture's shape. */
    private void turned(boolean land) {
        if (land == landscape) return;
        landscape = land;
        if (auto && !land && uprightBack(this)) {
            close();
            return;
        }
        if (cur != null) {
            aspect = Aspect.get(store, cur.key, landscape);
            video.apply(aspect);
        }
        if (panelShown()) centreStrip();
        if (tilesShown()) drawTile(false);
    }

    // ---- playing ----------------------------------------------------------------------------------------------

    /** The shared player's host here: its state shown over the picture. */
    private final Playback.Host host = new Playback.Host() {
        @Override public PlayerView view() { return video; }

        @Override public boolean full() { return true; }

        @Override public void onState(int state) {
            spinner.setVisibility(state == Playback.LOADING || state == Playback.RECONNECTING ? View.VISIBLE : View.GONE);
            if (state == Playback.RECONNECTING) {
                message.setText(R.string.live_reconnecting);
                message.setVisibility(View.VISIBLE);
            } else if (state == Playback.FAILED) {
                message.setText(R.string.live_not_playing);
                message.setVisibility(View.VISIBLE);
            } else if (pb.paused()) {
                message.setText(R.string.live_paused);
                message.setVisibility(View.VISIBLE);
            } else {
                message.setVisibility(View.GONE);
            }
        }

        @Override public void onVideoSize(@NonNull VideoSize size) {
            if (pipSupported()) setPictureInPictureParams(pipParams());
        }

        @Override public void onTakenOver() {
            // The guide plays something itself now (one stream at a time): this small window goes.
            finish();
        }

        @Override public void onNotice(String words) {
            if (!isInPictureInPictureMode()) Toast.makeText(PlayerActivity.this, words, Toast.LENGTH_LONG).show();
        }

        @Override public void onReplay(boolean replaying) {
            extras.onReplay(replaying);
        }

        @Override public void onFirstFrame() {
            // The channel's tracks are known now: its own subtitles, or the automatic ones.
            if (captions != null && !isInPictureInPictureMode()) captions.check();
        }
    };

    // ---- casting ----------------------------------------------------------------------------------------------

    /** Casting started or stopped elsewhere (the sheet, Live TV's bar), or how it is going changed. */
    private void castChanged() {
        if (com.gathin.tvhomephone.cast.Casting.active()) {
            if (pb.player() != null) pb.stop();
            captions.pause();
            showCastCover();
            return;
        }
        if (castCover == null) return;
        root.removeView(castCover);
        castCover = null;
        // Stopped: the channel back on the phone.
        if (!isFinishing() && cur != null) {
            pb.take(host);
            watch(cur);
            captions.resume();
        }
    }

    private void showCastCover() {
        if (castCover != null) return;
        castCover = new CastViews.Cover(this, () -> CastSheet.show(this, cur), this::close);
        castCover.setPadding(cut.left, cut.top, cut.right, cut.bottom);
        // Under the top bar's buttons (✕ stays to hand), over the picture and the panel.
        root.addView(castCover, Math.max(0, root.indexOfChild(top)), new FrameLayout.LayoutParams(-1, -1));
        hideOverlay();
    }

    /**
     * The channel's own subtitles, when it plainly sends some: DVB subtitles, the stream's subtitle playlists, or
     * captions it names (a language or a label). HLS gets a captions track assumed for every stream, with or without
     * captions in it, so a bare one doesn't count. Null for none.
     */
    private Track ownSubtitles() {
        for (Track t : tracks(C.TRACK_TYPE_TEXT)) {
            Format f = t.group.getTrackFormat(t.index);
            boolean cc = MimeTypes.APPLICATION_CEA608.equals(f.sampleMimeType) || MimeTypes.APPLICATION_CEA708.equals(f.sampleMimeType);
            boolean named = f.language != null && !f.language.isEmpty() && !"und".equals(f.language)
                    || f.label != null && !f.label.trim().isEmpty();
            if (!cc || named) return t;
        }
        return null;
    }

    /** Plays a channel here (the same one already open carries on): its shape, the panel. */
    private void watch(Channel ch) {
        if (ch == null) return;
        if (com.gathin.tvhomephone.cast.Casting.active()) {
            // Cast to a TV or a device: the channel goes there, and the phone shows where it is.
            Channel there = com.gathin.tvhomephone.cast.Casting.channel();
            cur = ch;
            if (there == null || !there.key.equals(ch.key)) com.gathin.tvhomephone.cast.Casting.change(this, CastSheet.what(this, ch));
            if (pb.player() != null) pb.stop();
            showCastCover();
            return;
        }
        cur = ch;
        boolean same = pb.live(ch);
        if (!same && captions != null) captions.channelChanged();
        pb.play(ch);
        if (!same) {
            extras.game(null);
            found = null;
            lookedAt = 0;
            if (matchButton != null) matchButton.setVisibility(View.GONE);
            extras.closeMatch();
        }
        // Held (the audio went elsewhere for a while): full screen is for watching.
        if (pb.paused()) pb.resume();
        aspect = Aspect.get(store, ch.key, landscape);
        video.apply(aspect);
        if (swiped) {
            // A swipe: its channel card says what's on; the panel, when up, follows the change; the tile goes.
            if (panelShown()) showPanel();
            else if (tilesShown()) hideOverlay();
        } else if (!same || !overlayShown()) showOverlay();
        lookForMatch();
    }

    /**
     * The ad-break screen's session on the channel playing (AdBreak.Session), looked for on its own as the channel starts
     * and every two minutes, off the main thread; null while there is none (or not yet known). In turn: the listings
     * (the channel's own guide: the programme on now, or the next, naming one of today's games: its whole time counts);
     * else a game found another way (the tile's, the scoreboard's, the channel's name, the games' channels): the hour
     * before kick-off to half an hour after full time. ScoreOverlay adds the general one (no game, the broadcaster's
     * scoreboard seen) itself.
     */
    private AdBreak.Session adSession;
    private String adKey;
    private long adLooked;
    private boolean adFinding;

    private AdBreak.Session adSession() {
        long now = System.currentTimeMillis();
        if (cur == null) return null;
        if (!cur.key.equals(adKey)) adSession = null;
        if (adFinding || (cur.key.equals(adKey) && now - adLooked < OverMatches.LOOK_MS)) return adSession;
        Channel ch = cur;
        List<OverMatches.Programme> guide = OverMatches.guide(lib.schedule(ch.key), now, AdBreak.NEXT_WITHIN_MS);
        adKey = ch.key;
        adLooked = now;
        if (!OverMatches.worthLooking(ch.name, ch.group, guide, com.gathin.tvhomephone.sports.Channels.british())) {
            adSession = null;
            return null;
        }
        Game tile = channelGame();
        adFinding = true;
        App.io.execute(() -> {
            AdBreak.Session got = null;
            try {
                List<Game> today = com.gathin.tvhomephone.sports.OnAir.today(OverMatches.FRESH_MS);
                got = AdBreak.fromListings(guide, today, now);
                if (got == null) {
                    List<Game> on = AdBreak.candidates(today, now);
                    Game g = null;
                    if (tile != null && tile.id != null) for (Game x : on) if (tile.id.equals(x.id)) g = x;
                    java.util.Map<String, List<String>> none = java.util.Collections.emptyMap();
                    if (g == null) g = OverMatches.recogniseAmong(ch.name, guide, on, none, none);
                    if (g == null && !on.isEmpty()) {
                        java.util.Map<String, List<String>> own = new java.util.HashMap<>(), usual = new java.util.HashMap<>();
                        for (Game x : on) {
                            List<String> mine = com.gathin.tvhomephone.sports.Channels.own(x), all = com.gathin.tvhomephone.sports.Channels.names(x);
                            all.removeAll(mine);
                            own.put(x.id, mine);
                            usual.put(x.id, all);
                        }
                        g = OverMatches.recogniseAmong(ch.name, guide, on, own, usual);
                    }
                    got = AdBreak.fromGame(g, guide.isEmpty() ? null : guide.get(0).title, now);
                }
            } catch (RuntimeException ignored) {
                // looked for again at the next look
            }
            AdBreak.Session s = got;
            App.main.post(() -> {
                adFinding = false;
                if (cur != null && cur.key.equals(ch.key)) adSession = s;
            });
        });
        return adSession;
    }

    /** The programme on the channel playing now, from its own guide (a general session's line); null without one. */
    private String programmeNow() {
        if (cur == null) return null;
        List<OverMatches.Programme> guide = OverMatches.guide(lib.schedule(cur.key), System.currentTimeMillis(), 0);
        return guide.isEmpty() ? null : guide.get(0).title;
    }

    /** Whether the channel playing may be showing a match: a sports channel, a football channel, a fixture in its guide. */
    private boolean sportsNow() {
        if (cur == null) return false;
        List<OverMatches.Programme> guide = OverMatches.guide(lib.schedule(cur.key), System.currentTimeMillis(), 0);
        return OverMatches.worthLooking(cur.name, cur.group, guide, com.gathin.tvhomephone.sports.Channels.british());
    }

    /** The channel's game: the tile's; else, on Automatic, the match the player found on the channel itself. */
    private Game channelGame() {
        if (info != null && info.game != null) return info.game;
        return found != null && OverMatches.AUTO.equals(OverMatches.mode(this)) ? found : null;
    }

    /**
     * On Automatic: whether the channel playing shows a football match, live or within 15 minutes of kick-off
     * (OverMatches), as it starts and every two minutes; one found comes on the board by itself (its alerts waiting for
     * this stream too) where the tile has no game for it. Ask and Off: nothing looked for, as before.
     */
    private void lookForMatch() {
        if (cur == null || finding || extras == null || !OverMatches.AUTO.equals(OverMatches.mode(this))) return;
        if (info != null && info.game != null) return;
        long now = System.currentTimeMillis();
        if (lookedAt > 0 && now - lookedAt < OverMatches.LOOK_MS) return;
        Channel ch = cur;
        List<OverMatches.Programme> guide = OverMatches.guide(lib.schedule(ch.key), now);
        if (!OverMatches.worthLooking(ch.name, ch.group, guide, com.gathin.tvhomephone.sports.Channels.british())) return;
        lookedAt = now;
        finding = true;
        OverMatches.find(ch, guide, g -> {
            finding = false;
            if (isFinishing() || isDestroyed() || cur == null || !cur.key.equals(ch.key)) return;
            boolean shown = (info != null && info.game != null) || (g != null && found != null && g.id.equals(found.id));
            if (OverMatches.decide(OverMatches.mode(this), g != null, shown) != OverMatches.SHOW) return;
            found = g;
            extras.game(g);
            if (matchButton != null) matchButton.setVisibility(View.VISIBLE);
        });
    }

    private void step(int by) {
        if (order == null || order.isEmpty()) return;
        // Hidden channels are passed over (one hidden from the guide while this played in the small window too).
        int at = Hidden.step(order.size(), pos, by, i -> lib.isHidden(order.get(i)));
        if (at < 0 || at == pos) return;
        pos = at;
        watch(order.get(pos));
    }

    /**
     * A swipe's change of channel (ChannelSwipe): the next or previous in the category (hidden ones passed over), the
     * picture sliding out the way the finger went and the new one in from the other side under a brief veil, and the
     * quick channel card.
     */
    private void swipeTo(int direction) {
        if (order == null || order.isEmpty() || cur == null) return;
        int at = Hidden.step(order.size(), pos, ChannelSwipe.step(direction), i -> lib.isHidden(order.get(i)));
        if (at < 0 || at == pos) return;
        pos = at;
        Channel next = order.get(pos);
        slide(direction);
        swiped = true;
        try {
            watch(next);
        } finally {
            swiped = false;
        }
        // The guide panel up already says what's on (it followed the change): the card only over the picture.
        if (!panelShown()) card.show(next, direction);
    }

    /** The picture slides the way the finger went and back in from the other side, a veil over it while it changes. */
    private void slide(int direction) {
        float w = Math.max(1, root.getWidth()) * 0.16f, d = direction == ChannelSwipe.NEXT ? -1 : 1;
        video.animate().cancel();
        veil.animate().cancel();
        veil.setVisibility(View.VISIBLE);
        veil.animate().alpha(0.62f).setDuration(120).start();
        video.animate().translationX(d * w).setDuration(120).setInterpolator(TvLook.ease2()).withEndAction(() -> {
            video.setTranslationX(-d * w);
            video.animate().translationX(0).setDuration(300).setInterpolator(TvLook.ease()).start();
            veil.animate().alpha(0f).setStartDelay(160).setDuration(280)
                    .withEndAction(() -> veil.setVisibility(View.GONE)).start();
        }).start();
    }

    /** What stops a swipe changing channel now (ChannelSwipe.State). */
    private ChannelSwipe.State swipeState() {
        ChannelSwipe.State s = new ChannelSwipe.State();
        s.off = !ChannelSwipe.on(this);
        s.pip = isInPictureInPictureMode();
        s.replaying = pb.replaying();
        s.adjusting = extras != null && extras.adjusting();
        s.locked = extras != null && extras.locked();
        s.matchOpen = extras != null && extras.matchShown();
        s.casting = com.gathin.tvhomephone.cast.Casting.active();
        return s;
    }

    /** Straight to a channel (from the mini guide or Similar channels). */
    private void switchTo(Channel ch) {
        if (ch == null) return;
        int i = order.indexOf(ch);
        if (i < 0) {
            order = lib.list(Library.ALL, "");
            neighbours.notifyDataSetChangedSafe();
            i = order.indexOf(ch);
        }
        if (i < 0) return;
        pos = i;
        watch(ch);
    }

    /** The stream again from the start (after it stalls, or to catch up with live). */
    private void restart() {
        pb.restart();
    }

    // ---- the picture's shape --------------------------------------------------------------------------------------

    private void setAspect(Aspect a, boolean announce) {
        if (cur == null) return;
        aspect = a;
        Aspect.set(store, cur.key, landscape, a);
        video.apply(a);
        if (announce) {
            zoom.setText(aspectLabel(a));
            fade(zoom, true);
            h.removeCallbacks(hideZoom);
            h.postDelayed(hideZoom, 900);
        }
    }

    private int aspectLabel(Aspect a) {
        switch (a) {
            case FILL: return R.string.live_fill;
            case STRETCH: return R.string.live_stretch;
            case WIDE: return R.string.live_wide;
            case FOUR_THREE: return R.string.live_four_three;
            default: return R.string.live_fit;
        }
    }

    // ---- picture in picture -----------------------------------------------------------------------------------

    private boolean pipSupported() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    private PictureInPictureParams pipParams() {
        Rational r = new Rational(16, 9);
        ExoPlayer player = pb.player();
        if (player != null) {
            VideoSize v = player.getVideoSize();
            if (v.width > 0 && v.height > 0) {
                float ratio = v.width * v.pixelWidthHeightRatio / v.height;
                if (ratio > 0.42f && ratio < 2.38f) r = new Rational(Math.round(v.width * v.pixelWidthHeightRatio), v.height);
            }
        }
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder().setAspectRatio(r);
        // Android 12 and later: into the small window on its own as the app is left (unless turned off in the options).
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(autoPip(this));
        return b.build();
    }

    /** Whether leaving the app while a channel plays takes it into picture in picture. */
    static boolean autoPip(Context c) {
        return !"0".equals(Aspect.prefs(c).get(AUTO_PIP));
    }

    /** Whether Android lets the app open a small window (a phone's settings can turn it off for an app). */
    private boolean pipAllowed() {
        if (!pipSupported()) return false;
        try {
            android.app.AppOpsManager ops = getSystemService(android.app.AppOpsManager.class);
            if (ops == null) return true;
            int mode = Build.VERSION.SDK_INT >= 29
                    ? ops.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), getPackageName())
                    : ops.checkOpNoThrow(android.app.AppOpsManager.OPSTR_PICTURE_IN_PICTURE, android.os.Process.myUid(), getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED || mode == android.app.AppOpsManager.MODE_DEFAULT;
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Into the small window; false when it can't go (no small windows here, or turned off for the app). */
    private boolean enterPip() {
        if (!pipSupported() || pb.player() == null) return false;
        if (!pipAllowed()) {
            pipOff();
            return false;
        }
        try {
            return enterPictureInPictureMode(pipParams());
        } catch (IllegalStateException e) {
            // Not allowed here (a TV, or turned off for the app): it just keeps playing full screen.
            pipOff();
            return false;
        }
    }

    /** The picture in picture button: into the small window, and off the app to the home screen, the picture over it. */
    private void pipAndLeave() {
        leaveOnPip = true;
        if (!enterPip()) leaveOnPip = false;
    }

    /** The phone's home screen, with the small window over it. */
    private void goHome() {
        if (!isInPictureInPictureMode()) return;
        try {
            startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
            // no home screen to go to: the app stays behind the small window
        }
    }

    /** Picture in picture is turned off for the app on this phone: says so, with the way to turn it on. */
    private void pipOff() {
        if (isFinishing()) return;
        new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.live_pip_off_title)
                .setMessage(R.string.live_pip_off)
                .setPositiveButton(R.string.live_pip_settings, (d, w) -> {
                    Intent i = new Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS", android.net.Uri.parse("package:" + getPackageName()));
                    try {
                        startActivity(i);
                    } catch (RuntimeException e) {
                        startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:" + getPackageName())));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT < 31 && autoPip(this) && pipAllowed() && pb.player() != null && pb.player().isPlaying()) enterPip();
    }

    @Override public void onPictureInPictureModeChanged(boolean inPip, @NonNull Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        extras.pip(inPip);
        if (inPip) captions.pause();
        if (inPip) {
            hideOverlay();
            // The small window has the picture alone: no channel card, the picture back in its place.
            if (card != null) card.out(false);
            video.animate().cancel();
            video.setTranslationX(0);
            if (veil != null) {
                veil.animate().cancel();
                veil.setVisibility(View.GONE);
            }
            // Asked for with the button: off the app, so the picture is over the home screen and other apps.
            if (leaveOnPip) {
                leaveOnPip = false;
                h.postDelayed(this::goHome, 150);
            }
            if (sheet != null) sheet.dismiss();
            if (hint != null) hint.setVisibility(View.GONE);
            level.setVisibility(View.GONE);
            zoom.setVisibility(View.GONE);
            message.setVisibility(View.GONE);
        } else if (getLifecycle().getCurrentState() == Lifecycle.State.CREATED) {
            // The small window was closed: the stream stops with it.
            finish();
        } else {
            // Back to full screen, perhaps turned round meanwhile.
            turned(isLandscape(getResources().getConfiguration()));
            captions.resume();
        }
    }

    // ---- the guide panel ------------------------------------------------------------------------------------------

    /** What's on: the info tile in the style chosen (Auto picks by the programme), or the guide panel. */
    private void showOverlay() {
        if (cur == null || isInPictureInPictureMode()) return;
        if (style == TileKind.Style.PANEL) showPanel();
        else showTile();
    }

    /** The info tile for the programme on now; the game, the film's details and the posters follow as they are read. */
    private void showTile() {
        if (cur == null || isInPictureInPictureMode()) return;
        if (panel.getVisibility() == View.VISIBLE) fade(panel, false);
        // The tile says it all: a swipe's card goes.
        if (card != null) card.out(true);
        TileData.Info fresh = TileData.now(this, lib, cur);
        boolean same = info != null && info.enriched && info.id().equals(fresh.id());
        boolean animate = !tilesShown();
        if (!same) info = fresh;
        marks(info);
        seen = info.id();
        drawTile(animate);
        // Read again: always for another programme, and for a game (its score moves on).
        if (!same || info.kind == TileKind.Kind.MATCH) {
            TileData.Info asked = info;
            // A game's tile read again: its new score and stats wait as long as the picture runs behind (ScreenTime),
            // so the tile never tells what the screen hasn't shown yet.
            long wait = same && asked.enriched ? ScreenTime.behindMs() : 0;
            TileData.enrich(this, asked, done -> h.postDelayed(() -> {
                if (isFinishing() || info == null || !done.id().equals(info.id())) return;
                info = done;
                // The tile's game, else (on Automatic) the match found on the channel: as it was on Ask.
                extras.game(channelGame());
                matchButton.setVisibility(channelGame() != null ? View.VISIBLE : View.GONE);
                marks(info);
                if (tilesShown()) drawTile(false);
            }, wait));
        }
        fade(tiles, true);
        fade(top, true);
        keepPanel();
    }

    /** The tile drawn again in its place (the style, the way the phone is held, what has been read). */
    private void drawTile(boolean animate) {
        if (info == null) return;
        TileKind.Style s = style == TileKind.Style.AUTO ? TileKind.auto(info.kind) : style;
        if (s == TileKind.Style.PANEL || s == TileKind.Style.AUTO) s = TileKind.Style.RIBBON;
        tiles.removeAllViews();
        tiles.addView(InfoTile.build(this, info, s, wideTile(), tileActions, animate), new FrameLayout.LayoutParams(-1, -1));
    }

    /**
     * Tests (the player's options): the info tile in this style now, on what's on, made-up parts where it is not of that
     * kind (PlayerTests.tileInfo); the guide panel for PANEL. The style chosen stays, and the next time the tile comes
     * it is the real one again.
     */
    private void testTile(TileKind.Style s) {
        if (cur == null || isInPictureInPictureMode()) return;
        if (s == TileKind.Style.PANEL || s == TileKind.Style.AUTO) {
            showPanel();
            return;
        }
        TileData.Info t = PlayerTests.tileInfo(info != null ? info : TileData.now(this, lib, cur), s,
                com.gathin.tvhomephone.sports.TestAlerts.sample(this, null));
        marks(t);
        if (panel.getVisibility() == View.VISIBLE) fade(panel, false);
        tiles.removeAllViews();
        tiles.addView(InfoTile.build(this, t, s, wideTile(), tileActions, true), new FrameLayout.LayoutParams(-1, -1));
        fade(tiles, true);
        fade(top, true);
        keepPanel();
    }

    /** Across the picture on its side or on a tablet; stacked upright on a phone. */
    private boolean wideTile() {
        return landscape || getResources().getConfiguration().screenWidthDp >= 600;
    }

    /** The stream's own marks for the tile: its picture (4K, 1080p…), HDR, its sound, S when it carries subtitles. */
    private void marks(TileData.Info i) {
        ExoPlayer player = pb.player();
        i.hd = player != null && player.getVideoSize().height >= 720;
        i.subtitles = !tracks(C.TRACK_TYPE_TEXT).isEmpty();
        i.video = player == null ? "" : MediaTags.video(player.getVideoFormat());
        i.hdr = player == null ? "" : MediaTags.hdr(player.getVideoFormat());
        i.audio = player == null ? "" : MediaTags.audio(player.getAudioFormat());
    }

    private boolean tilesShown() {
        return tiles != null && tiles.getVisibility() == View.VISIBLE && tiles.getAlpha() > 0.5f;
    }

    private boolean panelShown() {
        return panel.getVisibility() == View.VISIBLE && panel.getAlpha() > 0.5f;
    }

    /** The tile's buttons. */
    private final InfoTile.Actions tileActions = new InfoTile.Actions() {
        @Override public void restart() {
            PlayerActivity.this.restart();
        }

        @Override public void favourite() {
            toggleFavourite();
        }

        @Override public boolean isFavourite() {
            return cur != null && Picks.isFavourite(PlayerActivity.this, cur.key);
        }

        @Override public void similar() {
            similarSheet();
        }

        @Override public void info() {
            hideOverlay();
            PlayerActivity.this.info();
        }

        @Override public boolean remind(TileData.Info i) {
            if (i == null || i.nextTitle.isEmpty()) return false;
            Context c = PlayerActivity.this;
            boolean on = Reminders.toggle(c, i.ch, i.nextTitle, i.nextStart);
            Toast.makeText(c, on ? getString(R.string.live_reminder_set, i.nextTitle, Ui.day(c, i.nextStart), Ui.clock(i.nextStart))
                    : getString(R.string.live_reminder_removed), Toast.LENGTH_SHORT).show();
            return on;
        }

        @Override public boolean reminded(TileData.Info i) {
            return i != null && !i.nextTitle.isEmpty() && Reminders.has(PlayerActivity.this, i.ch, i.nextStart);
        }

        @Override public void step(int by) {
            PlayerActivity.this.step(by);
        }

        @Override public String neighbour(int by) {
            if (order == null || order.isEmpty()) return "";
            int at = Hidden.step(order.size(), pos, by, i -> lib.isHidden(order.get(i)));
            if (at < 0 || at == pos) return "";
            Channel ch = order.get(at);
            int n = lib.number(ch);
            return n > 0 ? String.valueOf(n) : ch.name;
        }

        @Override public void matchCentre(Game g) {
            hideOverlay();
            startActivity(GameActivity.intent(PlayerActivity.this, g));
        }

        @Override public void guide() {
            openGuide();
        }

        @Override public void alsoShowing(Game g) {
            hideOverlay();
            AlsoShowing.open(PlayerActivity.this, g, cur, ch -> {
                if (ch != cur) switchTo(ch);
            });
        }
    };

    /** The guide panel: the channel, now and next, and the mini guide of the channels either side. */
    private void showPanel() {
        if (cur == null || isInPictureInPictureMode()) return;
        if (tiles.getVisibility() == View.VISIBLE) fade(tiles, false);
        int n = lib.number(cur);
        number.setText(n > 0 ? String.valueOf(n) : "");
        number.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        name.setText(cur.name);
        group.setText(cur.group);
        group.setVisibility(cur.group.isEmpty() ? View.GONE : View.VISIBLE);
        initials.setText(cur.logo.isEmpty() ? Ui.initials(cur.name) : "");
        // The logo on a tile in its own colour, with nothing behind it, or on black (LogoTiles); no logo, its initials in
        // the soft box.
        int lp = LogoTiles.pad(this, 5);
        logo.setPadding(lp, lp, lp, lp);
        if (cur.logo.isEmpty()) {
            logoBox.setBackground(TvLook.glass(this, 12));
            Glide.with(logo).clear(logo);
        } else {
            LogoTiles.show(logoBox, 12, false, logo, cur.logo, TvLook.GROUND, null);
        }
        markStar();
        Guide.Schedule s = lib.schedule(cur.key);
        long now = System.currentTimeMillis();
        int at = s == null ? -1 : s.at(now), next = s == null ? -1 : s.after(now);
        if (at >= 0) {
            nowTitle.setText(s.title[at].isEmpty() ? cur.name : s.title[at]);
            nowTime.setText(Ui.range(s.start[at], s.stop[at]));
            nowTime.setVisibility(View.VISIBLE);
            progress.setVisibility(View.VISIBLE);
            progress.setProgress((int) (1000 * (now - s.start[at]) / Math.max(1, s.stop[at] - s.start[at])));
        } else {
            nowTitle.setText(R.string.live_no_guide);
            nowTime.setVisibility(View.GONE);
            progress.setVisibility(View.INVISIBLE);
        }
        if (next >= 0) {
            nextLine.setText(getString(R.string.live_next_at, Ui.clock(s.start[next]), s.title[next]));
            nextLine.setVisibility(View.VISIBLE);
        } else nextLine.setVisibility(View.GONE);
        if (sleepAt > 0) {
            sleepLine.setText(getString(R.string.live_sleep_in, Math.max(1, (int) ((sleepAt - now + 59_999) / 60_000))));
            sleepLine.setVisibility(View.VISIBLE);
        } else sleepLine.setVisibility(View.GONE);
        boolean was = panel.getVisibility() == View.VISIBLE && panel.getAlpha() > 0.5f;
        if (card != null) card.out(false);
        showRecent();
        neighbours.notifyDataSetChangedSafe();
        // The mini guide opens on this channel (and follows a change of channel); otherwise it stays where it was swiped to.
        if (!was || !cur.key.equals(centred)) {
            centred = cur.key;
            centreStrip();
        }
        fade(panel, true);
        fade(top, true);
        keepPanel();
    }

    private void centreStrip() {
        strip.post(() -> {
            LinearLayoutManager lm = (LinearLayoutManager) strip.getLayoutManager();
            if (lm != null) lm.scrollToPositionWithOffset(stripRecent ? 0 : pos, stripRecent ? 0 : Math.max(0, (strip.getWidth() - Ui.dp(this, 216)) / 2));
        });
    }

    private void keepPanel() {
        h.removeCallbacks(hide);
        if (tiles.getVisibility() != View.VISIBLE) {
            h.postDelayed(hide, OVERLAY_MS);
            return;
        }
        // The info tile stays as long as chosen in the options, or until a swipe down takes it away (TILE_UNTIL_SWIPE).
        long ms = tileMs();
        if (ms > 0) h.postDelayed(hide, ms);
    }

    /** How long the info tile stays: the seconds chosen in the options (8 at first), or 0 for until it is swiped down. */
    private long tileMs() {
        int s = prefs().getInt(TILE_SECONDS, (int) (TILE_MS / 1000));
        return s <= 0 ? 0 : s * 1000L;
    }

    /** The info tile's time in the options: seconds, or 0 for until it is swiped down. */
    static final String TILE_SECONDS = "tile_seconds";
    static final int[] TILE_TIMES = {4, 8, 15, 30, 0};

    private void hideOverlay() {
        h.removeCallbacks(hide);
        fade(panel, false);
        fade(tiles, false);
        fade(top, false);
    }

    private boolean overlayShown() {
        return panelShown() || tilesShown();
    }

    private static void fade(View v, boolean in) {
        if (v == null) return;
        v.animate().cancel();
        if (in) {
            if (v.getVisibility() != View.VISIBLE) {
                v.setAlpha(0f);
                v.setVisibility(View.VISIBLE);
            }
            v.animate().alpha(1f).setDuration(160).start();
        } else {
            v.animate().alpha(0f).setDuration(220).withEndAction(() -> v.setVisibility(View.GONE)).start();
        }
    }

    private void markStar() {
        boolean fav = cur != null && Picks.isFavourite(this, cur.key);
        star.setImageResource(fav ? R.drawable.live_ic_star : R.drawable.live_ic_star_outline);
        star.setImageTintList(ColorStateList.valueOf(fav ? Ui.STAR : Ui.TEXT));
        star.setContentDescription(getString(fav ? R.string.live_fav_remove : R.string.live_fav_add));
    }

    private void toggleFavourite() {
        if (cur == null) return;
        boolean on = Picks.toggle(this, cur.key);
        Toast.makeText(this, getString(on ? R.string.live_fav_added : R.string.live_fav_removed, cur.name), Toast.LENGTH_SHORT).show();
        markStar();
    }

    /** The mini guide: the channels either side with their now and next; tap one to change channel. */
    private final class Neighbours extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public int getItemCount() {
            List<Channel> items = stripItems();
            return items == null ? 0 : items.size();
        }

        @SuppressLint("NotifyDataSetChanged")
        void notifyDataSetChangedSafe() {
            notifyDataSetChanged();
        }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.dp(c, 208), -1);
            lp.rightMargin = Ui.dp(c, 8);
            card.setLayoutParams(lp);
            LinearLayout head = new LinearLayout(c);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView num = TvLook.text(c, "", 12, TvLook.L2, true);
            num.setPadding(0, 0, Ui.dp(c, 8), 0);
            head.addView(num);
            FrameLayout box = new FrameLayout(c);
            // The logo on a small tile in its own colour or black, a little wider for its room (LogoTiles); or as it is.
            boolean boxed = LogoTiles.boxed(c);
            TextView ini = TvLook.text(c, "", 10, Ui.SOFT, true);
            ini.setGravity(Gravity.CENTER);
            box.addView(ini, new FrameLayout.LayoutParams(-1, -1));
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            box.addView(img, new FrameLayout.LayoutParams(-1, -1));
            head.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, boxed ? 44 : 34), Ui.dp(c, boxed ? 26 : 22)));
            TextView nm = TvLook.text(c, "", 12, TvLook.L2, true);
            nm.setSingleLine(true);
            nm.setEllipsize(TextUtils.TruncateAt.END);
            nm.setPadding(Ui.dp(c, 8), 0, 0, 0);
            head.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(head);
            TextView now = TvLook.text(c, "", 14, Ui.TEXT, true);
            now.setSingleLine(true);
            now.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
            np.topMargin = Ui.dp(c, 4);
            card.addView(now, np);
            TextView next = TvLook.text(c, "", 12, TvLook.L3, false);
            next.setSingleLine(true);
            next.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(next);
            card.setTag(new View[]{num, ini, img, nm, now, next});
            RecyclerView.ViewHolder vh = new RecyclerView.ViewHolder(card) {};
            card.setOnClickListener(v -> {
                int at = vh.getBindingAdapterPosition();
                List<Channel> items = stripItems();
                if (at >= 0 && at < items.size()) {
                    if (items.get(at) == cur) hideOverlay();
                    else switchTo(items.get(at));
                }
            });
            return vh;
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Channel ch = stripItems().get(position);
            Context c = holder.itemView.getContext();
            View[] v = (View[]) holder.itemView.getTag();
            boolean me = ch == cur;
            // Cards of glass (07's), the one playing ringed in white.
            holder.itemView.setBackground(me ? Ui.ring(c, 0x26FFFFFF, Ui.TEXT, 2, 14) : TvLook.glass(c, 14));
            int n = lib.number(ch);
            ((TextView) v[0]).setText(n > 0 ? String.valueOf(n) : "");
            ((TextView) v[1]).setText(ch.logo.isEmpty() ? Ui.initials(ch.name) : "");
            ImageView img = (ImageView) v[2];
            View box = (View) img.getParent();
            int p = LogoTiles.boxed(c) ? LogoTiles.pad(c, 3) : 0;
            img.setPadding(p, p, p, p);
            ((TextView) v[0]).setTextColor(TvLook.L2);
            if (ch.logo.isEmpty()) {
                box.setBackground(null);
                Glide.with(img).clear(img);
            } else {
                // Logo's colour: the whole card (its number, logo and name) in it, a touch lighter than the tile; the one
                // playing still ringed.
                View card = holder.itemView;
                LogoTiles.show(box, 6, false, img, ch.logo, TvLook.GROUND, cols -> {
                    android.graphics.drawable.Drawable row = LogoTiles.row(c, cols, me, Ui.TEXT, 14);
                    if (row != null) {
                        card.setBackground(row);
                        ((TextView) v[0]).setTextColor(Ui.TEXT);
                    }
                }, null);
            }
            ((TextView) v[3]).setText(ch.name);
            Guide.Schedule s = lib.schedule(ch.key);
            long now = System.currentTimeMillis();
            int at = s == null ? -1 : s.at(now), nx = s == null ? -1 : s.after(now);
            ((TextView) v[4]).setText(at >= 0 && !s.title[at].isEmpty() ? s.title[at] : getString(R.string.live_no_guide_info));
            TextView next = (TextView) v[5];
            next.setText(nx >= 0 ? getString(R.string.live_next_at, Ui.clock(s.start[nx]), s.title[nx]) : "");
            holder.itemView.setContentDescription((n > 0 ? n + " " : "") + ch.name + (at >= 0 ? ", " + s.title[at] : ""));
        }
    }

    // ---- options ----------------------------------------------------------------------------------------------

    private static final class Track {
        final Tracks.Group group;
        final int index;
        final String label;

        Track(Tracks.Group group, int index, String label) {
            this.group = group;
            this.index = index;
            this.label = label;
        }
    }

    private List<Track> tracks(int type) {
        List<Track> out = new ArrayList<>();
        ExoPlayer player = pb.player();
        if (player == null) return out;
        int n = 0;
        for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() != type) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                out.add(new Track(g, i, trackLabel(type, g.getTrackFormat(i), ++n)));
            }
        }
        return out;
    }

    private String trackLabel(int type, Format f, int n) {
        if (type == C.TRACK_TYPE_VIDEO) {
            String q = f.height > 0 ? f.height + "p" : getString(R.string.live_track, n);
            if (f.bitrate > 0) q += String.format(Locale.UK, " · %.1f Mbps", f.bitrate / 1_000_000f);
            return q;
        }
        String l = f.label != null && !f.label.trim().isEmpty() ? f.label.trim() : "";
        if (l.isEmpty() && f.language != null && !f.language.isEmpty() && !"und".equals(f.language)) {
            String d = Locale.forLanguageTag(f.language).getDisplayLanguage(Locale.UK);
            l = d.isEmpty() ? f.language : d;
        }
        if (l.isEmpty() && f.sampleMimeType != null && (f.sampleMimeType.equals(MimeTypes.APPLICATION_CEA608)
                || f.sampleMimeType.equals(MimeTypes.APPLICATION_CEA708))) l = getString(R.string.live_captions);
        if (l.isEmpty()) l = getString(R.string.live_track, n);
        if (type == C.TRACK_TYPE_AUDIO && f.channelCount >= 6) l += " · 5.1";
        return l;
    }

    private boolean overridden(Track t) {
        ExoPlayer player = pb.player();
        if (player == null) return false;
        TrackSelectionOverride o = player.getTrackSelectionParameters().overrides.get(t.group.getMediaTrackGroup());
        return o != null && o.trackIndices.contains(t.index);
    }

    private void choose(Track t) {
        ExoPlayer player = pb.player();
        if (player == null) return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setOverrideForType(new TrackSelectionOverride(t.group.getMediaTrackGroup(), t.index))
                .setTrackTypeDisabled(t.group.getType(), false)
                .build());
    }

    private void automatic(int type, boolean off) {
        ExoPlayer player = pb.player();
        if (player == null) return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(type)
                .setTrackTypeDisabled(type, off)
                .build());
    }

    /** The options sheet from the panel's ⋯. */
    private void options() {
        ExoPlayer player = pb.player();
        if (cur == null || player == null) return;
        hideOverlay();
        Context c = this;
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 16));
        TextView head = Ui.text(c, cur.name, 20, Ui.TEXT, true);
        head.setSingleLine(true);
        head.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(head);

        // The picture's shape.
        List<TextView> shapes = new ArrayList<>();
        LinearLayout shapeRow = chipRow(c, box, R.string.live_picture);
        for (Aspect a : Aspect.values()) {
            TextView t = Ui.guideChip(c, getString(aspectLabel(a)), a == aspect);
            t.setOnClickListener(v -> {
                setAspect(a, false);
                for (TextView x : shapes) Ui.styleChip(c, x, x == v);
            });
            shapes.add(t);
            addChip(shapeRow, t);
        }

        // The info tile: Auto (by what's on) or one style for everything, the guide panel as before among them.
        List<TextView> looks = new ArrayList<>();
        LinearLayout lookRow = chipRow(c, box, R.string.tile_style);
        for (TileKind.Style st : TileKind.Style.values()) {
            TextView t = Ui.guideChip(c, getString(styleLabel(st)), st == style);
            t.setOnClickListener(v -> {
                style = st;
                prefs().edit().putString("tile_style", st.name()).apply();
                for (TextView x : looks) Ui.styleChip(c, x, x == v);
            });
            looks.add(t);
            addChip(lookRow, t);
        }
        TextView note = Ui.text(c, getString(R.string.tile_auto_note), 12, Ui.DIM, false);
        note.setPadding(0, Ui.dp(c, 6), 0, 0);
        box.addView(note);

        // The info tile's size (Small leaves the most of the picture) and how long it stays.
        List<TextView> sizes = new ArrayList<>();
        LinearLayout sizeRow = chipRow(c, box, R.string.tile_size);
        String sizeNow = prefs().getString(InfoTile.SIZE, "SMALL");
        int[] sizeLabels = {R.string.tile_size_small, R.string.tile_size_medium, R.string.tile_size_large};
        for (int i = 0; i < InfoTile.SIZES.length; i++) {
            String sz = InfoTile.SIZES[i];
            TextView t = Ui.guideChip(c, getString(sizeLabels[i]), sz.equals(sizeNow));
            t.setOnClickListener(v -> {
                prefs().edit().putString(InfoTile.SIZE, sz).apply();
                for (TextView x : sizes) Ui.styleChip(c, x, x == v);
                if (tilesShown()) drawTile(false);
            });
            sizes.add(t);
            addChip(sizeRow, t);
        }
        List<TextView> times = new ArrayList<>();
        LinearLayout timeRow = chipRow(c, box, R.string.tile_time);
        int timeNow = prefs().getInt(TILE_SECONDS, (int) (TILE_MS / 1000));
        for (int secs : TILE_TIMES) {
            TextView t = Ui.guideChip(c, secs > 0 ? getString(R.string.tile_time_seconds, secs) : getString(R.string.tile_time_swipe),
                    secs == timeNow);
            t.setOnClickListener(v -> {
                prefs().edit().putInt(TILE_SECONDS, secs).apply();
                for (TextView x : times) Ui.styleChip(c, x, x == v);
            });
            times.add(t);
            addChip(timeRow, t);
        }

        TrackSelectionParameters params = player.getTrackSelectionParameters();
        List<Track> audio = tracks(C.TRACK_TYPE_AUDIO);
        if (audio.size() > 1) {
            LinearLayout row = chipRow(c, box, R.string.live_audio);
            List<TextView> chips = new ArrayList<>();
            boolean anyOverride = false;
            for (Track t : audio) anyOverride |= overridden(t);
            for (Track t : audio) {
                boolean on = anyOverride ? overridden(t) : t.group.isTrackSelected(t.index);
                TextView chip = Ui.guideChip(c, t.label, on);
                chip.setOnClickListener(v -> {
                    choose(t);
                    for (TextView x : chips) Ui.styleChip(c, x, x == v);
                });
                chips.add(chip);
                addChip(row, chip);
            }
        }

        // Subtitles: off, the channel's own (when it sends any), or automatic (made on the phone from what is said).
        List<Track> text = tracks(C.TRACK_TYPE_TEXT);
        {
            LinearLayout row = chipRow(c, box, R.string.live_subtitles);
            List<TextView> chips = new ArrayList<>();
            boolean autoOn = LiveCaptions.on(c);
            boolean off = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
            boolean anySelected = false;
            for (Track t : text) anySelected |= t.group.isTrackSelected(t.index);
            TextView none = Ui.guideChip(c, getString(R.string.live_off), !autoOn && (off || !anySelected));
            none.setOnClickListener(v -> {
                automatic(C.TRACK_TYPE_TEXT, true);
                captions.set(false);
                for (TextView x : chips) Ui.styleChip(c, x, x == v);
            });
            chips.add(none);
            addChip(row, none);
            for (Track t : text) {
                String label = text.size() == 1 ? getString(R.string.captions_from_channel)
                        : getString(R.string.captions_from_channel_named, t.label);
                TextView chip = Ui.guideChip(c, label, !autoOn && !off && t.group.isTrackSelected(t.index));
                chip.setOnClickListener(v -> {
                    captions.set(false);
                    choose(t);
                    for (TextView x : chips) Ui.styleChip(c, x, x == v);
                });
                chips.add(chip);
                addChip(row, chip);
            }
            TextView auto = Ui.guideChip(c, getString(R.string.captions_automatic), autoOn);
            auto.setOnClickListener(v -> {
                if (ownSubtitles() == null) automatic(C.TRACK_TYPE_TEXT, true);
                captions.set(true);
                for (TextView x : chips) Ui.styleChip(c, x, x == v);
            });
            chips.add(auto);
            addChip(row, auto);
            TextView capNote = Ui.text(c, getString(R.string.captions_note), 12, Ui.DIM, false);
            capNote.setPadding(0, Ui.dp(c, 6), 0, 0);
            box.addView(capNote);
        }

        List<Track> video = tracks(C.TRACK_TYPE_VIDEO);
        if (video.size() > 1) {
            LinearLayout row = chipRow(c, box, R.string.live_quality);
            List<TextView> chips = new ArrayList<>();
            boolean anyOverride = false;
            for (Track t : video) anyOverride |= overridden(t);
            TextView auto = Ui.guideChip(c, getString(R.string.live_auto), !anyOverride);
            auto.setOnClickListener(v -> {
                automatic(C.TRACK_TYPE_VIDEO, false);
                for (TextView x : chips) Ui.styleChip(c, x, x == v);
            });
            chips.add(auto);
            addChip(row, auto);
            for (Track t : video) {
                TextView chip = Ui.guideChip(c, t.label, overridden(t));
                chip.setOnClickListener(v -> {
                    choose(t);
                    for (TextView x : chips) Ui.styleChip(c, x, x == v);
                });
                chips.add(chip);
                addChip(row, chip);
            }
        }

        // Sleep timer.
        LinearLayout sleepRow = chipRow(c, box, R.string.live_sleep);
        List<TextView> sleeps = new ArrayList<>();
        int setMin = sleepAt <= 0 ? 0 : sleepMinutes;
        for (int m : SLEEP) {
            TextView chip = Ui.guideChip(c, m == 0 ? getString(R.string.live_off) : getString(R.string.live_sleep_min, m), m == setMin);
            chip.setOnClickListener(v -> {
                setSleep(m);
                for (TextView x : sleeps) Ui.styleChip(c, x, x == v);
            });
            sleeps.add(chip);
            addChip(sleepRow, chip);
        }

        // Similar channels.
        addSimilar(c, box);

        // And the rest, one to a line.
        box.addView(new View(c), new LinearLayout.LayoutParams(-1, Ui.dp(c, 14)));
        boolean fav = Picks.isFavourite(c, cur.key);
        action(c, box, fav ? R.drawable.live_ic_star : R.drawable.live_ic_star_outline,
                fav ? R.string.live_fav_remove : R.string.live_fav_add, () -> {
                    toggleFavourite();
                    if (sheet != null) sheet.dismiss();
                });
        if (pipSupported()) action(c, box, R.drawable.live_ic_pip, R.string.live_pip, () -> {
            if (sheet != null) sheet.dismiss();
            pipAndLeave();
        });
        if (pipSupported()) {
            boolean on = autoPip(c);
            action(c, box, R.drawable.live_ic_pip, on ? R.string.live_auto_pip_on : R.string.live_auto_pip_off, () -> {
                Aspect.prefs(c).put(AUTO_PIP, autoPip(c) ? "0" : "1");
                setPictureInPictureParams(pipParams());
                Toast.makeText(c, autoPip(c) ? R.string.live_auto_pip_now_on : R.string.live_auto_pip_now_off, Toast.LENGTH_SHORT).show();
                if (sheet != null) sheet.dismiss();
            });
        }
        if (auto) {
            boolean up = uprightBack(c);
            action(c, box, R.drawable.live_ic_fullscreen, up ? R.string.live_upright_on : R.string.live_upright_off, () -> {
                Aspect.prefs(c).put(UPRIGHT_BACK, uprightBack(c) ? "0" : "1");
                orient();
                Toast.makeText(c, uprightBack(c) ? R.string.live_upright_now_on : R.string.live_upright_now_off, Toast.LENGTH_SHORT).show();
                if (sheet != null) sheet.dismiss();
            });
        }
        boolean swipeOn = ChannelSwipe.on(c);
        action(c, box, R.drawable.live_ic_similar, swipeOn ? R.string.tv07_swipe_on : R.string.tv07_swipe_off, () -> {
            ChannelSwipe.setOn(c, !ChannelSwipe.on(c));
            Toast.makeText(c, ChannelSwipe.on(c) ? R.string.tv07_swipe_now_on : R.string.tv07_swipe_now_off, Toast.LENGTH_SHORT).show();
            if (sheet != null) sheet.dismiss();
        });
        extras.options(box);
        action(c, box, R.drawable.live_ic_replay, R.string.live_restart, () -> {
            if (sheet != null) sheet.dismiss();
            restart();
        });
        action(c, box, R.drawable.live_ic_info, R.string.live_info, () -> {
            if (sheet != null) sheet.dismiss();
            info();
        });

        NestedScrollView scroll = new NestedScrollView(c);
        scroll.addView(box, new FrameLayout.LayoutParams(-1, -2));
        BottomSheetDialog mine = Sheets.open(c, scroll, true);
        sheet = mine;
        mine.setOnDismissListener(d -> {
            if (sheet == mine) sheet = null;
        });
    }

    /** Similar channels as a row of cards with their logos (tap one to change to it); false when there are none. */
    private boolean addSimilar(Context c, LinearLayout box) {
        List<Channel> similar = lib.similar(cur, 24);
        if (!similar.isEmpty()) {
            LinearLayout row = chipRow(c, box, R.string.live_similar);
            for (Channel ch : similar) {
                LinearLayout card = new LinearLayout(c);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 14), 0);
                card.setBackground(Ui.touch(c, false, Ui.GLASS, 14));
                FrameLayout lb = new FrameLayout(c);
                // The playlist's logo, else Sky's guide logo by the channel's name.
                String logo = !ch.logo.isEmpty() ? ch.logo : com.gathin.tvhomephone.sports.ChannelLogos.sky(ch.name);
                TextView ini = Ui.text(c, logo == null ? Ui.initials(ch.name) : "", 10, Ui.SOFT, true);
                ini.setGravity(Gravity.CENTER);
                lb.addView(ini, new FrameLayout.LayoutParams(-1, -1));
                ImageView img = new ImageView(c);
                img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                // The logo on a small tile in its own colour or black (LogoTiles), or as it is.
                boolean boxed = LogoTiles.boxed(c);
                int p = boxed ? LogoTiles.pad(c, 3) : 0;
                img.setPadding(p, p, p, p);
                if (logo != null) LogoTiles.show(lb, 6, false, img, logo, LogoTiles.ON_NAVY, null);
                lb.addView(img, new FrameLayout.LayoutParams(-1, -1));
                card.addView(lb, new LinearLayout.LayoutParams(Ui.dp(c, boxed ? 46 : 40), Ui.dp(c, 28)));
                TextView nm = Ui.text(c, ch.name, 13, Ui.TEXT, true);
                nm.setSingleLine(true);
                nm.setPadding(Ui.dp(c, 8), 0, 0, 0);
                nm.setMaxWidth(Ui.dp(c, 200));
                nm.setEllipsize(TextUtils.TruncateAt.END);
                card.addView(nm);
                card.setOnClickListener(v -> {
                    if (sheet != null) sheet.dismiss();
                    switchTo(ch);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 48));
                lp.rightMargin = Ui.dp(c, 8);
                row.addView(card, lp);
            }
        }
        return !similar.isEmpty();
    }

    /** The tile's Similar channels: the row alone in a sheet. */
    private void similarSheet() {
        if (cur == null) return;
        hideOverlay();
        Context c = this;
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), Ui.dp(c, 20));
        if (!addSimilar(c, box)) {
            Toast.makeText(c, R.string.live_similar_none, Toast.LENGTH_SHORT).show();
            return;
        }
        BottomSheetDialog mine = Sheets.open(c, box, true);
        sheet = mine;
        mine.setOnDismissListener(d -> {
            if (sheet == mine) sheet = null;
        });
    }

    /** A tile style's name (the options, and the test chips: PlayerTests). */
    static int styleLabel(TileKind.Style st) {
        switch (st) {
            case RIBBON: return R.string.tile_ribbon;
            case CARD: return R.string.tile_card;
            case MATCH: return R.string.tile_match;
            case CINEMA: return R.string.tile_cinema;
            case PANEL: return R.string.tile_panel;
            default: return R.string.tile_auto;
        }
    }

    private LinearLayout chipRow(Context c, LinearLayout box, int label) {
        TextView t = Ui.text(c, getString(label).toUpperCase(Locale.UK), 12, Ui.G_SELECT, true);
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

    private static void addChip(LinearLayout row, TextView chip) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(row.getContext(), 8);
        row.addView(chip, lp);
    }

    private static void action(Context c, LinearLayout box, int icon, int label, Runnable r) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        row.setBackground(Ui.touch(c, false, 0, 12));
        ImageView i = new ImageView(c);
        i.setImageResource(icon);
        i.setImageTintList(ColorStateList.valueOf(Ui.G_SELECT));
        row.addView(i, new LinearLayout.LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24)));
        TextView t = Ui.text(c, c.getString(label), 16, Ui.TEXT, false);
        t.setPadding(Ui.dp(c, 16), 0, 0, 0);
        row.addView(t);
        row.setOnClickListener(v -> r.run());
        box.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(c, 52)));
    }

    private void setSleep(int minutes) {
        h.removeCallbacks(sleep);
        sleepAt = minutes <= 0 ? 0 : System.currentTimeMillis() + minutes * 60_000L;
        sleepMinutes = Math.max(0, minutes);
        if (sleepAt > 0) {
            h.postDelayed(sleep, minutes * 60_000L);
            Toast.makeText(this, getString(R.string.live_sleep_in, minutes), Toast.LENGTH_SHORT).show();
        }
    }

    /** What's on now (and next) on this channel, in full. */
    private void info() {
        if (cur == null) return;
        Guide.Schedule s = lib.schedule(cur.key);
        int at = s == null ? -1 : s.at(System.currentTimeMillis());
        BottomSheetDialog mine = Sheets.programme(this, lib, cur, at, new Details.Actions() {
            @Override public void watch(Channel ch) {
                if (ch != cur) switchTo(ch);
            }

            @Override public void reminded(Channel ch, long start, boolean on) {}

            @Override public void favourite(Channel ch) {
                if (ch == cur) toggleFavourite();
                else Picks.toggle(PlayerActivity.this, ch.key);
            }

            @Override public void more(Channel ch, int index) {}
        });
        sheet = mine;
        mine.setOnDismissListener(d -> {
            if (sheet == mine) sheet = null;
        });
    }

    // ---- gestures ---------------------------------------------------------------------------------------------

    private AudioManager audio;

    private float brightness() {
        float b = getWindow().getAttributes().screenBrightness;
        if (b >= 0) return b;
        try {
            return Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f;
        } catch (Settings.SettingNotFoundException e) {
            return 0.5f;
        }
    }

    private void setBrightness(float v) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = Math.max(0.02f, Math.min(1f, v));
        getWindow().setAttributes(lp);
        showLevel(R.drawable.live_ic_brightness, R.string.live_brightness, lp.screenBrightness);
    }

    private float volume() {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return max <= 0 ? 0 : audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
    }

    private void setVolume(float v) {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float f = Math.max(0f, Math.min(1f, v));
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(f * max), 0);
        } catch (SecurityException e) {
            // Do Not Disturb can refuse it.
        }
        showLevel(R.drawable.live_ic_volume, R.string.live_volume, f);
    }

    private void showLevel(int icon, int label, float f) {
        levelIcon.setImageResource(icon);
        levelIcon.setContentDescription(getString(label));
        levelText.setText(getString(R.string.live_percent, Math.round(f * 100)));
        levelBar.setProgress(Math.round(f * 100));
        h.removeCallbacks(hideLevel);
        if (level.getVisibility() != View.VISIBLE || level.getAlpha() < 1f) fade(level, true);
    }

    @SuppressLint("ClickableViewAccessibility")
    private View gestures() {
        View touch = new View(this);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        touch.setOnTouchListener(new Gestures(this, new Gestures.Callback() {
            @Override public void onTap() {
                if (hint != null && hint.getVisibility() == View.VISIBLE) return;
                if (pb.state() == Playback.FAILED) {
                    pb.restart();
                    return;
                }
                if (pb.paused()) {
                    pb.resume();
                    return;
                }
                if (overlayShown()) hideOverlay();
                else showOverlay();
            }

            @Override public void onDoubleTap() {
                setAspect(aspect.toggled(landscape), true);
            }

            @Override public void onDoubleTapAt(int third) {
                // Left: back 10 s (the rewind); right: on 10 s in a replay; the middle flips to the channel before.
                if (third < 0 || (third > 0 && pb.replaying())) {
                    extras.doubleTap(third < 0);
                    return;
                }
                Channel before = third == 0 ? pb.previous() : null;
                if (before != null && lib.byKey(before.key) != null) {
                    switchTo(lib.byKey(before.key));
                    Toast.makeText(PlayerActivity.this, before.name, Toast.LENGTH_SHORT).show();
                    return;
                }
                onDoubleTap();
            }

            @Override public void onPinch(float scale) {
                float s = Math.max(0.85f, Math.min(1.2f, scale));
                video.setScaleX(s);
                video.setScaleY(s);
            }

            @Override public void onPinchEnd(float scale) {
                video.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                Aspect a = aspect.pinched(scale);
                if (a != aspect) setAspect(a, true);
            }

            @Override public boolean onFling(float startX, float width, float dx, float dy, float vx, float vy, long ms) {
                ChannelSwipe.State s = swipeState();
                int d = ChannelSwipe.decide(s, startX, width, dx, dy, vx, vy, ms, getResources().getDisplayMetrics().density);
                if (d != ChannelSwipe.NONE) {
                    swipeTo(d);
                    return true;
                }
                // Swiping on but not a clear horizontal fling: nothing (a sloppy one never opens the guide by mistake).
                // Anything else having the picture (a replay, Move and resize…): the swipes as they were.
                return !s.off && !s.blocked() && Math.abs(dx) > Math.abs(dy) * 1.3f;
            }

            @Override public void onSwipe(int d) {
                // Kept until swiped down (the options): a swipe down while it shows takes the tile away instead.
                if (d == Gestures.DOWN && tilesShown() && tileMs() == 0) {
                    hideOverlay();
                    return;
                }
                if (ChannelSwipe.on(PlayerActivity.this)) {
                    // Left and right are the channels (onFling): up brings the guide panel, down puts it away.
                    if (d == Gestures.UP) {
                        if (!panelShown()) showPanel();
                    } else if (d == Gestures.DOWN) {
                        if (overlayShown()) hideOverlay();
                    } else if (d == Gestures.LEFT) {
                        if (!panelShown()) showPanel();
                    } else if (d == Gestures.RIGHT) {
                        if (overlayShown()) hideOverlay();
                    }
                    return;
                }
                if (d == Gestures.UP) step(1);
                else if (d == Gestures.DOWN) step(-1);
                else if (d == Gestures.LEFT) {
                    // The mini guide in from the side (whatever the tile), and away again the other way.
                    if (!panelShown()) showPanel();
                } else if (d == Gestures.RIGHT) {
                    if (overlayShown()) hideOverlay();
                }
            }

            @Override public float edgeLevel(int edge) {
                return edge == Gestures.LEFT_EDGE ? brightness() : volume();
            }

            @Override public void onEdge(int edge, float level) {
                if (edge == Gestures.LEFT_EDGE) setBrightness(level);
                else setVolume(level);
            }

            @Override public void onEdgeEnd() {
                h.postDelayed(hideLevel, 700);
            }
        }, 48));
        return touch;
    }

    /** The first time: what the gestures do, once. */
    private void showHint() {
        if (isFinishing() || isInPictureInPictureMode() || hint != null) return;
        FrameLayout cover = new FrameLayout(this);
        cover.setBackgroundColor(0xB3000000);
        cover.setClickable(true);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 22);
        card.setPadding(pad, pad, pad, Ui.dp(this, 14));
        card.setBackground(TvLook.mat(this, 22));
        card.addView(Ui.text(this, getString(R.string.live_hint_title), 20, Ui.TEXT, true));
        boolean swipe = ChannelSwipe.on(this);
        int[] lines = {R.string.live_hint_tap, swipe ? R.string.tv07_hint_swipe : R.string.live_hint_swipe,
                swipe ? R.string.tv07_hint_up : R.string.live_hint_sides, R.string.live_hint_zoom, R.string.live_hint_edges};
        for (int l : lines) {
            TextView t = Ui.text(this, "•  " + getString(l), 15, Ui.SOFT, false);
            t.setPadding(0, Ui.dp(this, 10), 0, 0);
            card.addView(t);
        }
        TextView ok = Ui.text(this, getString(R.string.live_got_it), 15, Ui.G_INK, true);
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(Ui.dp(this, 22), 0, Ui.dp(this, 22), 0);
        ok.setBackground(TvLook.white(this, 24));
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-2, Ui.dp(this, 48));
        op.topMargin = Ui.dp(this, 18);
        op.gravity = Gravity.END;
        card.addView(ok, op);
        int width = getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32) - cut.left - cut.right;
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Math.min(Ui.dp(this, 460), width), -2, Gravity.CENTER);
        cover.addView(card, cp);
        View.OnClickListener done = v -> {
            prefs().edit().putBoolean("hint_seen", true).apply();
            fade(cover, false);
        };
        ok.setOnClickListener(done);
        cover.setOnClickListener(done);
        root.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        hint = cover;
        cover.setPadding(cut.left, cut.top, cut.right, cut.bottom);
        h.postDelayed(() -> {
            if (cover.getVisibility() == View.VISIBLE) done.onClick(cover);
        }, 9000);
    }

    // ---- the screen ---------------------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private View build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        video = new Video(this);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));
        // The veil the picture slides under as a swipe changes channel (it takes no touch).
        veil = new View(this);
        veil.setBackgroundColor(0xFF000000);
        veil.setAlpha(0f);
        veil.setVisibility(View.GONE);
        veil.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(veil, new FrameLayout.LayoutParams(-1, -1));
        root.addView(gestures(), new FrameLayout.LayoutParams(-1, -1));
        // The automatic subtitles: over the picture, under everything else (they take no touch).
        captionView = new com.gathin.tvhomephone.captions.CaptionView(this);
        captionView.setPadding(0, 0, 0, Ui.dp(this, 30));
        root.addView(captionView, new FrameLayout.LayoutParams(-1, -1));

        spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.TEXT));
        root.addView(spinner, new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48), Gravity.CENTER));

        message = Ui.text(this, "", 16, Ui.TEXT, true);
        message.setGravity(Gravity.CENTER);
        message.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 14));
        message.setBackground(TvLook.mat(this, 16));
        message.setVisibility(View.GONE);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        mp.leftMargin = mp.rightMargin = Ui.dp(this, 32);
        root.addView(message, mp);

        zoom = Ui.text(this, "", 18, Ui.TEXT, true);
        zoom.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 10));
        zoom.setBackground(TvLook.mat(this, 22));
        zoom.setVisibility(View.GONE);
        root.addView(zoom, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        level = new LinearLayout(this);
        level.setOrientation(LinearLayout.VERTICAL);
        level.setGravity(Gravity.CENTER_HORIZONTAL);
        level.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 16));
        level.setBackground(TvLook.mat(this, 20));
        levelIcon = new ImageView(this);
        levelIcon.setImageTintList(ColorStateList.valueOf(Ui.TEXT));
        level.addView(levelIcon, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
        levelText = Ui.text(this, "", 15, Ui.TEXT, true);
        levelText.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        level.addView(levelText);
        levelBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        levelBar.setMax(100);
        TvLook.line(levelBar);
        level.addView(levelBar, new LinearLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 6)));
        level.setVisibility(View.GONE);
        root.addView(level, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        root.addView(buildPanel(), new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        // The quick channel card a swipe brings, at the picture's foot (over the tile, under the buttons).
        card = new ChannelCard(this, lib);
        card.setOnClickListener(v -> {
            card.out(true);
            showPanel();
        });

        // The info tile: only its cards take a touch; a tap anywhere else reaches the picture (and puts it away).
        tiles = new FrameLayout(this) {
            @Override public boolean dispatchTouchEvent(MotionEvent e) {
                keepPanel();
                return super.dispatchTouchEvent(e);
            }
        };
        tiles.setVisibility(View.GONE);
        root.addView(tiles, new FrameLayout.LayoutParams(-1, -1));
        root.addView(card, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        // Picture in picture and close, top right.
        top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        matchButton = button(R.drawable.live_ic_stats, R.string.live3_match_centre, v -> extras.openMatch());
        matchButton.setVisibility(View.GONE);
        top.addView(matchButton);
        top.addView(button(R.drawable.live_ic_cast, R.string.cast_button, v -> {
            hideOverlay();
            CastSheet.show(this, cur);
        }));
        top.addView(button(R.drawable.live_ic_more, R.string.live_options, v -> options()));
        if (pipSupported()) top.addView(button(R.drawable.live_ic_pip, R.string.live_pip, v -> pipAndLeave()));
        top.addView(button(R.drawable.live_ic_close, R.string.live_close, v -> close()));
        top.setVisibility(View.GONE);
        root.addView(top, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));

        // The panel and the buttons kept clear of the camera's cutout (the picture itself goes under it).
        extras = new PlayerExtras(this, root, pb, new PlayerExtras.Host() {
            @Override public Channel current() { return cur; }

            @Override public void play(Channel ch) { switchTo(ch); }

            @Override public com.gathin.tvhomephone.sports.Game game() { return channelGame(); }

            @Override public void closeSheet() {
                if (sheet != null) sheet.dismiss();
            }

            @Override public void testTile(TileKind.Style s) {
                PlayerActivity.this.testTile(s);
            }
        });
        // The scoreboard over the picture, under the tile and the buttons.
        extras.overlay(new ScoreOverlay(this, root, video, new ScoreOverlay.Host() {
            @Override public Channel current() { return cur; }

            @Override public com.gathin.tvhomephone.sports.Game game() { return channelGame(); }

            @Override public void openMatch() { extras.openMatch(); }

            @Override public void play(Channel ch) { switchTo(ch); }

            @Override public void mute(boolean on) {
                ExoPlayer p = pb.player();
                if (p != null) p.setVolume(on ? 0f : 1f);
            }

            @Override public void duck(boolean on) {
                ExoPlayer p = pb.player();
                if (p != null) p.setVolume(on ? 0.3f : 1f);
            }

            @Override public void hideControls() {
                hideOverlay();
            }

            @Override public com.gathin.tvhomephone.sports.Game soonGame() {
                AdBreak.Session s = adSession();
                return s == null ? null : s.game;
            }

            @Override public AdBreak.Session adSession() { return PlayerActivity.this.adSession(); }

            @Override public String programmeNow() { return PlayerActivity.this.programmeNow(); }

            @Override public String[] streamTags() {
                ExoPlayer p = pb.player();
                if (p == null) return null;
                return new String[]{MediaTags.video(p.getVideoFormat()), MediaTags.hdr(p.getVideoFormat()), MediaTags.audio(p.getAudioFormat())};
            }

            @Override public boolean muted() {
                ExoPlayer p = pb.player();
                return p != null && p.getVolume() <= 0f || volume() <= 0f;
            }

            @Override public boolean sportsNow() { return PlayerActivity.this.sportsNow(); }
        }));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            clearCutout();
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            extras.inset(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        return root;
    }

    private void clearCutout() {
        // On a tablet the panel's words and mini guide keep to the middle 960dp, as wide as reads well.
        int screen = getResources().getDisplayMetrics().widthPixels;
        int side = com.gathin.tvhomephone.glass.Wide.tablet(this)
                ? com.gathin.tvhomephone.glass.Wide.side(screen, Ui.dp(this, 960), Ui.dp(this, 24))
                : Ui.dp(this, 10);
        // The panel floats as a card of glass (07's), a little in from the sides and the foot.
        FrameLayout.LayoutParams pl = (FrameLayout.LayoutParams) panel.getLayoutParams();
        pl.leftMargin = side + cut.left;
        pl.rightMargin = side + cut.right;
        pl.bottomMargin = Ui.dp(this, 10) + cut.bottom;
        panel.setLayoutParams(pl);
        panel.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12));
        if (card != null) {
            FrameLayout.LayoutParams cl = (FrameLayout.LayoutParams) card.getLayoutParams();
            cl.width = Math.min(Ui.dp(this, 460), screen - 2 * Ui.dp(this, 14) - cut.left - cut.right);
            cl.bottomMargin = Ui.dp(this, 22) + cut.bottom;
            card.setLayoutParams(cl);
        }
        FrameLayout.LayoutParams tp = (FrameLayout.LayoutParams) top.getLayoutParams();
        tp.topMargin = cut.top;
        tp.rightMargin = cut.right;
        tp.leftMargin = cut.left;
        top.setLayoutParams(tp);
        if (hint != null) hint.setPadding(cut.left, cut.top, cut.right, cut.bottom);
        tiles.setPadding(cut.left, cut.top, cut.right, cut.bottom);
    }

    /** The guide panel along the foot: the channel, now and next, and the mini guide of the channels either side. */
    @SuppressLint("ClickableViewAccessibility")
    private View buildPanel() {
        Context c = this;
        panel = new LinearLayout(c) {
            @Override public boolean dispatchTouchEvent(MotionEvent e) {
                // Kept up while it's being used.
                keepPanel();
                return super.dispatchTouchEvent(e);
            }
        };
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);
        panel.setPadding(Ui.dp(c, 14), Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12));
        // 07's frosted panel over the picture: tinted glass with its bright hairline (no live blur over a stream).
        panel.setBackground(TvLook.mat(c, 26));
        panel.setElevation(Ui.dp(c, 8));

        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        number = TvLook.text(c, "", 21, Ui.TEXT, true);
        number.setPadding(0, 0, Ui.dp(c, 12), 0);
        row.addView(number);
        FrameLayout box = new FrameLayout(c);
        logoBox = box;
        box.setBackground(TvLook.glass(c, 12));
        box.setElevation(Ui.dp(c, 2));
        initials = TvLook.text(c, "", 14, Ui.SOFT, true);
        initials.setGravity(Gravity.CENTER);
        box.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = LogoTiles.pad(c, 5);
        logo.setPadding(p, p, p, p);
        box.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        row.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 68), Ui.dp(c, 46)));

        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 8), 0);
        LinearLayout names = new LinearLayout(c);
        names.setGravity(Gravity.CENTER_VERTICAL);
        name = TvLook.text(c, "", 13.5f, TvLook.L2, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(name, new LinearLayout.LayoutParams(-2, -2));
        group = TvLook.text(c, "", 12, TvLook.L3, false);
        group.setSingleLine(true);
        group.setEllipsize(TextUtils.TruncateAt.END);
        group.setPadding(Ui.dp(c, 10), 0, 0, 0);
        names.addView(group, new LinearLayout.LayoutParams(0, -2, 1f));
        sleepLine = Ui.text(c, "", 12, Ui.G_SELECT, true);
        sleepLine.setPadding(Ui.dp(c, 10), 0, 0, 0);
        names.addView(sleepLine);
        info.addView(names);
        // What's on across the whole width (a narrow phone kept no room for it beside its times), 07's white line for how
        // far in, then its times with what's next after them.
        nowTitle = TvLook.text(c, "", 18, Ui.TEXT, true);
        nowTitle.setLetterSpacing(-0.015f);
        nowTitle.setSingleLine(true);
        nowTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = Ui.dp(c, 3);
        info.addView(nowTitle, np);
        progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        TvLook.line(progress);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 4));
        pp.topMargin = Ui.dp(c, 6);
        pp.bottomMargin = Ui.dp(c, 5);
        info.addView(progress, pp);
        LinearLayout when = new LinearLayout(c);
        when.setGravity(Gravity.CENTER_VERTICAL);
        nowTime = TvLook.text(c, "", 12, TvLook.L2, false);
        nowTime.setSingleLine(true);
        nowTime.setPadding(0, 0, Ui.dp(c, 10), 0);
        when.addView(nowTime);
        nextLine = TvLook.text(c, "", 12, TvLook.L3, false);
        nextLine.setSingleLine(true);
        nextLine.setEllipsize(TextUtils.TruncateAt.END);
        when.addView(nextLine, new LinearLayout.LayoutParams(0, -2, 1f));
        info.addView(when, new LinearLayout.LayoutParams(-1, -2));
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        star = button(R.drawable.live_ic_star_outline, R.string.live_favourite, v -> toggleFavourite());
        row.addView(star);
        row.addView(button(R.drawable.live_ic_more, R.string.live_options, v -> options()));
        panel.addView(row);

        // Over the mini guide: the channels either side, or Recently watched; and Back to the channel before.
        LinearLayout tabs = new LinearLayout(c);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabChannels = stripTab(c, R.string.tv07_strip_channels, false);
        tabRecent = stripTab(c, R.string.tv07_strip_recent, true);
        LinearLayout.LayoutParams t1 = new LinearLayout.LayoutParams(-2, Ui.dp(c, 40));
        t1.rightMargin = Ui.dp(c, 6);
        tabs.addView(tabChannels, t1);
        tabs.addView(tabRecent, new LinearLayout.LayoutParams(-2, Ui.dp(c, 40)));
        backTo = TvLook.text(c, "", 12.5f, Ui.TEXT, true);
        backTo.setSingleLine(true);
        backTo.setEllipsize(TextUtils.TruncateAt.END);
        backTo.setGravity(Gravity.CENTER);
        backTo.setMaxWidth(Ui.dp(c, 220));
        backTo.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0);
        backTo.setOnClickListener(v -> {
            if (backChannel != null) switchTo(lib.byKey(backChannel.key) != null ? lib.byKey(backChannel.key) : backChannel);
        });
        tabs.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1f));
        LinearLayout.LayoutParams bt = new LinearLayout.LayoutParams(-2, Ui.dp(c, 40));
        bt.leftMargin = Ui.dp(c, 10);
        tabs.addView(backTo, bt);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = Ui.dp(c, 8);
        tp.rightMargin = Ui.dp(c, 6);
        panel.addView(tabs, tp);

        strip = new RecyclerView(c);
        strip.setLayoutManager(new LinearLayoutManager(c, LinearLayoutManager.HORIZONTAL, false));
        strip.setAdapter(neighbours);
        strip.setItemAnimator(null);
        strip.setClipToPadding(false);
        strip.setContentDescription(getString(R.string.live_mini_guide));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 76));
        sp.topMargin = Ui.dp(c, 6);
        panel.addView(strip, sp);
        panel.setVisibility(View.GONE);
        return panel;
    }

    /** One of the strip's two tabs (Channels, Recent): a pill of glass, white when chosen. */
    private TextView stripTab(Context c, int label, boolean recent) {
        TextView t = TvLook.text(c, getString(label), 13, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 14), 0);
        t.setOnClickListener(v -> {
            if (stripRecent == recent) return;
            stripRecent = recent;
            paintStripTabs();
            neighbours.notifyDataSetChangedSafe();
            if (recent) strip.scrollToPosition(0);
            else centreStrip();
            keepPanel();
        });
        return t;
    }

    private void paintStripTabs() {
        for (TextView t : new TextView[]{tabChannels, tabRecent}) {
            boolean on = (t == tabRecent) == stripRecent;
            t.setBackground(on ? TvLook.white(this, 20) : TvLook.glass(this, 20));
            t.setTextColor(on ? TvLook.INK : Ui.TEXT);
            t.setSelected(on);
        }
    }

    /** The strip's channels now: the category's (the mini guide) or Recently watched. */
    private List<Channel> stripItems() {
        return stripRecent ? recentList : order;
    }

    /** Recently watched for the panel (Recents), and Back to the channel before. */
    private void showRecent() {
        recentList = Recents.list(Picks.recent(this), lib::resolve, lib::isHidden, cur == null ? null : cur.key, Recents.SHOWN);
        backChannel = recentList.isEmpty() ? null : recentList.get(0);
        backTo.setText(backChannel == null ? "" : getString(R.string.tv07_back_to_short, backChannel.name));
        backTo.setContentDescription(backChannel == null ? null : getString(R.string.tv07_back_to, backChannel.name));
        backTo.setBackground(backChannel == null ? null : TvLook.glass(this, 20));
        backTo.setVisibility(backChannel == null ? View.INVISIBLE : View.VISIBLE);
        tabRecent.setVisibility(recentList.isEmpty() ? View.GONE : View.VISIBLE);
        if (recentList.isEmpty()) stripRecent = false;
        paintStripTabs();
    }

    private ImageButton button(int icon, int label, View.OnClickListener l) {
        ImageButton b = Ui.icon(this, icon, label);
        // A little larger to touch on a tablet.
        int s = com.gathin.tvhomephone.glass.Wide.tablet(this) ? 56 : 48;
        b.setBackground(TvLook.dark(this, s / 2f));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, s), Ui.dp(this, s));
        lp.leftMargin = Ui.dp(this, 8);
        b.setLayoutParams(lp);
        return b;
    }
}

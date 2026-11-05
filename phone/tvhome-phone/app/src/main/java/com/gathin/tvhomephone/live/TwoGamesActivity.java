package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Game;
import com.gathin.tvhomephone.sports.OnAir;
import com.gathin.tvhomephone.sports.ScoreBug;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Two games at once, on the phone on its side: two of the family's channels side by side (or one big and the other
 * small in its corner), each with its game's scores box when it shows one, its channel and playlist, and one of them
 * with the sound (outlined in white, the speaker lit): tap the other to give it the sound. Under them: Sound, Swap
 * sides, Big + small (or Side by side), Change (the second channel: those showing the same game first) and One game
 * (the player on the game with the sound). Each stream plays as Live TV's player plays one (FFmpeg for the sound the
 * phone can't decode, the next playlist's copy when one drops, a few tries again), the main player let go while this is
 * open. Before a second stream from the same playlist starts, its account's connections are checked: one at a time
 * (or all in use) offers the same channel from another playlist instead. Stream addresses are never logged.
 */
@OptIn(markerClass = UnstableApi.class)
public final class TwoGamesActivity extends AppCompatActivity {
    private static final String EXTRA_FIRST = "two_first", EXTRA_SECOND = "two_second";
    private static final String S_SOUND = "two_sound", S_SPLIT = "two_split";
    private static final int RETRIES = 3;
    private static final long BAR_MS = 6000, GAME_MS = 30_000;
    /** The small one's share of the width in Big + small. */
    static final float SMALL = 0.32f;

    /** connection(): fine to play both; the same playlist's account allows no second stream just now. */
    static final int OK = 0, LIMIT = 1;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Side[] sides = {new Side(0), new Side(1)};
    private Library lib;
    private FrameLayout root, stage;
    private LinearLayout bar;
    private TextView soundButton, layoutButton, note;
    private int sound;
    private boolean split = true, started;
    private Insets cut = Insets.NONE;
    private BottomSheetDialog sheet;
    private final Runnable hideBar = () -> fade(bar, false);
    private final Runnable games = new Runnable() {
        @Override public void run() {
            for (Side s : sides) findGame(s);
            h.postDelayed(this, GAME_MS);
        }
    };

    /** Two channels at once; secondKey null: the second is asked for. */
    public static Intent intent(Context c, String firstKey, String secondKey) {
        Intent i = new Intent(c, TwoGamesActivity.class).putExtra(EXTRA_FIRST, firstKey);
        if (secondKey != null) i.putExtra(EXTRA_SECOND, secondKey);
        if (!(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** One of the two: its channel (the copy from which playlist plays), its player and what is drawn over it. */
    private final class Side {
        final int index;
        Channel ch;
        int alt, retries;
        boolean hls, failed;
        Game game;
        ExoPlayer player;
        FrameLayout frame;
        PlayerView view;
        ScoreBug bug;
        TextView label, empty;
        ImageView speaker;
        ProgressBar spinner;

        Side(int index) {
            this.index = index;
        }

        Channel stream() {
            return ch == null ? null : alt > 0 && alt <= ch.alternates.size() ? ch.alternates.get(alt - 1) : ch;
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        lib = Library.get(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.hide(WindowInsetsCompat.Type.systemBars());
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        sides[0].ch = lib.byKey(getIntent().getStringExtra(EXTRA_FIRST));
        sides[1].ch = lib.byKey(getIntent().getStringExtra(EXTRA_SECOND));
        if (state != null) {
            sound = state.getInt(S_SOUND, 0);
            split = state.getBoolean(S_SPLIT, true);
            Channel a = lib.byKey(state.getString(EXTRA_FIRST)), b = lib.byKey(state.getString(EXTRA_SECOND));
            if (a != null) sides[0].ch = a;
            if (b != null) sides[1].ch = b;
        }
        if (sides[0].ch == null) {
            android.widget.Toast.makeText(this, R.string.live_gone, android.widget.Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        for (Side s : sides) s.hls = s.ch != null && Streams.looksHls(s.ch);
        showLabels();
        showBar();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(S_SOUND, sound);
        out.putBoolean(S_SPLIT, split);
        if (sides[0].ch != null) out.putString(EXTRA_FIRST, sides[0].ch.key);
        if (sides[1].ch != null) out.putString(EXTRA_SECOND, sides[1].ch.key);
    }

    @Override protected void onStart() {
        super.onStart();
        if (sides[0].ch == null) return;
        // One stream at a time from the main player: it lets its own go while two play here.
        Playback.get(this).stop();
        started = true;
        start(sides[0]);
        if (sides[1].ch != null) start(sides[1]);
        else h.postDelayed(() -> pick(sides[1]), 500);
        h.post(games);
    }

    @Override protected void onStop() {
        super.onStop();
        started = false;
        h.removeCallbacks(games);
        for (Side s : sides) release(s);
    }

    @Override protected void onDestroy() {
        if (sheet != null) sheet.dismiss();
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ---- the screen ---------------------------------------------------------------------------------------------

    private View build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(-1, -1));
        for (Side s : sides) buildSide(s);

        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(this, 14);
        bar.setPadding(pad, Ui.dp(this, 8), pad, Ui.dp(this, 10));
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xF20B0C21, 0xB30B0C21, 0x000B0C21}));
        soundButton = pill(getString(R.string.two_sound, ""), true, v -> giveSound(1 - sound));
        soundButton.setCompoundDrawablesRelativeWithIntrinsicBounds(tinted(R.drawable.live_ic_volume, Ui.NAVY_INK), null, null, null);
        soundButton.setCompoundDrawablePadding(Ui.dp(this, 6));
        bar.addView(soundButton, pillParams());
        bar.addView(pill(getString(R.string.two_swap), false, v -> swap()), pillParams());
        layoutButton = pill(getString(R.string.two_big_small), false, v -> {
            split = !split;
            layoutButton.setText(split ? R.string.two_big_small : R.string.two_side_by_side);
            place(true);
            showBar();
        });
        bar.addView(layoutButton, pillParams());
        bar.addView(pill(getString(R.string.two_change), false, v -> pick(sides[1])), pillParams());
        bar.addView(pill(getString(R.string.two_one_game), false, v -> oneGame()), pillParams());
        note = Ui.text(this, "", 12, Ui.SOFT, false);
        note.setMaxLines(2);
        note.setEllipsize(TextUtils.TruncateAt.END);
        note.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        bar.addView(note, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(bar, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            stage.setPadding(cut.left, cut.top, cut.right, cut.bottom);
            bar.setPadding(pad + cut.left, Ui.dp(this, 8), pad + cut.right, Ui.dp(this, 10) + cut.bottom);
            place(false);
            return insets;
        });
        stage.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) place(false);
        });
        stage.setOnClickListener(v -> showBar());
        return root;
    }

    private void buildSide(Side s) {
        s.frame = new FrameLayout(this);
        s.frame.setBackgroundColor(0xFF05060F);
        s.frame.setClipToOutline(true);
        s.frame.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(TwoGamesActivity.this, 8));
            }
        });
        s.view = new PlayerView(this);
        s.view.setUseController(false);
        s.view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        s.view.setShutterBackgroundColor(Color.BLACK);
        s.frame.addView(s.view, new FrameLayout.LayoutParams(-1, -1));

        s.spinner = new ProgressBar(this);
        s.spinner.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
        s.frame.addView(s.spinner, new FrameLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36), Gravity.CENTER));

        s.empty = Ui.text(this, "", 14, Ui.TEXT, true);
        s.empty.setGravity(Gravity.CENTER);
        s.empty.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        s.empty.setBackground(Ui.round(this, 0xB30B0C21, 18));
        s.empty.setVisibility(View.GONE);
        s.frame.addView(s.empty, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        s.bug = new ScoreBug(this);
        s.bug.setPivotX(0);
        s.bug.setPivotY(0);
        s.bug.setVisibility(View.GONE);
        int bh = Ui.dp(this, 64);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(Math.round(bh * ScoreBug.RATIO), bh, Gravity.TOP | Gravity.START);
        bp.leftMargin = bp.topMargin = Ui.dp(this, 10);
        s.frame.addView(s.bug, bp);

        s.speaker = new ImageView(this);
        s.speaker.setScaleType(ImageView.ScaleType.CENTER);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34), Gravity.TOP | Gravity.END);
        sp.rightMargin = sp.topMargin = Ui.dp(this, 10);
        s.frame.addView(s.speaker, sp);

        s.label = Ui.text(this, "", 12, Ui.TEXT, true);
        s.label.setMaxLines(1);
        s.label.setEllipsize(TextUtils.TruncateAt.END);
        s.label.setBackground(Ui.round(this, 0x8C000000, 10));
        s.label.setPadding(Ui.dp(this, 9), Ui.dp(this, 4), Ui.dp(this, 9), Ui.dp(this, 4));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        lp.leftMargin = lp.bottomMargin = Ui.dp(this, 10);
        s.frame.addView(s.label, lp);

        s.frame.setOnClickListener(v -> {
            showBar();
            if (s.ch == null) pick(s);
            else if (s.failed) retry(s);
            else giveSound(s.index);
        });
        stage.addView(s.frame, new FrameLayout.LayoutParams(0, 0));
    }

    /**
     * The two frames on a stage w by h, {x, y, width, height} each (pure, for the tests): side by side, each as wide
     * as half the stage less the gap and 16:9 within it, in the middle of the height; or Big + small, the first 16:9
     * as big as the stage allows in its middle, the second SMALL of the width in its lower right corner, margin in from
     * the side and bottom up from the foot (clear of the buttons).
     */
    static int[][] frames(int w, int h, boolean split, int gap, int margin, int bottom) {
        if (w <= 0 || h <= 0) return new int[][]{{0, 0, 0, 0}, {0, 0, 0, 0}};
        if (split) {
            int half = (w - gap) / 2;
            boolean wide = half * 9 / 16 <= h;
            int fw = wide ? half : h * 16 / 9, fh = wide ? half * 9 / 16 : h;
            int y = (h - fh) / 2;
            return new int[][]{{(half - fw) / 2, y, fw, fh}, {half + gap + (half - fw) / 2, y, fw, fh}};
        }
        int bw = Math.min(w, h * 16 / 9), bh = bw * 9 / 16;
        int bx = (w - bw) / 2, by = (h - bh) / 2;
        int sw = Math.round(w * SMALL), sh = sw * 9 / 16;
        return new int[][]{{bx, by, bw, bh}, {bx + bw - sw - margin, by + bh - sh - bottom, sw, sh}};
    }

    /** Each side in its frame, the scores box sized to it, the one with the sound outlined. */
    private void place(boolean animate) {
        int w = stage.getWidth() - stage.getPaddingLeft() - stage.getPaddingRight();
        int ht = stage.getHeight() - stage.getPaddingTop() - stage.getPaddingBottom();
        if (w <= 0 || ht <= 0) return;
        int[][] f = frames(w, ht, split, Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 76));
        for (Side s : sides) {
            int[] r = f[s.index];
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) s.frame.getLayoutParams();
            lp.width = r[2];
            lp.height = r[3];
            lp.leftMargin = r[0];
            lp.topMargin = r[1];
            s.frame.setLayoutParams(lp);
            s.frame.setElevation(!split && s.index == 1 ? Ui.dp(this, 8) : 0);
            if (animate) {
                s.frame.setAlpha(0.6f);
                s.frame.animate().alpha(1f).setDuration(200).start();
            }
            float bugW = Ui.dp(this, 64) * ScoreBug.RATIO;
            float scale = Math.min(0.55f, r[2] * 0.62f / bugW);
            s.bug.setScaleX(scale);
            s.bug.setScaleY(scale);
            ((FrameLayout.LayoutParams) s.label.getLayoutParams()).bottomMargin = Ui.dp(this, !split && s.index == 0 ? 70 : 10);
        }
        // The small one on top.
        sides[1].frame.bringToFront();
        bar.bringToFront();
        showSound();
    }

    private void showSound() {
        for (Side s : sides) {
            boolean on = s.index == sound;
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(Ui.dp(this, 8));
            ring.setColor(Color.TRANSPARENT);
            if (on && sides[1 - s.index].ch != null) ring.setStroke(Ui.dp(this, 3), Color.WHITE);
            // The small one's edge, over the big one's picture.
            else if (!split && s.index == 1) ring.setStroke(Ui.dp(this, 1.5f), 0x8CFFFFFF);
            s.frame.setForeground(ring);
            s.speaker.setImageDrawable(tinted(on ? R.drawable.live_ic_volume : R.drawable.live_ic_muted, on ? Ui.NAVY_INK : Color.WHITE));
            s.speaker.setBackground(Ui.round(this, on ? Color.WHITE : 0x8C000000, 17));
            s.speaker.setContentDescription(getString(on ? R.string.two_sound_here : R.string.two_muted));
            s.speaker.setVisibility(s.ch == null ? View.GONE : View.VISIBLE);
            if (s.player != null) s.player.setVolume(on ? 1f : 0f);
        }
        Side with = sides[sound];
        soundButton.setText(getString(R.string.two_sound, soundName(with.game == null ? null : with.game.homeName, with.ch == null ? null : with.ch.name)));
        soundButton.setContentDescription(getString(R.string.two_sound, with.game != null ? shortName(with) : with.ch == null ? "" : with.ch.name));
        // Measured again for its new words (a pill sized for the old ones would cut them short).
        soundButton.requestLayout();
    }

    /** The side's name for the Sound button: the home club when it shows a game ("Everton v"), else the channel. */
    private static String shortName(Side s) {
        return s.game.homeName + " v " + s.game.awayName;
    }

    /** Which game or channel has the sound, short enough for its button. */
    static String soundName(String home, String channel) {
        return home != null && !home.isEmpty() ? home : channel == null ? "" : channel;
    }

    private void showLabels() {
        boolean several = Sources.list(this).size() > 1;
        for (Side s : sides) {
            Channel st = s.stream();
            if (st == null) {
                s.label.setVisibility(View.GONE);
                s.empty.setText(R.string.two_pick_empty);
                s.empty.setVisibility(View.VISIBLE);
                s.spinner.setVisibility(View.GONE);
                continue;
            }
            Sources.Entry e = Sources.get(this, Library.playlistOf(st.key));
            s.label.setText(several && e != null ? s.ch.name + " · " + e.name : s.ch.name);
            s.label.setVisibility(View.VISIBLE);
        }
        showNote();
    }

    /** What the two streams take: a connection from each playlist, or two from one (and how many it allows). */
    private void showNote() {
        Channel a = sides[0].stream(), b = sides[1].stream();
        if (a == null || b == null) {
            note.setText("");
            return;
        }
        String pa = Library.playlistOf(a.key), pb = Library.playlistOf(b.key);
        if (!pa.equals(pb)) {
            note.setText(R.string.two_note_each);
            return;
        }
        Sources.Entry e = Sources.get(this, pa);
        Sources.Info i = Sources.info(this, pa);
        String name = e == null ? "" : e.name;
        note.setText(i != null && i.maxConnections > 0 ? getString(R.string.two_note_same, name, 2, i.maxConnections)
                : getString(R.string.two_note_same_unknown, name));
    }

    private void showBar() {
        h.removeCallbacks(hideBar);
        fade(bar, true);
        h.postDelayed(hideBar, BAR_MS);
    }

    private static void fade(View v, boolean in) {
        if (in && v.getVisibility() != View.VISIBLE) {
            v.setAlpha(0f);
            v.setVisibility(View.VISIBLE);
        }
        v.animate().cancel();
        v.animate().alpha(in ? 1f : 0f).setDuration(180).withEndAction(() -> {
            if (!in) v.setVisibility(View.GONE);
        }).start();
    }

    private TextView pill(String words, boolean primary, View.OnClickListener l) {
        TextView t = Ui.text(this, words, 13, primary ? Ui.NAVY_INK : Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setMaxWidth(Ui.dp(this, 220));
        t.setMinHeight(Ui.dp(this, 40));
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        t.setBackground(Ui.touch(this, false, primary ? Ui.TEXT : 0x1FFFFFFF, 20));
        t.setOnClickListener(v -> {
            l.onClick(v);
            showBar();
        });
        return t;
    }

    private LinearLayout.LayoutParams pillParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, Ui.dp(this, 40));
        p.rightMargin = Ui.dp(this, 8);
        return p;
    }

    private android.graphics.drawable.Drawable tinted(int res, int color) {
        android.graphics.drawable.Drawable d = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, res);
        if (d == null) return null;
        d = d.mutate();
        d.setTint(color);
        int s = Ui.dp(this, 18);
        d.setBounds(0, 0, s, s);
        return d;
    }

    // ---- playing --------------------------------------------------------------------------------------------------

    private void start(Side s) {
        if (!started || s.stream() == null) return;
        release(s);
        s.failed = false;
        s.empty.setVisibility(View.GONE);
        s.spinner.setVisibility(View.VISIBLE);
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(getApplicationContext())
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true);
        ExoPlayer p = new ExoPlayer.Builder(getApplicationContext(), renderers).build();
        // The side with the sound takes the phone's audio focus; the other plays muted without asking for it.
        p.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                s.index == sound);
        p.setHandleAudioBecomingNoisy(true);
        p.setVolume(s.index == sound ? 1f : 0f);
        p.addListener(listener(p));
        s.player = p;
        s.view.setPlayer(p);
        prepare(s);
        showLabels();
        findGame(s);
    }

    private void prepare(Side s) {
        if (s.player == null || s.stream() == null) return;
        s.player.setMediaSource(Streams.source(s.stream(), s.hls));
        s.player.prepare();
        s.player.setPlayWhenReady(true);
    }

    private void release(Side s) {
        if (s.player == null) return;
        s.view.setPlayer(null);
        s.player.release();
        s.player = null;
    }

    private void retry(Side s) {
        s.retries = 0;
        s.alt = 0;
        s.hls = Streams.looksHls(s.ch);
        start(s);
    }

    /** The side a player plays on now (it moves with Swap sides), or null once let go. */
    private Side sideOf(ExoPlayer p) {
        for (Side s : sides) if (s.player == p) return s;
        return null;
    }

    private Player.Listener listener(ExoPlayer p) {
        return new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                Side s = sideOf(p);
                if (s == null) return;
                s.spinner.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
                if (state == Player.STATE_READY) {
                    s.retries = 0;
                    s.failed = false;
                    s.empty.setVisibility(View.GONE);
                } else if (state == Player.STATE_ENDED) {
                    // A live stream doesn't end: the provider closed it.
                    dropped(s);
                }
            }

            @Override public void onPlayerError(@NonNull PlaybackException e) {
                // Only the kind of error: the stream's address (with the login in it) is never written out.
                android.util.Log.w("LiveTv", "two games: " + e.getErrorCodeName());
                Side s = sideOf(p);
                if (s == null) return;
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    s.player.seekToDefaultPosition();
                    s.player.prepare();
                    return;
                }
                if (!s.hls && (e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                        || e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)) {
                    s.hls = true;
                    prepare(s);
                    return;
                }
                dropped(s);
            }
        };
    }

    /** Dropped: on to the next playlist's copy at once, then a few tries again, then given up (tap to try again). */
    private void dropped(Side s) {
        if (s.ch != null && s.alt < s.ch.alternates.size()) {
            s.alt++;
            s.retries = 0;
            s.hls = Streams.looksHls(s.stream());
            prepare(s);
            showLabels();
            return;
        }
        if (s.retries < RETRIES) {
            s.retries++;
            s.spinner.setVisibility(View.VISIBLE);
            ExoPlayer p = s.player;
            h.postDelayed(() -> {
                Side x = sideOf(p);
                if (x != null) prepare(x);
            }, 1500L * s.retries);
            return;
        }
        s.failed = true;
        s.spinner.setVisibility(View.GONE);
        s.empty.setText(R.string.two_not_playing);
        s.empty.setVisibility(View.VISIBLE);
    }

    private void giveSound(int index) {
        if (index == sound || sides[index].ch == null) return;
        Side old = sides[sound], now = sides[index];
        AudioAttributes a = new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build();
        // The one going quiet lets the focus go first, so the other taking it doesn't pause it.
        if (old.player != null) old.player.setAudioAttributes(a, false);
        sound = index;
        if (now.player != null) now.player.setAudioAttributes(a, true);
        showSound();
    }

    private void swap() {
        if (sides[1].ch == null) return;
        Side a = sides[0], b = sides[1];
        Channel ch = a.ch;
        int alt = a.alt, retries = a.retries;
        boolean hls = a.hls, failed = a.failed;
        Game g = a.game;
        ExoPlayer p = a.player;
        a.ch = b.ch; a.alt = b.alt; a.retries = b.retries; a.hls = b.hls; a.failed = b.failed; a.game = b.game; a.player = b.player;
        b.ch = ch; b.alt = alt; b.retries = retries; b.hls = hls; b.failed = failed; b.game = g; b.player = p;
        // The pictures move with their streams (no new connection).
        a.view.setPlayer(null);
        b.view.setPlayer(null);
        a.view.setPlayer(a.player);
        b.view.setPlayer(b.player);
        for (Side x : sides) {
            x.empty.setVisibility(x.failed ? View.VISIBLE : View.GONE);
            x.spinner.setVisibility(View.GONE);
        }
        sound = 1 - sound;
        for (Side s : sides) showGame(s);
        showLabels();
        showSound();
    }

    /** Back to one game: the player on the game with the sound. */
    private void oneGame() {
        Side s = sides[sound];
        if (s.ch == null) s = sides[0];
        startActivity(PlayerActivity.intent(this, s.ch.key));
        finish();
    }

    // ---- the games --------------------------------------------------------------------------------------------------

    /** The game a side's channel shows (its programme in the guide, or an event channel's own name), read again. */
    private void findGame(Side s) {
        Channel ch = s.ch;
        if (ch == null) return;
        Guide.Schedule sc = lib.schedule(ch.key);
        long now = System.currentTimeMillis();
        int at = sc == null ? -1 : sc.at(now);
        String title = at >= 0 ? sc.title[at] : "", desc = at >= 0 ? sc.desc[at] : "";
        long start = at >= 0 ? sc.start[at] : 0, stop = at >= 0 ? sc.stop[at] : 0;
        boolean sports = TileKind.sportsChannel(ch.name, ch.group);
        App.io.execute(() -> {
            Game g = gameFor(title, desc, start, stop, ch.name, sports, OnAir.today(GAME_MS));
            h.post(() -> {
                if (s.ch != ch) return;
                s.game = g;
                showGame(s);
                showSound();
            });
        });
    }

    /**
     * The game a channel shows now (pure, for the tests): its programme read as a fixture (on a sports channel, or a
     * title that reads as one), else the channel's own name ("EPL 03: Arsenal v Chelsea"); null when neither does.
     */
    static Game gameFor(String title, String desc, long start, long stop, String channel, boolean sportsChannel, List<Game> today) {
        Game g = null;
        if (sportsChannel || OnAir.looksLikeFixture(title) || OnAir.looksLikeFixture(desc)) g = OnAir.match(title, desc, start, stop, today);
        if (g == null && OnAir.looksLikeFixture(channel)) g = OnAir.match(channel, "", 0, 0, today);
        return g;
    }

    private void showGame(Side s) {
        if (s.game == null) {
            s.bug.setVisibility(View.GONE);
            return;
        }
        s.bug.show(s.game);
        s.bug.setVisibility(View.VISIBLE);
    }

    // ---- the second channel -------------------------------------------------------------------------------------------

    /**
     * Whether a second stream from playlist b can play beside one from playlist a (pure, for the tests): LIMIT when
     * it is the same playlist and its account allows one stream, or as many as it says are in use already; OK
     * otherwise (and when the account says nothing).
     */
    static int connection(String a, String b, int maxConnections, int activeConnections) {
        if (a == null || !a.equals(b)) return OK;
        if (maxConnections == 1) return LIMIT;
        if (maxConnections > 1 && activeConnections >= maxConnections) return LIMIT;
        return OK;
    }

    /** The channel itself or its copy from a playlist other than notFrom, which first; null when every copy is from it. */
    static Channel elsewhere(Channel ch, String notFrom) {
        if (ch == null) return null;
        if (!Library.playlistOf(ch.key).equals(notFrom)) return ch;
        for (Channel a : ch.alternates) if (!Library.playlistOf(a.key).equals(notFrom)) return a;
        return null;
    }

    /** The sheet to pick a side's channel: those showing the first side's game first, then every channel, with search. */
    private void pick(Side s) {
        if (isFinishing() || (sheet != null && sheet.isShowing())) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 8));
        box.setBackgroundColor(Ui.G_PANEL);
        box.addView(Ui.text(this, getString(R.string.two_pick_title), 19, Ui.TEXT, true));
        EditText search = new EditText(this);
        search.setHint(R.string.live_search_hint);
        search.setHintTextColor(Ui.DIM);
        search.setTextColor(Ui.TEXT);
        search.setSingleLine(true);
        search.setBackground(Ui.round(this, Ui.GLASS, 20));
        search.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 44));
        sp.topMargin = Ui.dp(this, 10);
        sp.bottomMargin = Ui.dp(this, 6);
        box.addView(search, sp);
        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        Picker adapter = new Picker(ch -> {
            if (sheet != null) sheet.dismiss();
            choose(s, ch);
        });
        list.setAdapter(adapter);
        int height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.62f);
        box.addView(list, new LinearLayout.LayoutParams(-1, height));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence q, int a, int b, int c) {}

            @Override public void onTextChanged(CharSequence q, int a, int b, int c) {}

            @Override public void afterTextChanged(Editable q) {
                adapter.all(lib.list(Library.ALL, q.toString()));
            }
        });
        adapter.all(lib.list(Library.ALL, ""));
        Side other = sides[1 - s.index];
        Game g = other.game;
        if (g != null) {
            Context app = getApplicationContext();
            Channel current = other.ch;
            App.io.execute(() -> {
                List<Channel> same = new ArrayList<>();
                for (AlsoShowing.Row r : AlsoShowing.rows(app, g, current)) {
                    if (r.ch != null && (current == null || !r.ch.key.equals(current.key))) same.add(r.ch);
                }
                h.post(() -> adapter.same(same));
            });
        }
        sheet = Sheets.open(this, box, true);
    }

    /** A channel picked for a side: the connections checked first. */
    private void choose(Side s, Channel picked) {
        Side other = sides[1 - s.index];
        Channel there = other.stream();
        if (there == null) {
            set(s, picked);
            return;
        }
        String pa = Library.playlistOf(there.key), pb = Library.playlistOf(picked.key);
        Sources.Info info = Sources.info(this, pb);
        int ok = connection(pa, pb, info == null ? 0 : info.maxConnections, info == null ? 0 : info.activeConnections);
        if (ok == OK) {
            set(s, picked);
            return;
        }
        Sources.Entry from = Sources.get(this, pb);
        String name = from == null ? "" : from.name;
        Channel alt = elsewhere(picked, pa);
        AlertDialog.Builder d = new AlertDialog.Builder(this);
        if (alt != null) {
            Sources.Entry to = Sources.get(this, Library.playlistOf(alt.key));
            String toName = to == null ? "" : to.name;
            d.setMessage(getString(R.string.two_limit_alt, name, picked.name, toName))
                    .setPositiveButton(getString(R.string.two_use, toName), (x, w) -> set(s, picked, picked.alternates.indexOf(alt) + 1));
        } else {
            d.setMessage(getString(R.string.two_limit_none, name));
        }
        d.setNeutralButton(R.string.two_try_anyway, (x, w) -> set(s, picked)).setNegativeButton(R.string.two_cancel, null).show();
    }

    private void set(Side s, Channel ch) {
        set(s, ch, 0);
    }

    /** A side plays ch (from its alt-th copy: 0 its own). */
    private void set(Side s, Channel ch, int alt) {
        s.ch = ch;
        s.alt = Math.max(0, alt);
        s.retries = 0;
        s.game = null;
        s.hls = Streams.looksHls(s.stream());
        showGame(s);
        start(s);
        showSound();
        place(false);
    }

    /** The picker's rows: a heading, the same game's channels, a heading, every channel (or what the search finds). */
    private final class Picker extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final java.util.function.Consumer<Channel> picked;
        private List<Channel> same = new ArrayList<>(), all = new ArrayList<>();
        private final List<Object> rows = new ArrayList<>();

        Picker(java.util.function.Consumer<Channel> picked) {
            this.picked = picked;
        }

        void same(List<Channel> s) {
            same = s;
            rebuild();
        }

        void all(List<Channel> a) {
            all = a;
            rebuild();
        }

        @android.annotation.SuppressLint("NotifyDataSetChanged")
        private void rebuild() {
            rows.clear();
            if (!same.isEmpty()) {
                rows.add(getString(R.string.two_pick_also));
                rows.addAll(same);
                rows.add(getString(R.string.two_pick_all));
            }
            rows.addAll(all);
            notifyDataSetChanged();
        }

        @Override public int getItemViewType(int position) {
            return rows.get(position) instanceof String ? 0 : 1;
        }

        @Override public int getItemCount() {
            return rows.size();
        }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            Context c = parent.getContext();
            if (type == 0) {
                TextView t = Ui.text(c, "", 12, Ui.G_TIME, true);
                t.setLetterSpacing(0.08f);
                t.setPadding(Ui.dp(c, 4), Ui.dp(c, 12), 0, Ui.dp(c, 6));
                t.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerView.ViewHolder(t) {};
            }
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(Ui.dp(c, 6), Ui.dp(c, 8), Ui.dp(c, 6), Ui.dp(c, 8));
            row.setBackground(Ui.touch(c, false, 0, 12));
            row.setMinimumHeight(Ui.dp(c, 52));
            TextView name = Ui.text(c, "", 15, Ui.TEXT, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            TextView on = Ui.text(c, "", 13, Ui.SOFT, false);
            on.setSingleLine(true);
            on.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(name);
            row.addView(on);
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(row) {};
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object o = rows.get(position);
            if (o instanceof String) {
                ((TextView) holder.itemView).setText(((String) o).toUpperCase(java.util.Locale.UK));
                return;
            }
            Channel ch = (Channel) o;
            LinearLayout row = (LinearLayout) holder.itemView;
            int n = lib.number(ch);
            ((TextView) row.getChildAt(0)).setText(n > 0 ? n + "  " + ch.name : ch.name);
            Guide.Schedule s = lib.schedule(ch.key);
            int at = s == null ? -1 : s.at(System.currentTimeMillis());
            ((TextView) row.getChildAt(1)).setText(at >= 0 && !s.title[at].isEmpty() ? s.title[at] : getString(R.string.live_no_guide_info));
            row.setContentDescription(getString(R.string.two_watch_named, ch.name));
            row.setOnClickListener(v -> picked.accept(ch));
        }
    }
}

package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
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
import androidx.media3.ui.PlayerView;

/**
 * A film or an episode from the family's own playlist (Vod), full screen on its side: the player's own controls (play,
 * pause, the bar to seek along), its name at the top while they show, the same sound as Live TV (FFmpeg for AC-3 and
 * the rest), and where it was left kept, so it carries on from there next time (and on Watch's Continue watching row,
 * VodHistory). When it won't play from one playlist, the same film or episode in the next one is tried, from where it
 * had got to. An episode that ends offers the next one, playing it after ten seconds unless stopped; a sleep timer
 * stops it after 15, 30 or 60 minutes, or at the end of this one. CC: its subtitles, the stream's own or OpenSubtitles'
 * (VodSubtitles).
 */
@OptIn(markerClass = UnstableApi.class)
public final class VodPlayerActivity extends AppCompatActivity {
    private static final String EXTRA_URL = "url", EXTRA_UA = "ua", EXTRA_REF = "ref", EXTRA_TITLE = "title",
            EXTRA_ALTS = "alts", EXTRA_META = "meta", EXTRA_EPS = "eps", EXTRA_LABEL = "label";
    /** The picture's shapes (PlayerView's resize modes) and the ways round it may turn, as the options name them. */
    private static final int[] SHAPES = {androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT,
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM, androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL};
    private static final String[] SHAPE_NAMES = {"Fit", "Fill (crop)", "Fit width", "Stretch"};
    private static final int[] TURNS = {android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR,
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT};
    private static final String[] TURN_NAMES = {"Turn with the phone", "Landscape", "Portrait"};
    /** The next episode plays after this many seconds unless stopped. */
    private static final int NEXT_IN = 10;
    /** Carried on from where it was left only after a minute in, and not in its last two minutes. */
    private static final long RESUME_AFTER = 60_000L, END_GAP = 120_000L;

    private ExoPlayer player;
    private String url, resumeKey;
    /** Where it plays ({address as the index has it, user agent, referrer}), its backups after; which is playing. */
    private final java.util.List<String[]> plays = new java.util.ArrayList<>();
    private int playing;
    /** What Continue watching keeps of it (name, TMDB id, poster, season and episode), and a series' episodes. */
    private org.json.JSONObject meta = new org.json.JSONObject();
    private String episodesJson = "";
    private java.util.List<Vod.Episode> episodes = new java.util.ArrayList<>();
    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
    private FrameLayout root;
    /** The scoreboard and its alerts over the film. */
    private ScoreBoard board;
    private BurnGuard guard;
    private PlayerView view;
    private TextView sleepPill, sourcesPill, ccPill;
    /** Its subtitles: the stream's own, or a file from OpenSubtitles. */
    private VodSubtitles subs;
    /** The information bar: the name, then where it plays from and the picture's and sound's marks. */
    private LinearLayout infoBar, tagRow, topRight;
    private View nextCard;
    /** The sleep timer: when it stops it (0: off), or at the end of this one. */
    private long sleepAt;
    private boolean sleepAtEnd;
    private final Runnable sleep = () -> {
        if (player != null) player.pause();
        Toast.makeText(this, "Sleep timer: stopped", Toast.LENGTH_SHORT).show();
        finish();
    };

    /** The player for a film or episode: its address as the index has it (an Xtream one made from the login here). */
    public static Intent intent(Context c, String url, String ua, String ref, String title) {
        return new Intent(c, VodPlayerActivity.class).putExtra(EXTRA_URL, url).putExtra(EXTRA_UA, ua == null ? "" : ua)
                .putExtra(EXTRA_REF, ref == null ? "" : ref).putExtra(EXTRA_TITLE, title == null ? "" : title);
    }

    /** The plays with the one chosen (by its place) first, the rest after it as its backups. */
    public static java.util.List<String[]> chosenFirst(java.util.List<String[]> plays, int chosen) {
        java.util.List<String[]> out = new java.util.ArrayList<>(plays);
        if (chosen > 0 && chosen < out.size()) out.add(0, out.remove(chosen));
        return out;
    }

    /** The same, with where else it plays: {address, user agent, referrer}, its own first (Vod.Item.plays()). */
    public static Intent intent(Context c, java.util.List<String[]> plays, String title) {
        String[] first = plays.get(0);
        Intent i = intent(c, first[0], first[1], first[2], title).putExtra(EXTRA_LABEL, first.length > 3 && first[3] != null ? first[3] : "");
        java.util.ArrayList<String> alts = new java.util.ArrayList<>();
        for (int k = 1; k < plays.size(); k++) {
            String[] p = plays.get(k);
            alts.add(p[0] + "\u001f" + (p[1] == null ? "" : p[1]) + "\u001f" + (p[2] == null ? "" : p[2])
                    + "\u001f" + (p.length > 3 && p[3] != null ? p[3] : ""));
        }
        return i.putStringArrayListExtra(EXTRA_ALTS, alts);
    }

    /**
     * The same, with what Continue watching keeps of it (meta(): its name, TMDB id, poster, season and episode) and, for
     * an episode, its series' episodes (Vod.episodesJson) to go on with.
     */
    public static Intent intent(Context c, java.util.List<String[]> plays, String title, org.json.JSONObject meta, String episodes) {
        return intent(c, plays, title).putExtra(EXTRA_META, meta == null ? "" : meta.toString())
                .putExtra(EXTRA_EPS, episodes == null ? "" : episodes);
    }

    /** What Continue watching keeps of a film or episode (any of it may be empty or 0). */
    public static org.json.JSONObject meta(String name, int tmdb, boolean tv, String poster, int season, int episode, String episodeTitle) {
        org.json.JSONObject o = new org.json.JSONObject();
        try {
            o.put("name", name == null ? "" : name).put("tmdb", tmdb).put("tv", tv).put("poster", poster == null ? "" : poster)
                    .put("s", season).put("e", episode).put("ept", episodeTitle == null ? "" : episodeTitle);
        } catch (org.json.JSONException ignored) {
            // what there is
        }
        return o;
    }

    /** A film or episode from Continue watching, played again from where it was left. */
    public static Intent intent(Context c, VodHistory.Entry e) {
        return intent(c, e.plays, e.title, meta(e.name, e.tmdb, e.tv, e.poster, e.season, e.episode, e.episodeTitle), e.episodes);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String stored = getIntent().getStringExtra(EXTRA_URL);
        String firstLabel = getIntent().getStringExtra(EXTRA_LABEL);
        plays.add(new String[]{stored, getIntent().getStringExtra(EXTRA_UA), getIntent().getStringExtra(EXTRA_REF), firstLabel == null ? "" : firstLabel});
        java.util.ArrayList<String> alts = getIntent().getStringArrayListExtra(EXTRA_ALTS);
        if (alts != null) for (String a : alts) plays.add(a.split("\u001f", -1));
        // The first that is still in a playlist.
        playing = -1;
        for (int k = 0; k < plays.size() && playing < 0; k++) {
            String u = Vod.address(this, plays.get(k)[0]);
            if (u != null && !u.isEmpty()) playing = k;
        }
        if (stored == null || playing < 0) {
            Toast.makeText(this, "This one isn't in your playlist any more", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        url = Vod.address(this, plays.get(playing)[0]);
        resumeKey = Integer.toHexString(stored.hashCode());
        try {
            String m = getIntent().getStringExtra(EXTRA_META);
            if (m != null && !m.isEmpty()) meta = new org.json.JSONObject(m);
        } catch (org.json.JSONException ignored) {
            // nothing kept of it
        }
        episodesJson = getIntent().getStringExtra(EXTRA_EPS) == null ? "" : getIntent().getStringExtra(EXTRA_EPS);
        episodes = Vod.episodesFrom(episodesJson);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        view = new PlayerView(this);
        view.setShowNextButton(false);
        view.setShowPreviousButton(false);
        view.setKeepScreenOn(true);
        view.setResizeMode(SHAPES[Math.max(0, Math.min(SHAPES.length - 1, look().getInt("shape", 0)))]);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        // The information bar, top left: the name, then where it plays from and the picture's and sound's marks.
        infoBar = new LinearLayout(this);
        infoBar.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, getIntent().getStringExtra(EXTRA_TITLE), 18, Ui.TEXT, true);
        title.setShadowLayer(Ui.dp(this, 4), 0, 0, 0xCC000000);
        title.setMaxLines(2);
        infoBar.addView(title);
        tagRow = new LinearLayout(this);
        tagRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(-2, -2);
        trp.topMargin = Ui.dp(this, 6);
        infoBar.addView(tagRow, trp);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tp.leftMargin = tp.topMargin = Ui.dp(this, 22);
        tp.rightMargin = Ui.dp(this, 200);
        root.addView(infoBar, tp);
        // Top right, with the controls: the sources (when there are several), the sleep timer, the small window, more.
        topRight = new LinearLayout(this);
        topRight.setGravity(Gravity.CENTER_VERTICAL);
        ccPill = pill("CC");
        ccPill.setContentDescription("Subtitles");
        ccPill.setOnClickListener(v -> {
            if (subs != null) subs.menu();
        });
        topRight.addView(ccPill, pillParams());
        sourcesPill = pill("");
        sourcesPill.setOnClickListener(v -> sourcesMenu());
        topRight.addView(sourcesPill, pillParams());
        sleepPill = pill("");
        sleepPill.setOnClickListener(v -> sleepMenu());
        topRight.addView(sleepPill, pillParams());
        if (pipSupported()) {
            TextView pip = pill("▣");
            pip.setContentDescription("Small window");
            pip.setOnClickListener(v -> enterPip());
            topRight.addView(pip, pillParams());
        }
        TextView more = pill("⋮");
        more.setContentDescription("Picture and turning");
        more.setOnClickListener(v -> moreMenu());
        topRight.addView(more, pillParams());
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        sp.rightMargin = sp.topMargin = Ui.dp(this, 16);
        root.addView(topRight, sp);
        showSleep();
        // The followed clubs' game on now and the alerts, over the film (ScoreBoard), out of the controls' way.
        board = new ScoreBoard(this, new ScoreBoard.Source() {
            @Override public Channel channel() { return null; }

            @Override public com.gathin.tvhomephone.sports.Game game() { return null; }

            @Override public long behindMs(boolean channelGame) { return 0; }

            @Override public void tapped() {
                view.showController();
            }

            @Override public void held() {
                ScoreBoard.setOn(VodPlayerActivity.this, false);
                board.stop();
                board.view.setVisibility(View.GONE);
                android.widget.Toast.makeText(VodPlayerActivity.this, com.gathin.tvhomephone.R.string.live4_board_off, android.widget.Toast.LENGTH_LONG).show();
            }

            @Override public void alertTapped(com.gathin.tvhomephone.sports.Events.Alert a) {}
        });
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        bp.leftMargin = Ui.dp(this, 22);
        bp.topMargin = Ui.dp(this, 18);
        root.addView(board.view, 1, bp);
        guard = new BurnGuard(this, 2f, new View[]{board.view}, new BurnGuard.Dims[]{board.bug});
        board.changed = guard::poke;
        board.view.setVisibility(ScoreBoard.on(this) ? View.VISIBLE : View.GONE);
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) v -> {
            infoBar.setVisibility(v);
            topRight.setVisibility(v);
            board.view.setVisibility(v == View.VISIBLE || !ScoreBoard.on(this) ? View.GONE : View.VISIBLE);
        });
        setRequestedOrientation(TURNS[Math.max(0, Math.min(TURNS.length - 1, look().getInt("turn", 0)))]);
        setContentView(root);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.hide(WindowInsetsCompat.Type.systemBars());
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true);
        player = new ExoPlayer.Builder(this, renderers).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        NightSound.follow(this, player);
        view.setPlayer(player);
        subs = new VodSubtitles(this, player, view, resumeKey, meta, getIntent().getStringExtra(EXTRA_TITLE), this::subsChanged);
        subs.start();
        subsChanged();
        load(0);
        long at = prefs().getLong(resumeKey, 0);
        if (at > RESUME_AFTER) {
            player.seekTo(at);
            Toast.makeText(this, "Carrying on from " + clock(at), Toast.LENGTH_SHORT).show();
        }
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(@NonNull PlaybackException e) {
                if (next()) {
                    Toast.makeText(VodPlayerActivity.this, "Trying your other playlist", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(VodPlayerActivity.this, "It wouldn't play from your playlist", Toast.LENGTH_LONG).show();
            }

            @Override public void onTracksChanged(@NonNull androidx.media3.common.Tracks tracks) {
                showTags();
            }

            @Override public void onVideoSizeChanged(@NonNull androidx.media3.common.VideoSize size) {
                showTags();
                if (pipSupported()) setPictureInPictureParams(pipParams());
            }

            @Override public void onPlaybackStateChanged(int s) {
                if (s != Player.STATE_ENDED) return;
                prefs().edit().remove(resumeKey).apply();
                VodHistory.remove(VodPlayerActivity.this, resumeKey);
                if (sleepAtEnd) {
                    sleep.run();
                    return;
                }
                offerNext();
            }
        });
        player.prepare();
        player.setPlayWhenReady(true);
        showSources();
        showTags();
        if (pipSupported()) setPictureInPictureParams(pipParams());
    }

    // ---- the sources --------------------------------------------------------------------------------------------

    private static String labelOf(String[] p, int i) {
        String l = p.length > 3 && p[3] != null ? p[3] : "";
        return l.isEmpty() ? "Source " + (i + 1) : l;
    }

    private void showSources() {
        sourcesPill.setVisibility(plays.size() > 1 ? View.VISIBLE : View.GONE);
        sourcesPill.setText("Sources · " + plays.size());
    }

    /** Every source for it, the one playing ticked: another one picked carries on from the same moment. */
    private void sourcesMenu() {
        String[] names = new String[plays.size()];
        for (int i = 0; i < names.length; i++) names[i] = labelOf(plays.get(i), i);
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Play from")
                .setSingleChoiceItems(names, playing, (d, w) -> {
                    d.dismiss();
                    if (w == playing || player == null) return;
                    String u = Vod.address(this, plays.get(w)[0]);
                    if (u == null || u.isEmpty()) {
                        Toast.makeText(this, "That one isn't in your playlists any more", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long pos = Math.max(0, player.getCurrentPosition());
                    playing = w;
                    url = u;
                    load(pos);
                    player.prepare();
                    player.setPlayWhenReady(true);
                    showTags();
                    Toast.makeText(this, "Playing from " + names[w], Toast.LENGTH_SHORT).show();
                }).show();
    }

    // ---- the information bar ------------------------------------------------------------------------------------

    /** Where it plays from, and the picture's and sound's marks as the stream itself says them. */
    private void showTags() {
        if (tagRow == null) return;
        tagRow.removeAllViews();
        if (plays.size() > 1 || !labelOf(plays.get(playing), playing).startsWith("Source ")) {
            addTag(labelOf(plays.get(playing), playing), false);
        }
        if (player != null) {
            String v = MediaTags.video(player.getVideoFormat()), hdr = MediaTags.hdr(player.getVideoFormat()),
                    a = MediaTags.audio(player.getAudioFormat());
            if (!v.isEmpty()) addTag(v, true);
            if (!hdr.isEmpty()) addTag(hdr, true);
            if (!a.isEmpty()) addTag(a, true);
        }
        if (subs != null && subs.hasOwn()) addTag("CC", true);
    }

    /** The CC button lit while subtitles are on, and the information bar's marks again. */
    private void subsChanged() {
        if (ccPill == null || subs == null) return;
        ccPill.setBackground(Ui.touch(this, subs.isOn(), 0x66000000, 22));
        showTags();
    }

    private void addTag(String words, boolean boxed) {
        TextView t = Ui.text(this, words, 12, Ui.TEXT, true);
        t.setPadding(Ui.dp(this, 7), Ui.dp(this, 2), Ui.dp(this, 7), Ui.dp(this, 2));
        t.setBackground(boxed ? Ui.ring(this, 0x33000000, 0xE6FFFFFF, 1, 4) : Ui.round(this, 0x99000000, 4));
        t.setShadowLayer(boxed ? 0 : Ui.dp(this, 2), 0, 0, 0xCC000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(this, 6);
        tagRow.addView(t, lp);
    }

    // ---- the picture's shape, which way round, the small window -------------------------------------------------

    private SharedPreferences look() {
        return getApplicationContext().getSharedPreferences("vod_look", Context.MODE_PRIVATE);
    }

    private TextView pill(String words) {
        TextView t = Ui.text(this, words, 14, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(Ui.dp(this, 44));
        t.setMinWidth(Ui.dp(this, 44));
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        t.setBackground(Ui.touch(this, false, 0x66000000, 22));
        return t;
    }

    private LinearLayout.LayoutParams pillParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = Ui.dp(this, 8);
        return lp;
    }

    /** The picture's shape and which way round it turns, kept for the next time. */
    private void moreMenu() {
        String[] items = new String[SHAPE_NAMES.length + TURN_NAMES.length + 1];
        int shape = look().getInt("shape", 0), turn = look().getInt("turn", 0);
        for (int i = 0; i < SHAPE_NAMES.length; i++) items[i] = (i == shape ? "✓  " : "     ") + "Picture: " + SHAPE_NAMES[i];
        for (int i = 0; i < TURN_NAMES.length; i++) items[SHAPE_NAMES.length + i] = (i == turn ? "✓  " : "     ") + TURN_NAMES[i];
        items[items.length - 1] = "     " + (NightSound.available() ? (NightSound.on(this) ? "Night sound: on" : "Night sound: off") : "Night sound (Android 9 and later)");
        new androidx.appcompat.app.AlertDialog.Builder(this).setItems(items, (d, w) -> {
            if (w < SHAPE_NAMES.length) {
                look().edit().putInt("shape", w).apply();
                view.setResizeMode(SHAPES[w]);
            } else if (w < SHAPE_NAMES.length + TURN_NAMES.length) {
                int t = w - SHAPE_NAMES.length;
                look().edit().putInt("turn", t).apply();
                setRequestedOrientation(TURNS[t]);
            } else if (NightSound.available()) {
                NightSound.set(this, !NightSound.on(this));
            }
        }).show();
    }

    private boolean pipSupported() {
        return android.os.Build.VERSION.SDK_INT >= 26
                && getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    private android.app.PictureInPictureParams pipParams() {
        android.app.PictureInPictureParams.Builder b = new android.app.PictureInPictureParams.Builder();
        androidx.media3.common.VideoSize vs = player == null ? null : player.getVideoSize();
        if (vs != null && vs.width > 0 && vs.height > 0) {
            float r = vs.width * vs.pixelWidthHeightRatio / vs.height;
            r = Math.max(0.42f, Math.min(2.39f, r));
            b.setAspectRatio(new android.util.Rational(Math.round(r * 1000), 1000));
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(player != null && player.isPlaying());
        return b.build();
    }

    /** The film on in a small window over other apps (it carries on playing there). */
    private void enterPip() {
        if (!pipSupported()) return;
        try {
            enterPictureInPictureMode(pipParams());
        } catch (IllegalStateException e) {
            Toast.makeText(this, "This phone has the small window turned off for Homefield", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (android.os.Build.VERSION.SDK_INT < 31 && pipSupported() && player != null && player.isPlaying()) enterPip();
    }

    @Override public void onPictureInPictureModeChanged(boolean inPip, @NonNull android.content.res.Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        if (board != null) board.view.setVisibility(inPip || !ScoreBoard.on(this) ? View.GONE : View.VISIBLE);
        if (inPip) {
            view.hideController();
            view.setUseController(false);
            infoBar.setVisibility(View.GONE);
            topRight.setVisibility(View.GONE);
            if (nextCard != null) nextCard.setVisibility(View.GONE);
        } else if (getLifecycle().getCurrentState() == androidx.lifecycle.Lifecycle.State.CREATED) {
            // The small window was closed: so is the film (where it got to is kept).
            finish();
        } else {
            view.setUseController(true);
            if (nextCard != null) nextCard.setVisibility(View.VISIBLE);
        }
    }

    /** The one playing now given to the player, from position (ms). */
    private void load(long position) {
        String[] p = plays.get(playing);
        Channel as = new Channel("vod:" + resumeKey, getIntent().getStringExtra(EXTRA_TITLE), "", "", url, "", p[1], p[2], 0);
        player.setMediaSource(Streams.source(as, url.contains(".m3u8")), position);
    }

    /** On to the next playlist that has it, from where it had got to; false when there is none left. */
    private boolean next() {
        if (player == null) return false;
        long pos = Math.max(0, player.getCurrentPosition());
        for (int k = playing + 1; k < plays.size(); k++) {
            String u = Vod.address(this, plays.get(k)[0]);
            if (u == null || u.isEmpty()) continue;
            playing = k;
            url = u;
            load(pos);
            player.prepare();
            player.setPlayWhenReady(true);
            return true;
        }
        return false;
    }

    @Override protected void onStart() {
        super.onStart();
        if (board != null && ScoreBoard.on(this)) {
            board.start();
            guard.start();
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (board != null && !isInPictureInPictureMode()) {
            board.stop();
            guard.stop();
        }
        if (player == null || isInPictureInPictureMode()) return;
        // Where it was left: kept unless it was nearly over.
        long pos = player.getCurrentPosition(), dur = player.getDuration();
        SharedPreferences.Editor e = prefs().edit();
        if (pos > RESUME_AFTER && (dur <= 0 || dur - pos > END_GAP)) e.putLong(resumeKey, pos);
        else if (dur > 0 && dur - pos <= END_GAP) e.remove(resumeKey);
        e.apply();
        if (pos > RESUME_AFTER || (dur > 0 && dur - pos <= END_GAP)) VodHistory.record(this, entry(pos, dur));
        player.pause();
    }

    /** What Continue watching keeps of this one, at this position. */
    private VodHistory.Entry entry(long pos, long dur) {
        VodHistory.Entry v = new VodHistory.Entry();
        v.key = resumeKey;
        v.title = getIntent().getStringExtra(EXTRA_TITLE) == null ? "" : getIntent().getStringExtra(EXTRA_TITLE);
        v.name = meta.optString("name", v.title);
        if (v.name.isEmpty()) v.name = v.title;
        v.tmdb = meta.optInt("tmdb");
        v.tv = meta.optBoolean("tv");
        v.poster = meta.optString("poster");
        v.season = meta.optInt("s");
        v.episode = meta.optInt("e");
        v.episodeTitle = meta.optString("ept");
        v.position = pos;
        v.duration = Math.max(0, dur);
        v.at = System.currentTimeMillis();
        v.plays.addAll(plays);
        v.episodes = episodesJson;
        return v;
    }

    // ---- the next episode ---------------------------------------------------------------------------------------

    /** At an episode's end: the next one offered, playing after NEXT_IN seconds unless stopped. */
    private void offerNext() {
        if (episodes.isEmpty() || isFinishing()) return;
        int at = Vod.nextIndex(episodes, meta.optInt("s"), meta.optInt("e"));
        if (at < 0) return;
        Vod.Episode ep = episodes.get(at);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        card.setPadding(pad, pad, pad, Ui.dp(this, 12));
        card.setBackground(Ui.round(this, 0xEB0B0C21, 18));
        TextView label = Ui.text(this, "NEXT EPISODE", 11, Ui.DIM, true);
        label.setLetterSpacing(0.08f);
        card.addView(label);
        String own = ep.title == null ? "" : ep.title.trim();
        String name = meta.optString("name");
        TextView what = Ui.text(this, (ep.season > 0 ? "S" + ep.season + " " : "") + "E" + ep.number
                + (own.isEmpty() || own.equalsIgnoreCase(name) ? "" : " · " + own), 16, Ui.TEXT, true);
        what.setMaxLines(2);
        what.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
        card.addView(what);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView play = Ui.text(this, "", 14, Ui.NAVY_INK, true);
        play.setGravity(Gravity.CENTER);
        play.setMinHeight(Ui.dp(this, 44));
        play.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        play.setBackground(Ui.touch(this, false, Ui.TEXT, 22));
        TextView cancel = Ui.text(this, "Cancel", 14, Ui.TEXT, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setMinHeight(Ui.dp(this, 44));
        cancel.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), 0);
        row.addView(play);
        row.addView(cancel);
        card.addView(row);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(this, 300), -2, Gravity.BOTTOM | Gravity.END);
        lp.rightMargin = lp.bottomMargin = Ui.dp(this, 24);
        root.addView(card, lp);
        nextCard = card;
        int[] left = {NEXT_IN};
        Runnable[] count = new Runnable[1];
        count[0] = () -> {
            if (nextCard != card) return;
            if (left[0] <= 0) {
                playEpisode(ep);
                return;
            }
            play.setText("Play now · " + left[0]);
            left[0]--;
            h.postDelayed(count[0], 1000);
        };
        count[0].run();
        play.setOnClickListener(v -> playEpisode(ep));
        cancel.setOnClickListener(v -> {
            h.removeCallbacks(count[0]);
            root.removeView(card);
            nextCard = null;
        });
    }

    /** That episode in a new player (its own resume and Continue watching entry), this one closed. */
    private void playEpisode(Vod.Episode ep) {
        if (isFinishing()) return;
        nextCard = null;
        String name = meta.optString("name");
        String title = (name.isEmpty() ? "" : name + " · ") + (ep.season > 0 ? "S" + ep.season + " " : "") + "E" + ep.number;
        org.json.JSONObject m = meta(name, meta.optInt("tmdb"), true, meta.optString("poster"), ep.season, ep.number, ep.title);
        startActivity(intent(this, ep.plays(), title, m, episodesJson));
        finish();
    }

    // ---- the sleep timer ----------------------------------------------------------------------------------------

    private void sleepMenu() {
        boolean episode = meta.optBoolean("tv") || !episodes.isEmpty();
        String[] items = {"Off", "15 minutes", "30 minutes", "60 minutes", episode ? "End of this episode" : "End of this film"};
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Sleep timer").setItems(items, (d, w) -> {
            h.removeCallbacks(sleep);
            sleepAt = 0;
            sleepAtEnd = w == 4;
            if (w >= 1 && w <= 3) {
                int min = w == 1 ? 15 : w == 2 ? 30 : 60;
                sleepAt = System.currentTimeMillis() + min * 60_000L;
                h.postDelayed(sleep, min * 60_000L);
            }
            if (w > 0) Toast.makeText(this, w == 4 ? "Stops at the end" : "Stops in " + items[w], Toast.LENGTH_SHORT).show();
            showSleep();
        }).show();
    }

    private void showSleep() {
        if (sleepPill == null) return;
        if (sleepAtEnd) sleepPill.setText("Sleep · at the end");
        else if (sleepAt > 0) sleepPill.setText("Sleep · " + Math.max(1, (sleepAt - System.currentTimeMillis() + 59_999) / 60_000) + " min");
        else sleepPill.setText("Sleep");
        if (sleepAt > 0) h.postDelayed(this::showSleep, 30_000);
    }

    @Override protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (subs != null) subs.release();
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    private SharedPreferences prefs() {
        return getApplicationContext().getSharedPreferences("vod_resume", Context.MODE_PRIVATE);
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(java.util.Locale.UK, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(java.util.Locale.UK, "%d:%02d", s / 60, s % 60);
    }
}

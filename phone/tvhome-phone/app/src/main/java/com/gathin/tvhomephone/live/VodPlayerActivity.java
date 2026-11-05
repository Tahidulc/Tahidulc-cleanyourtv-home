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
 * stops it after 15, 30 or 60 minutes, or at the end of this one.
 */
@OptIn(markerClass = UnstableApi.class)
public final class VodPlayerActivity extends AppCompatActivity {
    private static final String EXTRA_URL = "url", EXTRA_UA = "ua", EXTRA_REF = "ref", EXTRA_TITLE = "title",
            EXTRA_ALTS = "alts", EXTRA_META = "meta", EXTRA_EPS = "eps";
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
    private TextView sleepPill;
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

    /** The same, with where else it plays: {address, user agent, referrer}, its own first (Vod.Item.plays()). */
    public static Intent intent(Context c, java.util.List<String[]> plays, String title) {
        String[] first = plays.get(0);
        Intent i = intent(c, first[0], first[1], first[2], title);
        java.util.ArrayList<String> alts = new java.util.ArrayList<>();
        for (int k = 1; k < plays.size(); k++) {
            String[] p = plays.get(k);
            alts.add(p[0] + "\u001f" + (p[1] == null ? "" : p[1]) + "\u001f" + (p[2] == null ? "" : p[2]));
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
        plays.add(new String[]{stored, getIntent().getStringExtra(EXTRA_UA), getIntent().getStringExtra(EXTRA_REF)});
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
        PlayerView view = new PlayerView(this);
        view.setShowNextButton(false);
        view.setShowPreviousButton(false);
        view.setKeepScreenOn(true);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        TextView title = Ui.text(this, getIntent().getStringExtra(EXTRA_TITLE), 18, Ui.TEXT, true);
        title.setShadowLayer(Ui.dp(this, 4), 0, 0, 0xCC000000);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        tp.leftMargin = tp.topMargin = Ui.dp(this, 22);
        root.addView(title, tp);
        // The sleep timer's button, top right, with the controls.
        sleepPill = Ui.text(this, "", 14, Ui.TEXT, true);
        sleepPill.setGravity(Gravity.CENTER);
        sleepPill.setMinHeight(Ui.dp(this, 44));
        sleepPill.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        sleepPill.setBackground(Ui.touch(this, false, 0x66000000, 22));
        sleepPill.setOnClickListener(v -> sleepMenu());
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        sp.rightMargin = sp.topMargin = Ui.dp(this, 16);
        root.addView(sleepPill, sp);
        showSleep();
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) v -> {
            title.setVisibility(v);
            sleepPill.setVisibility(v);
        });
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

    @Override protected void onStop() {
        super.onStop();
        if (player == null) return;
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

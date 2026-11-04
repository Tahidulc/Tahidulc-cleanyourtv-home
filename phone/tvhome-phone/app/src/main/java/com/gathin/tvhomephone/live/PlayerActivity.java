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
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
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

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.Lifecycle;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.gathin.tvhomephone.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Live TV's player, full screen: one of the family's own channels (HLS or MPEG-TS). A tap brings up the guide panel at
 * the foot (the channel's number, logo and name, what is on now with its times and how far in, what's next, and the
 * channels either side with their now and next: swipe through them and tap one to change channel without leaving the
 * picture); it goes after a few seconds. Its ⋯ opens the options: the picture's shape (remembered per channel), audio,
 * subtitles, quality, similar channels, the favourite, a sleep timer, picture in picture, restarting the stream and the
 * programme's details. Swipe up or down for the next or previous channel; double tap or pinch between Fit and Fill;
 * slide on the left edge for brightness and on the right for volume. Picture in picture on leaving, the phone's audio
 * focus, the screen kept on, and a few retries when the stream drops. Landscape on phones; any way round on tablets.
 * The stream's address is never logged (it can hold the login).
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {
    static final String EXTRA_KEY = "live_key", EXTRA_GROUP = "live_group";
    private static final int RETRIES = 5;
    private static final long OVERLAY_MS = 6000;
    private static final int[] SLEEP = {0, 15, 30, 60, 90};

    private final Handler h = new Handler(Looper.getMainLooper());
    private Library lib;
    private Aspect.Store store;
    private ExoPlayer player;
    private Video video;
    private LinearLayout panel, top;
    private FrameLayout root;
    private TextView number, initials, name, group, nowTitle, nowTime, nextLine, sleepLine, message, zoom;
    private ImageView logo;
    private ProgressBar spinner, progress;
    private ImageButton star;
    private RecyclerView strip;
    private final Neighbours neighbours = new Neighbours();
    private LinearLayout level;
    private ImageView levelIcon;
    private TextView levelText;
    private ProgressBar levelBar;
    private View hint;
    private BottomSheetDialog sheet;
    private List<Channel> order = new ArrayList<>();
    private int pos;
    private Channel cur;
    private int retries;
    private boolean asHls;
    private Aspect aspect = Aspect.FIT;
    private long sleepAt;
    private int sleepMinutes;
    private String centred;
    private final Runnable hide = this::hideOverlay;
    private final Runnable retry = () -> play(false);
    private final Runnable hideLevel = () -> fade(level, false);
    private final Runnable hideZoom = () -> fade(zoom, false);
    private final Runnable sleep = () -> {
        if (player != null) player.pause();
        Toast.makeText(this, R.string.live_sleeping, Toast.LENGTH_LONG).show();
        finish();
    };

    /** Opens the player on a channel (by key), swiping through the given group's list (Library.ALL for all). */
    public static void start(Context c, String key, String group) {
        Intent i = new Intent(c, PlayerActivity.class).putExtra(EXTRA_KEY, key).putExtra(EXTRA_GROUP, group == null ? Library.ALL : group);
        if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        lib = Library.get(this);
        store = Aspect.prefs(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Phones turn on their side; tablets play whichever way they are held.
        if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        setContentView(build());
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.hide(WindowInsetsCompat.Type.systemBars());
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (!take(getIntent())) {
            finish();
            return;
        }
        SharedPreferences p = prefs();
        if (!p.getBoolean("hint_seen", false)) h.postDelayed(this::showHint, 700);
    }

    private SharedPreferences prefs() {
        return getApplicationContext().getSharedPreferences("live_player", Context.MODE_PRIVATE);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (take(intent) && player != null) play(true);
    }

    /** The channel and its list from the intent; false when the channel is gone from the playlist. */
    private boolean take(Intent i) {
        Channel c = lib.byKey(i.getStringExtra(EXTRA_KEY));
        if (c == null) {
            Toast.makeText(this, R.string.live_gone, Toast.LENGTH_LONG).show();
            return false;
        }
        String g = i.getStringExtra(EXTRA_GROUP);
        order = lib.list(g == null ? Library.ALL : g, "");
        if (!order.contains(c)) order = lib.list(Library.ALL, "");
        pos = Math.max(0, order.indexOf(c));
        cur = c;
        neighbours.notifyDataSetChangedSafe();
        return true;
    }

    @Override protected void onStart() {
        super.onStart();
        if (player == null && cur != null) {
            player = new ExoPlayer.Builder(this).build();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
            player.setHandleAudioBecomingNoisy(true);
            player.addListener(listener);
            video.setPlayer(player);
            play(true);
        }
    }

    @Override protected void onStop() {
        super.onStop();
        if (!isInPictureInPictureMode()) release();
    }

    @Override protected void onDestroy() {
        release();
        if (sheet != null) sheet.dismiss();
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void release() {
        h.removeCallbacks(retry);
        if (player != null) {
            player.removeListener(listener);
            player.release();
            player = null;
            video.setPlayer(null);
        }
    }

    // ---- playing ----------------------------------------------------------------------------------------------

    /** Plays the current channel; fresh: a new channel (its retries, format guess, shape and tracks start again). */
    private void play(boolean fresh) {
        if (player == null || cur == null) return;
        h.removeCallbacks(retry);
        if (fresh) {
            retries = 0;
            asHls = Streams.looksHls(cur);
            Picks.watched(this, cur.key);
            aspect = Aspect.get(store, cur.key);
            applyAspect();
            // Another channel's audio, subtitle or quality choice means nothing here (subtitles off stays off).
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverrides().build());
            showOverlay();
        }
        player.setMediaSource(Streams.source(cur, asHls));
        player.prepare();
        player.setPlayWhenReady(true);
        message.setVisibility(View.GONE);
        spinner.setVisibility(View.VISIBLE);
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override public void onPlaybackStateChanged(int s) {
            if (s == Player.STATE_READY) {
                retries = 0;
                spinner.setVisibility(View.GONE);
                message.setVisibility(View.GONE);
            } else if (s == Player.STATE_BUFFERING) {
                spinner.setVisibility(View.VISIBLE);
            }
        }

        @Override public void onPlayerError(@NonNull PlaybackException e) {
            // Only the kind of error: the stream's address (with the login in it) is never written out.
            android.util.Log.w("LiveTv", "playback: " + e.getErrorCodeName());
            if (player == null) return;
            if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player.seekToDefaultPosition();
                player.prepare();
                return;
            }
            // An address with no extension that is really HLS: once more as HLS.
            if (!asHls && (e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                    || e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)) {
                asHls = true;
                play(false);
                return;
            }
            if (retries < RETRIES) {
                retries++;
                message.setText(R.string.live_reconnecting);
                message.setVisibility(View.VISIBLE);
                h.postDelayed(retry, Math.min(8000, 1000L << (retries - 1)));
            } else {
                spinner.setVisibility(View.GONE);
                message.setText(R.string.live_not_playing);
                message.setVisibility(View.VISIBLE);
            }
        }

        @Override public void onVideoSizeChanged(@NonNull VideoSize size) {
            if (pipSupported()) setPictureInPictureParams(pipParams());
        }
    };

    private void step(int by) {
        if (order == null || order.isEmpty()) return;
        pos = ((pos + by) % order.size() + order.size()) % order.size();
        cur = order.get(pos);
        play(true);
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
        cur = ch;
        play(true);
    }

    /** The stream again from the start (after it stalls, or to catch up with live). */
    private void restart() {
        retries = 0;
        play(false);
    }

    // ---- the picture's shape --------------------------------------------------------------------------------------

    /** The player's view, with a shape it can be told (16:9 or 4:3) in place of the stream's own. */
    static final class Video extends PlayerView {
        private float forced, natural;

        Video(Context c) {
            super(c);
        }

        Video(Context c, AttributeSet a) {
            super(c, a);
        }

        @Override protected void onContentAspectRatioChanged(AspectRatioFrameLayout frame, float ratio) {
            natural = ratio;
            super.onContentAspectRatioChanged(frame, forced > 0 ? forced : ratio);
        }

        void force(float ratio) {
            forced = ratio;
            AspectRatioFrameLayout f = findViewById(androidx.media3.ui.R.id.exo_content_frame);
            float r = ratio > 0 ? ratio : natural;
            if (f != null && r > 0) f.setAspectRatio(r);
        }
    }

    private void applyAspect() {
        switch (aspect) {
            case FILL: video.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM); break;
            case STRETCH: video.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL); break;
            default: video.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT); break;
        }
        video.force(aspect.ratio);
    }

    private void setAspect(Aspect a, boolean announce) {
        if (cur == null) return;
        aspect = a;
        Aspect.set(store, cur.key, a);
        applyAspect();
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
        if (player != null) {
            VideoSize v = player.getVideoSize();
            if (v.width > 0 && v.height > 0) {
                float ratio = v.width * v.pixelWidthHeightRatio / v.height;
                if (ratio > 0.42f && ratio < 2.38f) r = new Rational(Math.round(v.width * v.pixelWidthHeightRatio), v.height);
            }
        }
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder().setAspectRatio(r);
        // Android 12 and later: into the small window on its own as the app is left.
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(true);
        return b.build();
    }

    private void enterPip() {
        if (!pipSupported() || player == null) return;
        try {
            enterPictureInPictureMode(pipParams());
        } catch (IllegalStateException e) {
            // Not allowed here (a TV, or turned off for the app): it just keeps playing full screen.
        }
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT < 31 && player != null && player.isPlaying()) enterPip();
    }

    @Override public void onPictureInPictureModeChanged(boolean inPip, @NonNull Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        if (inPip) {
            hideOverlay();
            if (sheet != null) sheet.dismiss();
            if (hint != null) hint.setVisibility(View.GONE);
            level.setVisibility(View.GONE);
            zoom.setVisibility(View.GONE);
            message.setVisibility(View.GONE);
        } else if (getLifecycle().getCurrentState() == Lifecycle.State.CREATED) {
            // The small window was closed.
            finish();
        }
    }

    // ---- the guide panel ------------------------------------------------------------------------------------------

    private void showOverlay() {
        if (cur == null || isInPictureInPictureMode()) return;
        int n = lib.number(cur);
        number.setText(n > 0 ? String.valueOf(n) : "");
        number.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        name.setText(cur.name);
        group.setText(cur.group);
        group.setVisibility(cur.group.isEmpty() ? View.GONE : View.VISIBLE);
        initials.setText(cur.logo.isEmpty() ? Ui.initials(cur.name) : "");
        if (cur.logo.isEmpty()) Glide.with(logo).clear(logo);
        else Glide.with(logo).load(cur.logo).into(logo);
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
            if (lm != null) lm.scrollToPositionWithOffset(pos, Math.max(0, (strip.getWidth() - Ui.dp(this, 216)) / 2));
        });
    }

    private void keepPanel() {
        h.removeCallbacks(hide);
        h.postDelayed(hide, OVERLAY_MS);
    }

    private void hideOverlay() {
        h.removeCallbacks(hide);
        fade(panel, false);
        fade(top, false);
    }

    private boolean overlayShown() {
        return panel.getVisibility() == View.VISIBLE && panel.getAlpha() > 0.5f;
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
        @Override public int getItemCount() { return order == null ? 0 : order.size(); }

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
            TextView num = Ui.text(c, "", 12, Ui.G_SELECT, true);
            num.setPadding(0, 0, Ui.dp(c, 8), 0);
            head.addView(num);
            FrameLayout box = new FrameLayout(c);
            TextView ini = Ui.text(c, "", 10, Ui.SOFT, true);
            ini.setGravity(Gravity.CENTER);
            box.addView(ini, new FrameLayout.LayoutParams(-1, -1));
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            box.addView(img, new FrameLayout.LayoutParams(-1, -1));
            head.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 22)));
            TextView nm = Ui.text(c, "", 12, Ui.SOFT, true);
            nm.setSingleLine(true);
            nm.setEllipsize(TextUtils.TruncateAt.END);
            nm.setPadding(Ui.dp(c, 8), 0, 0, 0);
            head.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(head);
            TextView now = Ui.text(c, "", 14, Ui.TEXT, true);
            now.setSingleLine(true);
            now.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
            np.topMargin = Ui.dp(c, 4);
            card.addView(now, np);
            TextView next = Ui.text(c, "", 12, Ui.DIM, false);
            next.setSingleLine(true);
            next.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(next);
            card.setTag(new View[]{num, ini, img, nm, now, next});
            RecyclerView.ViewHolder vh = new RecyclerView.ViewHolder(card) {};
            card.setOnClickListener(v -> {
                int at = vh.getBindingAdapterPosition();
                if (at >= 0 && at < order.size()) {
                    if (order.get(at) == cur) hideOverlay();
                    else switchTo(order.get(at));
                }
            });
            return vh;
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Channel ch = order.get(position);
            Context c = holder.itemView.getContext();
            View[] v = (View[]) holder.itemView.getTag();
            boolean me = ch == cur;
            holder.itemView.setBackground(me ? Ui.ring(c, 0x332A6BD8, Ui.G_SELECT, 2, 12) : Ui.touch(c, false, 0xCC132D6B, 12));
            int n = lib.number(ch);
            ((TextView) v[0]).setText(n > 0 ? String.valueOf(n) : "");
            ((TextView) v[1]).setText(ch.logo.isEmpty() ? Ui.initials(ch.name) : "");
            ImageView img = (ImageView) v[2];
            if (ch.logo.isEmpty()) Glide.with(img).clear(img);
            else Glide.with(img).load(ch.logo).into(img);
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
        TrackSelectionOverride o = player.getTrackSelectionParameters().overrides.get(t.group.getMediaTrackGroup());
        return o != null && o.trackIndices.contains(t.index);
    }

    private void choose(Track t) {
        if (player == null) return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setOverrideForType(new TrackSelectionOverride(t.group.getMediaTrackGroup(), t.index))
                .setTrackTypeDisabled(t.group.getType(), false)
                .build());
    }

    private void automatic(int type, boolean off) {
        if (player == null) return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(type)
                .setTrackTypeDisabled(type, off)
                .build());
    }

    /** The options sheet from the panel's ⋯. */
    private void options() {
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

        List<Track> text = tracks(C.TRACK_TYPE_TEXT);
        if (!text.isEmpty()) {
            LinearLayout row = chipRow(c, box, R.string.live_subtitles);
            List<TextView> chips = new ArrayList<>();
            boolean off = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
            boolean anySelected = false;
            for (Track t : text) anySelected |= t.group.isTrackSelected(t.index);
            TextView none = Ui.guideChip(c, getString(R.string.live_off), off || !anySelected);
            none.setOnClickListener(v -> {
                automatic(C.TRACK_TYPE_TEXT, true);
                for (TextView x : chips) Ui.styleChip(c, x, x == v);
            });
            chips.add(none);
            addChip(row, none);
            for (Track t : text) {
                TextView chip = Ui.guideChip(c, t.label, !off && t.group.isTrackSelected(t.index));
                chip.setOnClickListener(v -> {
                    choose(t);
                    for (TextView x : chips) Ui.styleChip(c, x, x == v);
                });
                chips.add(chip);
                addChip(row, chip);
            }
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
        List<Channel> similar = lib.similar(cur, 24);
        if (!similar.isEmpty()) {
            LinearLayout row = chipRow(c, box, R.string.live_similar);
            for (Channel ch : similar) {
                LinearLayout card = new LinearLayout(c);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 14), 0);
                card.setBackground(Ui.touch(c, false, Ui.GLASS, 14));
                FrameLayout lb = new FrameLayout(c);
                TextView ini = Ui.text(c, ch.logo.isEmpty() ? Ui.initials(ch.name) : "", 10, Ui.SOFT, true);
                ini.setGravity(Gravity.CENTER);
                lb.addView(ini, new FrameLayout.LayoutParams(-1, -1));
                ImageView img = new ImageView(c);
                img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if (!ch.logo.isEmpty()) Glide.with(img).load(ch.logo).into(img);
                lb.addView(img, new FrameLayout.LayoutParams(-1, -1));
                card.addView(lb, new LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 28)));
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
            enterPip();
        });
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

    private static final int MIDDLE = 0, LEFT = 1, RIGHT = 2;
    private int zone;
    private boolean edge, multi;
    private float downX, downY, startLevel, pinch = 1f;
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
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        GestureDetector g = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(@NonNull MotionEvent e) { return true; }

            @Override public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (hint != null && hint.getVisibility() == View.VISIBLE) return true;
                if (message.getVisibility() == View.VISIBLE && retries >= RETRIES) {
                    play(true);
                    return true;
                }
                if (overlayShown()) hideOverlay();
                else showOverlay();
                return true;
            }

            @Override public boolean onDoubleTap(@NonNull MotionEvent e) {
                setAspect(aspect.toggled(), true);
                return true;
            }

            @Override public boolean onFling(MotionEvent a, @NonNull MotionEvent b, float vx, float vy) {
                if (edge || multi || zone != MIDDLE) return false;
                if (Math.abs(vy) > Math.abs(vx) * 1.3f && Math.abs(vy) > Ui.dp(PlayerActivity.this, 400)) {
                    step(vy < 0 ? 1 : -1);
                    return true;
                }
                return false;
            }
        });
        ScaleGestureDetector scale = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(@NonNull ScaleGestureDetector d) {
                pinch = 1f;
                return true;
            }

            @Override public boolean onScale(@NonNull ScaleGestureDetector d) {
                pinch *= d.getScaleFactor();
                float s = Math.max(0.85f, Math.min(1.2f, pinch));
                video.setScaleX(s);
                video.setScaleY(s);
                return true;
            }

            @Override public void onScaleEnd(@NonNull ScaleGestureDetector d) {
                video.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                Aspect a = aspect.pinched(pinch);
                if (a != aspect) setAspect(a, true);
            }
        });
        touch.setOnTouchListener((v, e) -> {
            scale.onTouchEvent(e);
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                multi = false;
                edge = false;
                downX = e.getX();
                downY = e.getY();
                float w = v.getWidth();
                zone = downX < w * 0.3f ? LEFT : downX > w * 0.7f ? RIGHT : MIDDLE;
                startLevel = zone == LEFT ? brightness() : zone == RIGHT ? volume() : 0;
            } else if (action == MotionEvent.ACTION_POINTER_DOWN && !multi) {
                // Two fingers: a pinch, not a tap or a swipe.
                multi = true;
                MotionEvent cancel = MotionEvent.obtain(e);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                g.onTouchEvent(cancel);
                cancel.recycle();
            }
            if (multi) return true;
            if (action == MotionEvent.ACTION_MOVE && zone != MIDDLE) {
                float dy = downY - e.getY(), dx = e.getX() - downX;
                if (!edge && Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) edge = true;
                if (edge) {
                    float f = startLevel + dy / Math.max(1f, v.getHeight() * 0.8f);
                    if (zone == LEFT) setBrightness(f);
                    else setVolume(f);
                }
            }
            g.onTouchEvent(e);
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && edge) {
                edge = false;
                h.postDelayed(hideLevel, 700);
            }
            return true;
        });
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
        card.setBackground(Ui.round(this, Ui.G_PANEL, 20));
        card.addView(Ui.text(this, getString(R.string.live_hint_title), 20, Ui.TEXT, true));
        int[] lines = {R.string.live_hint_tap, R.string.live_hint_swipe, R.string.live_hint_zoom, R.string.live_hint_edges};
        for (int l : lines) {
            TextView t = Ui.text(this, "•  " + getString(l), 15, Ui.SOFT, false);
            t.setPadding(0, Ui.dp(this, 10), 0, 0);
            card.addView(t);
        }
        TextView ok = Ui.text(this, getString(R.string.live_got_it), 15, Ui.G_INK, true);
        ok.setGravity(Gravity.CENTER);
        ok.setPadding(Ui.dp(this, 22), 0, Ui.dp(this, 22), 0);
        ok.setBackground(Ui.touch(this, false, Ui.G_SELECT, 24));
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-2, Ui.dp(this, 48));
        op.topMargin = Ui.dp(this, 18);
        op.gravity = Gravity.END;
        card.addView(ok, op);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(Math.min(Ui.dp(this, 460), getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 32)), -2, Gravity.CENTER);
        cover.addView(card, cp);
        View.OnClickListener done = v -> {
            prefs().edit().putBoolean("hint_seen", true).apply();
            fade(cover, false);
        };
        ok.setOnClickListener(done);
        cover.setOnClickListener(done);
        root.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        hint = cover;
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
        video.setUseController(false);
        video.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        video.setKeepContentOnPlayerReset(true);
        video.setShutterBackgroundColor(0xFF000000);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));
        root.addView(gestures(), new FrameLayout.LayoutParams(-1, -1));

        spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(Ui.TEXT));
        root.addView(spinner, new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48), Gravity.CENTER));

        message = Ui.text(this, "", 16, Ui.TEXT, true);
        message.setGravity(Gravity.CENTER);
        message.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 14));
        message.setBackground(Ui.round(this, 0xCC0B0C21, 14));
        message.setVisibility(View.GONE);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        mp.leftMargin = mp.rightMargin = Ui.dp(this, 32);
        root.addView(message, mp);

        zoom = Ui.text(this, "", 18, Ui.TEXT, true);
        zoom.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 10));
        zoom.setBackground(Ui.round(this, 0xCC0A1A4C, 22));
        zoom.setVisibility(View.GONE);
        root.addView(zoom, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        level = new LinearLayout(this);
        level.setOrientation(LinearLayout.VERTICAL);
        level.setGravity(Gravity.CENTER_HORIZONTAL);
        level.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 16));
        level.setBackground(Ui.round(this, 0xCC0A1A4C, 18));
        levelIcon = new ImageView(this);
        levelIcon.setImageTintList(ColorStateList.valueOf(Ui.TEXT));
        level.addView(levelIcon, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)));
        levelText = Ui.text(this, "", 15, Ui.TEXT, true);
        levelText.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        level.addView(levelText);
        levelBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        levelBar.setMax(100);
        levelBar.setProgressTintList(ColorStateList.valueOf(Ui.G_SELECT));
        levelBar.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.LINE));
        level.addView(levelBar, new LinearLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 6)));
        level.setVisibility(View.GONE);
        root.addView(level, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        root.addView(buildPanel(), new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        // Picture in picture and close, top right.
        top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        if (pipSupported()) top.addView(button(R.drawable.live_ic_pip, R.string.live_pip, v -> enterPip()));
        top.addView(button(R.drawable.live_ic_close, R.string.live_close, v -> finish()));
        top.setVisibility(View.GONE);
        root.addView(top, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));
        return root;
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
        int side = Ui.dp(c, 20);
        panel.setPadding(side, Ui.dp(c, 36), side, Ui.dp(c, 12));
        panel.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF2050B2C, 0xE00B2766, 0x000B2766}));

        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        number = Ui.text(c, "", 22, Ui.G_SELECT, true);
        number.setPadding(0, 0, Ui.dp(c, 12), 0);
        row.addView(number);
        FrameLayout box = new FrameLayout(c);
        box.setBackground(Ui.round(c, 0x26FFFFFF, 10));
        initials = Ui.text(c, "", 14, Ui.SOFT, true);
        initials.setGravity(Gravity.CENTER);
        box.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        logo = new ImageView(c);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p = Ui.dp(c, 5);
        logo.setPadding(p, p, p, p);
        box.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        row.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 68), Ui.dp(c, 46)));

        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 8), 0);
        LinearLayout names = new LinearLayout(c);
        names.setGravity(Gravity.CENTER_VERTICAL);
        name = Ui.text(c, "", 15, Ui.SOFT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(name, new LinearLayout.LayoutParams(-2, -2));
        group = Ui.text(c, "", 12, Ui.DIM, false);
        group.setSingleLine(true);
        group.setEllipsize(TextUtils.TruncateAt.END);
        group.setPadding(Ui.dp(c, 10), 0, 0, 0);
        names.addView(group, new LinearLayout.LayoutParams(0, -2, 1f));
        sleepLine = Ui.text(c, "", 12, Ui.G_SELECT, true);
        sleepLine.setPadding(Ui.dp(c, 10), 0, 0, 0);
        names.addView(sleepLine);
        info.addView(names);
        LinearLayout now = new LinearLayout(c);
        now.setGravity(Gravity.CENTER_VERTICAL);
        nowTitle = Ui.text(c, "", 18, Ui.TEXT, true);
        nowTitle.setSingleLine(true);
        nowTitle.setEllipsize(TextUtils.TruncateAt.END);
        now.addView(nowTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        nowTime = Ui.text(c, "", 13, Ui.G_SELECT, true);
        nowTime.setPadding(Ui.dp(c, 10), 0, 0, 0);
        now.addView(nowTime);
        info.addView(now);
        progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(Ui.RED));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Ui.LINE));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 4));
        pp.topMargin = Ui.dp(c, 5);
        pp.bottomMargin = Ui.dp(c, 4);
        info.addView(progress, pp);
        nextLine = Ui.text(c, "", 13, Ui.SOFT, false);
        nextLine.setSingleLine(true);
        nextLine.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(nextLine);
        row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        star = button(R.drawable.live_ic_star_outline, R.string.live_favourite, v -> toggleFavourite());
        row.addView(star);
        row.addView(button(R.drawable.live_ic_more, R.string.live_options, v -> options()));
        panel.addView(row);

        strip = new RecyclerView(c);
        strip.setLayoutManager(new LinearLayoutManager(c, LinearLayoutManager.HORIZONTAL, false));
        strip.setAdapter(neighbours);
        strip.setItemAnimator(null);
        strip.setClipToPadding(false);
        strip.setContentDescription(getString(R.string.live_mini_guide));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 76));
        sp.topMargin = Ui.dp(c, 12);
        panel.addView(strip, sp);
        panel.setVisibility(View.GONE);
        return panel;
    }

    private ImageButton button(int icon, int label, View.OnClickListener l) {
        ImageButton b = Ui.icon(this, icon, label);
        b.setBackground(Ui.touch(this, false, 0x990B0C21, 24));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48));
        lp.leftMargin = Ui.dp(this, 8);
        b.setLayoutParams(lp);
        return b;
    }
}

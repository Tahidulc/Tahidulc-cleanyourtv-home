package com.gathin.homefieldtv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import com.gathin.homefieldtv.captions.CaptionView;
import com.gathin.homefieldtv.captions.Captioner;
import com.gathin.homefieldtv.captions.VoskModel;
import com.gathin.homefieldtv.engine.Exec;

import java.util.List;
import java.util.Locale;

/**
 * Subtitles on live TV, over the full-screen picture only (not the small ones on Home and in the guide): the family's
 * choice for every channel, kept on this TV, of Off (as it starts), From the channel (the subtitles a channel sends:
 * DVB, its subtitle playlists or captions) or Automatic (made on this TV from what is said, as it is said, by
 * captions.Captioner; a channel's own show instead when it plainly sends some). In the family's look and size. The
 * words move up out of the way of the banner and the catch-up bar, and go while the live screen's rows are up.
 *
 * <p>Where the TV has no speech recognition of its own, Automatic needs an English speech model: the family is asked
 * first (the live screen's card), and it comes once, from Vosk's site, into the app's own storage. Nothing heard leaves
 * the TV. The channel's own tracks stay off while nothing shows them, so the TV doesn't decode them for nothing.
 */
@OptIn(markerClass = UnstableApi.class)
final class LiveSubs implements Captioner.Screen {
    static final int OFF = 0, CHANNEL = 1, AUTO = 2;
    static final String[] MODES = {"Off", "From the channel", "Automatic"};
    static final String[] LOOKS = {"White with an outline", "White on a black box", "Yellow with an outline"};
    static final String[] SIZES = {"Smaller", "Usual", "Bigger", "Biggest"};
    private static final float[] SCALES = {0.8f, 1f, 1.25f, 1.5f};
    /** The words' height at the usual size, in design pixels: about the phone's at 1.6 times, on a 1080p screen. */
    static final float WORDS = 58f;
    /** Where the words sit when nothing is over the picture: their foot this far above the screen's. */
    static final float FOOT = 72f;
    private static final String PREFS = "live_subs";

    /** The screen that can ask the family before the speech model is fetched (the live screen, while it shows). */
    interface Asker {
        void askModel();
    }

    /** A track of the channel's subtitles: its group and its place in it. */
    static final class Pick {
        final Tracks.Group group;
        final int index;

        Pick(Tracks.Group group, int index) {
            this.group = group;
            this.index = index;
        }
    }

    // ---- what the family chose ------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int mode(Context c) {
        int m = prefs(c).getInt("mode", OFF);
        return m < OFF || m > AUTO ? OFF : m;
    }

    static int look(Context c) {
        int l = prefs(c).getInt("look", CaptionView.LOOK_OUTLINE);
        return l < 0 || l >= LOOKS.length ? CaptionView.LOOK_OUTLINE : l;
    }

    static int size(Context c) {
        int s = prefs(c).getInt("size", 1);
        return s < 0 || s >= SIZES.length ? 1 : s;
    }

    private final Context app;
    private final CaptionView words;
    private final ChannelSubs own;
    private final Captioner captioner;
    private ExoPlayer player;
    /** The picture is full screen; the words have room (they go while the live screen's rows are up). */
    private boolean full, room = true;
    /** Asked about the speech model since Automatic was last chosen (not again until it is chosen again). */
    private boolean asked;
    private float lift;
    /** The speech model on its way: how far (-1: not coming); who wants to know (Settings, while it shows). */
    private int fetching = -1;
    private Runnable watcher;
    Asker asker;
    /** Previews: words standing in for what is heard. */
    static String previewWords;

    LiveSubs(Context c, FrameLayout layer) {
        app = c.getApplicationContext();
        own = new ChannelSubs(c);
        words = new CaptionView(c);
        // The lines kept to about 40 letters, as broadcast subtitles are; their foot clear of the screen's edge.
        int side = Math.round(Ui.f(270)), foot = Math.round(Ui.f(FOOT));
        own.setPadding(side, Math.round(Ui.f(60)), side, foot);
        words.setPadding(side, 0, side, foot);
        own.setVisibility(View.GONE);
        words.setVisibility(View.GONE);
        layer.addView(own, new FrameLayout.LayoutParams(-1, -1));
        layer.addView(words, new FrameLayout.LayoutParams(-1, -1));
        captioner = new Captioner(c, this);
        restyle();
    }

    /** The family's look and size, as Settings has them. */
    void restyle() {
        float px = Ui.f(WORDS * SCALES[size(app)]);
        int look = look(app);
        own.setLook(look, px);
        // CaptionView sizes its words against 19 sp: the scale that makes them px tall on this screen.
        float sp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 19f, app.getResources().getDisplayMetrics());
        words.setLook(look, sp > 0 ? px / sp : 1f);
    }

    /** A new choice for every channel (the live screen's menu, Settings). */
    void setMode(int m) {
        prefs(app).edit().putInt("mode", m).apply();
        asked = false;
        if (m != AUTO) words.clear();
        update();
    }

    void setLook(int look) {
        prefs(app).edit().putInt("look", look).apply();
        restyle();
    }

    void setSize(int size) {
        prefs(app).edit().putInt("size", size).apply();
        restyle();
    }

    // ---- what the player says -----------------------------------------------------------------------------------

    /** A player made: the channel's subtitle tracks off until something shows them. */
    void attach(ExoPlayer p) {
        player = p;
        select(null);
        update();
    }

    /** The player let go (another screen, a film, the app off screen): listening stops, the words go. */
    void detach() {
        player = null;
        captioner.stop();
        own.clear();
        words.clear();
        show();
    }

    /** Another channel: what was said on the last one goes; its tracks are looked at once the new one's are known. */
    void channelChanged() {
        captioner.clear();
        own.clear();
        if (player != null) select(null);
    }

    /** Where the picture is now (screen pixels; empty when it isn't shown) and, inside that, the picture itself. */
    void frame(Rect where, int x, int y, int w, int h) {
        boolean f = where != null && !where.isEmpty() && where.width() >= Ui.f(1700);
        // The channel's pictures of words go on the picture, but never off the screen (a picture zoomed past its edges).
        if (f) {
            int l = Math.max(x, where.left), t = Math.max(y, where.top), r = Math.min(x + w, where.right), b = Math.min(y + h, where.bottom);
            own.setFrame(l, t, Math.max(0, r - l), Math.max(0, b - t));
        } else {
            own.setFrame(x, y, w, h);
        }
        if (f == full) return;
        full = f;
        update();
    }

    /** The channel's tracks are known, or changed. */
    void tracksChanged() {
        update();
    }

    /** What the channel's subtitles say now. */
    void cues(List<Cue> c) {
        own.setCues(c);
        show();
    }

    /**
     * Over the picture: the words' foot kept above y (design pixels: the banner's or the catch-up bar's top), or at
     * the usual place (a negative y with room true), or gone for now (room false: the live screen's rows).
     */
    void room(boolean has, float above) {
        room = has;
        float to = above <= 0 ? 0f : Math.max(0f, Ui.f(1080f - FOOT) - Ui.f(above));
        if (to != lift) {
            lift = to;
            if (Exec.inline) {
                words.setTranslationY(-to);
                own.setTranslationY(-to);
            } else {
                words.animate().translationY(-to).setDuration(220).start();
                own.animate().translationY(-to).setDuration(220).start();
            }
        }
        show();
    }

    /** What is chosen and what the channel has, put into effect: its own subtitles, listening, or neither. */
    private void update() {
        if (Exec.inline) {
            words.setText(full && mode(app) == AUTO && previewWords != null ? previewWords : "");
            show();
            return;
        }
        if (player == null || !full) {
            captioner.stop();
            if (player != null) select(null);
            own.clear();
            words.clear();
            show();
            return;
        }
        int m = mode(app);
        Tracks tracks = player.getCurrentTracks();
        Pick plain = own(tracks, false);
        Pick shown = m == CHANNEL ? own(tracks, true) : m == AUTO ? plain : null;
        select(shown);
        if (shown == null) own.clear();
        if (m != AUTO || plain != null) {
            captioner.stop();
            words.clear();
        } else if (!tracks.isEmpty() && !captioner.running() && fetching < 0 && !VoskModel.fetching()
                && (!asked || Captioner.canListen(app))) {
            // Known to have none of its own: listening (the model asked for first where the TV has nothing).
            captioner.start(LiveTv.TAP);
        }
        show();
    }

    /** The channel's subtitle track shown (null: none, and the track kind off), only when that changes. */
    private void select(Pick p) {
        if (player == null) return;
        TrackSelectionParameters now = player.getTrackSelectionParameters();
        TrackSelectionOverride want = p == null ? null : new TrackSelectionOverride(p.group.getMediaTrackGroup(), p.index);
        TrackSelectionOverride have = null;
        for (TrackSelectionOverride o : now.overrides.values()) if (o.getType() == C.TRACK_TYPE_TEXT) have = o;
        boolean off = now.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
        if (off == (p == null) && (want == null ? have == null : want.equals(have))) return;
        TrackSelectionParameters.Builder b = now.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, p == null);
        if (want != null) b.addOverride(want);
        player.setTrackSelectionParameters(b.build());
    }

    private void show() {
        boolean on = full && room;
        own.setVisibility(on && own.showing() ? View.VISIBLE : View.GONE);
        boolean listening = Exec.inline ? previewWords != null : captioner.running() || fetching >= 0;
        words.setVisibility(on && listening && mode(app) == AUTO ? View.VISIBLE : View.GONE);
    }

    /**
     * The channel's own subtitles, when it plainly sends some: DVB subtitles, its subtitle playlists, or captions it
     * names (a language or a label); with bare too, also a captions track nothing names, which may hold none (a stream
     * is given one whether it has captions or not). Null for none.
     */
    static Pick own(Tracks tracks, boolean bare) {
        Pick unnamed = null;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) continue;
                Format f = g.getTrackFormat(i);
                if (plainlyOwn(f.sampleMimeType, f.codecs, f.language, f.label)) return new Pick(g, i);
                if (unnamed == null) unnamed = new Pick(g, i);
            }
        }
        return bare ? unnamed : null;
    }

    /**
     * Pure: whether a subtitle track is plainly the channel's own: anything but closed captions, or captions it names.
     * Subtitles read as they come in carry Media3's own kind, theirs in codecs.
     */
    static boolean plainlyOwn(String mime, String codecs, String language, String label) {
        String kind = MimeTypes.APPLICATION_MEDIA3_CUES.equals(mime) && codecs != null ? codecs : mime;
        boolean cc = MimeTypes.APPLICATION_CEA608.equals(kind) || MimeTypes.APPLICATION_CEA708.equals(kind);
        boolean named = language != null && !language.isEmpty() && !"und".equals(language)
                || label != null && !label.trim().isEmpty();
        return !cc || named;
    }

    /** A line on what the channel playing sends, for the menu ("" when nothing is known yet). */
    String about() {
        if (Exec.inline) return "This channel sends none of its own";
        if (player == null) return "";
        Tracks t = player.getCurrentTracks();
        if (t.isEmpty()) return "";
        if (own(t, false) != null) return "This channel sends subtitles of its own";
        return own(t, true) != null ? "This channel may carry closed captions" : "This channel sends none of its own";
    }

    // ---- the speech model -----------------------------------------------------------------------------------------

    /** Whether Automatic here needs the speech model fetched first (no recogniser of the TV's own, none fetched yet). */
    boolean needsModel() {
        return Exec.inline || !Captioner.canListen(app);
    }

    /** Whether the speech model is what Automatic uses here (the TV has no recogniser of its own). */
    boolean usesModel() {
        return Exec.inline || !Captioner.deviceListens(app);
    }

    /** How far the speech model has come (-1: not coming). */
    int fetching() {
        return fetching;
    }

    /** Settings, while it shows the speech model's line: told how the fetch goes (null: no one). */
    void watch(Runnable w) {
        watcher = w;
    }

    /** The line asking the family, with the sizes (each kept on one line with its MB). */
    static String askLine() {
        return String.format(Locale.UK, "This TV has no speech recognition of its own, so automatic subtitles need Homefield's "
                + "English speech model: a one-time download of about %d MB (about %d MB on the TV). It stays on this "
                + "TV, and what's said on the channels never leaves it.", VoskModel.SIZE_MB, VoskModel.UNPACKED_MB);
    }

    /** The family said yes: the model fetched, how far shown in the words' place, listening once it is in. */
    void fetch() {
        if (fetching >= 0 || VoskModel.fetching()) return;
        fetching = 0;
        progress();
        VoskModel.download(app, new VoskModel.Progress() {
            @Override public void progress(int percent) {
                if (percent >= 0) fetching = percent;
                LiveSubs.this.progress();
            }

            @Override public void done(boolean ok) {
                fetching = -1;
                words.setText(ok ? "Automatic subtitles are ready" : "");
                if (!ok) Toast.makeText(app, "Couldn't get the speech model. Try again later.", Toast.LENGTH_LONG).show();
                if (watcher != null) watcher.run();
                update();
            }
        });
    }

    private void progress() {
        words.setText(String.format(Locale.UK, "Getting the speech model… %d%%", fetching));
        show();
        if (watcher != null) watcher.run();
    }

    // ---- what the captioner says ------------------------------------------------------------------------------

    @Override public void words(String text) {
        words.setText(text);
    }

    @Override public void notice(String said) {
        if (full && room) Toast.makeText(app, said, Toast.LENGTH_LONG).show();
    }

    @Override public void needModel() {
        if (asked || fetching >= 0) return;
        if (asker == null) return;
        asked = true;
        asker.askModel();
    }

    /** The question couldn't be put now (something else is up): put next time listening is tried. */
    void askLater() {
        asked = false;
    }
}

package com.gathin.homefieldtv.captions;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Automatic subtitles from what is said on the channel, made on the device: the sound taken from the player (AudioTap),
 * the TV's own on-device recogniser where it has one (Android 13 and later), otherwise the offline model once it is
 * there (VoskModel), and the words kept readable (CaptionText) for the screen (CaptionView). Nothing heard leaves the
 * device. Main thread. Self-contained, so the TV app can use it as it is.
 */
public final class Captioner {
    /** What the screen is told: the words to show, a line about how it is going, and that the model is wanted. */
    public interface Screen {
        void words(String text);

        void notice(String words);

        /** No recogniser on the device and no model yet: ask the family before fetching it (VoskModel.SIZE_MB). */
        void needModel();
    }

    private static final String PREFS = "captions";
    private final Context app;
    private final Screen screen;
    private final CaptionText text = new CaptionText();
    private CaptionEngine engine;
    private AudioTap tap;

    public Captioner(Context c, Screen screen) {
        app = c.getApplicationContext();
        this.screen = screen;
    }

    private SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Whether the device's own recogniser does it here (Android 13 and later, on the device, and it hasn't failed). */
    public static boolean deviceListens(Context c) {
        Context app = c.getApplicationContext();
        return android.os.Build.VERSION.SDK_INT >= 33
                && !app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("own_failed", false)
                && OnDeviceEngine.available(app);
    }

    /** Whether listening can start now without asking: the device's own recogniser, or the offline model is here. */
    public static boolean canListen(Context c) {
        return deviceListens(c) || VoskModel.ready(c.getApplicationContext());
    }

    public boolean running() {
        return engine != null;
    }

    /** Listening starts on this player's sound (the engine the device can do); needModel() when there is none yet. */
    public void start(AudioTap from) {
        stop();
        tap = from;
        CaptionEngine e;
        boolean own = false;
        if (android.os.Build.VERSION.SDK_INT >= 33 && deviceListens(app)) {
            e = new OnDeviceEngine(app);
            own = true;
        } else if (VoskModel.ready(app)) {
            e = new VoskEngine(VoskModel.dir(app));
        } else {
            screen.needModel();
            return;
        }
        engine = e;
        boolean deviceOwn = own;
        e.start(new CaptionEngine.Listener() {
            @Override public void partial(String words) {
                if (engine != e) return;
                text.partial(words);
                screen.words(text.shown());
            }

            @Override public void line(String words) {
                if (engine != e) return;
                text.line(words);
                screen.words(text.shown());
            }

            @Override public void failed(String why, boolean tryOther) {
                if (engine != e) return;
                stop();
                if (tryOther && deviceOwn) {
                    // The TV's own can't do it here: the offline model from now on.
                    prefs().edit().putBoolean("own_failed", true).apply();
                    if (tap != null) start(tap);
                } else {
                    screen.notice(why);
                }
            }
        });
        from.listen(e::feed);
    }

    /** Listening stops (the words on screen stay until they fade). */
    public void stop() {
        if (tap != null) tap.listen(null);
        if (engine != null) engine.stop();
        engine = null;
    }

    /** Another channel: what was said on the last one goes. */
    public void clear() {
        text.clear();
        screen.words("");
    }
}

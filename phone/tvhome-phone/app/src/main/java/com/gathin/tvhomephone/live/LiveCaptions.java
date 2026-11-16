package com.gathin.tvhomephone.live;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.captions.CaptionView;
import com.gathin.tvhomephone.captions.Captioner;
import com.gathin.tvhomephone.captions.VoskModel;

/**
 * Automatic subtitles on live TV, in the full-screen player: on or off for every channel (Settings' Live TV, and the
 * player's Subtitles), the words made on the phone from what is said on the channel (captions.Captioner) and shown over
 * the picture in the family's subtitle look and size. A channel that sends subtitles of its own shows those instead.
 * Off while the channel is cast to a TV, in the small window and when the player isn't on screen.
 */
final class LiveCaptions implements Captioner.Screen {
    private static final String KEY = "auto_live";

    /** On for every live channel (off unless switched on). */
    static boolean on(Context c) {
        return Subtitles.prefs(c).getBoolean(KEY, false);
    }

    static void setOn(Context c, boolean on) {
        Subtitles.prefs(c).edit().putBoolean(KEY, on).apply();
    }

    /** What the player says about the channel now: whether it sends subtitles of its own, and to show them. */
    interface Host {
        boolean channelHasOwn();

        void showOwn();

        boolean casting();
    }

    private final Activity a;
    private final CaptionView view;
    private final Host host;
    private final Captioner captioner;
    private boolean visible, asked;

    LiveCaptions(Activity a, CaptionView view, Host host) {
        this.a = a;
        this.view = view;
        this.host = host;
        captioner = new Captioner(a, this);
        restyle();
    }

    /** The family's subtitle look and size, as set for films. */
    void restyle() {
        int look = Subtitles.look(a);
        view.setLook(look == 1 ? CaptionView.LOOK_BOX : look == 2 ? CaptionView.LOOK_YELLOW : CaptionView.LOOK_OUTLINE,
                Subtitles.SIZES[Subtitles.size(a)]);
    }

    /** The player is on screen (or back): listening starts when they're on and the channel has none of its own. */
    void resume() {
        visible = true;
        check();
    }

    /** Off screen, in the small window, or cast elsewhere: listening stops. */
    void pause() {
        visible = false;
        captioner.stop();
        view.clear();
    }

    /** Another channel: the last one's words go, and it is looked at again once it plays. */
    void channelChanged() {
        captioner.clear();
        captioner.stop();
    }

    /** The channel's tracks are known (it has started): its own subtitles, or listening. */
    void check() {
        if (!visible || !on(a) || host.casting()) {
            captioner.stop();
            if (!on(a)) view.clear();
            return;
        }
        if (host.channelHasOwn()) {
            captioner.stop();
            view.clear();
            host.showOwn();
            return;
        }
        if (!captioner.running() && !VoskModel.fetching()) captioner.start(Playback.TAP);
    }

    /** Turned on or off from the player. */
    void set(boolean on) {
        setOn(a, on);
        asked = false;
        if (on) check();
        else {
            captioner.stop();
            view.clear();
        }
    }

    // ---- what the captioner says ------------------------------------------------------------------------------

    @Override public void words(String text) {
        view.setText(text);
    }

    @Override public void notice(String words) {
        if (visible) Toast.makeText(a, words, Toast.LENGTH_LONG).show();
    }

    @Override public void needModel() {
        if (asked || a.isFinishing()) return;
        asked = true;
        new AlertDialog.Builder(a)
                .setTitle(R.string.captions_title)
                .setMessage(a.getString(R.string.captions_model_ask, VoskModel.SIZE_MB))
                .setPositiveButton(R.string.captions_model_get, (d, w) -> fetch())
                .setNegativeButton(R.string.captions_not_now, (d, w) -> { })
                .show();
    }

    private void fetch() {
        view.setText(a.getString(R.string.captions_model_getting, 0));
        VoskModel.download(a, new VoskModel.Progress() {
            @Override public void progress(int percent) {
                if (visible && percent >= 0) view.setText(a.getString(R.string.captions_model_getting, percent));
            }

            @Override public void done(boolean ok) {
                view.setText(ok ? a.getString(R.string.captions_model_ready) : "");
                if (!ok) {
                    if (visible) Toast.makeText(a, R.string.captions_model_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                check();
            }
        });
    }
}

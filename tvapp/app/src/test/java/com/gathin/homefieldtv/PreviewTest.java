package com.gathin.homefieldtv;

import static org.junit.Assume.assumeTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.homefieldtv.engine.Catalog;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.PreviewData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;

/**
 * Every screen drawn at 1920 x 1080 with a made-up playlist (PreviewData), driven with the remote's keys, each saved as
 * a PNG in the folder PREVIEW_OUT names. Skipped unless PREVIEW_OUT is set; nothing here goes into the app.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class PreviewTest {
    private File out;

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void press(Activity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    private static void press(Activity a, int code, int times) {
        for (int i = 0; i < times; i++) press(a, code);
    }

    private void shot(Activity a, String name) throws Exception {
        idle();
        View root = a.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1920, 1080);
        Bitmap b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        // Twice: a picture that arrived during the first draw is in the second.
        root.draw(new Canvas(b));
        idle();
        b.eraseColor(0);
        root.draw(new Canvas(b));
        try (FileOutputStream o = new FileOutputStream(new File(out, name + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        }
    }

    @Test public void everyScreen() throws Exception {
        String dir = System.getProperty("preview.out", "");
        String root = System.getProperty("preview.pics", "");
        assumeTrue(!dir.isEmpty() && !root.isEmpty());
        out = new File(dir);
        //noinspection ResultOfMethodCallIgnored
        out.mkdirs();
        Exec.inline = true;
        Context app = RuntimeEnvironment.getApplication();
        PreviewData.load(app, root);
        LiveTv.previewStill = BitmapFactory.decodeFile(new File(root, "skin-preview/img/t225385-bd960.png").getPath());

        TvActivity a = Robolectric.buildActivity(TvActivity.class).setup().get();
        idle();
        shot(a, "01-home");

        // Into the rows: on now, then down to the new films.
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        shot(a, "02-home-on-now");
        press(a, KeyEvent.KEYCODE_DPAD_DOWN, 2);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        shot(a, "03-home-new-films");
        press(a, KeyEvent.KEYCODE_BACK);

        // The TV guide.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN, 2);
        shot(a, "04-guide");
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT, 2);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN, 6);
        shot(a, "05-guide-later");
        press(a, KeyEvent.KEYCODE_DPAD_LEFT, 3);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN, 2);
        shot(a, "06-guide-sport");

        // A channel full screen, its banner up (the remote from the categories back into the grid).
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        shot(a, "07-live-banner");
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        shot(a, "08-live-next");
        // Back hides the banner, Back again is Home (the menu still on TV guide).
        press(a, KeyEvent.KEYCODE_BACK);
        press(a, KeyEvent.KEYCODE_BACK);

        // Movies, a film's page.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        shot(a, "09-movies");
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT, 2);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        shot(a, "10-film");
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        shot(a, "11-film-more");
        press(a, KeyEvent.KEYCODE_BACK);
        press(a, KeyEvent.KEYCODE_BACK);
        press(a, KeyEvent.KEYCODE_BACK);

        // TV shows, a series' page and its episodes.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        shot(a, "12-tv-shows");
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        shot(a, "13-series");
        press(a, KeyEvent.KEYCODE_DPAD_DOWN, 2);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        shot(a, "14-series-episodes");
        press(a, KeyEvent.KEYCODE_BACK);
        press(a, KeyEvent.KEYCODE_BACK);
        press(a, KeyEvent.KEYCODE_BACK);

        // Search (opened as its key does, from wherever the remote is now).
        a.home();
        a.openSearch();
        idle();
        ((SearchScreen) a.top()).type("re");
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT, 6);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        shot(a, "15-search");
        press(a, KeyEvent.KEYCODE_BACK);
        press(a, KeyEvent.KEYCODE_BACK);

        // Settings: its panel over Home, then a login being typed in.
        a.home();
        a.openSettings();
        idle();
        shot(a, "16-settings");
        ((SettingsScreen) a.top()).preview(2, "Home", "http://provider.example:8080", "family");
        press(a, KeyEvent.KEYCODE_DPAD_DOWN, 2);
        shot(a, "17-settings-xtream");
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN, 2);
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT, 3);
        shot(a, "18-settings-keyboard");

        // A film playing, its bar up.
        Catalog.Title film = Catalog.get(app).recent(false, 1).get(0);
        LiveTv.previewStill = BitmapFactory.decodeFile(new File(root, "skin-preview/img/m969681-bd960.png").getPath());
        VodActivity.previewDur = 145 * 60_000L;
        VodActivity.previewPos = 52 * 60_000L + 17_000L;
        VodActivity.previewPlaying = false;
        Intent i = new Intent(app, VodActivity.class).putExtra(VodActivity.TITLE, film.key).putExtra(VodActivity.EPISODE, "")
                .putExtra(VodActivity.START, 0L);
        VodActivity v = Robolectric.buildActivity(VodActivity.class, i).setup().get();
        idle();
        shot(v, "19-player");
    }
}

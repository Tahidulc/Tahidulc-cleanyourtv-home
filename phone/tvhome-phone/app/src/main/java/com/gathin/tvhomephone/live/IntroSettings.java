package com.gathin.tvhomephone.live;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.sports.Ui;
import com.gathin.tvhomephone.sync.TvUpload;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The matchday intro's own section, at the top of Settings: on or off, and its film: the built-in films or a video of
 * the family's own (Android's file picker; kept here and sent to the paired TVs), with a line saying which is on.
 */
public final class IntroSettings {
    private IntroSettings() {}

    private static final List<Runnable> SHOWN = new CopyOnWriteArrayList<>();

    /** The video changed (chosen, or back to the built-in films): the section, if open, says so. */
    static void changed() {
        for (Runnable r : SHOWN) App.main.post(r);
    }

    public static View build(Context c) {
        LinearLayout col = Ui.column(c);
        col.addView(Ui.heading(c, c.getString(R.string.live47_title), c.getColor(R.color.text_soft)));
        col.addView(Ui.text(c, c.getString(R.string.live47_line), 13, c.getColor(R.color.text_dim), false));
        BoardSettings.row(c, col, c.getString(R.string.live45_intro), PlayerIntro.introOn(c), on -> PlayerIntro.setIntro(c, on));
        TextView now = Ui.text(c, "", 14, c.getColor(R.color.text), true);
        TextView resend = Ui.text(c, c.getString(R.string.live47_resend), 14, 0xFF6FB6FF, true);
        resend.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
        Runnable say = () -> {
            boolean own = IntroVideo.useOwn(c);
            now.setText(own ? c.getString(R.string.live47_now_own, IntroVideo.words(c)) : c.getString(R.string.live47_now_builtin));
            resend.setVisibility(own ? View.VISIBLE : View.GONE);
        };
        BoardSettings.styles(c, col, c.getString(R.string.live46_intro_video), new String[]{"builtin", "own"},
                new String[]{c.getString(R.string.live46_builtin), c.getString(R.string.live46_own)},
                IntroVideo.useOwn(c) ? "own" : "builtin", key -> {
                    if ("own".equals(key)) {
                        c.startActivity(new Intent(c, PickIntroActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } else if (IntroVideo.has(c)) {
                        IntroVideo.clear(c);
                        TvUpload.clearIntroVideo(c);
                        Toast.makeText(c, R.string.live46_back, Toast.LENGTH_SHORT).show();
                        say.run();
                    }
                });
        now.setPadding(0, Ui.dp(c, 8), 0, 0);
        col.addView(now);
        col.addView(resend);
        resend.setOnClickListener(v -> {
            if (!IntroVideo.useOwn(c)) return;
            Toast.makeText(c, "Sending it to the TV…", Toast.LENGTH_SHORT).show();
            TvUpload.introVideo(c, IntroVideo.file(c), IntroVideo.name(c), (line, err) -> Toast.makeText(c, line, Toast.LENGTH_LONG).show());
        });
        col.addView(Ui.text(c, c.getString(R.string.live47_hint), 13, c.getColor(R.color.text_dim), false));
        say.run();
        col.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                SHOWN.add(say);
                say.run();
            }

            @Override public void onViewDetachedFromWindow(View v) {
                SHOWN.remove(say);
            }
        });
        return col;
    }
}

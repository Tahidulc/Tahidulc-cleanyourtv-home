package com.gathin.tvhomephone.update;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.sync.Tvs;
import com.gathin.tvhomephone.watch.Ui;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.lang.ref.WeakReference;

/**
 * What the app shows of its own updates (AppUpdate): Settings' section (this version, what the TVs have, Update or
 * Check for updates, how far an update has got), the question when a newer version is found, the one-time "let
 * Homefield install its updates", and the update's progress.
 */
public final class UpdateSettings {
    private UpdateSettings() {}

    private static WeakReference<AlertDialog> showing = new WeakReference<>(null);

    /** Settings' section, last on the page in place of the version line. */
    public static View build(Fragment host) {
        Context c = host.requireContext();
        LinearLayout box = Ui.column(c);
        box.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 8));
        box.addView(Ui.label(c, "APP UPDATES", Ui.SHOWS));
        LinearLayout card = Ui.column(c);
        int p = Ui.dp(c, 14);
        card.setPadding(p, p, p, p);
        card.setBackground(Ui.glass(c));
        card.addView(Ui.text(c, "Homefield " + AppUpdate.myName(c), 16, 0xFFFFFFFF, true));
        TextView line = Ui.text(c, "", 13, Ui.DIM, false);
        line.setPadding(0, Ui.dp(c, 4), 0, 0);
        card.addView(line, Ui.fill());
        LinearProgressIndicator bar = bar(c);
        card.addView(bar, Ui.margins(Ui.fill(), c, 0, 12, 0, 2));
        LinearLayout buttons = Ui.row(c);
        MaterialButton update = Ui.pill(c, "Update", true, v -> {
            if (host.isAdded()) AppUpdate.update(host.requireActivity());
        });
        MaterialButton look = Ui.pill(c, "Check for updates", false, v -> {
            if (host.isAdded()) checkByHand(host.requireActivity());
        });
        buttons.addView(update, Ui.margins(Ui.wrap(), c, 0, 0, 8, 0));
        buttons.addView(look, Ui.wrap());
        card.addView(buttons, Ui.margins(Ui.fill(), c, 0, 12, 0, 0));
        box.addView(card, Ui.margins(Ui.fill(), c, 0, 10, 0, 0));
        Runnable paint = () -> {
            if (!host.isAdded()) return;
            AppUpdate.Stage s = AppUpdate.stage();
            AppUpdate.Offer o = AppUpdate.ready(c);
            boolean busy = AppUpdate.busy();
            line.setText(words(c, s, o));
            boolean moving = s == AppUpdate.Stage.FETCHING || s == AppUpdate.Stage.BUILDING || s == AppUpdate.Stage.INSTALLING;
            bar.setVisibility(moving ? View.VISIBLE : View.GONE);
            bar.setProgressCompat(Math.round(AppUpdate.share() * 1000), true);
            update.setVisibility(o != null && !busy ? View.VISIBLE : View.GONE);
            if (o != null) update.setText("Update to " + o.name);
            look.setVisibility(busy ? View.GONE : View.VISIBLE);
            look.setEnabled(s != AppUpdate.Stage.CHECKING);
            look.setText(s == AppUpdate.Stage.CHECKING ? "Looking…" : "Check for updates");
        };
        AppUpdate.addListener(paint);
        box.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                AppUpdate.addListener(paint);
                paint.run();
            }

            @Override public void onViewDetachedFromWindow(View v) {
                AppUpdate.removeListener(paint);
            }
        });
        paint.run();
        return box;
    }

    /** In a few words: what it is doing, or what the TVs have. */
    static String words(Context c, AppUpdate.Stage s, AppUpdate.Offer o) {
        int pc = Math.round(AppUpdate.share() * 100);
        switch (s) {
            case CHECKING:
                return "Looking on your TVs…";
            case FETCHING:
                return "Getting it from " + AppUpdate.from() + " · " + pc + "%";
            case BUILDING:
                return "Putting it together · " + pc + "%";
            case INSTALLING:
                return "Installing. Homefield closes for a moment as Android puts the new version in";
            case ASKING:
                return "Android asks first: tap Update on its screen to finish";
            default:
                break;
        }
        String why = AppUpdate.problem();
        if (why != null) return "The update didn't go in: " + why;
        if (o != null) {
            return "Homefield " + o.name + " is ready on " + o.tv + (o.notes.isEmpty() ? "" : ": " + o.notes) + " · "
                    + AppUpdate.megabytes(o.bytesFor(AppUpdate.myCode(c)));
        }
        if (!Tvs.paired(c)) return "Updates come from your TVs. Pair one above to get them";
        long at = AppUpdate.lookedAt(c);
        if (at == 0) return "Updates come from your TVs at home";
        String when = DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
        if (AppUpdate.tvsAnswered(c) == 0) return "None of your TVs answered (" + when + "). Updates come from them over the home Wi-Fi";
        return "Up to date · looked " + when;
    }

    /** Check for updates: the TVs looked at now, then the question, or a word on why there is none. */
    static void checkByHand(Activity a) {
        Context c = a.getApplicationContext();
        AppUpdate.check(c, () -> {
            if (a.isFinishing() || a.isDestroyed()) return;
            if (AppUpdate.ready(c) != null) {
                AppUpdate.ask(a, true);
            } else if (!Tvs.paired(c)) {
                Toast.makeText(a, "Pair a TV first: updates come from your TVs", Toast.LENGTH_LONG).show();
            } else if (AppUpdate.tvsAnswered(c) == 0) {
                Toast.makeText(a, "None of your TVs answered. On the home Wi-Fi with a TV on, try again", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(a, "Homefield is up to date", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** A newer version found: what is new, where from and how much, and Update or Later. */
    static void question(Activity a, AppUpdate.Offer o, long bytes) {
        String about = (o.notes.isEmpty() ? "" : o.notes + "\n\n") + "From " + o.tv + ", " + AppUpdate.megabytes(bytes)
                + ". Homefield closes for a moment while it updates, and everything stays as it is.";
        try {
            new MaterialAlertDialogBuilder(a).setTitle("Homefield " + o.name + " is ready")
                    .setMessage(about)
                    .setPositiveButton("Update", (d, w) -> AppUpdate.update(a))
                    .setNegativeButton("Later", null)
                    .show();
        } catch (RuntimeException ignored) {
            // the window has gone: asked again next time
        }
    }

    /** Android's one question first: Homefield allowed to install apps (its own updates). */
    static void allow(Activity a, Runnable going) {
        try {
            new MaterialAlertDialogBuilder(a).setTitle("Let Homefield install its updates")
                    .setMessage("Android asks once. On the next screen, turn on Allow from this source, then come back here: "
                            + "the update carries on by itself.")
                    .setPositiveButton("Open settings", (d, w) -> {
                        going.run();
                        Uri me = Uri.parse("package:" + a.getPackageName());
                        try {
                            a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, me));
                        } catch (ActivityNotFoundException e) {
                            try {
                                a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, me));
                            } catch (ActivityNotFoundException ignored) {
                                Toast.makeText(a, "Allow Homefield to install apps in Android's settings", Toast.LENGTH_LONG).show();
                            }
                        }
                    })
                    .setNegativeButton("Not now", null)
                    .show();
        } catch (RuntimeException ignored) {
            // the window has gone
        }
    }

    /** How far the update has got, until Android takes over (Hide: it goes on, in Settings too). */
    static void progress(Activity a) {
        if (a.isFinishing() || a.isDestroyed()) return;
        AlertDialog open = showing.get();
        if (open != null && open.isShowing()) return;
        Context c = a;
        LinearLayout col = Ui.column(c);
        int p = Ui.dp(c, 24);
        col.setPadding(p, Ui.dp(c, 8), p, 0);
        TextView line = Ui.text(c, "", 15, Ui.SOFT, false);
        LinearProgressIndicator bar = bar(c);
        col.addView(line, Ui.fill());
        col.addView(bar, Ui.margins(Ui.fill(), c, 0, 14, 0, 4));
        AlertDialog d;
        try {
            d = new MaterialAlertDialogBuilder(a).setTitle("Updating Homefield").setView(col).setPositiveButton("Hide", null).create();
        } catch (RuntimeException e) {
            return;
        }
        Runnable[] paint = new Runnable[1];
        paint[0] = () -> {
            if (!d.isShowing() || a.isDestroyed()) {
                AppUpdate.removeListener(paint[0]);
                return;
            }
            AppUpdate.Stage s = AppUpdate.stage();
            if (s == AppUpdate.Stage.IDLE || s == AppUpdate.Stage.CHECKING) {
                String why = AppUpdate.problem();
                AppUpdate.removeListener(paint[0]);
                if (why == null) {
                    d.dismiss();
                    return;
                }
                d.setTitle("The update didn't go in");
                line.setText(why);
                bar.setVisibility(View.GONE);
                if (d.getButton(DialogInterface.BUTTON_POSITIVE) != null) d.getButton(DialogInterface.BUTTON_POSITIVE).setText("OK");
                return;
            }
            line.setText(words(c, s, null));
            bar.setVisibility(s == AppUpdate.Stage.ASKING ? View.GONE : View.VISIBLE);
            bar.setProgressCompat(Math.round(AppUpdate.share() * 1000), true);
        };
        d.setOnDismissListener(x -> AppUpdate.removeListener(paint[0]));
        try {
            d.show();
        } catch (RuntimeException e) {
            return;
        }
        showing = new WeakReference<>(d);
        AppUpdate.addListener(paint[0]);
        paint[0].run();
    }

    private static LinearProgressIndicator bar(Context c) {
        LinearProgressIndicator b = new LinearProgressIndicator(c);
        b.setMax(1000);
        b.setIndicatorColor(0xFFFFFFFF);
        b.setTrackColor(Ui.GLASS);
        b.setTrackThickness(Ui.dp(c, 6));
        b.setTrackCornerRadius(Ui.dp(c, 3));
        b.setVisibility(View.GONE);
        return b;
    }
}

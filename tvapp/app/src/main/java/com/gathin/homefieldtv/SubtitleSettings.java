package com.gathin.homefieldtv;

import android.content.Context;

import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.subs.Langs;
import com.gathin.homefieldtv.subs.OpenSubtitles;
import com.gathin.homefieldtv.subs.SubsPrefs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings › Other › Subtitles, for films and episodes (the film player's Subtitles has the same look and finds them):
 *
 * <pre>
 *   OpenSubtitles     API key (typed as dots on the on-screen keyboard, then shown only by its last four letters),
 *                     Username and Password (optional: signing in gives more downloads a day), Check the login (how
 *                     many downloads a day it gives), Forget the login
 *   Languages         the family's languages for OpenSubtitles' search, English first as it comes
 *   Hearing impaired  files with the sounds written in first, or not
 *   Look              Size, Colour, Background (an outline or a black box), with the player's sample line
 * </pre>
 *
 * The key, the name and the password stay in the app's private preferences (SubsPrefs): never backed up, synced, logged or
 * shown, and never put back into the keyboard. The TV launcher keeps no OpenSubtitles login of its own (its settings and
 * provider were looked at), so they are typed here once; the phone's are its own.
 */
final class SubtitleSettings implements SettingsScreen.Pages {
    static final String PAGE = "other.subtitles", LANGS = PAGE + ".languages", LOOK = PAGE + ".look";
    static final String KEY = "os.key", USER = "os.user", PASS = "os.pass";

    private final SettingsScreen s;
    private final Context c;
    /** What Check the login said last (never the key or the login itself). */
    private static volatile String checked = "";

    private SubtitleSettings(SettingsScreen s, Context c) {
        this.s = s;
        this.c = c;
    }

    /** The page's owner for Settings (made as it is asked for: it keeps nothing of its own). */
    static SubtitleSettings of(SettingsScreen s, Context c) {
        return new SubtitleSettings(s, c);
    }

    static boolean ownsPage(String page) {
        return page.equals(PAGE) || page.startsWith(PAGE + ".");
    }

    static boolean ownsField(String field) {
        return field.equals(KEY) || field.equals(USER) || field.equals(PASS);
    }

    /** The line on Other that opens it. */
    static RowList.Row row(Context c) {
        return RowList.Row.item("page:" + PAGE, "Subtitles", SubsPrefs.hasKey(c)
                ? "OpenSubtitles " + (SubsPrefs.user(c).isEmpty() ? "with your key" : "signed in") + "  ·  " + SubsPrefs.languageWords(c)
                : "Films and episodes: add your OpenSubtitles key");
    }

    @Override public boolean owns(String page) {
        return ownsPage(page);
    }

    @Override public String title(String page) {
        switch (page) {
            case LANGS: return "Languages";
            case LOOK: return "Look";
            default: return "Subtitles";
        }
    }

    @Override public List<RowList.Row> rows(String page) {
        List<RowList.Row> out = new ArrayList<>();
        switch (page) {
            case LANGS:
                for (String code : Langs.CODES) {
                    boolean on = SubsPrefs.languages(c).contains(code);
                    out.add(RowList.Row.toggle("lang:" + code, Langs.name(code), "", on));
                }
                out.add(RowList.Row.note("OpenSubtitles is asked in these, and its results come in this order, English first."));
                break;
            case LOOK:
                look(c, out);
                break;
            default:
                account(out);
                out.add(RowList.Row.header("Films and episodes"));
                out.add(RowList.Row.item("page:" + LANGS, "Languages", SubsPrefs.languageWords(c)));
                out.add(RowList.Row.toggle("os:hi", "Hearing impaired first", "Files with the sounds written in too",
                        SubsPrefs.hearingFirst(c)));
                out.add(RowList.Row.item("page:" + LOOK, "Size, colour and background", SubsPrefs.styleWords(c)));
                out.add(RowList.Row.note("While a film or episode plays: OK, then Subtitles: Off, its own tracks, or Find on "
                        + "OpenSubtitles, with its timing half a second at a time. The choice is kept for the title."));
                break;
        }
        return out;
    }

    private void account(List<RowList.Row> out) {
        out.add(RowList.Row.header("OpenSubtitles"));
        String key = SubsPrefs.maskedKey(c);
        out.add(RowList.Row.item("os:key", "API key", key.isEmpty() ? "None yet  ·  OK to type it" : key));
        String user = SubsPrefs.user(c);
        out.add(RowList.Row.item("os:user", "Username", user.isEmpty() ? "Optional: more downloads a day" : user));
        out.add(RowList.Row.item("os:pass", "Password", SubsPrefs.hasPassword(c) ? "••••••••" : "None"));
        if (SubsPrefs.hasKey(c)) {
            out.add(RowList.Row.item("os:check", "Check the login", checked.isEmpty() ? "Asks OpenSubtitles once" : checked));
        }
        if (SubsPrefs.hasKey(c) || !user.isEmpty() || SubsPrefs.hasPassword(c)) {
            out.add(RowList.Row.item("os:forget", "Forget the login", "The key, the name and the password go from this TV"));
        }
        out.add(RowList.Row.note("Your own free API key from opensubtitles.com (your profile, API consumers). With your account's "
                + "name and password too, more files can be downloaded a day. They stay on this TV, in the app's own storage: "
                + "never synced, backed up, logged or shown."));
    }

    /** The look's choices, as the player's Subtitles' look has them. */
    static void look(Context c, List<RowList.Row> out) {
        int size = SubsPrefs.size(c), colour = SubsPrefs.colour(c), box = SubsPrefs.box(c);
        out.add(RowList.Row.header("Size"));
        for (int i = 0; i < SubsPrefs.SIZE_NAMES.length; i++) out.add(RowList.Row.radio("size:" + i, SubsPrefs.SIZE_NAMES[i], i == size));
        out.add(RowList.Row.header("Colour"));
        for (int i = 0; i < SubsPrefs.COLOUR_NAMES.length; i++) {
            out.add(RowList.Row.swatch("colour:" + i, SubsPrefs.COLOUR_NAMES[i], SubsPrefs.COLOURS[i], i == colour));
        }
        out.add(RowList.Row.header("Background"));
        for (int i = 0; i < SubsPrefs.BOX_NAMES.length; i++) out.add(RowList.Row.radio("box:" + i, SubsPrefs.BOX_NAMES[i], i == box));
    }

    @Override public void chosen(String page, RowList.Row row) {
        String tag = row.tag instanceof String ? (String) row.tag : "";
        if (tag.startsWith("page:")) {
            s.open(tag.substring(5));
            return;
        }
        if (tag.startsWith("lang:")) {
            String code = tag.substring(5);
            boolean on = !SubsPrefs.languages(c).contains(code);
            if (!on && SubsPrefs.languages(c).size() <= 1) {
                s.toast("At least one language stays on");
                return;
            }
            SubsPrefs.setLanguage(c, code, on);
            s.refresh();
            return;
        }
        if (tag.startsWith("size:") || tag.startsWith("colour:") || tag.startsWith("box:")) {
            int i = Integer.parseInt(tag.substring(tag.indexOf(':') + 1));
            if (tag.startsWith("size:")) SubsPrefs.setSize(c, i);
            else if (tag.startsWith("colour:")) SubsPrefs.setColour(c, i);
            else SubsPrefs.setBox(c, i);
            s.refresh();
            return;
        }
        switch (tag) {
            case "os:key":
                // Typed as dots, never shown again; the keyboard starts empty (a key is never put back into it).
                s.type(KEY, "Your OpenSubtitles API key", "", true);
                break;
            case "os:user":
                s.type(USER, "Your OpenSubtitles username", SubsPrefs.user(c), false);
                break;
            case "os:pass":
                s.type(PASS, "Your OpenSubtitles password", "", true);
                break;
            case "os:hi":
                SubsPrefs.setHearingFirst(c, !SubsPrefs.hearingFirst(c));
                s.refresh();
                break;
            case "os:check":
                check(s, c);
                break;
            case "os:forget":
                SubsPrefs.forget(c);
                checked = "";
                s.toast("The OpenSubtitles login is gone from this TV");
                s.refresh();
                break;
            default:
                break;
        }
    }

    /** Check the login: OpenSubtitles asked once (off the main thread); what it said on the line and in a word. */
    private static void check(SettingsScreen s, Context c) {
        checked = "Asking…";
        s.refresh();
        Context app = c.getApplicationContext();
        Exec.io(() -> {
            String said;
            try {
                int a = OpenSubtitles.check(app);
                said = SubsPrefs.user(app).isEmpty() ? "The key works" : a > 0 ? "Signed in  ·  " + a + " downloads a day" : "Signed in";
            } catch (IOException e) {
                said = e.getMessage() == null ? "OpenSubtitles couldn't be reached" : e.getMessage();
            }
            String words = said;
            Exec.main(() -> {
                checked = words;
                s.toast(words);
                s.refresh();
            });
        });
    }

    @Override public SettingsScreen.Picker picker(String key) {
        return null;
    }

    @Override public void picked(String key, int index) {}

    @Override public String value(String field) {
        // Only the name goes back into the keyboard; the key and the password never do.
        return USER.equals(field) ? SubsPrefs.user(c) : "";
    }

    @Override public void typed(String field, String text) {
        String t = text == null ? "" : text;
        switch (field) {
            case KEY:
                if (t.trim().isEmpty()) return;
                SubsPrefs.setKey(c, t);
                checked = "";
                s.toast("The OpenSubtitles key is kept on this TV");
                break;
            case USER:
                SubsPrefs.setUser(c, t);
                checked = "";
                break;
            case PASS:
                if (t.isEmpty()) return;
                SubsPrefs.setPassword(c, t);
                checked = "";
                s.toast("The password is kept on this TV");
                break;
            default:
                break;
        }
    }
}

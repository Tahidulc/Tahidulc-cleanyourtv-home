package com.gathin.tvhomephone.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.gathin.tvhomephone.App;
import com.gathin.tvhomephone.R;
import com.gathin.tvhomephone.glass.Icons;
import com.gathin.tvhomephone.live.SettingsLines;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Settings' sections as its main page lists them, under a few plain headings: each its key, its name, its glyph, the
 * builder that makes its page (each part's own, as it always was) and the one line under its name of how it is set now,
 * read off the main thread. Every section the one long page had is here, once.
 */
final class Sections {
    private Sections() {}

    /** Makes a section's page: each part's own builder. */
    interface Build {
        View make(Fragment host, ViewGroup parent);
    }

    /** How a section is set now, in a line (read off the main thread). */
    interface Line {
        String of(Context c);
    }

    static final class Section {
        final String key;
        final int title, glyph, group, about;
        final Build build;
        final Line line;

        Section(String key, int title, int glyph, int group, int about, Build build, Line line) {
            this.key = key;
            this.title = title;
            this.glyph = glyph;
            this.group = group;
            this.about = about;
            this.build = build;
            this.line = line;
        }
    }

    /** The headings, in the page's order. */
    static final int WATCHING = 0, SPORT = 1, CHANNELS = 2, FILMS = 3, TVS = 4, APP = 5;
    static final int[] GROUPS = {R.string.sn_group_watching, R.string.sn_group_sport, R.string.sn_group_channels,
            R.string.sn_group_films, R.string.sn_group_tvs, R.string.sn_group_app};

    /** The sections, in the main page's order (each heading's together). */
    static final List<Section> ALL = Collections.unmodifiableList(Arrays.asList(
            new Section(SettingsFragment.LIVE, R.string.live_settings_title, Icons.LIVE, WATCHING, R.string.sn_live_about,
                    com.gathin.tvhomephone.live.LiveTvSettings::build, SettingsLines::live),
            new Section(SettingsFragment.APPEARANCE, R.string.ps_appearance_title, SectionIcons.PALETTE, WATCHING, R.string.sn_look_about,
                    com.gathin.tvhomephone.live.AppearanceSettings::build, SettingsLines::appearance),
            new Section(SettingsFragment.REMINDERS, R.string.ps_reminders_title, SectionIcons.BELL, WATCHING, R.string.sn_reminders_about,
                    com.gathin.tvhomephone.live.ReminderSettings::build, SettingsLines::reminders),
            new Section(SettingsFragment.SPORTS, R.string.sn_sports, Icons.SPORTS, SPORT, R.string.sn_sports_about,
                    com.gathin.tvhomephone.sports.SportsSettings::build, Sections::sports),
            new Section(SettingsFragment.INTRO, R.string.sn_intro, Icons.PLAY, SPORT, R.string.sn_intro_about,
                    (host, parent) -> com.gathin.tvhomephone.live.IntroSettings.build(host.requireContext()), SettingsLines::intro),
            new Section(SettingsFragment.CHANNELS, R.string.ps_channels_title, SectionIcons.ROWS, CHANNELS, R.string.sn_channels_about,
                    com.gathin.tvhomephone.live.ChannelSettings::build, SettingsLines::channels),
            new Section(SettingsFragment.POSTERS, R.string.posters_title, Icons.FILM, FILMS, R.string.sn_posters_about,
                    com.gathin.tvhomephone.live.PosterSettings::build, SettingsLines::posters),
            new Section(SettingsFragment.SUBTITLES, R.string.subs_title, SectionIcons.CC, FILMS, R.string.sn_subs_about,
                    com.gathin.tvhomephone.live.SubtitleSettings::build, SettingsLines::subtitles),
            new Section(SettingsFragment.TORBOX, R.string.torbox_title, Icons.CLOUD, FILMS, R.string.sn_torbox_about,
                    com.gathin.tvhomephone.live.TorBoxSettings::build, SettingsLines::torbox),
            new Section(SettingsFragment.TVS, R.string.sn_tvs, SectionIcons.SCREENS, TVS, R.string.sn_tvs_about,
                    com.gathin.tvhomephone.sync.SyncSettings::build, Sections::tvs),
            new Section(SettingsFragment.UPDATES, R.string.sn_updates, SectionIcons.DOWNLOAD, APP, R.string.sn_updates_about,
                    (host, parent) -> com.gathin.tvhomephone.update.UpdateSettings.build(host), Sections::updates)));

    /** A section by its key (null for one not known). */
    static Section of(String key) {
        for (Section s : ALL) if (s.key.equals(key)) return s;
        return null;
    }

    /** Its place in the page's order (-1 for one not known). */
    static int at(String key) {
        for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).key.equals(key)) return i;
        return -1;
    }

    /** "3 starred clubs · alerts and pop-ups". */
    static String sports(Context c) {
        int n = com.gathin.tvhomephone.sports.Clubs.starred(c).size();
        return n == 0 ? c.getString(R.string.sn_sports_none) : c.getResources().getQuantityString(R.plurals.sn_clubs, n, n);
    }

    /** "2 TVs paired", or "No TVs paired yet". */
    static String tvs(Context c) {
        int n = com.gathin.tvhomephone.sync.Tvs.list(c).size();
        return n == 0 ? c.getString(R.string.sn_tvs_none) : c.getResources().getQuantityString(R.plurals.sn_tvs, n, n);
    }

    /** "This phone has Homefield 5.36". */
    static String updates(Context c) {
        String mine = com.gathin.tvhomephone.update.AppUpdate.myName(c);
        return mine.isEmpty() ? c.getString(R.string.sn_updates_about) : c.getString(R.string.sn_updates_line, mine);
    }

    /** Where the lines are read: one quiet thread of its own (tests run them in place). */
    static Executor work = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "settings-lines");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
    });

    /**
     * Every section's line read off the main thread, then handed back on it, in the page's order (a section whose line
     * could not be read keeps what it holds).
     */
    static void lines(Context c, Consumer<String[]> done) {
        Context app = c.getApplicationContext();
        work.execute(() -> {
            String[] out = new String[ALL.size()];
            for (int i = 0; i < out.length; i++) {
                try {
                    out[i] = ALL.get(i).line.of(app);
                } catch (RuntimeException e) {
                    out[i] = null;
                }
            }
            App.main.post(() -> done.accept(out));
        });
    }
}

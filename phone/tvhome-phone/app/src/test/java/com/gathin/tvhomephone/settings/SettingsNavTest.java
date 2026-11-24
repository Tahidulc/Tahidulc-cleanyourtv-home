package com.gathin.tvhomephone.settings;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.gathin.tvhomephone.R;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Settings made easier to navigate (the family's ask): a main page that lists every section the one long page had,
 * each opening its own page that back leaves for the list where it was; a search that finds a setting by its words on
 * the page it is on and opens it there; a playlist's notification still landing on Live TV's playlists; a tablet's two
 * panes still side by side, the sections made only when chosen.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w411dp-h891dp-port-xhdpi")
public class SettingsNavTest {
    private Executor work;
    private ActivityController<AppCompatActivity> ctl;

    @Before public void setUp() {
        work = Sections.work;
        Sections.work = Runnable::run;
        SettingsFragment.toPlaylists = false;
    }

    @After public void tearDown() {
        Sections.work = work;
        SettingsFragment.toPlaylists = false;
        Context c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("live_sources", Context.MODE_PRIVATE).edit().clear().commit();
        c.getSharedPreferences("settings_ui", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private static void idle() {
        for (int i = 0; i < 4; i++) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120));
            shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private SettingsFragment shown() {
        ctl = Robolectric.buildActivity(AppCompatActivity.class);
        ctl.get().setTheme(R.style.Theme_TvHome);
        AppCompatActivity a = ctl.setup().get();
        SettingsFragment f = new SettingsFragment();
        a.getSupportFragmentManager().beginTransaction().add(android.R.id.content, f, "settings").commitNow();
        idle();
        return f;
    }

    private static String words(View v) {
        List<String> out = new ArrayList<>();
        texts(v, out);
        return String.join(" | ", out);
    }

    private static void texts(View v, List<String> out) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView) out.add(((TextView) v).getText().toString());
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) texts(((ViewGroup) v).getChildAt(i), out);
    }

    private static void playlists(Context c, String... names) throws Exception {
        JSONArray a = new JSONArray();
        for (int i = 0; i < names.length; i++) {
            a.put(new JSONObject().put("id", "p" + i).put("name", names[i]).put("type", "m3u")
                    .put("m3u", "http://192.168.1.50/list" + i + ".m3u").put("guide", "").put("server", "").put("user", "").put("pass", ""));
        }
        c.getSharedPreferences("live_sources", Context.MODE_PRIVATE).edit().putString("list", a.toString()).commit();
    }

    // ---- the words ---------------------------------------------------------------------------------------------------

    @Test public void wordsAreFoundByTheirStarts() {
        assertArrayEquals(new String[]{"data", "sav"}, SettingsSearch.words("  Data  SAV "));
        assertArrayEquals(new String[]{"cafe"}, SettingsSearch.words("Café"));
        assertArrayEquals(new String[]{"dont"}, SettingsSearch.words("don't"));
        assertNotNull(SettingsSearch.match("Rewind", SettingsSearch.words("rew")));
        assertNotNull(SettingsSearch.match("Data saver (720p on mobile data)", SettingsSearch.words("mobile 720")));
        assertNotNull(SettingsSearch.match("Café Société", SettingsSearch.words("societe")));
        // Inside a word is not a match: "on" finds no "Connection".
        assertNull(SettingsSearch.match("Connection", SettingsSearch.words("on")));
        assertNull(SettingsSearch.match("Rewind", SettingsSearch.words("rew x")));
        // Where the words are, in the words as written (the accented letter whole).
        assertArrayEquals(new int[]{5, 12}, SettingsSearch.match("Café Société", SettingsSearch.words("société")));
        assertArrayEquals(new int[]{0, 4, 5, 8}, SettingsSearch.match("Data saver", SettingsSearch.words("data sav")));
        assertEquals(0, SettingsSearch.score("Picture quality", SettingsSearch.words("pic")));
        assertEquals(1, SettingsSearch.score("Picture quality", SettingsSearch.words("quality")));
        assertEquals(2, SettingsSearch.score("Picture quality", SettingsSearch.words("quality picture")));
        assertTrue(SettingsSearch.onOff("on"));
        assertEquals("a b", SettingsSearch.plain(" a\n\n b "));
        // A long line from a little before the word found, with it lit.
        String body = "Your own M3U playlists or Xtream Codes logins, one or several. Kept only on this phone (never sent to the TVs).";
        int[] at = SettingsSearch.match(body, SettingsSearch.words("phone"));
        CharSequence s = SettingsList.shown(body, at, 0xFFFFB547);
        assertTrue(s.toString(), s.toString().startsWith("…"));
        assertTrue(s.toString(), s.toString().contains("phone"));
    }

    @Test public void theListHasEverySectionTheOnePageHad() {
        // The long page's sections, every one, each once, in the list's order.
        Set<String> old = new HashSet<>(Arrays.asList("intro", "tvs", "sports", "live", "appearance", "channels", "reminders", "posters",
                "torbox", "subtitles", "updates"));
        Set<String> keys = new HashSet<>();
        for (Sections.Section s : Sections.ALL) assertTrue(s.key, keys.add(s.key));
        assertEquals(old, keys);
        assertEquals(SettingsFragment.KEYS.length, Sections.ALL.size());
        for (int i = 0; i < Sections.ALL.size(); i++) assertEquals(SettingsFragment.KEYS[i], Sections.ALL.get(i).key);
        assertEquals(3, SettingsFragment.keyAt(SettingsFragment.SPORTS));
        assertEquals(0, SettingsFragment.keyAt("gone"));

        SettingsFragment f = shown();
        Context c = f.requireContext();
        String all = words(f.getView());
        for (int g : Sections.GROUPS) assertTrue(all, all.contains(c.getString(g)));
        assertEquals(Sections.ALL.size(), f.list().rows().size());
        for (int i = 0; i < Sections.ALL.size(); i++) {
            View row = f.list().rows().get(i);
            assertTrue(words(row), words(row).startsWith(c.getString(Sections.ALL.get(i).title)));
            assertTrue("a large row", row.getHeight() >= (int) (56 * c.getResources().getDisplayMetrics().density));
        }
        // Each with how it is set now.
        assertTrue(all, all.contains("No playlists yet · Quality: Adaptive"));
        assertTrue(all, all.contains("No TVs paired yet"));
        // Nothing made before it is opened.
        for (Sections.Section s : Sections.ALL) assertNull(s.key, f.made(s.key));
        assertNull(f.showing());
    }

    @Test public void aSectionOpensOnItsOwnPageAndBackComesBackToTheList() {
        SettingsFragment f = shown();
        f.mainScroll().scrollTo(0, 120);
        int y = f.mainScroll().getScrollY();
        assertTrue("the list scrolls", y > 0);
        View subs = f.list().rows().get(Sections.at(SettingsFragment.SUBTITLES));
        assertTrue(subs.performClick());
        idle();
        assertEquals(SettingsFragment.SUBTITLES, f.showing());
        SettingsFragment.Page p = f.made(SettingsFragment.SUBTITLES);
        assertNotNull(p);
        assertEquals(View.VISIBLE, p.root.getVisibility());
        assertEquals(0f, p.root.getTranslationX(), 0.5f);
        String page = words(p.root);
        // Its name over it, with the way back, and the section as it always was.
        assertTrue(page, page.startsWith(f.getString(R.string.subs_title)));
        assertTrue(page, page.contains(f.getString(R.string.subs_hi)));
        assertEquals(View.INVISIBLE, f.mainScroll().getVisibility());
        // Only that one made.
        assertNull(f.made(SettingsFragment.LIVE));

        f.requireActivity().getOnBackPressedDispatcher().onBackPressed();
        idle();
        assertNull(f.showing());
        assertEquals(View.VISIBLE, f.mainScroll().getVisibility());
        assertEquals(View.GONE, p.root.getVisibility());
        assertEquals(y, f.mainScroll().getScrollY());

        // The arrow at its top does the same.
        subs.performClick();
        idle();
        assertEquals(SettingsFragment.SUBTITLES, f.showing());
        View up = ((ViewGroup) ((ViewGroup) p.root).getChildAt(0)).getChildAt(0);
        assertEquals(f.getString(R.string.sn_back), up.getContentDescription());
        up.performClick();
        idle();
        assertNull(f.showing());
    }

    @Test public void theSearchFindsASettingAndOpensItWhereItIs() {
        SettingsFragment f = shown();
        SettingsList l = f.list();
        l.query("data saver");
        idle();
        // Every page made for it, one at a time, none shown.
        assertTrue(f.allMade());
        assertNull(f.showing());
        List<View> hits = l.hits();
        assertFalse("found", hits.isEmpty());
        View first = hits.get(0);
        String said = words(first);
        assertTrue(said, said.contains(f.getString(R.string.live3_data_saver)));
        assertTrue(said, said.contains(f.getString(R.string.live_settings_title)));
        first.performClick();
        idle();
        assertEquals(SettingsFragment.LIVE, f.showing());
        SettingsFragment.Page p = f.made(SettingsFragment.LIVE);
        View at = SettingsSearch.locate(p.content, null, f.getString(R.string.live3_data_saver));
        assertNotNull(at);
        // Scrolled to it: it sits on screen, near the top of the page.
        int top = SettingsFragment.topIn(at, p.scroll.getChildAt(0)) - p.scroll.getScrollY();
        assertTrue("scrolled down to it", p.scroll.getScrollY() > 0);
        assertTrue("near the top: " + top, top >= 0 && top < p.scroll.getHeight() / 2);
        assertNotNull("lit", f.lit);
        // Back: the list again, still showing what was found.
        f.requireActivity().getOnBackPressedDispatcher().onBackPressed();
        idle();
        assertNull(f.showing());
        assertTrue(l.searching());
        assertFalse(l.hits().isEmpty());
        // Back again: the search cleared, the headings back.
        f.requireActivity().getOnBackPressedDispatcher().onBackPressed();
        idle();
        assertFalse(l.searching());
        assertTrue(words(f.getView()), words(f.getView()).contains(f.getString(R.string.sn_group_watching)));
    }

    @Test public void theSearchFindsSectionsByNameFirst() {
        SettingsFragment f = shown();
        f.list().query("subtitles");
        idle();
        List<View> hits = f.list().hits();
        assertFalse(hits.isEmpty());
        assertTrue(words(hits.get(0)), words(hits.get(0)).startsWith(f.getString(R.string.subs_title)));
        hits.get(0).performClick();
        idle();
        assertEquals(SettingsFragment.SUBTITLES, f.showing());
        assertEquals(0, f.made(SettingsFragment.SUBTITLES).scroll.getScrollY());
        // Nothing found says so.
        f.requireActivity().getOnBackPressedDispatcher().onBackPressed();
        f.list().query("zzqx");
        idle();
        assertTrue(f.list().hits().isEmpty());
        assertTrue(words(f.getView()), words(f.getView()).contains("Nothing in Settings matches “zzqx”"));
    }

    @Test public void aPlaylistsNotificationOpensLiveTvAtItsPlaylists() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        playlists(c, "Riverside Box", "Hilltop TV");
        SettingsFragment.toPlaylists = true;
        SettingsFragment f = shown();
        assertFalse(SettingsFragment.toPlaylists);
        assertEquals(SettingsFragment.LIVE, f.showing());
        SettingsFragment.Page p = f.made(SettingsFragment.LIVE);
        View at = SettingsFragment.playlistsOn(c, p.content);
        assertNotNull(at);
        assertEquals(f.getString(R.string.live_use), ((TextView) at).getText().toString());
        int top = SettingsFragment.topIn(at, p.scroll.getChildAt(0)) - p.scroll.getScrollY();
        assertTrue("the playlists near the top: " + top, top >= 0 && top < p.scroll.getHeight() / 3);
        assertTrue(p.scroll.getScrollY() > 0);

        // Again while Settings is open (the note tapped with it on screen): back on Live TV's playlists.
        f.requireActivity().getOnBackPressedDispatcher().onBackPressed();
        idle();
        assertNull(f.showing());
        SettingsFragment.toPlaylists = true;
        ctl.pause().resume();
        idle();
        assertFalse(SettingsFragment.toPlaylists);
        assertEquals(SettingsFragment.LIVE, f.showing());
    }

    @Test public void madeAgainItOpensWhereItWas() {
        SettingsFragment f = shown();
        f.list().rows().get(Sections.at(SettingsFragment.APPEARANCE)).performClick();
        idle();
        assertEquals(SettingsFragment.APPEARANCE, f.showing());
        // The colour theme chosen makes the app again: Appearance is still open.
        ctl.recreate();
        idle();
        SettingsFragment again = (SettingsFragment) ctl.get().getSupportFragmentManager().findFragmentByTag("settings");
        assertNotNull(again);
        assertEquals(SettingsFragment.APPEARANCE, again.showing());
    }

    @Test @Config(qualifiers = "sw800dp-w1280dp-h800dp-land-mdpi")
    public void aTabletKeepsItsTwoPanes() {
        SettingsFragment f = shown();
        View root = f.getView();
        assertTrue(root instanceof LinearLayout);
        assertEquals(LinearLayout.HORIZONTAL, ((LinearLayout) root).getOrientation());
        assertEquals(2, ((LinearLayout) root).getChildCount());
        // The same list, with its headings and search, beside the chosen section (Live TV, as it comes).
        String list = words(((ViewGroup) root).getChildAt(0));
        assertTrue(list, list.contains(f.getString(R.string.sn_group_watching)));
        assertEquals(Sections.ALL.size(), f.list().rows().size());
        assertEquals(SettingsFragment.LIVE, f.showing());
        assertNotNull(f.made(SettingsFragment.LIVE));
        assertNull("made only when chosen", f.made(SettingsFragment.TVS));
        assertTrue(f.list().rows().get(Sections.at(SettingsFragment.LIVE)).isSelected());

        f.list().rows().get(Sections.at(SettingsFragment.TVS)).performClick();
        idle();
        assertEquals(SettingsFragment.TVS, f.showing());
        assertEquals(View.VISIBLE, f.made(SettingsFragment.TVS).root.getVisibility());
        assertEquals(View.GONE, f.made(SettingsFragment.LIVE).root.getVisibility());
        assertTrue(f.list().rows().get(Sections.at(SettingsFragment.TVS)).isSelected());
        assertEquals(SettingsFragment.TVS, f.requireContext().getSharedPreferences("settings_ui", Context.MODE_PRIVATE)
                .getString("section", ""));

        // The search in the list opens what it finds beside it.
        f.list().query("deaf");
        idle();
        assertFalse(f.list().hits().isEmpty());
        f.list().hits().get(0).performClick();
        idle();
        assertEquals(SettingsFragment.SUBTITLES, f.showing());
        assertNotNull(f.lit);
    }
}

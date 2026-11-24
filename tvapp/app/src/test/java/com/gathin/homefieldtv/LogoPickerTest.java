package com.gathin.homefieldtv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.KeyEvent;

import com.gathin.broadcast.AiKey;
import com.gathin.broadcast.Claude;
import com.gathin.broadcast.LogoPacks;
import com.gathin.broadcast.LogoSpec;
import com.gathin.homefieldtv.engine.Channel;
import com.gathin.homefieldtv.engine.Exec;
import com.gathin.homefieldtv.engine.LogoPack;
import com.gathin.homefieldtv.engine.MadeLogos;
import com.gathin.homefieldtv.engine.MadeUpGuide;
import com.gathin.homefieldtv.prefs.Categories;
import com.gathin.homefieldtv.prefs.ChannelEdits;

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

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Change logo's catalogue, Make my own and Ask AI in Homefield TV: invented packs searched by a channel's name (grouped by
 * pack, OK keeps one), logos made from its name (Left and Right flip, OK keeps one as a see-through PNG of this app's),
 * Claude's design from a fake HTTP layer (never the real API), the choice drawn wherever the channel's logo is (and
 * handed to the launcher's cards through the provider), and Ask AI's key in Settings: masked, never shown or synced.
 * Invented channels (MadeUpGuide) and logos only.
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34, qualifiers = "w1920dp-h1080dp-land-mdpi")
public class LogoPickerTest {
    private ActivityController<TvActivity> tv;
    private Context app;
    private List<Channel> ch;
    private final List<String> asked = new ArrayList<>();

    @Before public void setUp() {
        Exec.inline = true;
        app = RuntimeEnvironment.getApplication();
        clear();
        ch = MadeUpGuide.load(app);
        tv = Robolectric.buildActivity(TvActivity.class).setup();
        idle();
        // The invented logos are never read here (no picture fetched): taken as readable.
        LogoPack.failed = u -> false;
        Map<LogoPacks.Pack, List<LogoPacks.Logo>> packs = new LinkedHashMap<>();
        packs.put(LogoPacks.Pack.TV_LOGOS, Arrays.asList(
                new LogoPacks.Logo(LogoPacks.Pack.TV_LOGOS, "Kestrel One", "uk", LogoPack.RAW + "countries/united-kingdom/kestrel-one-uk.png"),
                new LogoPacks.Logo(LogoPacks.Pack.TV_LOGOS, "Kestrel One Plus 1", "uk", LogoPack.RAW + "countries/united-kingdom/kestrel-one-plus1-uk.png"),
                new LogoPacks.Logo(LogoPacks.Pack.TV_LOGOS, "Fogbell News", "uk", LogoPack.RAW + "countries/united-kingdom/fogbell-news-uk.png")));
        packs.put(LogoPacks.Pack.IPTV_ORG, Arrays.asList(
                new LogoPacks.Logo(LogoPacks.Pack.IPTV_ORG, "Kestrel One", "uk", "https://logos.invalid/kestrel-one.png"),
                new LogoPacks.Logo(LogoPacks.Pack.IPTV_ORG, "Kestrel Kids", "ie", "https://logos.invalid/kestrel-kids.png")));
        LogoCatalogue.useForPreview(packs);
    }

    private void clear() {
        app.getSharedPreferences("channel_edits", Context.MODE_PRIVATE).edit().clear().commit();
        app.getSharedPreferences("live_groups", Context.MODE_PRIVATE).edit().clear().commit();
        ChannelEdits.forget();
        Categories.forget();
        AiKey.set(app, "");
        AiSettings.claude = Claude::new;
        LogoCatalogue.useForPreview(null);
    }

    @After public void after() {
        if (tv != null) tv.pause().stop().destroy();
        LogoPack.failed = Pics::failed;
        MadeUpGuide.unload(app);
        clear();
        GuideScreen.forgetList();
        tv = null;
        Exec.inline = false;
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static void press(TvActivity a, int code) {
        long t = SystemClock.uptimeMillis();
        a.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        a.dispatchKeyEvent(new KeyEvent(t, t + 40, KeyEvent.ACTION_UP, code, 0));
        idle();
    }

    private LogoScreen open(Channel c) {
        TvActivity a = tv.get();
        LogoScreen s = new LogoScreen(a, c);
        a.push(s);
        idle();
        assertTrue(a.top() == s);
        return s;
    }

    /** A fake Claude answering with this design, the request noted; never the real API. */
    private void fakeClaude(String design) {
        AiSettings.claude = () -> new Claude((url, headers, body, connect, read) -> {
            asked.add(new String(body, java.nio.charset.StandardCharsets.UTF_8));
            try {
                String text = new JSONObject().put("stop_reason", "end_turn").put("content", new JSONArray()
                        .put(new JSONObject().put("type", "text").put("text", design))).toString();
                return new Claude.Response(200, text, "");
            } catch (org.json.JSONException e) {
                throw new java.io.IOException(e);
            }
        }, ms -> {});
    }

    // ---- the tabs ----------------------------------------------------------------------------------------------------

    @Test public void upFromTheTopGoesToTheTabsAndAcross() {
        TvActivity a = tv.get();
        LogoScreen s = open(ch.get(0));
        assertEquals(LogoScreen.FOUND, s.tabNow());
        for (int i = 0; i < s.count() + 1; i++) press(a, KeyEvent.KEYCODE_DPAD_UP);
        assertTrue(s.onTabs());
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(LogoScreen.CATALOGUE, s.tabNow());
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(LogoScreen.MAKE, s.tabNow());
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(LogoScreen.ASK, s.tabNow());
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(LogoScreen.ASK, s.tabNow());
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        assertFalse(s.onTabs());
    }

    // ---- the catalogue -------------------------------------------------------------------------------------------------

    @Test public void theCatalogueIsSearchedByTheChannelsNameGroupedByPack() {
        TvActivity a = tv.get();
        Channel kestrel = ch.get(0);
        LogoScreen s = open(kestrel);
        s.tab(LogoScreen.CATALOGUE);
        idle();
        assertEquals(Arrays.asList(LogoPacks.Pack.TV_LOGOS, LogoPacks.Pack.IPTV_ORG), s.packsFound());
        assertTrue(s.catalogueLine().startsWith("TV Logos: "));
        // Down to iptv-org's row, OK: its address kept for every version of the channel, drawn wherever its logo is.
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("https://logos.invalid/kestrel-one.png", ChannelEdits.logo(kestrel));
        assertEquals("https://logos.invalid/kestrel-one.png", LogoPack.url(kestrel));
        assertEquals("From the catalogue", ChannelMenu.logoWords(kestrel));
        assertFalse(a.top() instanceof LogoScreen);

        // TV Logos' by its path, as the new logos are (Refresh logos reaches it).
        LogoScreen t = open(kestrel);
        t.tab(LogoScreen.CATALOGUE);
        idle();
        t.catalogueAt(1, 0);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals("countries/united-kingdom/kestrel-one-uk.png", ChannelEdits.logo(kestrel));
        assertEquals(LogoPack.urlOf("countries/united-kingdom/kestrel-one-uk.png"), LogoPack.url(kestrel));
    }

    @Test public void otherWordsAreTypedOnTheKeyboard() {
        TvActivity a = tv.get();
        LogoScreen s = open(ch.get(0));
        s.tab(LogoScreen.CATALOGUE);
        idle();
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertTrue(a.top() instanceof RenameScreen);
        ((RenameScreen) a.top()).typeAndDone("fogbell");
        idle();
        assertTrue(a.top() == s);
        assertEquals(Arrays.asList(LogoPacks.Pack.TV_LOGOS), s.packsFound());
        // Nothing for words no pack has: said so, and the other ways offered.
        s.search("zzqx nothing");
        idle();
        assertTrue(s.packsFound().isEmpty());
        assertTrue(s.catalogueLine().contains("make your own"));
    }

    // ---- Make my own -------------------------------------------------------------------------------------------------

    @Test public void madeLogosFlipAndOneIsKeptAsThisTvsPicture() throws Exception {
        TvActivity a = tv.get();
        Channel kestrel = ch.get(0);
        LogoScreen s = open(kestrel);
        s.tab(LogoScreen.MAKE);
        idle();
        assertEquals(LogoScreen.MADE, s.made().size());
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        assertEquals(1, s.madeAt());
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        press(a, KeyEvent.KEYCODE_DPAD_LEFT);
        assertEquals(LogoScreen.MADE - 1, s.madeAt());
        // Past the last: fresh ones.
        String was = s.made().get(0).toJson();
        press(a, KeyEvent.KEYCODE_DPAD_RIGHT);
        idle();
        assertEquals(0, s.madeAt());
        assertFalse(was.equals(s.made().get(0).toJson()));
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        idle();
        String choice = ChannelEdits.logo(kestrel);
        assertTrue(choice, MadeLogos.is(choice));
        File f = MadeLogos.file(choice);
        assertTrue(f.isFile());
        // A see-through PNG, drawn as the channel's logo everywhere (Pics reads "file:").
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        assertEquals(MadeLogos.HEIGHT, o.outHeight);
        assertEquals("file:" + f.getPath(), LogoPack.url(kestrel));
        assertEquals("Made by you", ChannelMenu.logoWords(kestrel));
        // The launcher's cards read it through the provider: content://…cards/logo/<name>, read-only, that name only.
        String shared = ChannelCardsProvider.shared(LogoPack.url(kestrel));
        assertEquals("content://" + ChannelCardsProvider.AUTHORITY + "/logo/" + f.getName(), shared);
        ChannelCardsProvider p = Robolectric.setupContentProvider(ChannelCardsProvider.class);
        ParcelFileDescriptor fd = p.openFile(Uri.parse(shared), "r");
        assertNotNull(fd);
        assertEquals(f.length(), fd.getStatSize());
        try {
            fd.close();
        } catch (RuntimeException | java.io.IOException ignored) {
            // Robolectric on a newer JDK can't close it; the test's process goes anyway
        }
        for (String bad : new String[]{"content://" + ChannelCardsProvider.AUTHORITY + "/logo/..%2Fchannel_cards.json",
                "content://" + ChannelCardsProvider.AUTHORITY + "/logo/backup.json"}) {
            try {
                p.openFile(Uri.parse(bad), "r");
                throw new AssertionError("served " + bad);
            } catch (java.io.FileNotFoundException expected) {
                // only made logos' names
            }
        }
        // The playlist's again: the made picture let go of once no channel uses it.
        LogoScreen again = open(kestrel);
        again.choose(LogoScreen.PLAYLIST);
        idle();
        assertFalse(f.exists());
    }

    @Test public void madeLogosGoOnEveryVersionOfTheChannel() throws Exception {
        Channel kestrel = ch.get(0);
        String choice = MadeLogos.save(app, Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888));
        ChannelEdits.setLogo(app, kestrel, choice);
        for (String k : ChannelEdits.keysOf(kestrel)) assertEquals(choice, ChannelEdits.logo(k));
        // One gone (a backup put back on another TV): the playlist's logo, never a blank.
        assertTrue(MadeLogos.file(choice).delete());
        assertEquals(new HashSet<>(Arrays.asList(choice)), ChannelEdits.logoChoices(app));
        assertTrue(MadeLogos.validName(MadeLogos.nameOf(choice)));
        assertFalse(MadeLogos.validName("../x.png"));
        assertNull(MadeLogos.nameOf("https://logos.invalid/a.png"));
    }

    // ---- Ask AI ----------------------------------------------------------------------------------------------------------

    @Test public void askAiDrawsClaudesDesignAndKeepsIt() throws Exception {
        TvActivity a = tv.get();
        Channel news = ch.get(4);
        LogoScreen s = open(news);
        s.tab(LogoScreen.ASK);
        idle();
        // No key: where to add one, nothing asked.
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        assertEquals(AiSettings.NO_KEY, s.aiNote());
        assertTrue(asked.isEmpty());

        AiKey.set(app, "sk-ant-made-up-key-for-the-tests-0000");
        LogoSpec design = new LogoSpec();
        design.text = "FOGBELL";
        design.sub = "NEWS";
        design.layout = LogoSpec.Layout.BADGE;
        design.mark = LogoSpec.Mark.WAVE;
        fakeClaude(design.validated().toJson());
        press(a, KeyEvent.KEYCODE_DPAD_DOWN);
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        idle();
        assertEquals(1, asked.size());
        assertTrue(asked.get(0).contains("Bold and modern"));
        assertTrue(asked.get(0).contains("Fogbell News"));
        assertFalse(asked.get(0).contains("sk-ant-"));
        assertNotNull(s.ai());
        assertEquals("FOGBELL", s.ai().text);
        assertTrue(s.aiNote().startsWith("Claude's design"));
        // The remote on Use this logo: OK keeps it as this TV's own picture.
        press(a, KeyEvent.KEYCODE_DPAD_CENTER);
        idle();
        assertTrue(MadeLogos.is(ChannelEdits.logo(news)));
        assertTrue(LogoPack.url(news).startsWith("file:"));
    }

    // ---- Ask AI's key in Settings ----------------------------------------------------------------------------------------

    @Test public void theKeyIsTypedOnceMaskedAndNeverShownOrSynced() {
        List<RowList.Row> rows = new ArrayList<>();
        AiSettings.rows(app, rows);
        assertEquals("None yet  ·  OK to type it", rows.get(0).value);
        String key = "sk-ant-api03-made-up-key-for-the-tests-wxyz";
        AiKey.set(app, key);
        rows.clear();
        AiSettings.rows(app, rows);
        for (RowList.Row r : rows) {
            assertFalse(String.valueOf(r.title).contains("made-up"));
            assertFalse(String.valueOf(r.value).contains("made-up"));
        }
        assertEquals("sk-ant-••••••••wxyz", rows.get(0).value);
        assertTrue(AiSettings.keyWords(app).startsWith("sk-ant-••••••••wxyz"));
        // Opus 5.5 as it comes; Sonnet and Haiku offered.
        assertEquals("claude-opus-5-5", AiKey.model(app));
        assertEquals(0, AiSettings.modelAt(app));
        assertEquals(3, AiSettings.modelNames().length);
        AiSettings.picked(app, 2);
        assertEquals("claude-haiku-5-5", AiKey.model(app));
        AiSettings.picked(app, 0);
        // Never in the sync store.
        assertTrue(com.gathin.homefieldtv.sync.SyncItems.changedSince(app, 0).toString().indexOf("made-up") < 0);
    }

    @Test public void aboutHasTheLogoPacksLicences() {
        List<RowList.Row> rows = new ArrayList<>();
        AddedSettings.licences(rows);
        StringBuilder all = new StringBuilder();
        for (RowList.Row r : rows) all.append(r.title).append(' ').append(r.value).append('\n');
        assertTrue(all.toString().contains("TV Logos"));
        assertTrue(all.toString().contains("iptv-org"));
        assertTrue(all.toString().contains("The Unlicense"));
    }
}

package com.gathin.homefieldtv.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The new logos matched to playlist names (LogoMatch), on the JVM with no network: an index in the project's own
 * naming (real-style file names, and invented ones), and the names playlists give (real-style and invented): quality
 * tags, country tags and separators, list numbers, +1 kept apart, aliases, regions, how sure each match is.
 */
public class LogoMatchTest {
    /** A small index as the TV keeps it: a line a folder, the logos' file names without ".png". */
    static final String INDEX = "# made for the tests\n"
            + "countries/united-kingdom\tbbc-one-uk bbc-two-uk bbc-one-scotland-uk bbc-cbbc-uk itv-uk itv-1-uk itv-1-plus-uk "
            + "channel-4-uk channel-4-plus-uk channel-5-uk e-4-uk 4-more-uk 5-usa-uk 92-news-uk-uk dave-uk sky-sports-main-event-uk "
            + "sky-sports-main-event-hz-uk sky-sports-main-event-icon-uk sky-sports-premier-league-uk sky-sports-plus-hz-uk "
            + "tnt-sports-1-uk tnt-sports-2-uk discovery-channel-uk sky-history-uk a-and-e-uk paramount-network-light-uk "
            + "paramount-network-uk kestrel-one-uk north-sports-1-uk\n"
            + "countries/united-kingdom/hd\tbbc-one-hd-uk sky-sports-main-event-uhd-uk tnt-sports-1-hd-uk\n"
            + "countries/united-kingdom/obsolete\tsony-movies-uk\n"
            + "countries/ireland\trte-one-ie rte-2-ie virgin-media-one-ie\n"
            + "countries/united-states\tdiscovery-channel-us fox-news-us a-and-e-us\n"
            + "countries/international\tbbc-world-news-int\n"
            + "countries/germany\tzdf-de\n";

    private static LogoMatch all() {
        return LogoMatch.parse(INDEX, null);
    }

    private static String best(LogoMatch m, String name, String group) {
        LogoMatch.Found f = m.best(name, group);
        return f == null ? null : f.logo.path;
    }

    private static final String UK = "countries/united-kingdom/";

    @Test public void theIndexIsReadForTheCountriesWanted() {
        LogoMatch m = all();
        assertEquals(41, m.size());
        assertTrue(m.countries.containsAll(Arrays.asList("united-kingdom", "ireland", "united-states", "international", "germany")));
        LogoMatch some = LogoMatch.parse(INDEX, new HashSet<>(Arrays.asList("united-kingdom", "ireland")));
        assertEquals(new HashSet<>(Arrays.asList("united-kingdom", "ireland")), some.countries);
        assertNull(best(some, "US: Fox News", null));
        assertEquals(0, LogoMatch.parse("", null).size());
        assertEquals(0, LogoMatch.parse(null, null).size());
    }

    @Test public void qualityTagsAndCountryTagsAreLeftOut() {
        LogoMatch m = all();
        assertEquals(UK + "bbc-one-uk.png", best(m, "UK: BBC ONE FHD", null));
        assertEquals(UK + "bbc-one-uk.png", best(m, "|UK| BBC One ᴴᴰ", null));
        assertEquals(UK + "bbc-one-uk.png", best(m, "[UK] BBC 1 HEVC H.265 50 FPS", null));
        assertEquals(UK + "bbc-one-uk.png", best(m, "UK | BBC One RAW", null));
        assertEquals(UK + "bbc-one-uk.png", best(m, "BBC One (GB)", null));
        assertEquals(UK + "bbc-two-uk.png", best(m, "UK - BBC TWO SD", null));
        assertEquals(UK + "e-4-uk.png", best(m, "E4 HD", null));
        assertEquals(UK + "channel-5-uk.png", best(m, "UK: CHANNEL 5 4K", null));
        assertEquals(UK + "a-and-e-uk.png", best(m, "A&E", null));
        // Small capitals and accents made plain.
        assertEquals(UK + "dave-uk.png", best(m, "ᴅᴀᴠᴇ", null));
    }

    @Test public void aTimeshiftIsKeptApart() {
        LogoMatch m = all();
        assertEquals(UK + "itv-1-plus-uk.png", best(m, "UK: ITV1 +1", null));
        assertEquals(UK + "itv-1-plus-uk.png", best(m, "ITV 1+1 HD", null));
        assertEquals(UK + "itv-1-uk.png", best(m, "ITV1 HD", null));
        assertEquals(UK + "channel-4-plus-uk.png", best(m, "Channel 4 +1", null));
        // No +1 logo for it: never the channel's own instead.
        assertNull(best(m, "Dave +1", null));
        assertNull(best(m, "BBC One +1", null));
        // Sky Sports+ is a channel of its own (its wide logo).
        assertEquals(UK + "sky-sports-plus-hz-uk.png", best(m, "Sky Sports+ HD", null));
    }

    @Test public void theNumbersInANameCount() {
        LogoMatch m = all();
        assertEquals(UK + "tnt-sports-1-uk.png", best(m, "TNT Sports 1 HD", null));
        assertEquals(UK + "tnt-sports-2-uk.png", best(m, "UK: TNT SPORT 2", null));
        assertNull(best(m, "TNT Sports 3", null));
        // A list number before the name goes; a name's own number stays.
        assertEquals(UK + "bbc-two-uk.png", best(m, "102 | BBC Two", null));
        assertEquals(UK + "bbc-two-uk.png", best(m, "1002 BBC Two HD", null));
        assertEquals(UK + "5-usa-uk.png", best(m, "5 USA", null));
        assertEquals(UK + "92-news-uk-uk.png", best(m, "92 News UK", null));
    }

    @Test public void aliasesAndRegions() {
        LogoMatch m = all();
        // ITV is ITV1, ahead of ITV's own mark.
        assertEquals(UK + "itv-1-uk.png", best(m, "ITV HD", null));
        assertEquals(UK + "bbc-cbbc-uk.png", best(m, "CBBC", null));
        assertEquals(UK + "4-more-uk.png", best(m, "More4", null));
        assertEquals(UK + "sky-sports-main-event-uk.png", best(m, "Sky Main Event", null));
        assertEquals(UK + "sky-sports-premier-league-uk.png", best(m, "Sky Sports PL", null));
        assertEquals(UK + "discovery-channel-uk.png", best(m, "Discovery", null));
        assertEquals(UK + "sky-history-uk.png", best(m, "History HD", null));
        // A region with no logo of its own: the channel's (a little less sure); one with its own logo: that.
        LogoMatch.Found london = m.best("BBC One London HD", null);
        assertNotNull(london);
        assertEquals(UK + "bbc-one-uk.png", london.logo.path);
        assertEquals(0.9f, london.sure, 0.001f);
        assertEquals(UK + "bbc-one-scotland-uk.png", best(m, "BBC One Scotland", null));
        assertEquals(UK + "itv-1-uk.png", best(m, "ITV London", null));
        // A generic word at its end.
        assertEquals(UK + "dave-uk.png", best(m, "Dave TV", null));
    }

    @Test public void theCountryDecidesHowSure() {
        LogoMatch m = all();
        // British by default; another country's when the name or the group says so.
        assertEquals(UK + "discovery-channel-uk.png", best(m, "Discovery Channel", null));
        assertEquals("countries/united-states/discovery-channel-us.png", best(m, "US: Discovery Channel", null));
        assertEquals("countries/united-states/discovery-channel-us.png", best(m, "Discovery Channel HD", "USA | Documentaries"));
        assertEquals("countries/united-states/a-and-e-us.png", best(m, "A&E", "USA ENTERTAINMENT"));
        assertEquals("countries/ireland/rte-one-ie.png", best(m, "IE: RTE One", null));
        assertEquals("countries/ireland/rte-one-ie.png", best(m, "RTÉ One HD", "Ireland"));
        // Only one country has it: sure enough even untagged.
        assertEquals("countries/united-states/fox-news-us.png", best(m, "Fox News", null));
        assertEquals(0.85f, m.best("Fox News", null).sure, 0.001f);
        // Tagged with another country than the only one that has it: not sure enough.
        assertNull(best(m, "DE: Fox News", null));
        assertEquals("countries/germany/zdf-de.png", best(m, "DE: ZDF HD", null));
        assertEquals("countries/international/bbc-world-news-int.png", best(m, "BBC World News", null));
        assertEquals(1f, m.best("UK: BBC ONE FHD", null).sure, 0.001f);
        assertEquals(0.6f, LogoMatch.sure("france", "united-kingdom", false), 0.001f);
        assertEquals(0.85f, LogoMatch.sure("ireland", "united-kingdom", false), 0.001f);
    }

    @Test public void thePlainLogoComesFirstTheUhdOneForAUhdChannel() {
        LogoMatch m = all();
        assertEquals(UK + "sky-sports-main-event-uk.png", best(m, "Sky Sports Main Event HD", null));
        assertEquals(UK + "hd/sky-sports-main-event-uhd-uk.png", best(m, "Sky Sports Main Event UHD", null));
        assertEquals(UK + "paramount-network-uk.png", best(m, "Paramount Network", null));
        // Every version is offered in Change logo, the best first.
        List<LogoMatch.Found> c = m.candidates("UK: SKY SPORTS MAIN EVENT FHD", null, 10);
        assertEquals(UK + "sky-sports-main-event-uk.png", c.get(0).logo.path);
        Set<String> paths = new HashSet<>();
        for (LogoMatch.Found f : c) paths.add(f.logo.path);
        assertTrue(paths.contains(UK + "sky-sports-main-event-hz-uk.png"));
        assertTrue(paths.contains(UK + "sky-sports-main-event-icon-uk.png"));
        assertTrue(paths.contains(UK + "hd/sky-sports-main-event-uhd-uk.png"));
        for (int i = 1; i < c.size(); i++) assertTrue(c.get(i - 1).sure >= c.get(i).sure);
        // An old channel's logo still matches it, after any current one.
        assertEquals(UK + "obsolete/sony-movies-uk.png", best(m, "Sony Movies", null));
    }

    @Test public void notSureNoNewLogo() {
        LogoMatch m = all();
        // Invented names the project has no logo for.
        assertNull(best(m, "Fieldside Sports 1 HD", null));
        assertNull(best(m, "Lantern Films", null));
        assertNull(best(m, "", null));
        assertNull(best(m, "UK:", null));
        // Most of its words only: offered in Change logo, never shown by itself.
        List<LogoMatch.Found> c = m.candidates("Sky Sports Main", null, 10);
        assertFalse(c.isEmpty());
        for (LogoMatch.Found f : c) assertTrue(f.sure < LogoMatch.SURE);
        assertNull(best(m, "Sky Sports Main", null));
    }

    @Test public void inventedNamesMatchTheirOwnLogos() {
        LogoMatch m = all();
        assertEquals(UK + "kestrel-one-uk.png", best(m, "UK: KESTREL ONE FHD", null));
        assertEquals(UK + "kestrel-one-uk.png", best(m, "Kestrel 1", null));
        assertEquals(UK + "north-sports-1-uk.png", best(m, "|UK| North Sport 1 ᴴᴰ", "UK | Sports"));
        assertNull(best(m, "North Sports 2", null));
    }

    @Test public void theCompactIndexFromTheProjectsFiles() {
        String text = LogoMatch.compact(Arrays.asList("README.md", "countries/united-kingdom/0_all_logos_mosaic.md",
                "countries/united-kingdom/bbc-one-uk.png", "countries/united-kingdom/hd/bbc-one-hd-uk.png",
                "countries/united-kingdom/screen-bug/sky-sports-bug-uk.png", "countries/ukraine/тест.png",
                "misc/vod/a-film.png", "countries/united-kingdom/bbc-two-uk.png"), "a note");
        assertEquals("# a note\ncountries/united-kingdom\tbbc-one-uk bbc-two-uk\ncountries/united-kingdom/hd\tbbc-one-hd-uk\n", text);
        LogoMatch m = LogoMatch.parse(text, null);
        assertEquals(3, m.size());
        assertEquals(UK + "bbc-one-uk.png", best(m, "BBC One", null));
    }

    @Test public void theCountriesAPlaylistHas() {
        Set<String> c = LogoMatch.countries(Arrays.asList("UK | Sports", "USA ENTERTAINMENT", "|DE| Sport", "Movies", "Kids"),
                Arrays.asList("FR: TF1 HD", "Kestrel One", "NL | NPO 1"), 100);
        assertTrue(c.containsAll(Arrays.asList("united-kingdom", "ireland", "international", "united-states", "germany", "france",
                "netherlands")));
        assertFalse(c.contains("italy"));
        assertEquals("ireland", LogoMatch.countryOfGroup("IRELAND"));
        assertEquals("united-kingdom", LogoMatch.countryOfGroup("United Kingdom | News"));
        assertNull(LogoMatch.countryOfGroup("Sports"));
        assertNull(LogoMatch.leadingTag("Kestrel One"));
        assertEquals("united-states", LogoMatch.leadingTag("US: CNN"));
    }

    @Test public void aLogosNameAndKind() {
        LogoMatch m = all();
        LogoMatch.Found f = m.candidates("Sky Sports Main Event UHD", null, 10).get(0);
        assertEquals("Sky Sports Main Event", f.logo.label());
        assertEquals("United Kingdom  ·  UHD  ·  HD", f.logo.about());
        assertEquals("BBC One", m.best("BBC One", null).logo.label());
    }

    @Test public void theIndexTheAppComesWith() throws Exception {
        File f = new File("src/main/assets/logos/fallback.txt");
        if (!f.exists()) f = new File("app/src/main/assets/logos/fallback.txt");
        assertTrue(f.exists());
        String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        LogoMatch m = LogoMatch.parse(text, null);
        assertTrue(m.size() > 1000);
        assertEquals(new HashSet<>(Arrays.asList("united-kingdom", "ireland", "international")), m.countries);
        assertEquals(UK + "bbc-one-uk.png", best(m, "UK: BBC ONE FHD", null));
        assertEquals(UK + "sky-sports-main-event-uk.png", best(m, "|UK| SKY SPORTS MAIN EVENT ᴴᴰ", null));
        assertEquals(UK + "itv-1-plus-uk.png", best(m, "UK: ITV 1 +1", null));
        assertEquals(UK + "tnt-sports-1-uk.png", best(m, "TNT Sports 1 FHD", null));
        assertEquals("countries/ireland/rte-one-ie.png", best(m, "IE: RTE One", null));
        // Names only: no picture's bytes in it.
        assertTrue(f.length() < 64 * 1024);
    }
}

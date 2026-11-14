package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Movie mode's pre-show: its running order, how a game's time is put, its adverts and its page's own sounds. */
public class MovieModeTest {
    @Test
    public void aNightAtThePicturesInOrder() {
        assertEquals(Arrays.asList("welcome", "ad", "ad", "sport", "card", "trailer", "between", "card", "trailer", "ad", "card",
                "trailer", "phones", "intro"), MovieMode.order(true));
        List<String> noGames = MovieMode.order(false);
        assertFalse(noGames.contains("sport"));
        // the intro is always the last thing before the film, the welcome the first
        assertEquals("welcome", noGames.get(0));
        assertEquals("intro", noGames.get(noGames.size() - 1));
    }

    @Test
    public void gamesSoldAsABigNight() {
        ZoneId z = ZoneId.of("Europe/London");
        long now = LocalDateTime.of(2026, 11, 14, 10, 0).atZone(z).toInstant().toEpochMilli();
        long tonight = LocalDateTime.of(2026, 11, 14, 20, 0).atZone(z).toInstant().toEpochMilli();
        long lunch = LocalDateTime.of(2026, 11, 14, 12, 30).atZone(z).toInstant().toEpochMilli();
        long tomorrow = LocalDateTime.of(2026, 11, 15, 16, 30).atZone(z).toInstant().toEpochMilli();
        long tuesday = LocalDateTime.of(2026, 11, 17, 19, 45).atZone(z).toInstant().toEpochMilli();
        assertEquals("TONIGHT 8:00PM", MovieMode.when(tonight, now, z));
        assertEquals("TODAY 12:30PM", MovieMode.when(lunch, now, z));
        assertEquals("TOMORROW 4:30PM", MovieMode.when(tomorrow, now, z));
        assertEquals("TUE 7:45PM", MovieMode.when(tuesday, now, z));
    }

    @Test
    public void advertsForThingsNobodyCanBuy() {
        Pattern betting = Pattern.compile("\\b(bet|bets|betting|odds|bookie|bookmaker|casino|gamble|gambling|lottery|wager)\\b");
        for (String[] a : MovieMode.ADS) {
            assertEquals(Arrays.toString(a), 8, a.length);
            for (String s : a) assertFalse(s, betting.matcher(s.toLowerCase(Locale.UK)).find());
            assertTrue(a[6].matches("#[0-9a-f]{6}") && a[7].matches("#[0-9a-f]{6}"));
        }
        for (String[] b : MovieMode.BETWEEN) {
            assertEquals(3, b.length);
            for (String s : b) assertFalse(s, betting.matcher(s.toLowerCase(Locale.UK)).find());
        }
        assertTrue(MovieMode.ADS.length >= 3 && MovieMode.BETWEEN.length >= 2);
    }

    @Test
    public void thePageHasEverySoundItPlays() throws Exception {
        File dir = new File("src/main/assets/cinema");
        assertTrue(new File(dir, "preshow.html").isFile());
        String js = new String(Files.readAllBytes(new File(dir, "preshow.js").toPath()), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("'(cin-[a-z]+)'").matcher(js);
        int n = 0;
        while (m.find()) {
            n++;
            assertTrue(m.group(1), new File(dir, "sfx/" + m.group(1) + ".ogg").isFile());
        }
        assertTrue(n > 5);
    }
}

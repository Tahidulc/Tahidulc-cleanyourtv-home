package com.gathin.tvhome;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/** The poster pages' lists kept on disk (ListCache): what is written comes back the same, and nothing else does. */
public class ListCacheTest {
    private static String[] title(String tv, String id, String name) {
        return new String[]{tv, id, name, "An overview\twith a tab,\na new line and a back\\slash", "2026", "7.4", "Crime · Drama",
                "https://image.tmdb.org/t/p/w342/a.jpg", ""};
    }

    @Test
    public void listComesBackTheSame() {
        List<String[]> rows = Arrays.asList(title("1", "42", "Lupin"), title("0", "7", "Verity"));
        String text = ListCache.encode("trending/movie/week|false", 1234L, rows);
        ListCache.Entry e = ListCache.decode("trending/movie/week|false", text);
        assertEquals(1234L, e.at);
        assertEquals(2, e.rows.size());
        assertArrayEquals(rows.get(0), e.rows.get(0));
        assertArrayEquals(rows.get(1), e.rows.get(1));
    }

    @Test
    public void anotherListsKeyIsNotRead() {
        String text = ListCache.encode("movie/popular?region=GB|false", 1L, Arrays.<String[]>asList(title("0", "1", "A")));
        assertNull(ListCache.decode("movie/upcoming?region=GB|false", text));
    }

    @Test
    public void aBrokenFileIsNotRead() {
        assertNull(ListCache.decode("k", null));
        assertNull(ListCache.decode("k", "k\nnot a time\n"));
        assertNull(ListCache.decode("k", "k\n5\nonly\tthree\tfields\n"));
    }

    @Test
    public void escapesRoundTrip() {
        for (String s : new String[]{"", "plain", "tab\there", "line\nbreak", "back\\slash", "\\t literal", "end\\"}) {
            assertEquals(s, ListCache.unescape(ListCache.escape(s)));
            assertFalse(ListCache.escape(s).contains("\t"));
            assertFalse(ListCache.escape(s).contains("\n"));
        }
    }

    @Test
    public void sameTitlesComparesKindAndId() {
        List<String[]> a = Arrays.asList(title("1", "42", "Lupin"), title("0", "7", "Verity"));
        List<String[]> renamed = Arrays.asList(title("1", "42", "Lupin (new name)"), title("0", "7", "Verity"));
        List<String[]> moved = Arrays.asList(title("0", "7", "Verity"), title("1", "42", "Lupin"));
        assertTrue(ListCache.sameTitles(a, renamed));
        assertFalse(ListCache.sameTitles(a, moved));
        assertFalse(ListCache.sameTitles(a, new ArrayList<>()));
    }

    @Test
    public void writtenToDiskAndReadBack() throws Exception {
        File dir = Files.createTempDirectory("lists").toFile();
        List<String[]> rows = Arrays.<String[]>asList(title("1", "42", "Lupin"));
        ListCache.write(dir, "on/the/air|true", 99L, rows);
        ListCache.Entry e = ListCache.read(dir, "on/the/air|true");
        assertEquals(99L, e.at);
        assertArrayEquals(rows.get(0), e.rows.get(0));
        assertNull(ListCache.read(dir, "something/else|true"));
        ListCache.prune(dir, -1L, System.currentTimeMillis() + 10_000L);
        assertNull(ListCache.read(dir, "on/the/air|true"));
    }
}

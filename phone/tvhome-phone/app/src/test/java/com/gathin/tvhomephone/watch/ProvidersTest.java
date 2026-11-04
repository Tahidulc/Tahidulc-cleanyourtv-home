package com.gathin.tvhomephone.watch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class ProvidersTest {
    private static JSONObject gb(String json) throws Exception {
        return new JSONObject(json);
    }

    @Test public void streamingAppOnThePhoneFirst() throws Exception {
        Providers.Choice c = Providers.choose(gb("{\"flatrate\":[{\"provider_id\":337},{\"provider_id\":8}]}"), "Lupin", 1, true,
                p -> p.equals("com.netflix.mediaclient"));
        assertEquals("com.netflix.mediaclient", c.pkg);
        assertEquals("Netflix", c.name);
        assertEquals("stream", c.kind);
        assertTrue(c.url.startsWith("https://www.netflix.com/search?q=Lupin"));
    }

    @Test public void notInstalledOpensTheServicesSite() throws Exception {
        Providers.Choice c = Providers.choose(gb("{\"flatrate\":[{\"provider_id\":337}]}"), "VisionQuest", 2, true, p -> false);
        assertNull(c.pkg);
        assertEquals("Disney+", c.name);
        assertTrue(c.url.startsWith("https://www.disneyplus.com/"));
    }

    @Test public void freeServicesCountAsStreaming() throws Exception {
        Providers.Choice c = Providers.choose(gb("{\"free\":[{\"provider_id\":38}]}"), "Doctor Who", 3, true, p -> p.equals("bbc.iplayer.android"));
        assertEquals("bbc.iplayer.android", c.pkg);
    }

    @Test public void onlyToRentGoesToAStore() throws Exception {
        Providers.Choice c = Providers.choose(gb("{\"rent\":[{\"provider_id\":2}]}"), "Wildwood", 4, false, p -> false);
        assertEquals("store", c.kind);
        assertEquals("Apple TV", c.name);
    }

    @Test public void nothingKnownFallsBackToTmdbsPage() throws Exception {
        Providers.Choice c = Providers.choose(null, "X", 5, false, p -> true);
        assertEquals("web", c.kind);
        assertEquals("https://www.themoviedb.org/movie/5/watch?locale=GB", c.url);
    }

    @Test public void onlyLicensedServicesAreEverChosen() {
        for (int id = 0; id < 3000; id++) {
            String[] s = Providers.service(String.valueOf(id));
            if (s == null) continue;
            for (String part : s) {
                String p = part.toLowerCase(java.util.Locale.ROOT);
                assertFalse(p.contains("debrid") || p.contains("torrent") || p.contains("tivimate") || p.contains("kleva") || p.contains("iptv"));
            }
        }
    }

    @Test public void whereLine() throws Exception {
        assertEquals("On Netflix, Prime Video", Providers.where(gb("{\"flatrate\":[{\"provider_id\":8},{\"provider_id\":9},{\"provider_id\":119}]}")));
        assertEquals("Rent or buy on Apple TV", Providers.where(gb("{\"buy\":[{\"provider_id\":2}]}")));
        assertEquals("Not streaming in the UK yet", Providers.where(null));
    }
}

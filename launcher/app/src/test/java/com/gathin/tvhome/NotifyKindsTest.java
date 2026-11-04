package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class NotifyKindsTest {
    @Test
    public void idsAreUniqueAndEveryKindIsInAGroup() {
        Set<String> ids = new HashSet<>();
        for (NotifyKinds.Kind k : NotifyKinds.ALL) {
            assertTrue("duplicate id " + k.id, ids.add(k.id));
            assertTrue(k.id + " has no group", NotifyKinds.GROUPS.contains(k.group));
            assertFalse(k.name.isEmpty());
            assertFalse(k.line.isEmpty());
            assertFalse(k.icon.isEmpty());
        }
        assertFalse(ids.contains(NotifyKinds.ALWAYS));
        for (String g : NotifyKinds.GROUPS) {
            boolean any = false;
            for (NotifyKinds.Kind k : NotifyKinds.ALL) any |= g.equals(k.group);
            assertTrue(g + " is empty", any);
        }
    }

    @Test
    public void everyCardKindMapsToAListedKind() {
        for (String kind : new String[]{"goal", "red", "ft", "var", "pen", "kickoff", "reminder", "fight", "ht", "table",
                "milestone", "prediction", "release"}) {
            assertNotNull(kind, NotifyKinds.get(NotifyKinds.ofCard(null, kind, false)));
        }
        assertEquals("f1live", NotifyKinds.ofCard(null, "f1", false));
        assertEquals("f1result", NotifyKinds.ofCard(null, "f1", true));
        assertEquals("tennis", NotifyKinds.ofCard("tennis", "notice", false));
        assertNull(NotifyKinds.ofCard(null, "notice", false));
    }

    @Test
    public void everySoundMapsToAListedKind() {
        for (String sound : new String[]{"goal", "red", "fulltime", "f1", "news", "fight", "tennis", "weather", "message", "reminder"}) {
            assertNotNull(sound, NotifyKinds.get(NotifyKinds.ofSound(sound)));
        }
        assertNull(NotifyKinds.ofSound("chime"));
    }

    @Test
    public void kindsSharingAThemeSwitchAreFoundByIt() {
        assertEquals(3, NotifyKinds.byFlag("rich_alerts").size());
        assertEquals(2, NotifyKinds.byFlag("f1_live_alerts").size());
        assertNull(NotifyKinds.byFlag("sky_alerts"));
        assertNull(NotifyKinds.byFlag(null));
    }
}

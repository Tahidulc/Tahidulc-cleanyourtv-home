package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** Live TV's categories: favourites first, hidden ones out, and their channels hidden with them. */
public class GroupsTest {
    @Test public void favouritesComeFirstInTheOrderStarred() {
        List<String> groups = Arrays.asList("UK | Sports", "UK | News", "USA | Sports", "Kids");
        assertEquals(Arrays.asList("Kids", "UK | Sports", "UK | News", "USA | Sports"),
                Groups.order(groups, Collections.emptySet(), Collections.singletonList("Kids")));
        assertEquals(Arrays.asList("USA | Sports", "UK | Sports", "UK | News", "Kids"),
                Groups.order(groups, Collections.emptySet(), Arrays.asList("USA | Sports", "UK | Sports")));
    }

    @Test public void hiddenOnesAreLeftOutAndGoneFavouritesIgnored() {
        List<String> groups = Arrays.asList("UK | Sports", "UK | News", "Adult");
        assertEquals(Arrays.asList("UK | Sports", "UK | News"),
                Groups.order(groups, new HashSet<>(Collections.singletonList("Adult")), Collections.singletonList("Gone")));
    }

    @Test public void aHiddenCategoryHidesItsChannels() {
        List<Channel> ch = Arrays.asList(
                new Channel("1", "BBC One", "UK | News", "", "http://x/1", "", null, null, 0),
                new Channel("2", "Sports 1", "UK | Sports", "", "http://x/2", "", null, null, 0),
                new Channel("3", "Movies 1", "Adult", "", "http://x/3", "", null, null, 0));
        boolean[] own = {false, true, false};
        assertArrayEquals(new boolean[]{false, true, true},
                Library.withGroups(own, ch, new HashSet<>(Collections.singletonList("Adult"))));
        assertArrayEquals(own, Library.withGroups(own, ch, Collections.emptySet()));
    }
}

package com.gathin.tvhomephone.live;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SimilarTest {
    private final List<Channel> channels = new ArrayList<>();

    private void add(String name, String group) {
        channels.add(new Channel("k" + channels.size(), name, group, "", "http://x", "", "", "", 0));
    }

    private Names.Info[] infos() {
        Names.Info[] in = new Names.Info[channels.size()];
        for (int i = 0; i < in.length; i++) in[i] = Names.info(channels.get(i).name, channels.get(i).group);
        return in;
    }

    private List<String> names(List<Integer> idx) {
        List<String> out = new ArrayList<>();
        for (int i : idx) out.add(channels.get(i).name);
        return out;
    }

    @Test public void familiesFromNames() {
        assertEquals("skysport", Names.info("UK: Sky Sports Main Event FHD", "").family);
        assertEquals("skysport", Names.info("Sky Sports+", "").family);
        assertEquals("skysport", Names.info("UK: Sky F1 HD", "").family);
        assertEquals("skycinema", Names.info("Sky Cinema Action HD", "").family);
        assertEquals("bbc", Names.info("BBC One HD", "").family);
        assertEquals("bbc", Names.info("UK: BBC Two", "").family);
        assertEquals("itv", Names.info("ITV2 HD", "").family);
        assertEquals("tntsport", Names.info("BT Sport 1", "").family);
        assertEquals("tntsport", Names.info("TNT Sports 2 HD", "").family);
        assertEquals("channel4", Names.info("Channel 4 HD", "").family);
        assertEquals("e4", Names.info("E4", "").family);
    }

    @Test public void sameFamilyFirstThenTheGroup() {
        add("UK: Sky Sports Main Event FHD", "UK Sports");   // 0, the one playing
        add("UK: Sky Sports Main Event SD", "UK Sports");    // 1, itself in another quality
        add("UK: TNT Sports 1 HD", "UK Sports");             // 2, same group
        add("UK: Sky Sports Football HD", "UK Sports");      // 3
        add("Sky Sports Football FHD", "Sports Backup");     // 4, the better copy of 3
        add("BBC One HD", "UK Entertainment");               // 5
        add("Sky Sports F1", "UK Sports");                   // 6
        add("UK: Sky Cinema Action", "UK Movies");           // 7
        add("Eurosport 1", "UK Sports");                     // 8
        List<String> got = names(Similar.pick(channels, infos(), 0, 10));
        assertEquals("Sky Sports Football FHD", got.get(0));
        assertEquals("Sky Sports F1", got.get(1));
        assertTrue(got.indexOf("UK: TNT Sports 1 HD") > got.indexOf("Sky Sports F1"));
        assertTrue(got.contains("Eurosport 1"));
        assertFalse(got.contains("UK: Sky Sports Main Event SD"));
        assertFalse(got.contains("UK: Sky Sports Football HD"));
        assertFalse(got.contains("BBC One HD"));
        assertFalse(got.contains("UK: Sky Cinema Action"));
        assertFalse(got.contains("UK: Sky Sports Main Event FHD"));
    }

    @Test public void bbcFamilyAndALimit() {
        add("BBC One HD", "Entertainment");
        add("BBC Two HD", "Entertainment");
        add("BBC Four", "Documentaries");
        add("ITV1 HD", "Entertainment");
        add("Channel 4 HD", "Entertainment");
        List<String> got = names(Similar.pick(channels, infos(), 0, 10));
        assertEquals("BBC Two HD", got.get(0));
        assertEquals("BBC Four", got.get(1));
        assertEquals(4, got.size());
        assertEquals(2, Similar.pick(channels, infos(), 0, 2).size());
        assertTrue(Similar.pick(channels, infos(), 99, 5).isEmpty());
    }
}

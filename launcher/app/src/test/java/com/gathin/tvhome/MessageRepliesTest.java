package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class MessageRepliesTest {
    private static final String PHONE = "192.168.1.50", OTHER_PHONE = "192.168.1.51", TV = "192.168.1.20";

    @Test public void aTvIsAnsweredOnThatTv() {
        assertEquals(MessageReplies.TV, MessageReplies.route(true, null));
        assertEquals(MessageReplies.TV, MessageReplies.route(true, ""));
    }

    @Test public void aPhonesMessagePassedOnByATvGoesBackThroughIt() {
        assertEquals(MessageReplies.RELAY, MessageReplies.route(true, PHONE));
    }

    @Test public void aBrowserIsAnsweredOnItsPage() {
        assertEquals(MessageReplies.PAGE, MessageReplies.route(false, null));
        // A "phone" from an address that is not one of the TVs is not trusted to pass anything on.
        assertEquals(MessageReplies.PAGE, MessageReplies.route(false, PHONE));
    }

    @Test public void aMessageIsRememberedWithWhereItCameFrom() {
        MessageReplies m = new MessageReplies();
        long a = m.received(TV, "Bedroom TV", null, 1_000L);
        long b = m.received(TV, "Phone", PHONE, 2_000L);
        assertNotEquals(a, b);
        MessageReplies.Sender s = m.sender(b);
        assertEquals(TV, s.host);
        assertEquals("Phone", s.name);
        assertEquals(PHONE, s.phone);
        assertEquals(2_000L, s.at);
        assertNull(m.sender(a).phone);
        assertNull(m.sender(0));
        long c = m.received(TV, null, "", 3_000L);
        assertEquals("", m.sender(c).name);
        assertNull(m.sender(c).phone);
    }

    @Test public void onlyTheLastMessagesAreRemembered() {
        MessageReplies m = new MessageReplies();
        long first = m.received(TV, "Mum", null, 1L);
        long last = first;
        for (int i = 0; i < MessageReplies.KEEP; i++) last = m.received(TV, "Mum", null, 2L + i);
        assertNull(m.sender(first));
        assertEquals(last, m.sender(last).id);
    }

    @Test public void repliesAfterAGivenTimeOnlyForThatPhone() {
        MessageReplies m = new MessageReplies();
        m.replied(1_000L, "Living Room TV", "On my way", PHONE);
        m.replied(2_000L, "Bedroom TV", "Yes", OTHER_PHONE);
        m.replied(3_000L, "Bedroom TV", "👍", PHONE);
        List<MessageReplies.Reply> all = m.since(0L, PHONE);
        assertEquals(2, all.size());
        assertEquals("On my way", all.get(0).text);
        assertEquals("Living Room TV", all.get(0).from);
        assertEquals("👍", all.get(1).text);
        List<MessageReplies.Reply> later = m.since(1_000L, PHONE);
        assertEquals(1, later.size());
        assertEquals(3_000L, later.get(0).at);
        assertTrue(m.since(3_000L, PHONE).isEmpty());
        assertEquals(1, m.since(0L, OTHER_PHONE).size());
        assertTrue(m.since(0L, "10.0.0.9").isEmpty());
        assertTrue(m.since(0L, null).isEmpty());
    }

    @Test public void twoRepliesInTheSameMillisecondAreBothSeen() {
        MessageReplies m = new MessageReplies();
        MessageReplies.Reply a = m.replied(5_000L, "TV", "Yes", PHONE);
        MessageReplies.Reply b = m.replied(5_000L, "TV", "Coming now", PHONE);
        MessageReplies.Reply c = m.replied(4_000L, "TV", "OK", PHONE);
        assertTrue(b.at > a.at);
        assertTrue(c.at > b.at);
        // A page that saw the first asks after its time and gets the other two.
        assertEquals(2, m.since(a.at, PHONE).size());
    }

    @Test public void onlyTheLastRepliesAreKept() {
        MessageReplies m = new MessageReplies();
        for (int i = 0; i < MessageReplies.KEEP + 5; i++) m.replied(1_000L + i, "TV", "Reply " + i, PHONE);
        List<MessageReplies.Reply> kept = m.since(0L, PHONE);
        assertEquals(MessageReplies.KEEP, kept.size());
        assertEquals("Reply 5", kept.get(0).text);
        assertEquals("Reply " + (MessageReplies.KEEP + 4), kept.get(kept.size() - 1).text);
    }

    @Test public void aPageIsOpenWhileItKeepsAsking() {
        MessageReplies m = new MessageReplies();
        assertFalse(m.open(PHONE, 10_000L));
        m.looked(PHONE, 10_000L);
        assertTrue(m.open(PHONE, 10_000L + MessageReplies.OPEN_MS));
        assertFalse(m.open(PHONE, 10_001L + MessageReplies.OPEN_MS));
        assertFalse(m.open(OTHER_PHONE, 10_000L));
        assertFalse(m.open(null, 10_000L));
    }
}

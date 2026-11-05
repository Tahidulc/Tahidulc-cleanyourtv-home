package com.gathin.tvhome;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quick reply's memory (QuickReply), free of Android so the JVM tests reach it: the messages shown here lately, each
 * with who sent it and from which address, so a reply finds its way back; and the replies kept for the phones'
 * "Message the TV" page (TvLink.page), which asks for them every few seconds while it is open. In memory only, the
 * last KEEP of each, gone with a restart. The words of a message are never kept here.
 */
final class MessageReplies {
    /** The one the TV uses. */
    static final MessageReplies ALL = new MessageReplies();

    static final int KEEP = 20;

    /**
     * Where a reply goes. TV: the TV that sent the message, as a message of its own (POST /message there). RELAY: the
     * TV that passed a phone's message on (DeliveryLink's /message/reply there), which keeps it for that phone's page.
     * PAGE: a phone's browser sent it here, so the reply is kept here for its page.
     */
    static final int TV = 1, RELAY = 2, PAGE = 3;

    /** How long after its last look a phone's page counts as open. */
    static final long OPEN_MS = 15_000L;

    /** A message shown here: its id, when it came, the address it came from, the name it gave, and the phone behind it. */
    static final class Sender {
        final long id, at;
        final String host, name;
        /** The phone's address when another TV passed its message on (TvLink.forward); null otherwise. */
        final String phone;

        Sender(long id, long at, String host, String name, String phone) {
            this.id = id;
            this.at = at;
            this.host = host;
            this.name = name == null ? "" : name;
            this.phone = phone == null || phone.isEmpty() ? null : phone;
        }
    }

    /** A reply kept for a phone's page: when, from which TV, the words, and the phone's address it is for. */
    static final class Reply {
        final long at;
        final String from, text, to;

        Reply(long at, String from, String text, String to) {
            this.at = at;
            this.from = from;
            this.text = text;
            this.to = to;
        }
    }

    private final ArrayDeque<Sender> senders = new ArrayDeque<>();
    private final ArrayDeque<Reply> replies = new ArrayDeque<>();
    private final Map<String, Long> looked = new HashMap<>();
    private long lastId;

    /**
     * The way back for a reply: a phone's browser (an address that is not one of the house's TVs) is answered on its
     * page here; one of the TVs by a message of its own, or, when it passed a phone's message on, through it.
     */
    static int route(boolean houseTv, String phone) {
        if (!houseTv) return PAGE;
        return phone != null && !phone.isEmpty() ? RELAY : TV;
    }

    /** A message about to be shown: remembered with where it came from; its id, for the reply. */
    synchronized long received(String host, String name, String phone, long now) {
        Sender s = new Sender(++lastId, now, host, name, phone);
        senders.addLast(s);
        while (senders.size() > KEEP) senders.removeFirst();
        return s.id;
    }

    /** The message with this id, while it is still remembered; null otherwise. */
    synchronized Sender sender(long id) {
        for (Sender s : senders) if (s.id == id) return s;
        return null;
    }

    /**
     * A reply for a phone's page (the phone's address to), kept with the time it was sent: never the same time as the
     * one before, so a page that has seen one never misses the next.
     */
    synchronized Reply replied(long now, String from, String text, String to) {
        Reply last = replies.peekLast();
        long at = last != null && now <= last.at ? last.at + 1 : now;
        Reply r = new Reply(at, from, text, to);
        replies.addLast(r);
        while (replies.size() > KEEP) replies.removeFirst();
        return r;
    }

    /** The replies for the phone at this address sent after the time given, oldest first. */
    synchronized List<Reply> since(long after, String who) {
        List<Reply> out = new ArrayList<>();
        for (Reply r : replies) if (r.at > after && r.to != null && r.to.equals(who)) out.add(r);
        return out;
    }

    /** The page at this address asked for its replies now. */
    synchronized void looked(String who, long now) {
        if (who == null) return;
        looked.put(who, now);
        if (looked.size() > 4 * KEEP) looked.clear();
    }

    /** Whether the page at this address has asked lately: open on that phone now. */
    synchronized boolean open(String who, long now) {
        Long at = who == null ? null : looked.get(who);
        return at != null && now - at <= OPEN_MS;
    }
}

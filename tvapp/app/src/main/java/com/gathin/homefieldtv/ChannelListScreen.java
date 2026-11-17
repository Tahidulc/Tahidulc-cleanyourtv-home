package com.gathin.homefieldtv;

import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.gathin.homefieldtv.engine.Channel;

/**
 * The channel list in its preview mode (ChannelList.PREVIEW), as the family's TiviMate opens it from the player (Left
 * held, or the bar's Channels list): opaque, the picture playing on in its window at the top right while the remote
 * goes through the channels. OK on another channel plays it there (the list stays), OK on the one playing goes back to
 * it full screen, Back too. Over catch-up the window keeps the programme from the archive until a channel is chosen.
 */
final class ChannelListScreen extends Screen implements ChannelList.Host {
    private ChannelList list;

    ChannelListScreen(TvActivity tv) {
        super(tv);
    }

    @Override boolean overVideo() {
        return true;
    }

    @Override View build() {
        FrameLayout root = new FrameLayout(tv);
        list = new ChannelList(tv, ChannelList.PREVIEW, this);
        root.addView(list, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    @Override void shown() {
        list.open(tv.live.channel());
        tv.live.place(new Rect(Ui.px(ChannelList.VIDEO_X), Ui.px(ChannelList.VIDEO_Y),
                Ui.px(ChannelList.VIDEO_X + ChannelList.VIDEO_W), Ui.px(ChannelList.VIDEO_Y + ChannelList.VIDEO_H)));
        tv.live.subs.room(false, -1f);
    }

    @Override void hidden() {
        tv.live.subs.room(true, -1f);
    }

    @Override void tick() {
        list.refresh();
    }

    @Override boolean key(int code, KeyEvent e) {
        return list.key(code);
    }

    @Override void ok() {
        list.ok();
    }

    @Override boolean back() {
        return list.back();
    }

    @Override public void tune(Channel c) {
        // Settings → Parental control: a locked channel after its PIN, then it plays in the window.
        PinScreen.forChannel(tv, c, () -> {
            tv.live.play(c);
            list.refresh();
        });
    }

    @Override public void full() {
        tv.pop();
    }

    @Override public void closed() {
        tv.pop();
    }

    @Override public void poke() {}
}

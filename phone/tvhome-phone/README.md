# Homefield: the TV Home app for Android phones and tablets

The launcher's own parts on a phone: Home with the scores box and weather, Watch (Discover, Movies and TV shows, a
title's page, the Episode map, Coming up, Because you watched, Film night, trailers), Sports (Match Centre, tables, F1,
This Weekend), alerts as phone notifications with the Control Centre's switches, and Live TV for the family's own M3U
playlist or Xtream Codes login. It pairs with the TVs as Delivery to TV does and keeps My list, ratings, the Film
night plan, Coming up's mutes, reminders and starred clubs the same everywhere (SYNC.md).

Full screen, a tap brings up an info tile that suits what's on (the Full-screen TV Info Tiles canvas): Match night for a
game (the scores box with both crests and the competition's logo, the fixture, kick-off or the score, each club's
form or the game so far), Cinematic for a film, Card for a series' episode and Glass ribbon for the rest, each with
the channel's logo, and facts in turn while a game is on (its numbers, the table, each side's run, earlier meetings,
top scorers). The player's options can set one style for everything, or the guide panel as before.

The scores box is the TV's, as at first: the competition's logo on its navy block, each club's crest on its own white
tile, and the blue middle with the score (or the kick-off time) and under it the clock live in minutes and seconds,
going on second by second between ESPN's reads (67:23; 90:00 with +4 on a red chip in added time), or HT, FT or
KICK-OFF.
Full screen, the picture goes into a small window as you leave the app (from the guide too; the player's options turn
that off), and a phone on its side stretches the picture to the screen. The info tile is Small at first (on its side a
card at the foot's left, a little over half the width, so the game shows; upright, in the room under the picture),
Medium or Large in the player's options, which also set how long it stays (4, 8, 15 or 30 seconds, or until a swipe
down takes it away). A game's tile and its page have "Also Showing On", from the family's own playlists: the channels
whose guide has the game and the playlist's channels of the broadcasters it is on, each with its logo, the likeliest
first and marked Best match; a channel's 4K/UHD version is listed too, with a "4K" mark on its logo (4K channels in Live
TV wear the same mark, and one with no logo of its own shows its channel's, Sky's, matched past "UK:" and "UHD").
Broadcasters the playlist has none of are named only when it has nothing for the game.

Players have live ratings out of ten (Ratings), worked out from ESPN's figures for them in the game: everyone on starts
at 6.4, goals, assists, shots on target and fouls won lift it, fouls, offsides, cards and own goals bring it down, a
keeper's saves and a clean sheet count, and the score moves it a touch. They show by each name on the line-ups pitch
(the best with a star) and the substitutes, and the full-screen tile's facts name each side's top rated.

A game's alerts come as cards: folded, what happened and who with the scoring side's crest; opened out, both crests on
white discs, the score with the scoring side's number lit, the minute, and the 90 minutes with each goal on them. Under
each, and under the kick-off and line-ups alerts too: Watch match (the game in Live TV, on the playlist channel that
"Also Showing On" puts first), Match and Mute game. "Following live" draws the live
games as the scores box's rows. A game's page has the header card (crests, score or kick-off, the clock, the scorers,
where it is on, Remind me), then Timeline (the game on a pitch, each shot, goal and card where it happened, the newest
pulsing, then every moment by its minute), Line-ups (both elevens on one pitch by their formations, the substitutes
under it, with the players' photos) and Stats in each side's colours. The line-ups come as both elevens on a pitch,
an F1 result as its podium, and "Following live" as the scoreboards with the first game's clock going on.

Films and series play only from your own playlists: Play looks in every playlist in use (its films and series, Vod:
matched by TMDB's id when the provider gives one, else by name and year; a series asks for its season and episode) and
plays it in the app; the line under the title says which playlists have it. Nothing else is looked for and no other
app is opened: when none of your playlists has a title, it says so. Live TV and those films and series play only the
playlists you give it; the app ships with no channels or sources. The tabs are Home, Sports, Live TV, Watch and
Settings.

Subtitles (CC in the film's player): the film's or episode's own, from your playlist's stream, come first, in the
languages you choose (Settings, Subtitles, in order). When it has none, OpenSubtitles.com is looked in by the title's
TMDB id (the series' with its season and episode), best first: your languages, the ones that fit the copy playing (its
frame rate and quality), SDH if you want it, then the most downloaded. Downloads, and searching, need your own free
OpenSubtitles API key, and signing in with that account gives more downloads a day; the key and account stay on the
phone. A file found is kept for that film, drawn by the app over the picture, and can be moved earlier or later as it
plays without the stream reloading. Size (four) and look (white outline, black box, yellow) are in the same menu. On
or off is kept for every film; with it on, a film with none of its own finds one by itself.

You can add more than one playlist (Settings, Live TV: Add playlist, an M3U address or an Xtream Codes login, with a
name). Each shows when it runs out, as its provider says (an Xtream account, or an M3U address from one: green, amber
under a fortnight, pink once run out), its connections and its channels. Use them all together or just one, from the
chips in Settings or the pill under Live TV's title. Together, a channel in more than one of them shows once (matched by
its name, picture and +1), and a film, series or episode the same: the others are kept as its backups, and the player
goes on to the next one when a stream won't play. The first playlist keeps its favourites and hidden channels as they
were. "Also Showing On" also finds a playlist's event channels named for the game ("EPL 03: Arsenal v Chelsea").

Live TV's picture can be adaptive or fixed (Settings, Live TV, Playing, or the player's options). Adaptive plays the
channel you picked and, when your connection can't keep up (three stalls in a minute, or one of five seconds), steps
down to the same channel in a lower quality from your playlists (4K to 1080p to 720p to SD), saying so; after a few
steady minutes it tries a step back up (waiting longer each time that fails). A stream with its own quality levels (HLS)
adapts by itself as well. Fixed always plays the resolution you choose, or the nearest under it when a channel has no
such version. Data saver keeps it to 720p on mobile data, 4K there waits until you ask for it, and channels start after
a second of picture instead of two and a half. The playlist that has started fastest and stalled least on this phone
(or its weekly check) is tried first for a channel that several have; the options say where it plays from.

The player keeps the last 5 or 10 minutes of what you watch on the phone (up to about 1 GB, deleted on the next
channel), so you can go back: double-tap the left of the picture for 10 seconds back (the right goes on again), drag
along the replay bar, or Back to live; a goal or red card alert for the game you are watching has See it again. Alerts
for that game wait until your stream shows the moment: tap "It's just happened" in the options as you see a goal, a card
or the kick-off and the channel's delay is set (or nudge it by five seconds); a line on the picture says an alert is
waiting. A goal in another game comes as a banner with Switch, a double-tap in the middle flips back to the channel
before, the small window keeps the score on it, and the options have night sound (quiet commentary up, loud moments
evened out; Android 9 and later), a touch lock (hold the lock to undo) and Watch on TV, which puts the channel on a
paired TV (it needs TV Home 3.91) and stops the phone's stream, with Continue on phone to come back.

Films and episodes from your playlist that you stop part-way come back on Watch, in a Continue from your playlist row
above the posters (films on Movies, episodes on TV shows, both on Discover), with how far in you got: tap one to carry
on, hold it to take it off. A series is there once, at its latest episode. When an episode ends, the next one is
offered and plays after ten seconds unless you cancel it, and the player has a sleep timer (15, 30 or 60 minutes, or
the end of this one). Live TV has a Sport choice beside Favourites, All and Recent: what is live or still to come today
in the competitions the app reads, and the F1 weekend's sessions, each on the channel of yours most sure to show it
(and how many more show it), grouped by competition, with Live now, Football and F1 to narrow it; tap one to watch it.
The clubs you follow are marked Your club there and on Home's scores box.

Your playlists are looked after: a notification a week and a day before one runs out (as its provider says), and a quiet
health check about once a week on Wi-Fi while nothing is playing (how long the playlist answers, how long a few of its
channels take to start, how many fail; never while the account's connections are all in use, one stream at a time, each
closed after a couple of seconds). Each playlist's card in Settings shows Healthy, Slow or Struggling with those three
numbers and a Check now; you're told only when one turns Struggling. Two minutes before a followed club's game (or one
you set a reminder for) a heads-up says "Kick-off in 2 min" and which of your channels it will be on, with Watch now.
Goals for the clubs you follow come on their own channel, "Your club's goals", with their own buzz (long, short, long).
The "Your club" home-screen widget (4×2, resizable) shows the live game (or the next one) as the scores box, the game
after it, and Watch while it is live.

Two games at once: from the player's options, two of your channels play together on the phone on its side, side by
side or one big with the other small in its corner. Each shows its game's scores box (from its guide programme, or an
event channel's own name) and which channel and playlist it is; the one with the sound is outlined in white, and a tap
on the other gives it the sound. Swap sides, Big + small (or Side by side), Change (the channels showing the same game
first, then every channel with search) and One game (back to the player on the game with the sound). Before a second
stream from the same playlist starts, its account's connections are checked: one at a time (or all in use) offers the
same channel from your other playlist instead. Each stream goes on to its backup copy when it drops.

Match centre: in the full-screen player, a panel up from the foot of the picture with the game's Timeline (its moments
first, then the shot map), Line-ups (with live ratings), Stats (two across) and the table as it stands with the live
score counted in and both clubs marked; read again every 30 seconds while it is on. Pull its handle down, or ✕, to
close it. The player's top bar has Match centre while the channel shows a game, and its options have Two games at once.

The app is called Homefield (it was TV Home; the TVs' launcher keeps its name), with a white house and a play button on
violet to coral for its icon. Live TV's categories can be hidden or made favourites: hold a category's chip (or a
channel, for its category), or open More, Categories. A favourite category comes first among the chips with a star; a
hidden one leaves the guide, the lists and search with its channels (a favourite channel in it stays in Favourites), and
Categories shows it again. Channels are made favourites with their star or by holding them, as before.

A film or episode found in more than one place (another playlist, or another version in the same one: its 4K and its
HD) asks which to play, each named by its playlist, the picture its name says and its file ("Main · 4K · MKV",
"Backup · HD"), the best match first; the others stay its backups. In the full-screen player, Sources switches to another
from the same moment. The player turns with the phone (or stays landscape or portrait: ⋮), has the picture's shapes (Fit,
Fill, Fit width, Stretch), night sound and the small window (▣, or on leaving the app), and its information bar shows
where it plays from and what the stream itself says of its picture and sound: 4K, 1080p, 720p or SD, Dolby Vision,
HDR10 or HLG, and Dolby Atmos, Dolby Digital 5.1, AAC Stereo and the like. Live TV's info tile shows the same marks.

Build: JDK 17, Android SDK 34. `./gradlew assembleRelease testReleaseUnitTest lintRelease`; the APK is in
`app/build/outputs/apk/release/`, signed with the debug key for sideloading.

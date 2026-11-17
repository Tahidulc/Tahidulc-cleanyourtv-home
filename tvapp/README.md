# Homefield TV

An IPTV app for Android TV only, in Homefield's own dark look from the first open: Home, the TV guide, live TV, films
and TV shows, Search, Settings and the player all have its near-black ground, its rail down the left, Outfit for the
writing, amber for what is chosen and white rings and cards for where the remote is, in the colour theme chosen. It plays the family's own playlists (an M3U address or an Xtream Codes login): their live
channels and guide, and their own films and TV shows, browsed by the playlist's categories. It ships with no
playlists or sources of its own, and films and series come only from the playlists added to it. The app's name and
wordmark are Homefield; it carries no Sky logo or name.

## Screens

- **Home:** the rail (Homefield's mark and the time, then Home, the TV guide, Films, TV shows, Sports, Search and
  Settings), where the remote starts, as on a menu: Up and Down along it, OK goes there, Right goes into the rows, Back
  from the rows comes back to it and Back from it goes back to the TV. The big picture of what the remote is on, as
  Films' is (its backdrop fading into the dark, its title, the amber star and its facts, its story), and the mini TV at
  the top right playing the last channel, on a dark card with LIVE, the channel and what's on. Then rows laid out as
  Films' are: On now (each channel's tile as the live screen draws it: a picture of what's on with the channel's logo,
  the title, a line about it and how far through in amber, its number and name and what's left), Continue watching,
  Your TorBox (the family's own TorBox films and shows that no playlist has, newest first, each with TorBox's cloud),
  New films, New TV shows (the posters as everywhere), Sport on now and favourite channels.
- **TV guide (dark):** the rail, then the categories down the side with how many channels each has (All channels,
  Favourites, Recently watched, Entertainment, Sport, News, Movies, Kids, Documentaries, Music, then the playlist's own
  groups). At the top, the channel the remote is on (its logo on its tile, its number and name, what's on now with how
  far through it is and what's left, and what's next) over the picture of the programme the remote is on; the times
  along a ruler (Today, 24-hour times, a mark at the time now); then a row a channel, its number and logo and its
  programmes in their own deep colours (as the phone's guide has them) with their times over their titles and the part
  already shown shaded, the programme the remote is on in amber. Under the channel's row a strip opens with the
  programme's picture (from Sky's public UK guide, else the channel's logo on its tile), its title, its times, Live or
  Starts in…, HD or 4K as the channel's name says, and two lines about it; the rows below move down to make room.
  Left and Right move through time, Up and Down through the channels, OK watches, holding OK brings up a small menu
  (watch it, from the start, add the channel to the favourites or take it off), the number keys go to a channel. On a
  channel whose provider keeps an archive, Left goes on back into the programmes already shown, as far as the archive
  goes (the guide keeps up to three days of them): each has a small circle arrow, its strip says Catch-up, OK plays it
  from its start, and Back comes back to the time now. No mini TV here: the stream is let go until a channel is chosen.
- **Live TV:** full screen. OK brings up the channel banner over a dark scrim: the channel's group and small logo at
  the top; its large logo, what's on (its times, how far through, what's left), the channel, the picture's size and
  frames a second as the player has them, what's next, a thin amber line across for how far through it is, and a row
  of the last ten channels watched on this TV (not the one playing), each with what's on and how far through. Left and
  Right go along the row; OK changes to one at once. Up and Down bring up the live screen without leaving the channel:
  the picture of what's on the channel the remote is on fills the screen behind, darkened; along the top what's on
  after it; its logo, the programme's title, the channel, the times and three lines about it; then the category's row
  of channels, each tile the channel's logo over a picture of what's on with its title, a line about it and how far
  through it is (on its black tile when there is no picture), and the next category's row below. Up and Down go
  through the channels, Left and Right along the row, OK changes to the one the remote is on. The channel keys and the
  number keys change at once; Back, or a few seconds with no key, hides it all. A stream that drops is tried on the
  same channel in another playlist, then again. Where the provider keeps the programme on now in its archive (or the
  stream itself reaches back to its start), the banner has Watch from the start over the logo (Left from the row, or
  the remote's rewind key) and the live screen says so; holding OK brings up the channel's menu.
- **Subtitles:** over full-screen live TV and catch-up only (not the small pictures on Home and in the guide), for
  every channel: Off (as it starts), From the channel (the subtitles a channel sends: DVB subtitles where it puts them,
  its subtitle playlists, captions) or Automatic, made on the TV from what's said as it's said, a channel's own showing
  instead when it plainly sends some (a bare captions track every stream is given doesn't count). Chosen from the
  channel's menu (OK held, Subtitles, the one chosen ticked) or Settings, Live TV, with their look (white with an
  outline, white on a black box, yellow) and size. The words keep about 40 letters to a line near the foot, move up
  above the banner and the catch-up bar while they are up, and go while the live screen's rows are up. Automatic uses
  the TV's own on-device speech recognition where it has it (Android 13 and later); where it hasn't, it asks first
  ("a one-time download of about 40 MB, about 70 MB on the TV") and only then fetches Vosk's small English model into
  the app's own storage, its progress where the words go. The sound is heard on the TV, by a copy taken in the
  player's sound chain; nothing heard leaves the TV. A channel's own tracks stay off while nothing shows them.
- **Reminders:** in the guide, OK held on a programme to come gives Remind me (Cancel reminder once one is set), and a
  bell on its block and "Reminder set" under it. A minute before it starts, a card comes up at the top right over
  whatever is on the TV, other apps too: the channel's logo on its tile, "Starts in 1 min" (then "Starting now"), the
  title, the channel and its time, and what the keys do. It takes the remote after a second and a half; OK opens Homefield
  TV on the channel, Back puts it away, any other key lets it go, and it goes by itself after about 20 seconds. Over
  other apps where Android lets Homefield TV (Display over other apps, asked for once, when the first reminder is set,
  with a way to Android's page); otherwise a notification that asks to come up as the same card. Its alarm is exact
  where Android allows it and never wakes the TV: a TV that is off stays off, and a card is shown only up to a few
  minutes after the start. Reminders are kept on the TV and made again after a restart and an update.
- **Catch-up:** a programme from the provider's archive: an Xtream Codes login's streams marked as kept (for the days
  the server says, its times in the server's own time zone; the older timeshift.php address when the newer one
  doesn't answer), and an M3U playlist's catchup, catchup-days and catchup-source (default, append, shift, Flussonic
  and Xtream; {utc}, {start}, {end}, {duration}, {offset}, {lutc}, {Y}{m}{d}{H}{M}{S} and the like filled in; also the
  older timeshift and tvg-rec), or an Xtream server's M3U address given its archive by the player API. Over the picture,
  along the foot: the programme's title, its day and times, the channel and Catch-up (or how far behind live it is:
  "−12:34 behind live"), then a bar across the programme from its start to its end, the part shown filled, the live
  edge marked, the knob where the picture is with play or pause in it, and the time in a bubble while it moves. Left
  and Right move it 10 seconds a press, more as they are held (30 seconds, a minute, three minutes), and the archive
  is asked for from there a moment after the remote rests; OK plays or pauses; Up or Back go live. It goes on into the
  next programme when one ends, and is live again at the live edge. The archive's address is made on the TV from the
  channel's own, and like it is never shown or written out. Channels with no archive don't offer any of it.
- **Sports:** Sports in the menu lists today's live and coming games (football, NFL, NBA, tennis, UFC, F1) with their
  crests and competitions; a game's page has the score and clock, events, live stats, line-ups, momentum and Watch on
  buttons (English channels first), all held back to match the channel being watched. Over a match in the player, the
  scoreboard: the game found from the channel and its guide, its clock set at kick-off with OK, alerts beside it, the
  kick-off and after-goal pop-ups and the line-ups, a cover for the channel's logo, a menu on OK held, and a Scoreboard
  section in Settings.
- **Moving about:** on the dark screens the picture behind cross-fades as the remote moves to another title or
  channel, the words fade in and rise into place a moment after, pages cross-fade into each other, and the poster or
  tile the remote is on grows a little inside a soft white ring. Opacity, a small move and scale only, nothing
  blurred, and each picture read once at the size it is shown, so it stays light on a TV with 1 GB of memory.
- **Films and TV shows (dark):** a slim rail down the left (Homefield's mark, the time, Home, the TV guide, Films, TV
  shows, Search, Settings). First every category at a glance: a row of posters a category (Continue watching and My
  list first, then Your TorBox, then Recently added) under a big picture of whatever the remote is on: its backdrop, title, facts (★
  rating, genre, running time, age rating), story and who is in it. Left from a row's first poster opens the side panel
  of lists: All categories, all the films (or shows), Continue watching, My list, then each category with how many it
  has; any of them but All categories shows as one grid of posters beside the panel.
- **Posters**, wherever they show (the rows, the grids, More like this), as the phone draws them: a 2:3 card with
  rounded corners whose foot fades into the poster's own colour, the title in bold over the fade with "Genre • ★ 8.2"
  under it, "New" on the top left for a title the playlist added in the last fortnight, the best picture the family's
  playlists have of it on the top right (4K, 1080p or 720p in a white chip, read from the names, with the Dolby Vision
  or Atmos mark where a name says so), and the title and year under the card. The poster the remote is on grows a little
  inside a white ring. One of the family's own TorBox titles has TorBox's green cloud on its top left, "New" beside it;
  without a TMDB poster its card is drawn in a colour of its own with its title. Settings, Posters: Landscape makes every
  card 16:9, as the phone's landscape posters: the title's
  wide backdrop (TMDB's at w780, the poster where there is none), the title at the foot's left in at most half the
  card's width (where the phone puts the title's logo; the TV has no logos), the genre and rating left-aligned under it,
  the fade rising from a quarter of the way down, the same badges and chips, and the title and year under the card.
- **A title (dark):** its backdrop filling the screen and its title large; Play (or Resume where it was left) with round
  buttons beside it (My list's heart, from the start, the trailer in the TV's YouTube app); the playlist it is from (Your
  TorBox for the family's own), its story and facts in the middle, who is in it on the right. Down from there: a series'
  seasons as pills over one row of every episode, season after season (each with its still, "S1 E8", its title, two
  lines of story, its length and when it was first shown; an episode only the family's TorBox has, with TorBox's cloud on
  its still, in its place among the playlist's), the season's pill following the row and a season chosen sliding the
  row to its first episode;
  then the trailer, the cast in circles (initials, as the playlist gives no photos), and more from its category.
- **Choose a source:** Play on a film's page (and From the start, and an episode chosen) first lists every source it
  has, as the phone's Choose a source: the family's own TorBox files of it, then the playlists' copies, each source
  under its own heading, each copy with its mark (TorBox's green with a cloud, a playlist's blue with a screen and its
  name), the logos of its picture, HDR and sound as its name says them (4K Ultra HD, Dolby Vision, HDR10, Dolby Atmos,
  Dolby and its channels, DTS-HD 7.1…) and its file's kind, and its file's name and size under them. The best is first,
  marked Best match, with the remote on it: OK plays the one chosen, and the rest stay its backups in that order, for
  Change source while it plays; Back goes back to the page. With one source it plays straight away. Resume and Continue
  watching carry on straight away from the copy it was started on (the others after it), and the next episode starts
  straight away from its best copy, as on the phone.
- **Player:** the playback bar, in the dark look of catch-up's (a scrim from the foot, the amber knob with pause or
  play in it, the time watched under it, the next episode on a dark card at the end of one). OK pauses and plays, Left and Right go back and on (faster when held), Back keeps the
  place, and a series goes on to the next episode. It plays from every copy there is, the best first, tried in turn:
  the family's own TorBox files of the film or episode first (all of them found before anything plays, unless Settings
  put them last), then the playlists' copies. Over the title, where it is playing from: TorBox's green mark with a
  cloud, or the playlist's blue one with a screen and the playlist's name, and what the copy's name says ("1080p ·
  MKV"). Up lists every copy as Choose a source does, the one playing ticked; another one chosen carries on from the
  same moment.
- **TorBox:** films and episodes already in the family's own TorBox account (its finished torrents, usenet and web
  downloads) play as copies beside the playlists', matched by their names: the title, with its year no more than a year
  apart, and a series' season and episode (from the file's name, its folders or its download's name). Every version is
  offered, the best first, with no limit on size, the same on every TV and on the phone: 4K, then 1080p, then 720p,
  then the rest; within the same picture Dolby Vision or HDR before SDR, then Dolby Atmos or surround before stereo,
  then the bigger file; AVI and the like after all the others. The playlists' copies go the best first too, where their
  names (or categories) say; TorBox's stay before them unless Settings say last. Films and series in
  the account that no playlist has are listed as titles of their own under Your TorBox (a film by its name and year, a
  series by its name, with its seasons and episodes from the files), in Movies and TV shows, their grids and on Home; a
  series' episodes that only TorBox has join the playlist's. Their posters, stories, cast and episode names come from
  TMDB where it knows the title, with the key the TV launcher on this TV hands over (launcher 5.33 and later; kept in
  memory only, never written down, logged or shown, and sent only to TMDB); what TMDB said is kept on this TV for a
  fortnight, and without it the posters are drawn. They are read again as the app comes back after a quarter of an
  hour, and when TorBox is turned on, checked or forgotten. Nothing is searched for or added to the account. The API key
  stays on this TV, is never shown again once saved and never written to the log, and goes only to TorBox: in its
  requests, and in a file's address as it starts.
- **Search:** the rail, the letters beside it (the key the remote is on in amber), and the channels, films and TV
  shows whose names have what is typed, on the right. Left from the letters goes on to the rail.
- **Settings:** the sections down a side panel beside the rail, each one's page to the right under its name, a field
  typed in on a dark sheet rising from the foot: the playlists (all of them together or one, their expiry dates, and catch-up: "Catch-up: 120 channels
  keep programmes for up to 7 days", or "Your provider keeps no catch-up for these channels"), adding an M3U playlist
  or an Xtream Codes login, TorBox (on or off, its copies first or last, the API key typed as dots, Check with TorBox:
  how many films and episodes it has ready, or TorBox's word why not, and Forget), reading them again, removing
  everything (from this TV only), and the channel logos: on black tiles (each logo trimmed and set in the middle of a
  black tile, a dark one turned light) or as the playlist gives them.
  Theme: Homefield (as it came), Midnight blue, Emerald, Crimson or Graphite, each with its own ground, panels, cards,
  accent, focus rings, guide colours and badges on every screen; a picture of the app under the list takes each theme's colours
  as the remote moves through them, and OK keeps one (the screens start again in it, back on Settings). Posters:
  Portrait / Landscape. Live TV: subtitles on live TV (Off, From the channel or Automatic), their look and size, and
  where the TV needs it the English speech model (downloaded from there on OK, with how far it has come, or "On this
  TV"). Reminders: the ones set, soonest first (OK cancels one), and whether their card may pop up over other apps (OK
  opens Android's page for it). Player: the buffer for live TV and for films, one Picture quality (Adaptive, which stays
  on the channel picked and uses its own copies as seamless quality levels, or a fixed 4K, 1080p, 720p, SD or Data
  saver), the decoders, the picture's shape (each channel keeps its own), matching the TV's frame rate, tunnelled
  playback and how long the channel banner stays. Sound: passed through or decoded, two speakers, languages and the
  sound's delay. Stream problems: reconnecting, the provider's adaptive stream and a playlist's own user agent. MENU (or
  the remote's settings key) while watching brings up the player's menu: the picture's shape, the sound's delay and
  track. General: the screen it starts on (Home, Live TV, the guide, films or TV shows) and starting when the TV starts.
  Appearance: the clock, programme times, text size, live TV's channel tiles, the guide's rows, progress bars and
  channel numbers. Remote, for live TV full screen: Up and Down, the channel keys' way round, OK and OK held, how long
  the number keys wait, the channel before, and the colour keys. Guide: how often the guide and the playlists are read
  again, reading them as the app starts, a time shift for a guide whose times are out, and more XMLTV guides that fill in
  what the playlists' own leave out. Categories: the live categories hidden (out of the guide, Live TV, channel up and
  down and Search; favourites stay), moved and renamed. Parental control: a four-number PIN, asked before a locked
  category's or channel's programme plays (channels locked from their menu in Live TV, OK held), locked channels shown
  or hidden, and Settings locked too. Backup: settings, playlists, favourites, recent channels, reminders and My list
  saved to a file on the TV and in Downloads, and put back from this TV's backup or a file picked; never the TorBox key,
  and nothing sent over the network. Sync: Sync now and when it last synced, and a switch
  for each kind (playlists, favourites and recents, Continue watching, settings), passed on by the launcher on this TV
  to the family's phones, tablets and other TVs; TorBox's key stays on each device. Updates: the version on this TV,
  Check for updates (it also looks quietly as the app starts)
  and when it last looked; when a newer Homefield TV has been put on this TV (update.json and the APK in the app's own
  folder, files/update/), its name, what's new and Update now. The APK is checked first (its size, its SHA-256, that it
  is Homefield TV, newer, and signed with the same key), then handed to Android's installer, which asks before it
  updates; the first time, Android asks to let Homefield TV install apps. A dot on Settings shows one is waiting, and
  What's new shows once after an update.
- **From the phone:** while Homefield TV is open (or playing), the family's phone can send it the channel it is
  watching (its Live TV's cast button). The TV makes itself known on the home network (`_homefieldtv._tcp`, with its
  name), listens on port 47830 when it is free (the phone tries it when the announcement doesn't reach it) and answers
  only addresses on that network: the channel is played only when this TV's own playlists have it (by its stream id,
  else its guide id, else its tidied name), with "Playing … from …" for a moment, and the phone's Stop lets go of it
  (back to Home, its mini TV left off). The phone's stream address and login are never used.

## Getting a playlist on to a TV

On the TV: Settings, then Add an Xtream Codes login (or Add an M3U playlist), typed on the on-screen keyboard. A playlist
is kept only when it gives live channels.

From a computer with ADB connected to the TV (the setup entry point needs a permission only the ADB shell has):

```
adb shell content insert --uri content://com.gathin.homefieldtv.setup/xtream \
  --bind name:s:Home --bind server:s:http://host:port --bind user:s:NAME --bind pass:s:PASSWORD
adb shell content insert --uri content://com.gathin.homefieldtv.setup/m3u --bind name:s:Home --bind url:s:'http://…'
adb shell content query --uri content://com.gathin.homefieldtv.setup/playlists
adb shell content delete --uri content://com.gathin.homefieldtv.setup/playlists/p1
adb shell content insert --uri content://com.gathin.homefieldtv.setup/torbox --bind key:s:KEY
adb shell content delete --uri content://com.gathin.homefieldtv.setup/torbox
```

The login stays in the app's private storage on that TV. It is never logged, and the app shows the password only as dots.
TorBox's API key is kept the same way and checked with TorBox at once: the answer is how many films and episodes it has
ready, or TorBox's word why not.

## Building

JDK 17, Android SDK 35. In this folder:

```
./gradlew --no-daemon assembleRelease testReleaseUnitTest lintRelease
```

The APK is `app/build/outputs/apk/release/app-release.apk`, signed with the debug key for sideloading.

## Versions

| Version | What changed |
|---|---|
| 1.0 | The first version: Home, the TV guide, live TV with the channel banner, Movies and TV shows from the playlists, title pages with seasons and episodes, the player with the playback bar, Search and Settings. |
| 1.1 | Channel logos on black tiles, drawn as the TV launcher and the phone draw them: in the guide (the channel column and the large logo at the top), the channel banner's cards, Home's channel cards and its large picture, and Search. Each logo from the playlist is trimmed and set in the middle of a black tile; a white JPEG box round it is taken away and a mostly dark logo is turned light. Settings, Channel logos: on black tiles, or as the playlist gives them. |
| 1.2 | Films and TV shows in a new dark look: the rail; every category at a glance under a big picture of the title the remote is on; the side panel of lists (All categories, all of them, Continue watching, My list, the categories with their counts) and a grid for each; posters drawn as the phone's (a fade into the poster's own colour, New, 4K / 1080p / 720p and Dolby marks from the playlists' names, genre and rating, title and year); a title's page with the backdrop full screen, Play and round buttons, the story, facts and cast, a series' season pills over one row of every episode, the trailer, the cast and more like it. My list (the heart). Names lose the picture and sound tags after them. Outfit (SIL Open Font License 1.1) for the dark screens. |
| 1.3 | The TV guide and live TV in the dark look. The guide in the phone's guide's colours, with the categories (and their counts) down the side, the channel's band at the top over the programme's picture, and a strip under the channel's row with the programme's picture from Sky's public UK guide, its times, what it is and two lines about it. OK while watching brings up the banner with the picture's size and frame rate as the player has them and a row of the last ten channels watched on this TV; Up and Down bring up the live screen, the programme's picture behind and each category's channels as tiles with a picture of what's on, its title and a line about it. Pictures cross-fade, words fade and rise, pages cross-fade, and posters and tiles grow inside a ring as the remote moves. |
| 1.4 | Colour themes in Settings, Theme: Homefield (as it came), Midnight blue, Emerald, Crimson and Graphite, each with its own ground, panels, cards, accent, focus rings, guide colours and New badge, and the blue screens tinted to match; a picture of the app under the list changes as the remote moves through them, and the one chosen is kept. Settings, Posters: Portrait / Landscape: landscape cards are 16:9 with the title's backdrop, as the phone's. The family's phone can send the channel it is watching to the TV while Homefield TV is open: played only when the TV's own playlists have it, from the home network only. |
| 1.5 | Updates from inside the app: Settings, Updates shows the version on this TV, Check for updates and when it last looked. A newer version put on this TV (update.json and the APK in the app's own folder) is offered once its size, SHA-256, package, version and signing key check out: its name, what's new and Update now, which hands it to Android's installer (Android asks first; the first time it asks to let Homefield TV install apps). A dot on Settings while one waits; it looks quietly as the app starts; What's new once after an update. From the phone: its Stop lets go of the channel it sent, and the TV listens on port 47830 when it is free. |
| 1.6 | Catch-up: programmes from the provider's archive (Xtream Codes' tv_archive in the server's time zone, M3U catchup, catchup-days and catchup-source of every kind) and the programme on now from its start. The guide goes back into the archive's programmes with a small circle arrow on each (OK plays one; Back comes back to now); the channel banner has Watch from the start; a bar across the programme with the live edge, the time in a bubble as it moves, Left and Right 10 seconds a press and faster held, OK to pause, Up to go live, on into the next programme. OK held in the guide and on live TV brings up a small menu (watch, from the start, favourites). Settings says how many channels keep programmes and for how long. Sports hub and scoreboard in the player: Sports in the menu with today's games, a page for each (score, clock, events, live stats, line-ups) held back to the channel, and the scoreboard over a match with its alerts, pop-ups and line-ups, and a Scoreboard section in Settings. |
| 1.7 | Subtitles on live TV and catch-up, for every channel: Off (as it starts), From the channel (DVB subtitles where the channel puts them, subtitle playlists, captions) or Automatic, made on the TV from what's said as it's said, with a channel's own showing instead when it plainly sends some. In the channel's menu (OK held, Subtitles) and Settings, Live TV, with their look (white with an outline, on a black box, yellow) and size. The words sit near the foot, move up above the banner and the catch-up bar, and go under the live screen's rows. Automatic uses the TV's own speech recognition where it has it (Android 13 and later), else asks before a one-time download of Vosk's small English model (about 40 MB, about 70 MB on the TV; Apache 2.0, with JNA); nothing heard leaves the TV. |
| 1.8 | Reminders: in the guide, OK held on a programme to come, Remind me, and a bell on its block; a minute before it starts a card pops up over whatever is on the TV, other apps too (the channel's logo, the title, "Starts in 1 min"): OK opens the channel, Back puts it away, gone after about 20 seconds. Display over other apps asked for once, with a notification instead where it isn't allowed; never wakes a TV that is off; kept after a restart; listed in Settings, Reminders. Player settings: buffer size, Picture quality (Adaptive keeps you on your channel as the quality follows your connection), decoder, aspect and zoom, frame-rate matching, sound passthrough and delay; the channel banner's time; the player's menu on MENU. |
| 1.11 | Every source shown before playing, the best first: Play lists every TorBox file and playlist copy of a film or episode, each with its mark, the logos of its picture, HDR and sound, its file's name and size; the best has the remote and OK plays it, the rest staying as its backups for Change source. One order everywhere, with no limit on size: 4K, then 1080p, 720p and the rest; Dolby Vision or HDR before SDR; Dolby Atmos or surround before stereo; then the bigger file; AVI and the like last; TorBox's before the playlists', which go the best first too. Resume and Continue watching carry on from the copy it was started on; the next episode starts from its best copy, as on the phone. |
| 1.10 | Your TorBox: the films and series in the family's own TorBox that no playlist has, as titles of their own, in a Your TorBox row on Home and first on Movies and TV shows, in their grids, with TorBox's green cloud on each poster. Their posters, stories and episode names from TMDB where it knows them (the key from the TV launcher 5.33, kept in memory only), drawn posters without. A series' episodes that only TorBox has join its row, each with the cloud. On a TV that shows 4K, TorBox's copies go 4K under 20 GB first, then 1080p, then the rest. Settings: the guide's and playlists' updates, a guide time shift and more XMLTV guides; live categories hidden, moved and renamed; parental control with a PIN for locked categories, channels and Settings; backup and restore, on this TV or from a file, without the TorBox key. |
| 1.9 | TorBox in Movies and TV shows: films and episodes already in the family's own TorBox account play as copies beside the playlists', every version of them, all found before anything plays and tried first (or last, as Settings say); nothing is searched for or added. The player's bar shows where it plays from (TorBox's green mark with a cloud, a playlist's blue one with a screen) and Up lists every copy to change source while watching, from the same moment. Settings, TorBox: on or off, first or last, the API key (typed as dots, or sent from a computer over adb), Check and Forget. Settings: start screen, start with the TV, clock and text size, guide rows, what OK, the colour keys and the number keys do. Sync: your playlists, favourite channels, categories, recents, Continue watching and shared settings follow you between your phones, tablets and this TV (needs the TV launcher 5.32); Remove everything resets this TV only. A reminder due while the TV's screen is off now waits and shows once it is on, while its programme has only just begun. |

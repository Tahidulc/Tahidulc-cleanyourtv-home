# Homefield TV

An IPTV app for Android TV only. From the first open it has the colour theme's ground and panels (Indigo • Blue as it
comes), the TV's own Roboto for the writing, the accent where the remote is and for what plays, and the menu down the
left that opens into a drawer; Settings and the side menus open in a panel down the right over the screen. It plays the family's own playlists (an M3U address or an Xtream Codes login): their live
channels and guide, and their own films and TV shows, browsed by the playlist's categories. It ships with no
playlists or sources of its own, and films and series come only from the playlists added to it. The app's name and
wordmark are Homefield; it carries no Sky logo or name.

## Screens

- **The menu:** the rail down the left of every screen (Homefield's mark, then Search, Home, the TV guide, Movies, TV
  shows, Sports, Recordings and My list, with Settings at the foot; the screen's own section in white). When the remote
  goes on to it, it opens into the drawer with each section's name, the remote's item lit in the accent and the screen's
  own in a quieter grey; Up and Down along it, OK goes there, Right or Back closes it.
- **Home:** one large card at a time with rounded corners, the artwork of what the remote is on filling it: its title
  big and bold over the picture (a channel's logo at the top right), a line of facts (season or running time, year,
  genre, age as "15+", five stars from its rating) and, along the card's foot, a strip of small pictures: a series'
  episodes (every season's in one row, each later season's first marked "Season 2", the one to carry on with first in
  view), a channel's next programmes (on now with how far in, the later ones with their times), or films like the one
  on the card. The page around the card takes the artwork's colour. Above it, Homefield's mark (OK opens the menu)
  and the sections as words: On now (or Your channels), Continue watching, Your TorBox, New films, New TV shows, Sport
  on now and Favourites; Left and Right go along a section's cards, the next picture cross-fading into the card as its
  name slides in from that side and the page's colour fades to its own; Up goes to the sections, Down into the
  strip, where the tile the remote is on grows with a white edge and shows its name. OK on a channel watches it, on a
  film or show opens its page, on Continue watching carries on, on an episode plays it and on a programme later sets a
  reminder; OK held on a channel makes it a favourite and CH+ and CH- change channel. Back goes from the strip to the
  card, then to the sections, then opens the menu, and Back from the menu goes back to the TV. Home doesn't play
  anything itself.

- **Home, Movies and TV Shows (Home style Editorial, as it comes):** rows that change with the day ("the categories for
  all this should be fluid and ever changing and the catalogue for these categories should always be changing and
  updated"), picked each day by engine/DayRows from one table (engine/CategorySet: each row's id, title, source and when
  it shows), the same all day so nothing moves while the family browses. Home: its carousel, then Continue Watching; Top 10 Today,
  New This Week (what the playlists added in the last week) and Because you watched (the title watched last), in the
  day's order; while in season, Christmas Films (December), Halloween Frights (October), Valentine's Romance (February's
  first fortnight) or Summer Blockbusters (21 June to August); the row for the part of the day (Easy Morning Watching
  from 5, Afternoon Adventures from 12, Tonight's Big Films from 17, Late-Night Thrillers from 21, as the page was made:
  it changes when the page is next come back to); a Genre of the Day and a Mood of the Day, going round all of them; and
  a dozen of the rest, a different dozen each day (each comes up within three days): the family's other film app's rows
  (Latest Releases, Trending Movies, Latest TV Shows, We think you will like these, Based on True Story, Critically
  Acclaimed, Oscar Winners: Best Picture, Crazy Plot Twists, Cult Classics and the rest, all still there), Trending TV
  Shows, Hidden Gems, Coming Soon and New in Your TorBox. Movies: a carousel, then Top 10 Films Today, Trending This
  Week and New This Week, and its rows the same way (the other app's 21 themed rows among them). TV Shows: a carousel,
  Top 10 Shows Today, Trending This Week and New Episodes This Week, then the same way a row for each streaming service
  TMDB names (HBO Max's, Prime Video's and Disney+'s; never Apple's or Netflix's), Binge-Worthy, Limited Series, Crime
  Dramas, Hidden Gem Series, New in Your Playlist and the rest. A themed row's titles turn with the day too, so it starts
  with others tomorrow. The titles come only from the family's playlists (and their own TorBox titles): TMDB's lists
  (trending, a genre, a keyword found by its name, a list, a network's shows, the recommendations for what was watched)
  are matched to them by name and year off the main thread (engine/MatchIndex, about 1.6 MB for 200,000 titles), a page
  at a time as the remote goes along; a row with nothing in the playlists is left out, and without TMDB the rows are the
  playlists' own (the newest, the categories named for a genre). The one exception is Coming Soon: its titles may not be
  out yet, each saying when it comes ("Coming 18 Dec"; OK plays its trailer in the TV's YouTube app). A row's titles are
  kept a few hours (today's lists three, what is new or coming four, the rest six) and asked for again off the main
  thread when the app comes back to the front after that, or while the page is open for the rows the remote isn't on;
  only the day's rows are kept. One row in focus at a time, under the tab bar: the title the remote is on grows from
  its poster into a wide card (its backdrop, TMDB's logo or its name, how far it was watched and its episode), the one
  it left shrinking back as the row slides it to the left (Lite: no cross-fade of the pictures); under the row its score,
  year, genre and length, and three lines of its story; the next row dimmed under it. Down past the last row goes back
  to the first; Back goes up to the tab bar. When the remote rests on a title in a row for 3 seconds (on every motion
  tier: "put trailers of what panel I'm on after 3 seconds"), its trailer plays inside the wide card ("Can you implement
  trailers to automatically play over a poster when I'm on it"): TMDB's trailers for it (the official trailer, then a teaser, the
  family's language first) or the playlist's own, on YouTube's own embedded player (no controls, muted unless Settings
  say otherwise) in one WebView kept for the app; the card's picture cross-fades to the video once it is playing (Lite:
  no cross-fade) and back as the remote moves on or the trailer ends. One that won't play (embedding turned off, an
  error, nothing within four seconds) is let go quietly for the next, or the poster stays; nothing is ever said on
  screen. It stops at once as the remote moves, goes up to the tab bar or anything comes over the page, and the player
  is let go of whole when the app is left, so nothing plays in the background. Home, Movies and TV Shows each open on a
  carousel of featured titles ("the top card carousel should be one card that goes automatically or I can scroll
  through"): what is trending today with what the playlists added this week (refreshed with the rows), ONE card across
  the page with nothing of the next at its side, its facts, View Details and small page dots under it. It moves on by
  itself every 7 seconds (the page's picture cross-fading, the old card sliding out as the new one and its words slide
  in; on the Lite tier the cards cross-fade instead), from its last back round to its first, while it is what the page
  shows (the remote on it, or up on the tab bar over it), never while the remote is in the rows below; Left and Right
  move it by hand, as far as its ends, and hold its own steps for 15 seconds. On a title the remote rests on for 3
  seconds, its trailer plays behind the card's words, as in a poster; the carousel waits while it does and moves on 2
  seconds after it ends (or as the remote moves it). OK opens a title's page, whose buttons are then a menu down the left: Play (or Resume), Trailer, More
  episodes, Add to My List, More like this. Settings › Appearance › Home style: Classic brings back Home's one big card
  at a time and Movies' and TV Shows' pills.

- **TV guide:** as the family's other app has it. At the top left the preview, 640 by 360, playing the channel last
  watched (or the programme from the archive Back came from), its picture clean: no name or "playing" box over it (3.6);
  to its right, on a frosted panel of its own (frosted glass over the page's blurred picture: in the Lite tier too,
  once the remote rests), the programme the
  remote is on: its title, a star for a favourite channel, its times ("10:00 — 11:00") with a bar and the minutes left
  for one on now ("16 min"), the picture's frames a second for the channel playing ("50 FPS", or as last seen on a
  channel watched before), the playlist and the group at the right, and all it says about it; behind the words, across
  the panel's right-hand part, the programme's own picture, found as the phone app finds it (overlays' PicFinder): the
  guide's own picture of it, XMLTV's icon or image, kept in a few bytes a programme; then the programme found by its
  channel and time, its title the same, in the UK guide catalogues the public websites read: Sky's episode picture
  (its "Image unavailable" card passed over), Freeview's (the broadcasters' own pictures), Virgin Media's (pay channels
  too: Sky Sports, TNT Sports, Sky Cinema, Gold, Discovery), then Sky's season or series picture; a game's own picture
  (TheSportsDB); then by its title: TVMaze's episode, TMDB's episode still, then the series' picture; the channels
  matched under each one's own names (HD, UK, regions tidied, a +1 channel as its own an hour on), every answer kept on
  the TV, requests kept apart, a plain user agent and no login. Only with none of those, the last resorts: a game's two
  clubs' crests on their colours, a picture for what it is (football, motor racing, news, film, kids... drawn in the
  app), else the channel's logo on its own colour, so no programme is blank: the same in the guide's cells, the info
  card and the reminder's panel. A placeholder (a plain card, a tiny one, the channel's logo) is never shown, and one
  too small for where it is shown, past 1.5 times its size, gives way to the next; placed by the rule the phone shares (overlays' PicPlace):
  covering a place close to its own shape, else the whole picture (or nearly) at the place's height beside the words,
  its faces whole, the place's own colour from its edges continuing smoothly behind it, never zoomed into a long cell
  or a short card; faded into the panel from the left, cross-fading to the next programme's as
  the remote moves (Left, Right, Up, Down), the pictures either side read ahead. Under it the date and the clock in the light
  accent, the times every half hour and a dot at the time now; then a row a channel, 88 high: its cell the channel's
  logo alone (no number, no tile round it and no name), as large as the cell allows with the same room all round and its
  own shape kept, read sharp at that size, on the logo's own colour in a deep, opaque shade (Behind logos: None, the logo
  on the plain row; Black tile, a black cell), its marks small and quiet in the cell's right-hand corner (▶ on the
  channel in the preview, the favourite's star, the archive's mark on one whose provider keeps an archive, HD); its
  programmes' cells each filled by the programme's own picture whatever their width, its faces whole (a cell too wide
  for that has the picture blurred behind and sharp where the faces are, the sharp picture feathered into it at both
  its edges; one the times cut shows the faces; a picture's own black bars, pillarbox or letterbox, and a pixel or two
  of its edge cropped before it is laid out, so no dark line runs through a cell), with a soft
  shade only under the title and times and a light fade under the line of how far through, the part already shown
  dimmer, the remote's cell lifted with a white ring (a programme with no picture in a plain cell); a bell on a
  programme with a reminder. The channel's number is still said by the guide for accessibility, and still typed to go
  to a channel. Up and Down move a row, the rows sliding
  so the remote's stays third from the top; Left and Right go along the programmes, the times paging so the remote's
  starts at the left (both glide on the display's own clock, and on smoothly while a key is held, never starting over);
  the channel keys page the rows, rewind and fast forward the times (back into an archive too).
  Left from the programme on now brings in the menu's rail and the groups beside it, pushing the guide right: the
  playlist's Favourites, All channels and Recently watched, its groups in the family's order and names (hidden and
  locked-away ones left out), then the guide's own categories; the guide shows a group as soon as the remote is on it.
  OK held on a group (a category, as the family calls them, and as every line they read says) opens its menu, as
  TiviMate's: Rename category (Restore category name once renamed), Hide category, Move category (its row lifts, Up and
  Down carry it past the others, OK puts it down, Back leaves it), Sort channels (By order in playlist, By name, By
  number or Manual, the one in use ticked), then Show all categories, Hide all categories and Manage categories
  (Settings); on Favourites, All channels or Recently watched, those last three. Left again opens the drawer. OK
  on a programme on now plays its channel in the preview, and OK on the channel playing there goes full screen (Back
  from the player comes back to the guide); OK on a programme to come opens a small menu under it (Remind, Record on a
  channel with catch-up, Add to My list, Programme description); on one already shown that the archive keeps, OK plays it
  from its start. OK held (or MENU) opens the whole menu: Search (for the programme's title) and Settings, a channel in
  several qualities' Quality (below), the programme's lines (Remind, Watch from start, Watch, Record, My list, its
  description), then under the channel's name TiviMate's Add to or Remove from Favourites, Block channel and Hide channel
  (out of every list at once: the guide, the channel lists, channel up and down, the numbers, search, Home's sections and
  the sports pages' Watch on), then Rename channel (always there: the keyboard over the guide, the name shown everywhere
  and found by search with its old one too; Restore channel name), Change logo (below), Move channel (TiviMate's Manage positions: its row lifts in its group, or among the favourites, Up and
  Down move it, OK puts it down and the group keeps that order, Sort channels then Manual; Back leaves it), Merge with
  (below) and Catch-up.
  Play/pause shows the description, Stop adds the channel to the favourites or takes it off, Back held goes back to the
  player and MENU held opens Search; the number keys go to a channel. Back comes back to the programmes on now, then
  opens the groups, then the drawer, then leaves the guide.
- **One row per channel:** a playlist that lists a channel in several qualities ("ITV 1 FHD", "ITV 1 HD", "ITV 1 SD")
  shows it once, everywhere a list of channels shows (the guide and its shelves, the channel list, Up and
  Down, CH+ and CH−, search, Home's On now), under its name without the quality ("ITV 1"), with its best version's
  programmes and logo. Its versions are found once a load, off the main thread: in the same category, by the guide id
  they share, else by the name with its quality tags (FHD, HD, SD, UHD, 4K, HEVC, H.265, 50 FPS, a "+" after one) and
  country tags ("UK:", "|UK|") left out, as the new logos read names; a +1, another number (Sky Sports 1 and 2), a region
  (BBC One Wales) or a version with a guide id of its own is another channel. OK plays its best (4K, then FHD, HD, then a
  name that says none, then SD; ties in the playlist's order); one that doesn't start or keeps stalling falls back to the
  next with a small note ("Playing ITV 1 HD: FHD wasn't answering"), after the tries a dropped stream has, and Picture
  quality's Adaptive still steps within what was chosen. Hold OK: Quality at the top of the card, Best then each version
  by its own name, the one playing ticked; OK plays it and it is kept for that channel until Best is chosen again. While
  it plays full screen, the player's Video tracks has the same Quality over Picture quality. Typing any version's number
  goes to the channel. What was kept for any of its versions is the channel's: a favourite, a lock (the PIN), a name of
  the family's own, a logo chosen, a place (Move channel), the recent channels and History, reminders (shown and
  cancelled from any version), catch-up (from a version that keeps an archive) and each version's frames a second. Hide
  channel, Rename channel and Change logo on it go on every version; a version hidden by itself before is only left out
  of its Quality. Settings, Channels & categories (or Appearance, TV guide), One row per channel (merge qualities): on
  as it comes; off, every version is a row of its own again.
- **Change logo:** from a channel's hold-OK card in the guide, a frosted card beside the menu (from x 760, the whole
  height) with four tabs, as the phone's logo picker: Found for it (the new logos found for it, the best first, each on
  its tile with its country and kind, and the playlist's own; the one shown now ticked), Catalogue (the free logo packs,
  TV Logos and iptv-org's database, searched by the channel's name without its tags, OK on the field for other words on
  the on-screen keyboard; each pack's index read once a week with the generic user agent and kept small in the cache,
  in memory only while the card is up; the results grouped by pack, each logo on its own cell colour as the guide's
  cells have them, Left and Right along a pack), Make my own (wordmarks, monograms, badges, stacked and roundels drawn
  from the channel's name and group, big with the others in a strip under it: Left and Right flip through them, fresh
  ones after the last) and Ask AI (quick asks or the family's own words: Claude designs one with the family's own key,
  Settings › Other › Ask AI, and the TV draws it; Use this logo). Up from the top of a tab goes to the tabs, Left and
  Right change them, Down comes back; OK keeps one, Back leaves it as it was. A logo made here is kept as a see-through
  PNG in the app's own files (`files/logos-made`, never in a backup; let go of once no channel uses it), one from the
  catalogue by its address (TV Logos' by its path, as the new logos are); either goes on every version of the channel
  and on a merge's kept channel, through the channel's edits as every choice does, and shows wherever its logo does:
  the guide, the info card, the mini guide, the reminders and the TV launcher's cards (a made one through the cards'
  provider, `content://com.gathin.homefieldtv.cards/logo/…`, read-only). One that can't be read is the playlist's.
- **Merged channels:** Merge with, on a channel's hold-OK card in the guide, opens a panel at the right ("Choose another
  channel to merge it with"): the channels it most likely is first (the same name, then its category, then a name sharing
  its first word), Up and Down to the channel to keep, OK merges ("BBC One HD merged into BBC One"). The channel merged
  leaves every list (as a hidden one does) and stands in for the one kept: its stream plays when that one's drops (after
  the kept channel's own qualities and copies), and its guide and catch-up are the kept channel's when it has none of its
  own. One row per channel makes a channel's qualities one row first; a merge joins whole rows, so the two never fight.
  A channel merged away can't take a merge, nor can a channel join one of its own versions ("That channel can't take a
  merge"). Separate merged channels on the kept channel's card, or Settings, Channels &amp; categories, Merged channels
  (each merge, OK to separate it; Separate all), bring them back.
- **Channel logos:** the new logos (Settings, Appearance, Logos: New logos (Homefield), as it comes, or the playlist's
  own) are the tv-logo project's free logos for dark grounds (github.com/tv-logo/tv-logos, free for personal use: here
  for the family's own viewing only; no logo is in the app or this repository). The TV asks GitHub for the list of the
  project's logos' names once a week (the small list the app comes with until then: the United Kingdom's, Ireland's and
  the international ones), and fetches each logo it shows from GitHub with Homefield's plain User-Agent and nothing
  about the family, keeping it with its other pictures at the size it is drawn. A channel's name is matched to a logo's
  with its quality and country tags left out ("UK: BBC ONE FHD", "|UK| …", "[UK] …"), a list number before it too, One
  as 1, a "+1" kept apart, a few aliases (ITV as ITV1, More4, CBBC, Sky Main Event) and its numbers always counting;
  where the playlist's country says otherwise, another country's. A new logo shows only where the match is sure (else
  the playlist's); Change logo in a channel's menu offers every one found. On the guide's rows, the channel lists, the
  info card, Home's channel cards, search, History, My list and the sports pages' Watch on.
- **Catch-up's archive:** from a channel's Catch-up in the guide's menu: the channel along the top, its programmes down
  the left under each day's name, the remote's always at the same height with the list sliding under it (the one on now
  in the light accent, those the archive no longer keeps greyed), the days beside them (today's in the light accent), and
  the programme's picture (Sky's public UK guide, else the channel's logo) over a card with its day, times and what it is
  about. Right goes to the days and Left back; OK plays a programme already shown from the archive, watches the one on
  now, or opens the small menu for one to come; OK held opens the whole menu.
- **Live TV:** full screen, the picture alone. A channel changed to (Up, Down, CH+, CH−) brings up the info card at the
  foot: it pops up from the screen's foot (rising and growing to its size with 07's springy ease, moved and scaled as it
  is drawn: the Lite tier's too), stays for Settings, Live TV, Information panel stays for (3, 5 as it comes, 8, 10 or 15
  seconds, or until Back; each further press starting it again) and drops back down; no key hints over it (OK, Left and
  MENU still bring up the overlay, the channel list and the bar). INFO brings it up too: a card in the playback panel's colour with the
  channel's logo alone (as the guide's cells have it: no number or name, which talk-back says), the programme's title,
  the date and the clock, its times with a bar and the minutes left, the playlist and the group, chips for the picture and the sound as the player has them (FHD, 50 FPS, STEREO; the
  frames a second as the stream says them, else counted from the frames it shows over a few seconds and taken to the
  usual rate: 24, 25, 30, 50 or 60, left out until they are known, and kept for each channel), two lines about it and
  what's next, and behind it all the programme's own picture filling the whole card (its faces whole, between the words
  and what's next, a shade only under the words and what's next; the plain card for a programme with none), cross-fading
  as Up, Down, CH+, CH− and INFO bring up another channel's card, the channels either side read ahead. The card comes up
  alone: nothing else over the picture (3.6 took away the Change channel panel at the top right, and the "Watching"
  box with the channel's number over it). CH+ and CH− bring up the next or previous channel's card without leaving the channel
  (OK then changes to it); Up and Down change channel (Settings, Remote can make them bring up the live screen's rows of
  channels instead, as Homefield TV came); Right goes back to the channel tuned to before (however briefly it played;
  again, back); the number keys go to a channel, the number typed in a box at the top right while it is typed. OK brings
  up the overlay, as the family chose it (the mock-up "C with programme pictures": "show 10 channels underneath the
  information panel ... if I press ok when watching a channel it will be at bottom"): the picture darkening from the
  middle down, the card low (from y 352, risen from the foot when it was up there) and under it "Recently watched" with
  how many, then the channels last watched in two rows of five cards (the newest first, up to ten, each once and never
  the one playing; a channel joins them once it has played 10 seconds, so going through the channels with Up and Down
  doesn't fill them, as on the phones): each card its programme on now, the programme's own picture filling it (the
  logo's own colour while it comes, or where there is none) under a dark shade from the left, the channel's logo on its
  colour at the top left, the channel's name and the programme's title beside it, the time left at the foot and how far
  through along it; the remote's card lifts with a white ring, its picture still seen. Left and Right go along a row,
  Down to the second and Up back, and OK watches the channel. Down past the second row brings up the settings in the
  cards' place, the card staying where it is ("Then I press the down button again and it will take me to the settings
  buttons"): pills of glass in two rows, each an icon, its name and what it is set to or where it goes, the remote's
  white: TV guide, Search, Channels list, History, Movies, TV shows, Recordings, My list and Settings, then Picture
  ("1920 × 1080"), Sound ("English · Stereo"), Audio offset, Subtitles, Display, Sleep timer, Favourites ("Add this
  channel" or "Remove"), Channel (its options) and Exit; Up from their first row goes back to the cards. MENU, or OK
  held with a panel up, brings up the same overlay with the remote straight on the settings. There is no Scoreboard among
  them: OK held on the plain picture brings up the scoreboard and logo menu (below), on every channel. Each of the
  picture's and sound's pills opens its list at the right, the card
  staying: Picture opens Video tracks, the Picture quality (Adaptive, 4K, 1080p, 720p, SD, Data saver); Sound, Audio
  tracks: the stream's sound tracks, or Off; Audio offset the sound's delay for this channel, 50 ms a press either way,
  with Reset and Apply to all channels; Subtitles, Closed captions (below); Display, Display mode: the picture's shape
  for this channel or all of them (Normal, Full, Zoom, Zoom - 2.35:1, Reduced, 4:3, 16:9, 2.35:1); Sleep timer stops
  the picture after 15 minutes to 4 hours; Channel's options are Watch from the start and Lock this channel. Left
  brings up the channel list over the picture, as the family picked it: under a shade from the left, the group's name
  at the top beside a round Left button, the channels of the group playing down the left as cards, each on its own
  logo's colour (the family: "make the panels for the channels the same colour as the channel logos are"), its logo on
  a darker tile of it, then what's on, how far through and the time left (the channel playing marked with three amber
  bars, the remote's card lifted with a white ring; a colour too light for white words gets a touch of shade until they
  read). Beside them the programmes of the one the remote is on as a timeline, the one on now on an amber band; at the
  top right its programme in a card with its own picture, LIVE on it, its times, quality and what it's about. Right
  goes onto the programmes, Up and Down along them, Left back: those already shown go back as far as the channel's
  catch-up keeps them, each with the catch-up mark beside its time, and OK plays one from its start; OK on the one on
  now plays the channel live, on one to come sets its reminder. Left on the channels opens the groups (the playlist,
  Favourites, All channels, then the family's groups), and OK plays a channel with the list staying. Left held, or
  Channels list, opens it on its own ground with the same cards and programmes and the picture playing on in a window
  at the top right. Up held
  brings up History, Right held the programme's description, Down held Search. Back goes to the TV guide with the
  channel in its picture; Back held stops the picture and goes Home. What is up goes after 10 seconds with no key. A
  stream that drops is tried on the same channel in another playlist, then again. Where the provider keeps the
  programme on now in its archive (or the stream itself reaches back to its start), the Channel pill's options have
  Watch from the start, as has the remote's rewind key.
- **History:** Up held while watching, or History among the overlay's settings: over the dimmed picture, the
  programmes watched on this TV in the last seven days, day by day, each with its times, the time it was watched and the
  channel's logo and name (a programme counts once it has been watched for 10 seconds, and once however often). OK on
  one on now plays its channel; on one the archive keeps, plays it from its start; one that has ended with no archive
  to play it from is left out. Right goes to an entry's delete button, and the bin at the top right clears them all
  after asking. Kept on this TV only.
- **Subtitles:** over full-screen live TV and catch-up only (not the small pictures on Home and in the guide), for
  every channel: Off (as it starts), From the channel (the subtitles a channel sends: DVB subtitles where it puts them,
  its subtitle playlists, captions) or Automatic, made on the TV from what's said as it's said, a channel's own showing
  instead when it plainly sends some (a bare captions track every stream is given doesn't count). Chosen from the
  player's settings (Subtitles, the one chosen ticked, with what the channel sends) or Settings, Live TV, with their
  look (white with an outline, white on a black box, yellow) and size. The words keep about 40 letters to a line near the
  foot, move up above the info card and the overlay while they are up, and go while the channel lists and the live
  screen's rows are up. Automatic uses
  the TV's own on-device speech recognition where it has it (Android 13 and later); where it hasn't, it asks first
  ("a one-time download of about 40 MB, about 70 MB on the TV") and only then fetches Vosk's small English model into
  the app's own storage, its progress where the words go. The sound is heard on the TV, by a copy taken in the
  player's sound chain; nothing heard leaves the TV. A channel's own tracks stay off while nothing shows them.
- **Reminders:** in the guide, OK (or OK held) on a programme to come gives Remind (Cancel reminder once one is set), and
  a bell on its cell and "Reminder set" by its times. A minute before it starts, a panel comes up at the top right over
  whatever is on the TV, other apps too, 800 by 450 in the 07 look: the programme's own picture filling it (its faces
  whole, clear of the words; the channel's logo on its colour where it has none), Homefield's mark with a bell and
  "Reminder", the countdown in an amber pill ("Starts in 2:45", then "Starting now"), the channel's logo on its tile, the
  title, its time and channel, the message ("Your reminder: … starts at 20:00 on …") and three buttons: Watch (OK), Remind
  again in 5 min, Dismiss (Back); Left and Right go between them. It takes the remote after a second and a half; any
  other key lets it go, and it goes by itself after about 30 seconds (an amber line along its foot runs out). Over
  other apps where Android lets Homefield TV (Display over other apps, asked for once, when the first reminder is set,
  with a way to Android's page); otherwise a notification that asks to come up as the same card. Its alarm is exact
  where Android allows it and never wakes the TV: a TV that is off stays off, and a card is shown only up to a few
  minutes after the start. Reminders are kept on the TV and made again after a restart and an update.
- **Catch-up:** a programme from the provider's archive: an Xtream Codes login's streams marked as kept (for the days
  the server says, its times in the server's own time zone; the older timeshift.php address when the newer one
  doesn't answer), and an M3U playlist's catchup, catchup-days and catchup-source (default, append, shift, Flussonic
  and Xtream; {utc}, {start}, {end}, {duration}, {offset}, {lutc}, {Y}{m}{d}{H}{M}{S} and the like filled in; also the
  older timeshift and tvg-rec), or an Xtream server's M3U address given its archive by the player API. It starts with
  the info card at the foot, the archive's mark and the programme's whole length in it. Left and Right on the picture go
  back and on at once: 10 seconds a press (Settings › VOD › Skip steps), then 30 seconds, a minute and three minutes as
  the key is held. The seek bar comes up by itself at the foot: the line across the programme (the accent up to the spot,
  light grey on to what has been on so far, a small mark where the picture still is), a white bubble over the knob
  ("21:14 · 34:10 of 1:00:00": the clock then, the time into the programme and its length; "Live" at the live edge), the
  programme's name, channel and times under it and how far it has moved ("−1:30"). The picture follows a moment after
  the remote rests, with one request to the archive however many presses (OK jumps at once, Back stays where the
  picture was); past the programme's start the bar is the programme before's, and on to the live edge it is live. The
  rewind and fast forward keys move the same way, play/pause pauses, previous and next go to the programme before and the
  next one. While the bar shows, Up and Down bring up the overlay; on the plain picture they change channel, live. OK
  brings up the overlay: the card at the top; across the foot the same line; the time ("34:10 / 1:00:00") at the left;
  the buttons in the middle with the remote on pause (the programme before, back 10 seconds, pause, on 30 seconds, the
  next programme or live; the ones that move add up press by press); at the right, in a pill, the programme from its
  start and LIVE. Up goes to the line itself (Left and Right move it the same way, OK jumps), Down brings up the row of
  tiles in the buttons' place and Up takes them away. CH+ and CH− bring up a channel's card; OK held or MENU the player's
  settings (their pills by themselves). Back closes the bar or the overlay, then goes to the TV guide. It goes on into the
  next programme when one ends. An Xtream archive is asked for from the minute and its picture moved on to the second
  where the stream can be moved in, and a move inside what was asked for is a move in the stream, nothing asked again.
  The archive's address is made on the TV from the channel's own, and like it is never shown or written out. Channels
  with no archive don't offer any of it.
- **Sports:** Sports is a page under the tab bar (its Sports tab), in 07's look over the blurred picture of what the
  remote is on: its title with its sections as pills beside it (Live, Today, Tomorrow, Yesterday, Tables, F1 &amp; more;
  Live what is on now, "No football is live right now. Today's games are under Today." when nothing is; Tomorrow and
  Yesterday the football in the competitions followed that day, "No football tomorrow in the competitions followed."
  when there is none) (Up from the top of a list goes to them, Left and
  Right or OK pick one, Down comes back, Up again goes to the tab bar; Back at a section's root goes to the tab bar
  too). Today lists today's live and coming games (football, NFL, NBA, tennis, UFC, F1) under LIVE NOW, LATER and
  FINISHED with their counts, a row each washed at its ends in its two sides' colours (the away side's second colour
  when the two are too close), the competition in small capitals, both crests and names, the score in boxes of each
  side's colour (or the kick-off) and how far on; the remote's row white and lifted. Beside it the game the remote is
  on, big: its top split on the slant between the two sides' colours, the competition and LIVE, both crests, the score
  huge with the clock (a red card when one was shown), the scorers and red cards so far, the family's own channels that
  show it (English first, with the programme on), whether the hub is in step with the channel being watched and its
  delay, and what OK does. Tables has the leagues on glass down the left (Premier League, Champions League,
  Championship, Scottish Premiership, LaLiga, Serie A, Bundesliga, Ligue 1, Europa League, Conference League) and the
  one the remote is on as its table on glass beside them (each club's place with ESPN's zone mark, crest, name, played,
  won, drawn, lost, goal difference and points; Right goes into it); Formula 1 has the drivers in championship order
  (formula1.com's picture of each in a round frame, team and wins, points), the one the remote is on beside them on a
  card in his team's colour, standing, with his place, points, wins and podiums and the next Grand Prix. A game's page
  (a "‹ Sports · Today" line where Back goes) has the two sides on its header in their colours, the score and clock or
  "Full time" with the crowd, what's happened, the stats, both line-ups, momentum and Watch on buttons (English
  channels first), all held back to match the channel being watched.
  OK on a club opens the club's page: Right from a football game in Today goes on to its two clubs, Up from the top of a
  game's page to its two sides, and OK on a table's row; OK on a driver opens his page. Over a match in the player, the
  scoreboard: the game found from the channel and its guide, its clock set at kick-off with OK, alerts beside it, the
  kick-off and after-goal pop-ups and the line-ups, and a cover for the channel's logo. Their menu is the TV launcher's
  over TiviMate, as the family asked ("hold down ok when I'm in full screen on the homefield tv app and the scoreboard pop
  up settings menu comes up ..., and then I can turn the scoreboard and logo on and off just like it is on tivimate"):
  hold OK while a channel plays full screen with nothing over it, on every channel, over a match or not (with none, what
  is set is kept for the next one: "No match on this channel now", "Reading the match", "Reading the scores"). Nowhere
  else: with the info card, the overlay or another panel up OK held is the player's settings (as MENU), and in the guide
  and catch-up it does what it did. A panel at the bottom centre in the launcher's dark blue, "Scoreboard and logo · Up and Down, Left and
  Right, OK · Back to close" at its top, its lines in the launcher's "Label:  ‹  Value  ›": Scoreboard (off, as on
  TiviMate: the board, what slides out beside it and its pop-ups away until it is on again, the game followed
  underneath), Logo's cover, Scoreboard look, Move and resize the scoreboard and the logo's cover (Left and Right the
  size, OK to move it with the arrows), Line-ups on screen (OK shows them now), My scoreboards (OK: the editor, Generate,
  Ask AI), New scoreboard, Timing on this channel, A channel not lined up yet, It's just happened, Ask at each kick-off,
  Alerts beside the board, VAR explained, Kick-off and goal pop-ups, Goal sound, Swishes and Back to the standard places
  (this channel or every channel); at its right the logo's cover as it will look on a pitch's green, with the board's
  looks either side of it under it. Up and Down along, Left and Right change, OK does it, Back closes it, and it closes
  itself after 15 seconds without a key or as anything comes over the picture. It is the only place for these settings
  (Settings has no Scoreboard page). Settings, Remote control, Player, Long OK can choose the player's settings or
  another instead. My scoreboards: the family's own boards (a BoardSpec, the same on
  the phone and the TV launcher), drawn by the board in its base look, its pop-outs and the kick-off strap in its
  colours; in the scoreboard and logo menu My scoreboards (Left and Right: the look as chosen or one of them; OK: the
  editor on the one in use) and New scoreboard. The editor sits down the right over the
  match, the board as it stands on a still of a made-up match at its top and the board over the match changing with it:
  which board, Rename, the layout, each colour (the swatches with the two clubs' colours first, hue, saturation and
  brightness, its hex), finish, typeface, crests, corners, border, the clubs' strips, accent line, how it reads (OK
  mends it), Generate (designed boards for the match or a mood, Left and Right, the one the remote is on shown on the
  board, OK starts from it, Up for more), Ask AI (quick asks or the family's own words, typed on the keyboard at the
  left of the picture; Claude's design taken in), Save, Save and use, Duplicate, Delete (OK twice: everywhere), Undo and
  Use the look instead. Saved boards are the family's "myboard" items, in this app's sync store, so a board saved here
  is on the phone and the other TVs after the next sync with the launcher on this TV, and theirs are here. Half time and
  full time come as the whistle reaches the picture, by the launcher's and the phone's one rule (Whistle, from the
  data: ESPN's flip at each read, FotMob's own length of each half to the second, the minutes added), never before
  45:00 or 90:00 and, on the channel's own clock set at kick-off, never before the minutes added are up; the clock
  stops there rather than running on. "Checking VAR" goes at its decision, the commentary's next line, FotMob no longer
  flagging the check, another event of the game or the score changing, 100 seconds at most (VarCheck); never under 8
  seconds. Added time is one red on every look (AddedTime.RED: Card's middle, the clock, the +N tab, the ADDED TIME
  pop-out), a custom board's own when it sets one. The kick-off, restart and after-goal pop-ups fold into the board where
  the family put it on this channel (Move and resize: its place and size), whether the board was up or away, and the
  board is back there after them, never in the standard corner. The scoreboard and the logo cover show only while the channel
  plays full screen with nothing over it: the info card, the OK overlay, the bar, the channel list, a number being typed
  or any screen or panel over the picture (the guide's preview, Home, Settings) takes them away at once, and
  they come back with plain playback; their pop-ups wait for it, or are let go when stale (a kick-off after two minutes,
  a goal's after ninety seconds), and after a longer time away the board catches up without popping up what it missed.
- **A club's page:** in 07, under the shell's "‹ back" line: the club's own colour behind (ESPN's, made deep enough for
  white writing; for a white or black club FotMob's, else ESPN's second colour), brightest a little above the middle
  and deepening to the edges, with a fine grain and a soft vignette; the crest and the Players and Management pills at
  the left, the lines' pills (All, Goalkeepers, Defenders, Midfielders, Forwards) at the right, white when on and glass
  in the club's colour when off; then the squad as a carousel of frosted-glass cards: the one the remote is on in the
  middle, larger and brighter, two smaller and fainter each side, each with his number in the club's coin at its top
  corner and his name at its foot; the middle one's line written down beside it and his surname huge under it, rising
  in as the remote moves. Every picture is a real one, the best there is, in the phone's order: his league's own cut-out
  (the Premier League's for a Premier League club, La Liga's for a La Liga club: head to waist in the club's kit, found
  by the club's and his names as the phone finds them), else ESPN's head and shoulders, both rising out of the card's top;
  else FotMob's face, as a portrait filling the card's top, cut to its shape; with no picture at all, the club's crest
  large and his initials. No shirt, head or figure is ever drawn in a picture's place. Left and Right glide the row a
  player on 07's ease (held, it glides on); Up goes to the pills, where Left and Right or OK pick one and Down comes
  back; OK on a card opens the player's page; Back closes. Management shows the manager and any staff FotMob lists, as
  cards in the same row. The squad is FotMob's (the club found once by its name in FotMob's search, its id then kept on
  this TV), with ESPN's roster read beside it for its players' head and shoulders, else ESPN's roster alone for the
  competition the club was met in (it names no manager: ESPN's list for a club is its managers of years gone by); the
  league's pictures are looked up as the cards come near.
- **A player's page:** in the same look, under a "‹ back" line with the club's name: his card of glass with his real
  picture rising out of it (or his face as a portrait in it, or the crest and his initials) and his number in the
  club's coin at its shoulder, his first name and surname huge, his position, his country with its flag and his age, a
  rule, what the figures are for, then this season's figures from FotMob for his main competition on glass tiles four
  across (appearances, goals, assists, clean sheets for a goalkeeper, yellow and red cards, minutes, rating; a
  manager's matches, won, drawn and lost at the club), each left out when the source has none. Round arrows at the sides
  when there is someone that way: Left and Right go to the previous and next of the squad as the club's page showed
  them (the same pills), sliding across; Back goes back, the club's row on him. His figures are one read of FotMob once
  the remote rests on him; the players either side are read after him.
- **An F1 driver's page:** in 07, under a "‹ Sports · Formula 1" line: the page dark with his team's colour glowing
  from its left, a big rounded card in that colour, his car number huge, italic and faded behind him, and the driver
  standing in his kit over the card's left half, rising above its top (formula1.com's picture, from the same family of
  addresses as the team logos; else ESPN's head and shoulders; with no picture of him at all, his team's logo large with
  his initials, never a drawn figure). At the right the next Grand Prix, its days, name and circuit with a countdown in
  days, hours and minutes on glass tiles, then his team's name very large with its logo on a glass tile, his number, his
  country's flag and his full name, his nationality and age (no source gives a place of birth), and his team's car from
  the side breaking out past the card's edge (formula1.com's, else a car drawn in the team's colour). Down the left his
  season so far under its year: Grands Prix and podiums (one read of his results this season, Jolpica's) and points
  (the championship's). Left and Right go to the driver before or after him in the championship, the page sliding across
  and its colour turning to the next team's.
- **Moving about:** on the dark screens the picture behind cross-fades as the remote moves to another title or
  channel, the words fade in and rise into place a moment after, pages cross-fade into each other, and the poster or
  tile the remote is on grows a little inside a soft white ring. Opacity, a small move and scale only, nothing
  blurred, and each picture read once at the size it is shown, so it stays light on a TV with 1 GB of memory.
- **Movies and TV shows:** the top bar (Home, TV shows, Movies, Recently added, Search and My list, with Homefield's
  mark in the middle and the clock at the right; Up from the rows goes to it), then the picture of the title the remote
  is on across the page in that picture's colour, under its TMDB logo (or its name in big lettering), its facts (rating,
  years, age, seasons or running time, 4K, Dolby Vision, Atmos, TorBox's cloud) and its story. Under it rows of
  artwork: Continue watching (wide pictures with the episode, the time left and a red bar), My list, Recently added,
  Your TorBox, then every category, each ending in "All 1,234" where it has more than the row shows, which opens all
  of them as a wall of posters with a sort (as in the playlist, by name, newest first or best rated). The poster the remote is on grows with a white edge;
  the rows glide up and along, and the picture and its words fade to the next as the remote moves.

- **Posters**, in Search and My list: the poster with
  rounded corners and a caption strip of the panel's colour under it with the title and year, cut short with "..", the
  rating in an accent chip at the poster's foot; "New" on the top left for a title the playlist added in the last
  fortnight, the best picture the family's playlists have of it on the top right (4K, 1080p or 720p in a white chip,
  with the Dolby Vision or Atmos mark where a name says so), TorBox's green cloud on one of the family's own TorBox
  titles (without a TMDB poster, a card in a colour of its own with its title), and how far through it is along the
  poster's foot. Where the remote is, the strip takes the focus colour and a thin light edge goes round the card.
  Settings, Appearance, Posters: Landscape makes every card 16:9, with the title's wide backdrop (TMDB's at w780, the
  poster where there is none) and the same strip under it.
- **A title:** its picture across the whole page, the page in its colour (a series' changing with the season), the
  title's TMDB logo (or its name in big lettering), a line of facts (★ rating, the years it ran, its age, its seasons
  or running time, the picture and sound marks, TorBox's cloud), then the red Play pill (Resume, or Resume S2 E4, and
  From the start where it was left part-way) and grey buttons: My List (a tick once on it), a film's trailer and, once
  something was started, taking it off Continue watching. A series then has its seasons and episodes as the family's
  recording of a series page has them: the season tabs and the first episodes peek in under the buttons, and Down from
  there the page rises to the series section, dark, the picture behind faded right down: the show's title art (TMDB's
  logo, or its name in big lettering) in the middle at the top, the season tabs under it ("Season 1  Season 2  Season
  3", the season in view bold and white, a white pill behind it while the remote is on the tabs), then ONE row of every
  season's episodes, each a 16:9 still with rounded corners (a thin white line on it as far as it was watched, UP NEXT on
  the one to carry on with) and under it "S2 E1" small and quiet, its name, two lines of its story and "58m  16 Jun
  2024". The episode the remote is on lifts and its words sit on a frosted rounded panel; Left and Right go along the
  episodes and the tab changes by itself as the row goes into the next season, or along the tabs and the row jumps to
  that season's first episode; OK on an episode plays it. Under them a Videos row: TMDB's trailers, teasers and extras on
  YouTube as 16:9 stills with their kind on a small badge at the top left (OK opens one in the TV's YouTube app). Down
  from there the page rises again: Overview and Genres side by side, the cast as round photos with the parts they play
  (OK searches for someone), and More like this, the poster the remote is on lifted. TMDB's logo, cast photos, seasons'
  posters and videos come with the key the TV launcher hands over (kept in memory only), and are kept on this TV for a
  fortnight.

- **The cover (Play):** Play on a film's page (and From the start, and an episode chosen) opens its cover, the
  family's cover B: on the left the poster (an episode's show's), popping in from a little smaller with a slight
  overshoot and its glow blooming after it; on the right, sliding in one after another, the title as its own logo
  (TMDB's, in the family's language, else English, else one with no words; the title in words while it comes or
  without one; an episode's show's logo with "S2 E4 · the episode's name" under it), the rating, year, length and
  certificate, the story (an episode's own), the cast with their photos (or initials on a tint), and PLAY FROM: every
  source, every TorBox file of it (none left out), each labelled from its name: TorBox's green tile with its cloud or the
  playlists' blue one with a television, the picture (4K, 1080p…), where it is from, HDR or Dolby Vision (or SDR), the
  sound (TrueHD Atmos, DTS-HD MA 7.1, DD+ 5.1, AAC Stereo…), the codec (HEVC, AV1, H.264), the release (REMUX, BluRay,
  WEB-DL, WEBRip), its size, a series pack's episode file under it. The best is first, marked BEST, with the remote on
  it (white); a long list scrolls. OK plays the one chosen from where it was left (the rest stay its backups, for
  Sources while it plays); Back goes back to the page. The page's ground is the colour theme's (Settings › Colour
  theme), with the poster's own colour glowing over it, kept dark enough for the words to read; on the Lite motion
  tier it simply fades in. Resume and Continue watching carry on straight away from the copy it was started on (the
  others after it), and the next episode starts straight away from its best copy, as on the phone.
- **Player:** films and episodes in the 07 look of the live player ("improve the player whilst watching movies and TV
  shows to have all the features you'd expect and you can choose different sources, subtitles with open subtitles
  support"). OK brings up the overlay: the title (and S1 E3 · the episode's name, or a film's year, length and kind) and
  the clock at the top; at the foot where it plays from (TorBox's green mark or the playlist's blue one, chips for its
  picture, HDR and sound, "1 of 3 sources"), the seek line with the time gone, the length and when it ends ("ends
  22:41"), the transport (◀◀ ⏯ ▶▶, a series' |◀ and ▶| either side; the remote on play) and a row of buttons under it:
  Sources, Subtitles, Audio, Episodes (a series), Speed, Picture and Info, each with what it is set to. Up from the
  transport goes to the seek line (Left and Right move the spot with a bubble saying where and how far, OK jumps there),
  Down to the buttons; the overlay goes after a few seconds untouched while it plays. On the plain picture Left and
  Right go back and on at once with the scrubber and its bubble (Settings › Other › VOD › Skip steps a press, further
  the longer the key is held), Up lists the sources and Down goes to the buttons; play/pause, rewind, fast forward,
  next and previous episode, stop, CC (Subtitles), the audio key (Audio), INFO (Info) and MENU (the buttons) work as
  they say. Back closes what is open, then stops and keeps the place for Resume and Continue watching.
- **Sources (the player's):** every copy, the best first, tried in turn: the family's own TorBox files of the film or
  episode first (all of them found before anything plays, unless Settings put them last), then the playlists' copies,
  each with its mark, the logos of its picture, HDR and sound, its file's name and size, the one playing ticked; another
  chosen carries on from the same moment. A copy that stops while it plays gives way to the next from the moment it had
  reached, with a short note at the top.
- **Subtitles (films and episodes):** Off, the copy's own tracks (by language and name, SDH and forced marked; a forced
  one in the family's languages still shows while Off), or OpenSubtitles: Find on OpenSubtitles searches by the title's
  IMDb or TMDB id (the provider's, else TMDB's with the launcher's key; its name and year when neither is known), the
  season and episode for an episode, in the family's languages, and lists the results with their language, release,
  hearing-impaired mark and downloads; OK downloads one (off the main thread, with timeouts), reads it (SubRip or
  WebVTT, italics, bold and colours kept) and shows it with the app's own subtitle renderer, clear of the overlay and
  the pickers. The timing moves half a second a press; the look is its size, colour (white, yellow, light blue) and
  background (an outline or a black box), with a sample line while it is chosen. The choice is kept for the title: a
  film plays with the same file and timing next time, a series' next episode finds the same language by itself.
  Settings › Other › Subtitles has the OpenSubtitles API key, username and password (typed on the on-screen keyboard,
  the key and password only ever as dots, kept in the app's own storage: never synced, backed up or logged), Check the
  login, the languages (English first), hearing impaired first, and the look. The TV launcher keeps no OpenSubtitles
  login, so it is typed here once.
- **Audio, Speed, Picture, Info:** the copy's sound tracks (language, channels, codec) and the sound's delay (50 ms a
  press, as the live player's Audio offset); 0.75× to 2× for the sitting; Fit, Fill, Zoom, 16:9, 4:3 and the rest of
  the live player's Display mode, one for every film; and the title's year, length, rating, age, kind, story, cast and
  director (an episode's own too).
- **Episodes and Up next:** a series' Episodes lists every season as pills and its episodes as rows with their
  pictures, length and day, the watched ones ticked, one part-watched with its bar, the one playing ticked; OK plays
  another from where it was left (its start if new). As an episode's credits come (a fortieth of it, 25 seconds to a
  minute before its end) Up next shows the next one's picture, name and a ten-second countdown (Settings › Other › VOD
  › Autoplay next episode; without it the card waits for OK): OK plays it now, Back puts it away and the episode plays
  on to its end. The next one starts from its best copy and goes on Continue watching.
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
- **Search:** from the menu (or Down held while watching): the microphone at the top left (voice search, where the TV
  has it), the search field, and search's settings at the top right. Under them the searches made before as chips in
  two columns, with a bin to clear them; OK on one searches it again. The keyboard floats at the bottom right while
  typing, its strip suggesting names found, and what is typed is looked for as it is typed (once the typing rests for
  180 ms, off the main thread): Movies, Channels and TV shows, each a row with the playlists it was found in as chips
  (Your TorBox for the family's own titles), films and shows as posters, channels as tiles with their logo, name and
  what's on. The results are dimmed while the remote is on the keyboard or the field. Back closes the keyboard, the
  remote left on the field; Down goes on to the first row's playlists, then its results; OK on a channel watches it, on
  a title opens its page. Settings, Other, Search: Show search history and Show favorite channels first.
- **Recordings:** from the menu: the recordings by day, newest first, as History lists programmes (the programme, its
  day and times, Scheduled, Recording, how many days it is kept, or Gone, and the channel), the one the remote is on
  described in a card at the right, and "Recordings" at the top right with its settings' button. OK plays one that has
  begun; Right, then OK on the bin, deletes one. The family's provider allows one stream at a time and Homefield TV
  writes no video to the TV, so a recording is a programme kept from its channel's catch-up: chosen in the TV guide, it
  plays from the provider's archive, from a few minutes before it to a few minutes after (Settings, Other, Recording),
  for as long as the provider keeps it. A channel without catch-up can't be recorded, and the screen says so. Empty:
  the record mark, "No recordings" and how to make one.
- **My list:** from the menu: its four lists down a column (My TV programs, My reminders, My movies, My TV shows), the
  one the remote is on beside it at once: the programmes added to My list from the TV guide each time they are on now
  or to come, soonest first (OK on one on now watches it); the reminders set, soonest first (the bin cancels one); the
  films and series on My list as posters under their words. An empty list shows the bookmark and "No programs" (or
  reminders, movies, TV shows).
- **Settings:** a panel down the right over the screen it was opened from, that screen dimmed, with the sections
  General, Playlists, EPG, Appearance, Channels & categories, Playback, Remote control, Parental controls, Other and
  About. OK opens one as a
  page that slides in from the right with its name in the panel's band; Back goes back a page, then closes the panel.
  Each row has its value under its name, a switch at the right where it is on or off, and section names are in the
  accent; a choice opens its list with the one in use ticked; a field is typed on a keyboard over a sheet risen from
  the foot, a password only ever as dots.
  General: starting when the TV starts; Start on: Last channel as it comes (the channel watched last plays full screen
  with its card as Homefield TV opens, also when it is opened again from the TV's Home or come back to after a while,
  not only when it starts afresh; never a locked channel, a programme from catch-up or a film), Home, the TV guide,
  Movies or TV shows (when it starts afresh); Back up data (settings, playlists, favourites, recent channels, reminders and My list saved to a
  file on the TV and in Downloads; never the TorBox key, and nothing sent over the network) and Restore data (this TV's
  backup or a file picked); Sync (Sync now and when it last synced, and a switch for each kind: playlists, favourites
  and recents, Continue watching, settings, passed on by the launcher on this TV to the family's phones, tablets and
  other TVs; TorBox's key stays on each device); and Remove everything (from this TV only).
  Playlists: each playlist with its expiry date, opening on its own page (its name, its address or server and login,
  the password as dots, its own user agent, using it alone or all of them together, removing it); TorBox (on or off, its
  copies first or last, the API key typed as dots, Check with TorBox: how many films and episodes it has ready, or
  TorBox's word why not, and Forget); adding an M3U playlist or an Xtream Codes login; Update all playlists and how
  often; and catch-up ("Catch-up: 120 channels keep programmes for up to 7 days", or "Your provider keeps no catch-up
  for these channels").
  EPG: its sources (the playlists' own guides, then more XMLTV guides that fill in what they leave out), a time shift
  for a guide whose times are out, how often it is read again and reading it as the app starts, Update EPG, Clear EPG,
  and the latest update ("EPG is successfully updated for 16436 channels on 17/11/2026 08:34").
  Appearance: TV guide (Channels sorting: By order in playlist as it comes, By name, By number or Manual, a category
  able to have its own; One row per channel; channel numbers, the guide's rows, progress bars; Hidden channels and
  Manage categories, as in Channels & categories); Player (the clock, 24-hour as it comes, 12-hour or off; how long the
  panels stay; live TV's channel tiles); Categories (how they are sorted); Logos (Logos priority: New logos (Homefield) as it comes,
  or Playlist's logos; Refresh logos, with how many logos the list has and from when; and Logo box colour: Logo's
  colour as it comes, None, Black tile, or one colour of the family's choosing for every box (Forest green, Midnight
  blue, Slate grey, Deep red, Plum, Teal, Charcoal; a logo too dark for it gets a soft glow)); Font size; Color theme: a primary
  colour (Indigo as it comes, Blue, Deep purple, Purple, Teal, Green,
  Red, Brown, Blue grey, Grey or Black) with an accent (Blue as it comes, Red, Pink, Purple, Indigo, Cyan, Teal, Green,
  Lime, Yellow, Amber, Orange or Deep orange), each a disc, the pair in use ticked and named ("Indigo • Blue"); OK keeps
  one and the screens start again in it, back on Settings. The themes from before are gone: a TV that had one comes up
  in Indigo • Blue. Then Home style (Editorial as it comes, or Classic), Home (Trailers: a title's trailer in the poster
  the remote rests on, On as it comes; their Sound, Off as it comes), Posters (Portrait / Landscape), Programme times (24-hour as it comes, or 12-hour) and
  Animations: Automatic (as it comes), Full or Lite. Lite keeps the look and the motion (cards lift, rows slide,
  pictures cross-fade) but leaves out what a slower TV's graphics chip finds hardest: the cards' tilt and the light
  sweeping across them, a lifted card's picture drifting under its frame, and the page's big blurred picture changing
  while the remote moves (its colour cross-fades instead, the picture comes in once the remote rests, and the frosted
  panels take that colour meanwhile, then are frosted glass over the picture again once it is in: 3.6 mended the guide's
  information panel staying on a flat wash of that colour); the widest programme cells fade their picture into its own colour rather than a
  blur of it. Automatic is Lite on a Mali-G52-class chip (the TCL), under 3 GB of memory, or when the first seconds of
  moving measure over 18 ms a frame (once a build); else Full (the Google TV Streamer). The page says which it chose and
  why.
  Channels & categories: One row per channel (merge qualities), on as it comes; Hidden channels (each hidden channel a
  line, a channel in several qualities hidden whole one line whose OK brings every version back, a version hidden by
  itself its own line; Show all channels); Manage categories (Show all categories, Hide all categories and each
  category's page: shown or hidden, out of the guide, Live TV, channel up and down and Search, favourites staying;
  moved, renamed and its Sort channels; hidden categories stay hidden when the playlists are read again); Rename
  channels (the channels renamed, then each category's channels with their logos, the playlist's name under one the
  family renamed; OK types a new name, nothing typed gives the playlist's back, kept when the playlists are read again);
  Merged channels (each merge made in the guide, OK to separate it, Separate all); and Channels sorting.
  Playback: the buffer for live TV and for films, the decoders, matching the TV's frame rate and tunnelled playback;
  Picture: one Picture quality (Adaptive, which stays on the channel picked and uses its own copies as seamless quality
  levels, or a fixed 4K, 1080p, 720p, SD or Data saver) and the picture's shape for channels and for films (Normal,
  Full, Zoom, Zoom - 2.35:1, Reduced, 4:3, 16:9, 2.35:1; a channel can keep its own); Sound: passed through or decoded,
  two speakers, languages and every channel's sound delay; Subtitles on live TV (Off, From the channel or Automatic),
  their look and size, and where the TV needs it the English speech model (downloaded from there on OK, with how far
  it has come, or "On this TV"); Stream problems: reconnecting and the provider's adaptive stream. MENU while
  watching brings up the player's settings instead: their Display, Audio offset and Sound change the shape, the delay
  and the track for the channel, and their Settings opens this panel.
  Remote control, Player: for live TV full screen, Up and Down, the channel keys' way round, OK and OK held (as it comes
  the scoreboard and logo menu, in plain playback only; once, OK held kept as the player's menu from before became the
  scoreboard and logo menu), how long
  the number keys wait, the channel before, and the colour keys.
  Parental controls: a switch for a four-number PIN, asked before a locked category's or channel's programme plays
  (channels locked from Channel options on the player's bar), locked channels shown or hidden, and Settings locked too.
  Other: Search (Show search history, Show favorite channels first), Reminders (the ones set, soonest first, OK
  cancelling one, and whether their card may pop up over other apps), Recording (minutes before a programme starts and
  after it ends), VOD (Autoplay next episode; Skip steps: 5, 10 as it comes, 15, 20 or 30 seconds or a minute a press of
  Left or Right in a film, an episode or catch-up) and Ask AI (the family's own Anthropic API key, typed once on the keyboard as dots, then
  shown masked, "sk-ant-••••••••abcd", kept only in the app's private preferences: never synced, backed up, logged or
  shown; Take the key away; the model: Claude Opus 5.5 as it comes, Sonnet 5.5 or Haiku 5.5; the requests go only to
  Anthropic's API, one at a time off the main thread. The phone's "Send key to the TVs" reaches the TV launcher: with no
  key of its own, Homefield TV takes it from the launcher on this TV as the page opens, and says so, or with Get the key
  from the TV launcher (its provider hands it to Homefield TV alone, signed with the launcher's key; the launcher 5.41
  or later); after Take the key away only when asked). The scoreboard's and the logo's settings
  are all in their menu over the player (hold OK while a channel plays full screen); the three switches kept before
  (Scoreboard, Logo cover, Logo cover only) became its two, once, each showing what it showed. The film player's Picture and sound has Sound (The film's sound, as it comes, or a
  track) and Subtitles (Off, or the copy's own; "This copy has no subtitles of its own").
  About: the version; Logo packs' licences (the catalogue's packs and their terms); If something goes wrong (the last problem noted and the ones before it, each with when and where
  in the app's own code: "Crash in …", "Out of memory in …", "Froze for 7 s in …", "Trouble in the background (the app
  carried on)"; Clear these notes; take a photo of them first to send; after a crash, out of memory or a freeze, "Homefield
  TV had a problem last time. Settings › About has a note of it." once as the app starts); and Updates: Check for
  updates (it also looks quietly as the app starts) and when it last looked; Automatic updates (Off as it comes: updates
  only from a computer; or Every time it opens: the Update address, an update.json in the same form with where its APK
  is, looked at as the app opens, the APK streamed to the update folder and checked as below, handed to Android at once
  where it lets Homefield TV install; what stood in the way in the family's words, and how much room the TV has); when a newer Homefield TV has been put on this TV (update.json and the APK in the app's own folder,
  files/update/), its name, what's new and Update now. The APK is checked first (its size, its SHA-256, that it is
  Homefield TV, newer, and signed with the same key), then handed to Android's installer, which asks before it updates;
  the first time, Android asks to let Homefield TV install apps. A dot on Settings shows one is waiting, and What's new
  shows once after an update, as a card over the screen.
- **From the phone:** while Homefield TV is open (or playing), the family's phone can send it the channel it is
  watching (its Live TV's cast button). The TV makes itself known on the home network (`_homefieldtv._tcp`, with its
  name), listens on port 47830 when it is free (the phone tries it when the announcement doesn't reach it) and answers
  only addresses on that network: the channel is played only when this TV's own playlists have it (by its stream id,
  else its guide id, else its tidied name), with "Playing … from …" for a moment, and the phone's Stop lets go of it
  (back to Home). The phone's stream address and login are never used.
- **From the TV launcher:** with the launcher's dock on Homefield TV, the row above it shows Homefield TV's own channel
  cards, as it shows TiviMate's channels on now: the channels watched last on this TV (newest first, one row per
  channel), the favourites, then the sport on now that the family can watch (live first), each with the programme's
  picture, title, times and how far through. OK on a card opens Homefield TV straight on that channel, full screen with
  its info card (`com.gathin.homefieldtv.action.PLAY_CHANNEL`, with the channel's key, its name and number as fallbacks:
  PlayRequest), whether Homefield TV was running or not; Home is kept out of sight until the channel plays, and a
  locked channel still asks for the PIN. The cards come from ChannelCardsProvider (`content://com.gathin.homefieldtv.cards/cards`),
  read only by an app signed with Homefield TV's key (the signature permission `READ_CHANNEL_CARDS`, which both apps
  declare): what ChannelCards last made a moment after the channels, the guide, the favourites or the channels watched
  changed, kept in memory and in a small file (`files/channel_cards.json`), never the playlists. Locked and hidden
  channels are never on them.

## How smooth it is

Every screen is drawn to stay within 20 ms a frame on the TCL (50 a second, 60 where it can) while the remote moves:
pictures read once at the size drawn (opaque ones in 16-bit colour), one shader each kept; shadows are small blurred
sprites drawn as one nine-patch (their middle left out under an opaque box), never made while moving; a page's blurred
ground is one draw (its dim composed into it); parts that don't move (a title's page ground, the club pages' grain, the
tab bar's mark and clock, blocks of words) are kept and drawn in one call, faded as a whole; only what changes is drawn
again (the tab bar's strip, Settings' panel, a side menu's rows), and a page fades in over the one it leaves without a
second layer; the window's own ground isn't drawn under an opaque page or a full-screen channel. Every animation runs
on the display's clock, so a frame missed skips ahead rather than slowing down.

Kept open: an error on any thread but the main one (out of memory too) is noted and carried on through, the pictures
not on screen and the guide beyond the next hours let go when memory runs out; the main thread is watched, a freeze of
four seconds or more noted with where it was; the scoreboard's tick and drawing are guarded (a bad reading puts the board
away, never closes the app). Answers from the scores, TorBox and TMDB are read whole only up to a limit, sized at once; an
M3U's films and episodes go straight to their file as the playlist is read (an episode's are read from there when its
series' page opens); the catalogue's titles keep their id in their key and are looked up and searched from plain arrays,
and a refresh keeps every title whose row hasn't changed, so the family's 200,000 films and series are held once
(TheFamilysSizeTest: under 96 MB of the JVM's heap with 27,000 channels and a guide of 90,000 programmes, all under
120). A while into a channel full screen, half the pictures the screens under it hold are let go.

The memory log (Android/data/com.gathin.homefieldtv/files/memlog.txt) has headroom= (how much more the Java heap may take)
and problems= (out of memory, freezes and trouble in the background since the app started) on each minute's memory line,
and after it a line of the frame times of each screen used that minute (Android's own measure of every frame drawn):

```
2026-11-18 20:31 frames motion=auto-lite Home n=812 p50=11.2 p95=19.8 over20=3.1% gpu95=9.0 | Guide n=140 p50=13.0 p95=22.4 over20=7.9% gpu95=12.1
```

motion: the tier (auto-lite, auto-full, lite, full); per screen: frames drawn, their median and 95th percentile (ms),
the share over 20 ms, and the graphics chip's 95th percentile (Android 12 and later). `FrameCostTest` measures every
screen's frame of motion on the JVM (drawing calls, bytes made, fill in whole screens, layers, perspective) in both
tiers and keeps each within its budget.

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
| 1.9 | TorBox in Movies and TV shows: films and episodes already in the family's own TorBox account play as copies beside the playlists', every version of them, all found before anything plays and tried first (or last, as Settings say); nothing is searched for or added. The player's bar shows where it plays from (TorBox's green mark with a cloud, a playlist's blue one with a screen) and Up lists every copy to change source while watching, from the same moment. Settings, TorBox: on or off, first or last, the API key (typed as dots, or sent from a computer over adb), Check and Forget. Settings: start screen, start with the TV, clock and text size, guide rows, what OK, the colour keys and the number keys do. Sync: your playlists, favourite channels, categories, recents, Continue watching and shared settings follow you between your phones, tablets and this TV (needs the TV launcher 5.32); Remove everything resets this TV only. A reminder due while the TV's screen is off now waits and shows once it is on, while its programme has only just begun. |
| 1.10 | Your TorBox: the films and series in the family's own TorBox that no playlist has, as titles of their own, in a Your TorBox row on Home and first on Movies and TV shows, in their grids, with TorBox's green cloud on each poster. Their posters, stories and episode names from TMDB where it knows them (the key from the TV launcher 5.33, kept in memory only), drawn posters without. A series' episodes that only TorBox has join its row, each with the cloud. On a TV that shows 4K, TorBox's copies go 4K under 20 GB first, then 1080p, then the rest. Settings: the guide's and playlists' updates, a guide time shift and more XMLTV guides; live categories hidden, moved and renamed; parental control with a PIN for locked categories, channels and Settings; backup and restore, on this TV or from a file, without the TorBox key. |
| 1.11 | Every source shown before playing, the best first: Play lists every TorBox file and playlist copy of a film or episode, each with its mark, the logos of its picture, HDR and sound, its file's name and size; the best has the remote and OK plays it, the rest staying as its backups for Change source. One order everywhere, with no limit on size: 4K, then 1080p, 720p and the rest; Dolby Vision or HDR before SDR; Dolby Atmos or surround before stereo; then the bigger file; AVI and the like last; TorBox's before the playlists', which go the best first too. Resume and Continue watching carry on from the copy it was started on; the next episode starts from its best copy, as on the phone. |
| 1.12 | The dark look everywhere, from the first open: Home, Search, Settings and the player's bar now look like Live TV, the guide, films and TV shows, and the app opens on its dark ground. Home has the rail (where the remote starts, as on the old menu), the big picture of what the remote is on, the mini TV at the top right on a dark card with the channel and what's on, and rows laid out as Films' are: On now and Sport on now as the live screen's tiles, Continue watching, Your TorBox, New films and New TV shows as posters. Search and Settings have the rail, Settings its sections on a side panel and the keyboard on a dark sheet; What's new, Updates and the PIN pad are dark too. The colour themes work on every screen, and a new icon and TV banner carry Homefield's amber mark. No more freezes with very big playlists: the channel lists and their counts, hidden and locked channels, Home's rows, the guide's categories, channel up and down, search, and films and TV shows with tens of thousands of titles are worked out off the main thread or kept ready, tried with 50,000 channels and 40,000 titles. With 1.11's every source shown before playing, the best first. |
| 1.13 | TiviMate's look and ways, on Homefield's own screens, in the Indigo • Blue theme the family's TiviMate uses (TiviMate's other primary and accent colours in Settings › Appearance). The icon rail that opens into the labelled menu; Settings as the panel from the right, in TiviMate's sections; Roboto; times written "10:00 — 11:00" and a 24-hour clock. Watching: the info card (INFO, and CH+/− showing the next channel without tuning), Up and Down changing channel, Right for the last channel, Left for the channel list over the picture with its groups, OK for the overlay with its seek line and tiles (TV guide, History, recent channels), the bar of round buttons with its pickers (picture, audio, audio offset per channel, captions, display mode with three new shapes, sleep timer, favourites, scoreboard, channel options), History, the description card and catch-up's player; Back to the guide. The guide: the preview top left with the programme's details, the dated time ruler with the now-dot, rows with number, logo tile and name, neutral blocks, the menu pushing it right, groups, the OK popup and the long-OK card (Remind, Watch from start, Record, My list, Favourites, Catch-up, Block channel), and the new catch-up archive by day. Search with the mic, history and results as you type; Movies and TV shows with the category column, poster rows and grid, title and series pages; My list; Recordings kept from catch-up (one stream per account, so nothing is recorded live). New settings that work: search history, favourite channels first, autoplay next episode, update or clear the guide now, groups sorting. |
| 1.13.1 | The screen stays on while a channel or a programme from the archive plays (or starts, or reconnects): the TV's screensaver no longer comes over what is being watched. Paused, stopped or failed, the TV may sleep as before. |
| 1.14 | Faster, smoother and lighter. Scrolling glides everywhere (the guide's rows and times, lists, groups, the menu, Search), holding a button keeps it moving smoothly, and the guide draws without making anything per frame. When a channel buffers, the app no longer redraws or calls the system on every stall, and a failing stream waits longer between tries. Memory: the player's buffer is capped (it could hold about 138 MB), pictures stay within a 40 MB cache, and memory is freed when the TV runs low. The guide's details and the player's channel card show the programme's picture behind them, changing as you move; FPS shows on the channel card, the OK overlay and the guide, measured from the picture when the channel doesn't say. |
| 1.15 | Movies, TV shows and a title's page in the TV show card look: the title's picture across the page in its own colour, its logo (or big lettering), the red Play pill, rows of artwork with the remote's lifted; a series keeps the phone's seasons (one row of every episode, the season pill following it, the page's colour changing with the season), then Overview and Genres, the cast's faces and More like this. Home is one large card at a time (channels, Continue watching, films and shows) with a strip of episodes, next programmes or like titles; the picture cross-fades as the remote moves and the page takes its colour. Hold OK in the guide for TiviMate's channel menu (Favourites, Block, Hide, Rename, Change logo, Move channel) and, on a group, Rename, Hide, Move, Sort channels and Manage groups; Settings › Appearance › TV guide has Hidden channels and Manage groups. New channel logos (the tv-logo project's, matched by name; Logos priority and Refresh in Settings › Appearance › Logos). The scoreboard and logo cover show only over a channel playing full screen, never over the guide, a menu or a panel. |
| 1.16 | Sports has Today, Tables and Formula 1: OK on a club opens its page in the club's colour with the squad as a carousel (Players or Management, the line filters, Left/Right gliding; the photo rising above the card, the number coin, the surname large), OK on a player opens his card with this season's figures, and an F1 driver's page comes in the team's colour with the giant number, the standing driver, the car and the next Grand Prix's countdown, Left/Right through the championship. The kick-off, restart and second-half pop-up now comes in the scoreboard's own look and folds onto it ("In my scoreboard's look", the default; Classic and Off kept). Nothing on the board from the live data comes before the picture shows it: the minutes added, cards, subs and VAR explained wait the channel's delay (three minutes with none set), and every look shows added time by the same rule. |
| 2.0 | The whole app in the 07 look the family approved: Inter, a glass tab bar along the top (Search, Home, Guide, Movies, TV Shows, Sports, My List, Settings) with its pill sliding, rounded cards that lift and tilt, frosted panels. Home is one big card at a time with its strip; Movies, TV Shows and the title pages in the title's colour, the season pill over every episode; Search with its own keyboard and tonight's programmes; My List with Recordings and tonight's reminders. The guide is the family's grid in 07: each channel's cell in its logo's own colour (Behind logos: Logo's colour, None or Black tile), programme pictures fitted around faces with nothing dark over them, the frosted details panel and the rounded preview; Shelves is a second guide style. The channel playing has a clean info card and a mini guide in coloured rows; hold OK and Change logo sit beside the guide; catch-up has day pills; Settings has two panes. Sports, the match page, the club's squad carousel, player and manager cards and the F1 driver page in 07, the players only with their real pictures (the league's photo, ESPN's, or the face as a portrait; else the crest and initials). One row per channel: a channel's FHD, HD and SD are one row, its best played (the next if it doesn't answer), hold OK for its Quality; favourites, locks, names, logos, reminders, History and catch-up follow it. Settings has Channels & categories (one row per channel, hidden channels, categories, renaming channels), the family's word categories everywhere, and Rename channel always in a channel's menu. |
| 3.4 | Everything since 2.0 (programme pictures filling every cell, logo-only channel cells, one row per channel with Quality on hold OK, Channels & categories, the Lite motion tier) and: kept open while a channel and the scoreboard play (background errors noted, never closing the app; the 200,000-title catalogue and the guide held compactly); 3.3's features rebuilt (If something goes wrong notes, Automatic updates, Merge with, Logo box colour, More scoreboard settings, Logo cover only, Sports Live/Today/Tomorrow/Yesterday, Skip steps, the film's sound); Home, Movies and TV Shows with Debrid Stream's rows in the 07 look, the focused title growing into its backdrop (Home style Editorial, Classic kept); playing the last channel on opening; reminders on the programme's picture; catch-up's bar to move along; pictures searched wider; no dark line through programme pictures; the information panel popping up on Up/Down and down again after the time chosen. Signed with the new key: installed once over a removed copy. |
| 3.5 | From the TV launcher: Homefield TV's live TV cards on the launcher's dock (recently watched, favourites, sport on now, served by a provider only the family's launcher may read), and OK on one opening Homefield TV straight on that channel full screen (PLAY_CHANNEL), the PIN still asked for a locked one. |
| 3.6 | While watching, Up/Down bring up the information panel alone (no channel panel beside it, no "Watching" box); the guide's little preview without its "playing" label; the guide's information panel frosted over the picture again on the TCL (Lite); a channel joins Recently watched once it has played 10 seconds, Right still flipping to the channel before. Offered inside the app (Settings › Updates). |
| 3.7 | No LIVE badge or category line on the information panel while watching, nor a playlist or category line on the guide's panel: the title starts at the top, the words moved up, with three lines about the programme on the information panel and four on the guide's. |
| 3.8 | Change logo is a card with Found for it, Catalogue (TV Logos and iptv-org, searched by the channel's name), Make my own and Ask AI (Claude designs one; the family's own key, typed in Settings › Other › Ask AI). My scoreboards in the player: the hold-OK scoreboard menu's My scoreboards, Scoreboard editor and New scoreboard, an editor over the match (colours, finish, typeface, crests, corners, border, strips, accent line), Generate and Ask AI; saved boards synced with the phone. |
| 3.9 | The channel's name back under its logo on the information panel while watching (centred, no number). Offered inside the app (Settings › Updates). |
| 4.0 | A reminder's panel never cuts its words short: a long title takes up to three lines and the message two, the panel growing taller for them with its buttons below; a channel whose logo isn't there shows its name on the tile in two lines. Offered inside the app (Settings › Updates). |
| 4.1 | The scoreboard in the player as the launcher's and the phone's: half time and full time as soon as the whistle reaches the picture (the data's flip, FotMob's own length of the half, the minutes added), never before 45:00 or 90:00 or, on the channel's clock set at kick-off, the minutes added; Checking VAR goes when play goes on (its decision, the commentary's next line, FotMob's flag gone, another event, 100 seconds at most); added time one red on every look, Card's middle and the +N tab alike (a custom board's own when set); the kick-off, restart and after-goal pop-ups fold into the board where the family put it on that channel, and it stays there. Ask AI's key taken from the TV launcher (the one the phone sends to the TVs) when there is none here: Settings › Other › Ask AI, Get the key from the TV launcher (launcher 5.41). Offered inside the app (Settings › Updates). |
| 4.2 | OK while watching, as the family's mock-up C: the information panel low on its programme's picture, under it the ten channels watched last in two rows of five, each on its programme's picture with its logo, name, title, time left and progress (OK watches it); Down past the second row, the settings as labelled buttons in two rows (TV guide, Search, Channels list, History, Movies, TV shows, Recordings, My list, Settings; Picture, Sound, Audio offset, Subtitles, Display, Sleep timer, Favourites, Channel, Exit); MENU straight to them. No Scoreboard button. Offered inside the app (Settings › Updates). |
| 4.3 | Hold OK while a channel plays full screen (nothing over the picture) for the scoreboard and logo menu, the TV launcher's over TiviMate: Scoreboard and Logo's cover on or off on any channel (kept for the next match when none is on), then the look, moving and sizing, line-ups, My scoreboards and the editor, timing, alerts, VAR explained, pop-ups and the sounds. Settings no longer has a Scoreboard page: everything on it is in this menu. OK's screen kept clean: "Recently Watched" alone over the channels, a little down arrow under them for the settings, no words on the keys, and the settings' buttons only say what a setting is set to. Offered inside the app (Settings › Updates). |
| 4.4 | Catch-up you can move about in: Left and Right on the picture go back and on at once (10 s a press, further the longer they are held), a bar with where you are and how far you've moved, the picture following a moment after the remote rests; rewind, fast forward, play/pause, previous and next work; OK for the buttons (the programme before, back 10, play/pause, on 30, the next or live, from the start, LIVE). Offered inside the app (Settings › Updates). |
| 4.5 | Films and episodes: a cover page before they play (the poster popping in, the title's own logo, rating, year, length, plot and cast, then every source to play from — every TorBox file and every playlist copy, each with its quality, HDR, sound, codec, release and size, the best lit), on the Colour theme's ground with the poster's glow. The player in the 07 look: OK for the overlay (title, where it plays from, the seek line with when it ends, the transport) and its buttons — Sources (carrying on from the same moment; a copy that stops hands over to the next), Subtitles (the film's own or OpenSubtitles in your languages, remembered per title, timing and look), Audio (tracks and delay), Episodes, Speed, Picture, Info; Up next at the credits. Settings › Other › Subtitles for the OpenSubtitles login. Offered inside the app (Settings › Updates). |
| 4.6 | The match pop-ups: after a goal the score alone (no RESTART, no scorer or minute); a FULL TIME pop-up with the final score as full time reaches the picture, in place of the full-time alert when pop-ups are on. Offered inside the app (Settings › Updates). |
| 4.7 | Left while watching, as the family's mock-up A: each channel's card on its logo's own colour with what's on and its progress (the playing one marked), its programmes as a timeline beside it (Right into it; past ones with the catch-up mark play from catch-up with OK), the programme's card at the top right. A series' page: its title art, season tabs, one row of every season's episodes that moves the tabs as it goes, and Videos. Trailers playing in the poster the remote rests on (Settings › Appearance › Home: Trailers, Sound). Home, Movies and TV Shows' rows change with the day and refresh every few hours; a carousel of featured titles at the top that moves by itself and with Left and Right. Offered inside the app (Settings › Updates). |
| 4.8 | Goal animations to choose from in the hold-OK menu (Goal animation): As today, GOAL band, Flip, Burst, Club animations (each of the top five leagues' clubs its own), Generic; six new goal sounds (Goal sound). As today stays the default. Offered inside the app (Settings › Updates). |
| 4.9 | The pop-up after a goal: the score, then its scorer a moment later (the name, the minute, (og) or (pen)). Offered inside the app (Settings › Updates). |
| 4.10 | Home, Movies and TV Shows: the carousel at the top is one card across the page, moving on by itself or by hand, and a trailer starts after 3 seconds on the card or panel you rest on. Offered inside the app (Settings › Updates). |
| 4.11 | Every programme has a picture, seen whole: the guide's own, then Sky's, then a wider search (TMDB, a match's two crests, a picture for what it is), then the channel's logo. Pictures fit their cell, the info card and the panel without being over-zoomed, and slide smoothly as the guide scrolls. Offered inside the app (Settings › Updates). |
| 4.12 | Home, Movies and TV Shows lighter for the graphics chip: about a third less painted per frame while moving (the blurred background drawn once, not twice, while it fades; no frame under an opaque poster; the card's shades drawn with its picture; a shadow's hidden middle left out). Holding a direction keeps up with the remote and stops as soon as it is let go (about a quarter of a second); the carousel steps whole slides while held. The look is unchanged. Offered inside the app (Settings › Updates). |

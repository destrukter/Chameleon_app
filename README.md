# Chameleon

An Android take on the social-deduction party game *The Chameleon*: everybody knows the secret word except
the chameleon, who has to bluff a clue that blends in. The app adds what the 3–8 player box can't do:

- **More players**: 3–30 in the local game, 2–16 in multiplayer (two is allowed for testing; play with three or more).
- **More chameleons**: always fewer than half the table. Multiplayer has one voting round per chameleon.
- **Bigger boards**: three presets, Small 4×4 (like the original card), Medium 5×5 and Large 6×6.
- **502 topic cards**, each with 36 words (`core/src/main/resources/categories.txt`). Smaller boards use a
  random subset of a topic's words, so the same topic plays differently each time.
- **Topic packs**: the cards are sorted into 12 themed packs, which can be switched on and off before a game.
  The packs are Animals & Nature, Food & Drink, Places & Travel, Sports & Outdoors, Gaming, Movies, TV & Books,
  Fantasy, Myth & History, Science & Tech, Arts, Music & Fashion, Home & Everyday Life, Out and About, and
  Describe It ("Things that are red", "Things with wheels", …).

- **Custom boards**: design your own 4×4, 5×5 or 6×6 boards (menu → *Custom boards*). They go into a "Custom"
  pack that can be selected only for games of a size you have boards for. In multiplayer the host's boards are
  used, and the board drawn at the start is sent to every player with the rest of the game state.

## Install

Download [`apk/chameleon.apk`](apk/chameleon.apk) to your phone and open it (allow installing from unknown
sources when asked). CI rebuilds and commits this file on every push.

## Local game (pass & play)

1. Type in everybody's names. The game counts them when you press *Start*. Pick the number of chameleons,
   the board size and the topic packs to play with (remembered for next time).
2. The topic card is shown. *Accept* it, or *Reshuffle* to draw a different topic.
3. Pass the phone around. Each player sees their name, taps *Show*, sees the secret word (or
   "You are the Chameleon!"), then taps *Hide & next*. There are no dice; the phone hands out the word directly.
4. After the last player, the board is shown together with a randomly chosen start player. Give your clues
   out loud and point at the chameleon. *Reveal* shows who it was and the secret word.
5. *Play again* returns to the player list with the same names, where you can add or remove players.
   You can also go back to the menu.

## Multiplayer

One phone hosts the lobby, and the game state lives on that phone. There is no game server: other phones
reach the host in one of two ways, and both can be used in the same lobby.

- **Direct (8-character code, e.g. `K7QM-2XRA`)**: for phones on the same Wi-Fi, connected to the host's mobile
  hotspot, or in the same VPN such as Tailscale or ZeroTier. The code encodes the host's IP address and port plus a
  checksum. Phones connect over TCP and no internet is needed. The lobby shows a separate code for each network
  the host is on (Wi-Fi and/or VPN).
- **Over the internet (12-character code, e.g. `E4KD-9QXM-27TB`)**: for friends anywhere. The host and players
  exchange the game messages through a free public MQTT broker (`broker.emqx.io`, falling back to
  `broker.hivemq.com` and `test.mosquitto.org`). The broker only forwards messages: they are end-to-end encrypted
  with AES-GCM using a key derived from the code, and the topic name is a hash of the code. This depends on those
  free public services being up.

- **Create lobby** shows the codes. Players type either code into the same field and choose a name; duplicate
  names are rejected.
- The host chooses the number of chameleons, the board size, the topic packs, the time to give a word, and the
  voting time. Everyone else sees the settings live in the lobby.
- The game starts right away. Non-chameleons see the secret word highlighted on the board. The turn order is
  random, and each player has to type their word before their timer runs out (otherwise the turn is skipped).
- A chat stays open the whole time for discussion.
- After everybody has given a word, voting starts. There are as many voting rounds as chameleons. Each round
  accuses the player with the most votes (the start player breaks ties). An accused chameleon gets one guess
  at the secret word.
- The results show the secret word, every chameleon, whether they guessed right, and the scores, using the
  board game's scoring applied per chameleon. Then the host can play again or go back to the lobby, and
  anyone can leave.
- A player who drops out mid-round can rejoin with the same name.

## Project layout

| Module | What |
| --- | --- |
| `core` | Pure Kotlin/JVM: word list, game engine (host-authoritative state machine), JSON-lines protocol, lobby codes, TCP and MQTT-relay transports. Unit-tested, including real games over loopback sockets and through an in-process MQTT broker. |
| `app` | Android app in Jetpack Compose: menu, rules, local game screens, and multiplayer lobby/game/chat screens. |

## Building

```bash
./gradlew :core:test          # engine, protocol, networking and word-list tests
./gradlew :app:assembleDebug  # app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17+ and the Android SDK (compileSdk 35, minSdk 26). The GitHub Actions workflow
(`.github/workflows/android.yml`) runs the tests and uploads debug and release APKs as build artifacts.

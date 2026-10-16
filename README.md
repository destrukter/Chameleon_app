# Chameleon

An Android take on the social-deduction party game *The Chameleon*: everybody knows the secret word except
the chameleon, who has to bluff a clue that blends in. The app adds what the 3–8 player box can't do:

- **More players**: 3–30 in the local game, 3–16 in multiplayer.
- **More chameleons**: always fewer than half the table. Multiplayer has one voting round per chameleon.
- **Bigger boards**: three presets, Small 4×4 (like the original card), Medium 5×5 and Large 6×6.
- **500 topic cards**, each with 36 words (`core/src/main/resources/categories.txt`). Smaller boards use a
  random subset of a topic's words, so the same topic plays differently each time.

## Local game (pass & play)

1. Type in everybody's names. The game counts them when you press *Start*. Pick the number of chameleons
   and the board size.
2. The topic card is shown. *Accept* it, or *Reshuffle* to draw a different topic.
3. Pass the phone around. Each player sees their name, taps *Show*, sees the secret word (or
   "You are the Chameleon!"), then taps *Hide & next*. There are no dice; the phone hands out the word directly.
4. After the last player, the board is shown together with a randomly chosen start player. Give your clues
   out loud and point at the chameleon. *Reveal* shows who it was and the secret word.
5. *Play again* returns to the player list with the same names, where you can add or remove players.
   You can also go back to the menu.

## Multiplayer (peer to peer, no server)

One phone hosts the lobby; the game state lives on that phone. The other players connect to it directly
over TCP on the local network. That means everybody must be **on the same Wi-Fi, or connected to the host's
mobile hotspot**. No internet connection or account is needed.

- **Create lobby** shows an 8-character code (e.g. `K7QM-2XRA`). The code encodes the host's local IP
  address and port plus a checksum, so it needs no server to look it up. Players join with a name, and
  duplicate names are rejected.
- The host chooses the number of chameleons, the board size, the time to give a word, and the voting time.
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
| `core` | Pure Kotlin/JVM: word list, game engine (host-authoritative state machine), JSON-lines protocol, lobby codes, socket host and client. Fully unit-tested, including a real game over loopback sockets. |
| `app` | Android app in Jetpack Compose: menu, rules, local game screens, and multiplayer lobby/game/chat screens. |

## Building

```bash
./gradlew :core:test          # engine, protocol, networking and word-list tests
./gradlew :app:assembleDebug  # app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17+ and the Android SDK (compileSdk 35, minSdk 26). The GitHub Actions workflow
(`.github/workflows/android.yml`) runs the tests and uploads debug and release APKs as build artifacts.

# Game2048

A 2048 clone for Android with a Jetpack Compose UI and a pure-Kotlin game engine.

## Features

- Classic 4×4, plus Mini 3×3 and Expert 6×6 board sizes
- A **daily challenge** (seeded, same board for everyone on the same date)
- Slide / merge / spawn animations, undo (up to 40 steps), best-score tracking
- Touch swipes and D-pad / arrow-key controls
- Optional sound and haptics, system/light/dark theme

## Engine vs. UI

- `app/src/main/java/com/gliffy/g2048/game/` — pure Kotlin game logic
  (`Game`, `Dir`, `Tile`, `Rng`). No Android imports, unit-tested.
- `app/src/main/java/com/gliffy/g2048/ui/` — Compose UI: `GameScreen`,
  `BoardCanvas` (input + animations), `GameMachine` (state, undo, persistence),
  `MoveEvent`, `Theme`.

## Build

```sh
# requires JDK 17 and the Android SDK (SDK location in local.properties)
gradle :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. There is no Gradle wrapper
commit; any Gradle 8.5+ with AGP 8.5.x works.

## Unit tests

```sh
gradle :app:testDebugUnitTest
```

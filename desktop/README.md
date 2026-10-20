# PokeDaisy desktop (prototype)

The game and the companion as two desktop windows (e.g. one per monitor), built from the
Android app's own code:

- **native/**: the app's native bridge (`app/src/main/cpp`: `pokedaisy_jni.c`, `pk_wide.c`, ...)
  and mGBA 0.10.5, built unchanged as a desktop library. Only `<android/log.h>` is stood in for
  (`native/shim/`, logs go to stderr).
- **The Kotlin side** reuses `ui-preview`'s data module and Android shims, and compiles the same
  app sources it does: `MgbaCore.kt` (the native calls), `TelemetrySampler` / `TelemetryStore`
  (live game memory -> the companion's data) and `CompanionScreen` (the companion itself).
- **src/main/kotlin/desktop/**: the desktop frontend - `Emulator.kt` (the frame loop, EXPAND,
  sound, companion sampling), `Main.kt` (the two windows, keyboard), `Check.kt` (a headless
  self-test that writes PNGs).

## Build and run

```bash
git submodule update --init third_party/mgba third_party/rcheevos
cmake -S desktop/native -B desktop/native/build -DCMAKE_BUILD_TYPE=Release
cmake --build desktop/native/build -j --target pokedaisy

cd desktop
gradle run -Prom=/path/firered.gba [-Psav=/path/firered.srm]
gradle selftest -Prom=/path/firered.gba [-Psav=...] [-Psteps=my.script]   # PNGs in build/check
```

Without `-Psav`, the save beside the ROM is used (`.srm`, else `.sav`); a new save is created as `.srm`, like RetroArch's.

**Keys**: arrows, X = A, Z = B, Enter = START, Backspace = SELECT, A = L, S = R, E = EXPAND on / off.

## Not done yet

Controllers, save states, fast-forward, the companion's buttons that press the game's
(battle controls, USE / FLY), settings, ZOOM / look-around, a Windows build. Game art in the
companion comes from the ROM (`RomArt`), as on Android.

Based on PokeDaisy by Lidor Itzhari - https://github.com/lidor30/pokedaisy (GPL v3).

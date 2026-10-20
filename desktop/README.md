# PokeDaisy desktop (prototype)

PokeDaisy on a PC, built from the Android app's own code: the app's real Library and Settings
screens, then the game with the companion docked beside it (F2 pops the companion out into a window of
its own, e.g. for a second monitor):

- **native/**: the app's native bridge (`app/src/main/cpp`: `pokedaisy_jni.c`, `pk_wide.c`, ...)
  and mGBA 0.10.5, built unchanged as a desktop library. Only `<android/log.h>` is stood in for
  (`native/shim/`, logs go to stderr).
- **The Kotlin side** reuses `ui-preview`'s data module and Android shims, and compiles the same
  app sources it does: `MgbaCore.kt` (the native calls), `TelemetrySampler` / `TelemetryStore`
  (live game memory -> the companion's data) and `CompanionScreen` (the companion itself).
- **src/main/kotlin/desktop/**: the desktop frontend - `Emulator.kt` (the frame loop, EXPAND,
  fast-forward, sound, companion sampling), `DesktopHost.kt` (runs the app's activities as one window, the
  system's file / folder pickers), `Bridges.kt` (the companion's SETTINGS and STATES), `Main.kt` (windows,
  menus, keyboard), `Check.kt` (a headless self-test that writes PNGs).
- **shims/**: `ui-preview`'s Android shims, with desktop behaviour where the app needs it (activities,
  pickers, `file:` URIs; Prefs kept in `userdata/settings.txt`).

## Build and run

```bash
git submodule update --init third_party/mgba third_party/rcheevos
cmake -S desktop/native -B desktop/native/build -DCMAKE_BUILD_TYPE=Release
cmake --build desktop/native/build -j --target pokedaisy

cd desktop
gradle run -Prom=/path/firered.gba [-Psav=/path/firered.srm]
gradle selftest -Prom=/path/firered.gba [-Psav=...] [-Psteps=my.script]   # PNGs in build/check
```

### Windows

```bash
# cross-compile the native library from Linux (apt install mingw-w64)
cmake -S native -B native/build-win -DCMAKE_TOOLCHAIN_FILE=mingw-w64.cmake -DCMAKE_BUILD_TYPE=Release
cmake --build native/build-win -j --target pokedaisy
gradle zipWindows        # build/windows/PokeDaisy-windows.zip
```

The zip is a portable folder: `PokeDaisy.bat` (double-click for the Library, or drag a ROM onto it), `lib/`, the
DLL in `native/`, the app's fonts in `assets/`, and `userdata/` (log, rebuilt art) created on first run. It uses
an installed Java 17+ (the .bat opens the download page if there is none). `native/build-win/wintest.exe` is a
smoke test of the cross-compiled core (boots a ROM + save, writes the EXPAND frame) for Wine or Windows. Windows
has no POSIX signals, so pk_wide.c's fault guard is off there (`PK_WIDE_NO_GUARD`).

Saves follow the app's own rules (Settings > FOLDERS, ALSO LOOK IN, a game's SAVE FOLDER), else the save beside
the ROM (`.srm`, else `.sav`); a new save is a `.srm`, like RetroArch's, in the chosen saves folder or beside the
ROM. Every start backs the save up to `pokedaisy-backups/` first, as on Android.

**Keys**: arrows, X = A, Z = B, Enter = START, Backspace = SELECT, A = L, S = R, E = EXPAND on / off,
Space = hold fast-forward, Tab = fast-forward on / off, F5 / F7 = save / load state, F6 / F8 = state slot,
F2 = companion docked / in its own window, F3 = hide it, Esc = back a screen in the Library / Settings.

## Not done yet

Controllers and key rebinding, the companion's buttons that press the game's (battle controls, USE / FLY),
shaders, ZOOM / look-around, no bundled Java on Windows. Game art in the companion comes from the ROM
(`RomArt`), as on Android.

Based on PokeDaisy by Lidor Itzhari - https://github.com/lidor30/pokedaisy (GPL v3).

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

### Linux / Batocera

```bash
cmake -S native -B native/build -DCMAKE_BUILD_TYPE=Release && cmake --build native/build -j --target pokedaisy
gradle tarLinux          # build/linux/PokeDaisy-linux-x64.tar.gz
```

The tarball carries a Java runtime made by `jlink` from the JDK running Gradle (Batocera has none), `PokeDaisy.sh`,
and `batocera/PokeDaisy.sh` for `/userdata/roms/ports` (first run: `/userdata/roms/gba`, saves with RetroArch's in
`/userdata/saves/gba`, full screen). Its runtime needs the build machine's glibc (Ubuntu 24.04: 2.38, so Batocera 39+);
`native/shim/glibc_compat.c` keeps `libpokedaisy.so` itself at 2.34. Window / font libraries a system may lack sit in
`jre/lib/fallback`, used only when `ldd` finds them missing.

**Controllers** (`Gamepad.kt`, Jamepad = SDL2's GameController, natives inside for Windows and Linux): buttons by
position, EAST = the GBA's A, like RetroArch. The Library / Settings get the pad as the Android key events it sends
there (the launcher's own D-pad / A / B / Y), else Compose focus; in a game WEST / Home steers the companion, NORTH the
HUD's buttons (focus rings, `LocalFocusRings`), SELECT + START leaves. `-Ppadfifo=PATH` reads `BTN down|up` lines
from a named pipe to test without a pad.

**Keys**: arrows, X = A, Z = B, Enter = START, Backspace = SELECT, A = L, S = R, E = EXPAND on / off,
Space = hold fast-forward, Tab = fast-forward on / off, F5 / F7 = save / load state, F6 / F8 = state slot,
Esc = the companion's side panel in / out (as the phone's BACK), F2 = companion in its own window, F3 = hide it,
F10 = borderless, F11 = full screen; Esc in the Library / Settings goes back a screen. The FIELD HUD shows over the
field (SETTINGS > HUD), View > Companion shape sets its 16:9 / 16:10 / 4:3 / Thor / fill shape.

## Not done yet

Controllers and key rebinding, the companion's buttons that press the game's (battle controls, USE / FLY),
shaders, ZOOM / look-around, no bundled Java on Windows. Game art in the companion comes from the ROM
(`RomArt`), as on Android.

Based on PokeDaisy by Lidor Itzhari - https://github.com/lidor30/pokedaisy (GPL v3).

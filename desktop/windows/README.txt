PokeDaisy desktop - prototype for Windows
=========================================

The game and PokeDaisy's companion as two windows (one per monitor works nicely).
Based on PokeDaisy by Lidor Itzhari - https://github.com/lidor30/pokedaisy (GPL v3).
This is an unofficial prototype, not a release of PokeDaisy.

FIRST TIME
  1. Unzip this whole folder somewhere (e.g. Documents\PokeDaisy). Don't run it from inside the zip.
  2. You need Java 17 or newer. If you don't have it, PokeDaisy.bat opens the free download page:
     install the Windows x64 "JRE" .msi and tick "Set JAVA_HOME" in the installer.

PLAY
  Double-click PokeDaisy.bat and pick your ROM (.gba), or drag the ROM onto PokeDaisy.bat.
  Windows may warn about an unknown app the first time: "More info" -> "Run anyway".

SAVES
  The save next to the ROM is used: "Game Name.srm" (RetroArch / Batocera's), else "Game Name.sav".
  A brand-new save is created as .srm, so it syncs with RetroArch / Batocera.
  Back up your save before the first try - this is a prototype.

KEYS (either window)
  Arrows = D-pad   X = A   Z = B   Enter = Start   Backspace = Select   A = L   S = R
  E = EXPAND (widescreen) on / off

NOT YET
  Controllers, save states, fast-forward, the companion's buttons that press the game
  (battle buttons, USE, FLY), settings.

PROBLEMS
  userdata\pokedaisy.log says what went wrong - send it along.

PokeDaisy desktop - prototype for Windows
=========================================

PokeDaisy's Library, Settings, the game and its companion on a Windows PC.
Based on PokeDaisy by Lidor Itzhari - https://github.com/lidor30/pokedaisy (GPL v3).
This is an unofficial prototype, not a release of PokeDaisy.

FIRST TIME
  1. Unzip this whole folder somewhere (e.g. Documents\PokeDaisy). Don't run it from inside the zip.
  2. You need Java 17 or newer. If you don't have it, PokeDaisy.bat opens the free download page:
     install the Windows x64 "JRE" .msi. An older Java (e.g. Java 8) can stay installed:
     PokeDaisy.bat looks for the newest Java 17+ and skips older ones.

PLAY
  Double-click PokeDaisy.bat. The Library opens: add your ROMs folder (the folder button / first-time setup, or
  Settings > FOLDERS), then pick a game. You can also drag a ROM onto PokeDaisy.bat to start it straight away.
  Windows may warn about an unknown app the first time: "More info" -> "Run anyway".
  Closing the game's window takes you back to the Library.

COMPANION
  It sits beside the game. View > Companion (or F2) pops it out into its own window - put that on your second
  monitor - and F2 again (or closing that window) puts it back. F3 hides / shows it.
  Its SETTINGS tab changes ASPECT (EXPAND = widescreen), fast-forward and more; STATES has 10 save-state slots.

SAVES
  Settings > FOLDERS picks where saves live (e.g. your Syncthing folder). Without that, the save next to the ROM
  is used: "Game Name.srm" (RetroArch / Batocera's), else "Game Name.sav". A brand-new save is created as .srm,
  so it syncs with RetroArch / Batocera. Every start keeps a backup in a "pokedaisy-backups" folder beside the
  save (the Library's RESTORE BACKUP lists them). Settings, art and save states live in the userdata folder here.

KEYS (game window)
  Arrows = D-pad   X = A   Z = B   Enter = Start   Backspace = Select   A = L   S = R
  E = EXPAND (widescreen) on / off      Space (hold) / Tab = fast-forward
  F5 / F7 = save / load state           F6 / F8 = previous / next slot
  F2 = companion beside the game / in its own window      F3 = companion hidden / shown

NOT YET
  Controllers, rebinding keys, the companion's buttons that press the game (battle buttons, USE, FLY),
  shaders, RetroAchievements.

PROBLEMS
  Run PokeDaisy-debug.bat instead: it shows which Java it uses and every error in a window
  that stays open. userdata\pokedaisy.log and userdata\launcher.log say what went wrong too.

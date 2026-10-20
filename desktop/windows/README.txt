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
  Settings opens in the same window: its back arrow, Esc or closing the window goes back to the Library.
  Game > Close game, or closing the game's window, takes you back to the Library.

COMPANION
  Like the phone app on a one-screen device: it starts locked beside the game. The padlock on its left edge
  unlocks it; it then slides over the game's right side (the game keeps running) and the X under the padlock
  slides it away. Esc, the HUD's COMPANION button or the tab on the right edge brings it back.
  Locked or open, Esc is the companion's BACK (closes a sub-page, a summary...).
  SETTINGS > COMPANION VIEW: SIDE PANEL (above) or WINDOW (framed in the middle of the game, like the phone's).
  F2 puts it in a window of its own for a second monitor (F2 again puts it back), F3 hides / shows it.
  View > Companion shape: 16:9 (default), 16:10, 4:3, the Thor's bottom screen, or fill its area.

HUD
  On the overworld the phone's HUD shows over the game: minimap, WILD, the bag's balls, COMPANION.
  Its options are in the companion's SETTINGS > HUD (or View > HUD over the game).

WINDOW
  F10 = borderless (the game fills its monitor, no title bar or menus), F11 = full screen; the same keys go back.
  View > Window has all three.

SAVES
  Settings > FOLDERS picks where saves live (e.g. your Syncthing folder). Without that, the save next to the ROM
  is used: "Game Name.srm" (RetroArch / Batocera's), else "Game Name.sav". A brand-new save is created as .srm,
  so it syncs with RetroArch / Batocera. Every start keeps a backup in a "pokedaisy-backups" folder beside the
  save (the Library's RESTORE BACKUP lists them). Settings, art and save states live in the userdata folder here.

CONTROLLER (any pad SDL knows: Xbox, PlayStation, 8BitDo, Switch...; buttons by position, like RetroArch)
  Library:  D-pad = choose, A (right button) = play, B (bottom) = the game's options, Y (left) = Settings,
            SELECT = sort, SELECT + START = quit PokeDaisy
  Game:     D-pad / left stick, A, B, L, R, START, SELECT = the GBA's
            X (top) = the HUD's cursor: D-pad picks a button, A presses it, B leaves
            Y (left) or Home = the companion in / out: D-pad moves, A presses, B goes back, L / R change tabs
            RT (hold) = fast-forward, LT = fast-forward on / off, R3 = widescreen (EXPAND) on / off
            SELECT + START = back to the Library

KEYS (game window)
  Arrows = D-pad   X = A   Z = B   Enter = Start   Backspace = Select   A = L   S = R
  E = EXPAND (widescreen) on / off      Space (hold) / Tab = fast-forward
  F5 / F7 = save / load state           F6 / F8 = previous / next slot
  Esc = companion in / out (and its BACK)   H = HUD cursor   F2 = companion in its own window   F3 = hidden / shown
  F10 = borderless   F11 = full screen

NOT YET
  Rebinding keys or buttons, the companion's buttons that press the game (battle buttons, USE, FLY),
  shaders, RetroAchievements.

PROBLEMS
  Run PokeDaisy-debug.bat instead: it shows which Java it uses and every error in a window
  that stays open. userdata\pokedaisy.log and userdata\launcher.log say what went wrong too.

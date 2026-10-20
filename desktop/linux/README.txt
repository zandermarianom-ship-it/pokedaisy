PokeDaisy desktop - prototype for Linux and Batocera (x86_64)
=============================================================

PokeDaisy's Library, Settings, the game and its companion on a Linux PC or a Batocera box.
Based on PokeDaisy by Lidor Itzhari - https://github.com/lidor30/pokedaisy (GPL v3).
This is an unofficial prototype, not a release of PokeDaisy.

Java is inside (the jre folder): nothing to install. It needs glibc 2.38 or newer: Batocera 39 or newer, Ubuntu 24.04,
Fedora 39, SteamOS 3.5... x86_64 only for now (not ARM handhelds).

LINUX
  Unzip anywhere and run PokeDaisy.sh (./PokeDaisy.sh, or ./PokeDaisy.sh game.gba to start one).
  PD_FULLSCREEN=1 ./PokeDaisy.sh starts full screen.

BATOCERA
  1. Copy the PokeDaisy folder to the Batocera share's system folder and rename it pokedaisy:
     \\BATOCERA\share\system\pokedaisy  (that's /userdata/system/pokedaisy on the box).
  2. Copy batocera/PokeDaisy.sh to \\BATOCERA\share\roms\ports\
  3. In Batocera: Game settings > Update gamelists. PokeDaisy is under Ports.
  The first start lists your GBA games (roms/gba) and keeps saves with RetroArch's (saves/gba, .srm), so a game saved
  in PokeDaisy continues in RetroArch and back. Everything else is in system/pokedaisy/userdata (batocera.log if it
  doesn't start).

CONTROLLER (any pad SDL knows: Xbox, PlayStation, 8BitDo, Switch...; buttons by position, like RetroArch)
  Library:  D-pad = choose, A (right button) = play, B (bottom) = the game's options, Y (left) = Settings,
            SELECT = sort, SELECT + START = quit PokeDaisy
  Game:     D-pad / left stick, A, B, L, R, START, SELECT = the GBA's
            X (top) = the HUD's cursor: D-pad picks a button, A presses it, B leaves
            Y (left) or Home = the companion in / out: D-pad moves, A presses, B goes back, L / R change tabs
            RT (hold) = fast-forward, LT = fast-forward on / off, R3 = widescreen (EXPAND) on / off
            SELECT + START = back to the Library

KEYBOARD
  Arrows = D-pad   X = A   Z = B   Enter = Start   Backspace = Select   A = L   S = R
  E = EXPAND   Space (hold) / Tab = fast-forward   F5 / F7 = save / load state   F6 / F8 = slot
  Esc = companion in / out (and its BACK)   H = HUD cursor   F2 = companion in its own window   F3 = hidden
  F10 = borderless   F11 = full screen

PROBLEMS
  A black window: PD_SOFTWARE=1 ./PokeDaisy.sh. userdata/pokedaisy.log says what went wrong.

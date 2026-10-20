#!/bin/bash
# Batocera's game list starts a game in PokeDaisy through this (es_systems_pokedaisy.cfg's command): full screen,
# straight into the game, and HOTKEY + START (or SELECT + START) quits back to the game list.
PD=/userdata/system/pokedaisy
U="$PD/userdata"
mkdir -p "$U"
if [ ! -f "$U/settings.txt" ]; then
    mkdir -p /userdata/saves/gba
    printf 's\troms_folder\t/userdata/roms/gba\ns\tsaves_dir_override\t/userdata/saves/gba\nb\tsetup_done\ttrue\nb\tsingle_screen\ttrue\n' > "$U/settings.txt"
fi
PD_FULLSCREEN=1 PD_QUIT_AFTER_GAME=1 bash "$PD/PokeDaisy.sh" "$1" > "$U/batocera-game.log" 2>&1

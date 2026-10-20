#!/bin/bash
# PokeDaisy for Batocera (x86_64, Batocera 39 or newer). Put the PokeDaisy folder in /userdata/system/pokedaisy and
# this file in /userdata/roms/ports, then update the games list: PokeDaisy shows under Ports.
PD=/userdata/system/pokedaisy
U="$PD/userdata"
mkdir -p "$U"
# First run: the Library lists Batocera's GBA folder, and saves go with RetroArch's (.srm in /userdata/saves/gba),
# so the same save plays in both.
if [ ! -f "$U/settings.txt" ]; then
    mkdir -p /userdata/saves/gba
    printf 's\troms_folder\t/userdata/roms/gba\ns\tsaves_dir_override\t/userdata/saves/gba\nb\tsetup_done\ttrue\nb\tsingle_screen\ttrue\n' > "$U/settings.txt"
fi
PD_FULLSCREEN=1 bash "$PD/PokeDaisy.sh" "$@" > "$U/batocera.log" 2>&1

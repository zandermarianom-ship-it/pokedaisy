#!/bin/bash
# PokeDaisy for Linux (x86_64): the Library, the game and its companion. Run it from anywhere:
#   ./PokeDaisy.sh [game.gba]
# PD_FULLSCREEN=1 starts full screen (a TV, Batocera), PD_DEBUG=1 logs to the terminal instead of userdata/pokedaisy.log,
# PD_SOFTWARE=1 draws without OpenGL (if the window stays black).
HERE="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"

# The bundled Java (a copy unzipped on Windows loses its run permission), else the system's.
chmod +x "$HERE"/jre/bin/* "$HERE"/jre/lib/jspawnhelper 2>/dev/null
JAVA="$HERE/jre/bin/java"
[ -x "$JAVA" ] || JAVA="$(command -v java)"
if [ -z "$JAVA" ]; then echo "PokeDaisy: no Java found (the jre folder is missing)" >&2; exit 1; fi

# Libraries the window and fonts need that a minimal system (Batocera) may lack: ours, only for what's missing.
if command -v ldd >/dev/null 2>&1 && [ -d "$HERE/jre/lib/fallback" ]; then
    if ldd "$HERE/jre/lib/libawt_xawt.so" "$HERE/jre/lib/libfontmanager.so" 2>/dev/null | grep "not found" | grep -qv "libjvm"; then
        export LD_LIBRARY_PATH="${LD_LIBRARY_PATH:+$LD_LIBRARY_PATH:}$HERE/jre/lib/fallback"
    fi
fi

export DISPLAY="${DISPLAY:-:0.0}"
[ -n "$PD_SOFTWARE" ] && export SKIKO_RENDER_API=SOFTWARE
OPTS=(-Xmx1g -Djava.library.path="$HERE/native" -Dscratch="$HERE/userdata" -Dsun.java2d.uiScale.enabled=true)
[ -n "$PD_FULLSCREEN" ] && OPTS+=(-Dpokedaisy.fullscreen=true)
[ -z "$PD_DEBUG" ] && OPTS+=(-Dlogfile="$HERE/userdata/pokedaisy.log")
mkdir -p "$HERE/userdata"
# The app's fonts are read from assets/.
cd "$HERE/assets" || exit 1
# PD_JAVA_OPTS: more Java options (e.g. -Xmx2g).
exec "$JAVA" "${OPTS[@]}" $PD_JAVA_OPTS -cp "$HERE/lib/*" desktop.WindowsKt "$@"

package desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.runtime.key
import com.pokedaisy.app.companion.GameAspect
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pokedaisy.app.MgbaCore
import com.pokedaisy.app.companion.data.RomArt
import com.pokedaisy.app.companion.data.RomRegionMap
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.ui.CompanionScreen
import java.io.File
import kotlin.math.min

/** PokeDaisy desktop: `run [-Prom=game.gba]` opens the Library (and the game, if one is given). */
fun main(args: Array<String>) = runDesktop(args.firstOrNull()?.let { File(it).absoluteFile })

/** Opened by double-click (PokeDaisy.bat): no console, so errors show as a window and everything is logged. */
fun startDesktop(args: Array<String>) {
    System.getProperty("logfile")?.let { path ->
        val log = java.io.PrintStream(java.io.FileOutputStream(path, false), true)
        System.setOut(log)
        System.setErr(log)
    }
    try {
        runDesktop(args.firstOrNull()?.let { File(it).absoluteFile })
    } catch (t: Throwable) {
        t.printStackTrace()
        showError("PokeDaisy couldn't start:\n\n$t\n\nDetails are in ${System.getProperty("logfile") ?: "the console"}.")
        System.exit(1)
    }
}

/** The data folder (userdata/ beside PokeDaisy.bat): settings, art rebuilt from ROMs, save states. */
val userdata: File get() = File(System.getProperty("scratch") ?: System.getProperty("java.io.tmpdir")).absoluteFile

/** The Library window (the app's own LibraryActivity), and a game window whenever one is playing. */
fun runDesktop(rom: File?) {
    // The app's settings (Prefs) kept between runs - set before anything reads them.
    if (System.getProperty("prefsFile") == null) System.setProperty("prefsFile", File(userdata, "settings.txt").path)
    runCatching { javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName()) }
    createSharedUiState()
    DesktopHost.open(com.pokedaisy.app.LibraryActivity::class.java)
    rom?.let(DesktopHost::startGame)
    application {
        if (DesktopHost.game == null) DesktopHost.screens.forEachIndexed { i, screen ->
            key(screen) {
                Window(
                    onCloseRequest = { if (i == 0) exitApplication() else screen.activity.finish() },
                    title = screen.title,
                    state = rememberWindowState(size = DpSize(1280.dp, 720.dp), position = WindowPosition(60.dp + (i * 30).dp, 40.dp + (i * 30).dp)),
                ) { ActivityScreen(screen) }
            }
        }
        DesktopHost.game?.let { g -> key(g) { GameWindows(g) } }
    }
    System.exit(0)
}

/** An activity's content laid out as on the top screen it was made for (1920 x 1080 at density 2.5), scaled to the window. */
@Composable
fun ActivityScreen(screen: DesktopHost.Screen) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val px = with(LocalDensity.current) { Pair(maxWidth.toPx(), maxHeight.toPx()) }
        val density = min(px.first / SCREEN_DP_W, px.second / SCREEN_DP_H)
        CompositionLocalProvider(LocalDensity provides Density(density)) {
            Box(Modifier.size(SCREEN_DP_W.dp, SCREEN_DP_H.dp)) { screen.content() }
        }
        DesktopHost.notice?.let { Notice(it) }
    }
}

@Composable
fun Notice(text: String) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
    Text(text, color = Color.White, modifier = Modifier.background(Color(0xE0202028)).padding(horizontal = 16.dp, vertical = 10.dp))
}

const val SCREEN_DP_W = 1920f / 2.5f
const val SCREEN_DP_H = 1080f / 2.5f

/** The open-a-ROM window (the system's own file dialog); null if cancelled. */
fun pickRom(): File? {
    val last = File(android.content.Context().filesDir, "last-rom.txt")
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "PokeDaisy - open a Pokemon ROM", java.awt.FileDialog.LOAD)
    runCatching { File(last.readText().trim()).parentFile?.takeIf { it.isDirectory }?.let { dialog.directory = it.path } }
    dialog.setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".gba") || it.endsWith(".gb") || it.endsWith(".gbc") } }
    dialog.file = "*.gba;*.gb;*.gbc" // Windows' dialog takes its filter from here
    dialog.isVisible = true
    val name = dialog.file ?: return null
    val f = File(dialog.directory, name)
    runCatching { last.writeText(f.path) }
    return f
}

fun showError(message: String) {
    println(message)
    if (!java.awt.GraphicsEnvironment.isHeadless())
        javax.swing.JOptionPane.showMessageDialog(null, message, "PokeDaisy", javax.swing.JOptionPane.ERROR_MESSAGE)
}

/** A game being played: its emulator, its companion's bridges and where the companion is. Ends with [onEnd]. */
class GameSession(val rom: File, val save: File, val onEnd: () -> Unit) {
    val config = DesktopConfig(File(userdata, "desktop.properties"))
    val emu = Emulator(rom, save)
    var crash by mutableStateOf<Throwable?>(null)
    var place by mutableStateOf(runCatching { CompanionPlace.valueOf(config.get("companion") ?: "") }.getOrDefault(CompanionPlace.DOCKED))
        private set
    val settings = DesktopSettings(emu, config, onClose = { onEnd() }, onHideCompanion = { moveCompanion(CompanionPlace.HIDDEN) })
    val slots = DesktopSlots(emu, File(userdata, "states"))

    init {
        println("ROM ${rom.path}, save ${save.path}")
        prepareArt(rom)
        emu.start(sound = true) { crash = it; it.printStackTrace() }
    }

    fun moveCompanion(to: CompanionPlace) { place = to; config.set("companion", to.name) }
    fun stop() = emu.stop()
}

/**
 * The game: one window with the companion beside it by default; View > Companion (or F2) pops the companion out into
 * a window of its own for a second monitor and back, F3 hides / shows it. Closing the game goes back to the Library.
 */
@Composable
fun ApplicationScope.GameWindows(g: GameSession) {
    val keys = { e: KeyEvent -> gameKey(g.emu, e, g.settings, g.slots, g.place, g::moveCompanion) }
    val snap by g.emu.store.snapshot.collectAsState()
    Window(
        onCloseRequest = g.onEnd,
        title = "PokeDaisy - ${g.rom.nameWithoutExtension}",
        state = rememberWindowState(size = DpSize(1600.dp, 760.dp), position = WindowPosition(30.dp, 30.dp)),
        onPreviewKeyEvent = keys,
    ) {
        GameMenus(g.settings, g.slots, g.place, g::moveCompanion, g.onEnd)
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            // Docked: the companion takes up to 40% of the width at its own shape, the game the rest.
            val companionW = if (g.place == CompanionPlace.DOCKED) minOf(maxWidth * 0.4f, maxHeight * (COMPANION_DP_W / COMPANION_DP_H)) else 0.dp
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { GameScreen(g.emu, g.crash) }
                if (g.place == CompanionPlace.DOCKED) Box(Modifier.width(companionW).fillMaxHeight()) {
                    CompanionPanel(snap, g.settings, g.slots)
                }
            }
        }
    }
    if (g.place == CompanionPlace.WINDOW) Window(
        // Closing it puts the companion back beside the game, rather than ending the game.
        onCloseRequest = { g.moveCompanion(CompanionPlace.DOCKED) },
        title = "PokeDaisy - companion (close to put it back beside the game)",
        state = rememberWindowState(size = DpSize(700.dp, 640.dp), position = WindowPosition(1660.dp, 30.dp)),
        onPreviewKeyEvent = keys,
    ) {
        MenuBar {
            Menu("Companion") {
                Item("Put back beside the game (F2)", onClick = { g.moveCompanion(CompanionPlace.DOCKED) })
                Item("Hide (F3)", onClick = { g.moveCompanion(CompanionPlace.HIDDEN) })
            }
        }
        CompanionPanel(snap, g.settings, g.slots)
    }
}

/** The game window's menus: the things the companion's SETTINGS can't reach on a desktop (or that want a key). */
@Composable
fun FrameWindowScope.GameMenus(
    settings: DesktopSettings, slots: DesktopSlots, place: CompanionPlace,
    moveCompanion: (CompanionPlace) -> Unit, exit: () -> Unit,
) = MenuBar {
    Menu("Game") {
        Item("Save state to slot ${slots.currentIndex} (F5)", onClick = { slots.requestSave(slots.currentIndex) })
        Item("Load state from slot ${slots.currentIndex} (F7)", onClick = { slots.requestLoad(slots.currentIndex) })
        Item("Next slot (F8)", onClick = { slots.select(slots.currentIndex + 1) })
        Item("Previous slot (F6)", onClick = { slots.select(slots.currentIndex - 1) })
        Separator()
        CheckboxItem("Fast-forward (Tab, or hold Space)", checked = settings.ffToggled, onCheckedChange = { settings.setFfToggled(it) })
        Separator()
        Item("Restart game", onClick = { settings.restartGame() })
        Item("Close game (back to the Library)", onClick = exit)
    }
    Menu("View") {
        RadioButtonItem("Companion beside the game (F2)", selected = place == CompanionPlace.DOCKED, onClick = { moveCompanion(CompanionPlace.DOCKED) })
        RadioButtonItem("Companion in its own window (F2)", selected = place == CompanionPlace.WINDOW, onClick = { moveCompanion(CompanionPlace.WINDOW) })
        RadioButtonItem("Companion hidden (F3)", selected = place == CompanionPlace.HIDDEN, onClick = { moveCompanion(CompanionPlace.HIDDEN) })
        Separator()
        GameAspect.entries.forEach { a ->
            RadioButtonItem("Screen: ${a.label}" + if (a == GameAspect.WIDE) " (E)" else "", selected = settings.gameAspect == a,
                enabled = a != GameAspect.WIDE || settings.wideSupported, onClick = { settings.setGameAspect(a) })
        }
    }
}

/**
 * The app's process-wide UI state (the language, DARK MODE, the OPTION look's colours...) lives in Kotlin objects
 * whose Compose state is created on first use. On Android one window composes, so that first use is safe; here two
 * windows compose at once, and a state created inside one window's unapplied snapshot can't be read by the other
 * ("Reading a state that was created after the snapshot was taken"). So they're all created here, up front.
 */
fun createSharedUiState() {
    listOf(
        "com.pokedaisy.app.companion.i18n.L10n",
        "com.pokedaisy.app.companion.ui.theme.DarkMode",
        "com.pokedaisy.app.companion.ui.theme.AppColors",
        "com.pokedaisy.app.companion.ui.OptionColors",
        "com.pokedaisy.app.companion.ui.CompanionStatusBar",
        "com.pokedaisy.app.companion.ui.CompanionTweaks",
        "com.pokedaisy.app.companion.ui.FieldHudFit",
        "com.pokedaisy.app.companion.FavoriteItems",
    ).forEach { name ->
        runCatching { Class.forName(name, true, Emulator::class.java.classLoader) }
            .onFailure { println("UI state $name not created up front: $it") }
    }
}

/** The save beside the ROM: RetroArch's .srm if there is one, else mGBA's .sav; a new save is a .srm, so it syncs
 * with RetroArch / Batocera (the same bytes: a Pokemon cart's 128 KB, which both read). */
fun defaultSave(rom: File): File {
    val base = rom.nameWithoutExtension
    return listOf("srm", "sav").map { File(rom.parentFile, "$base.$it") }.firstOrNull { it.isFile }
        ?: File(rom.parentFile, "$base.srm")
}

/** The companion's game art (party slots, bag, map...) is rebuilt from the player's own ROM, as on Android. */
fun prepareArt(rom: File) {
    val files = android.content.Context().filesDir
    val dir = RomArt.dir(files)
    val crc = com.pokedaisy.app.SaveStates.crc32(rom)
    // Once per ROM, as on Android: a hack's own art (its map, its party slots) joins what other ROMs gave.
    val scanned = File(dir, "scanned-$crc-v${RomArt.SCAN_VERSION}")
    if (!scanned.isFile) {
        val wrote = RomArt.extractTo(rom.readBytes(), dir)
        println("companion art: ${wrote.size} files from ${rom.name}")
        runCatching { scanned.writeText(rom.name) }
    }
    // A FireRed-engine hack's own region map (Unbound, Odyssey...), read from its ROM as the app does at launch.
    RomRegionMap.current = RomRegionMap.loadNow(files, crc, rom)
}

/**
 * Keyboard -> GBA keys: arrows, X = A, Z = B, Enter = START, Backspace = SELECT, A = L, S = R. Hotkeys: E = EXPAND,
 * Space (held) / Tab = fast-forward, F5 / F7 = save / load state, F6 / F8 = slot, F2 = companion beside the game or in
 * its own window, F3 = companion hidden / shown.
 */
fun gameKey(
    emu: Emulator, e: KeyEvent, settings: DesktopSettings, slots: DesktopSlots,
    place: CompanionPlace, moveCompanion: (CompanionPlace) -> Unit,
): Boolean {
    val down = e.type == KeyEventType.KeyDown
    val bit = when (e.key) {
        Key.DirectionUp -> MgbaCore.Key.UP
        Key.DirectionDown -> MgbaCore.Key.DOWN
        Key.DirectionLeft -> MgbaCore.Key.LEFT
        Key.DirectionRight -> MgbaCore.Key.RIGHT
        Key.X -> MgbaCore.Key.A
        Key.Z -> MgbaCore.Key.B
        Key.Enter -> MgbaCore.Key.START
        Key.Backspace -> MgbaCore.Key.SELECT
        Key.A -> MgbaCore.Key.L
        Key.S -> MgbaCore.Key.R
        Key.Spacebar -> { emu.ffHeld = down; return true }
        else -> {
            if (!down) return e.key in HOTKEYS
            if (e.key in HOTKEYS) println("hotkey ${e.key}")
            when (e.key) {
                Key.E -> settings.setGameAspect(if (settings.gameAspect == GameAspect.WIDE) GameAspect.ORIGINAL else GameAspect.WIDE)
                Key.Tab -> settings.setFfToggled(!settings.ffToggled)
                Key.F5 -> slots.requestSave(slots.currentIndex)
                Key.F7 -> slots.requestLoad(slots.currentIndex)
                Key.F6 -> slots.select(slots.currentIndex - 1)
                Key.F8 -> slots.select(slots.currentIndex + 1)
                Key.F2 -> moveCompanion(if (place == CompanionPlace.WINDOW) CompanionPlace.DOCKED else CompanionPlace.WINDOW)
                Key.F3 -> moveCompanion(if (place == CompanionPlace.HIDDEN) CompanionPlace.DOCKED else CompanionPlace.HIDDEN)
                else -> return false
            }
            return true
        }
    }
    emu.keys = if (down) emu.keys or bit else emu.keys and bit.inv()
    return true
}

private val HOTKEYS = setOf(Key.E, Key.Tab, Key.F5, Key.F7, Key.F6, Key.F8, Key.F2, Key.F3)

/** The game, as large as the window allows at its own shape, square pixels, black around it. */
@Composable
fun GameScreen(emu: Emulator, crash: Throwable?) {
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(emu) {
        var last: java.awt.image.BufferedImage? = null
        while (true) withFrameNanos {
            val f = emu.frame.get()
            if (f != null && f !== last) {
                last = f
                image = f.toComposeImageBitmap()
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val img = image
        if (crash != null) Text("The game stopped: ${crash.message}", color = Color.White)
        else if (img != null) Canvas(Modifier.fillMaxSize()) {
            val scale = min(size.width / img.width, size.height / img.height)
            // STRETCH: the GBA's picture made as wide as the window, at the window's height.
            val w = if (emu.stretch) size.width.toInt() else (img.width * scale).toInt()
            val h = (img.height * scale).toInt()
            drawImage(
                img,
                dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
                dstSize = IntSize(w, h),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

/** The app's own companion, sized like the AYN Thor's bottom screen (1240 x 1080 at density 2.625) to fill its area,
 * with the desktop's SETTINGS and STATES bridges. */
@Composable
fun CompanionPanel(snap: SnapshotView, settings: DesktopSettings, slots: DesktopSlots) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val px = with(LocalDensity.current) { Pair(maxWidth.toPx(), maxHeight.toPx()) }
        val density = min(px.first / COMPANION_DP_W, px.second / COMPANION_DP_H)
        CompositionLocalProvider(LocalDensity provides Density(density)) {
            Box(Modifier.size(COMPANION_DP_W.dp, COMPANION_DP_H.dp)) { CompanionScreen(snap, slots = slots, settings = settings) }
        }
    }
}

// The Thor's bottom screen in dp: 1240 x 1080 px at density 2.625.
const val COMPANION_DP_W = 1240f / 2.625f
const val COMPANION_DP_H = 1080f / 2.625f

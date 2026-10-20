package desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.runtime.key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import com.pokedaisy.app.companion.ui.CompanionBack
import com.pokedaisy.app.companion.ui.CompanionKeys
import com.pokedaisy.app.companion.ui.MenuKey
import com.pokedaisy.app.companion.ui.SidePanelHost
import com.pokedaisy.app.companion.ui.SidePanelHandle
import com.pokedaisy.app.companion.ui.SidePanelOpenTab
import com.pokedaisy.app.companion.ui.SidePanelCloseTab
import com.pokedaisy.app.companion.ui.sidePanelDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.pokedaisy.app.companion.ui.FieldHud
import com.pokedaisy.app.companion.data.hasData
import com.pokedaisy.app.companion.ui.LocalCompanionBack
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
    // First run: the companion gets a window of its own when there are two monitors, else it sits beside the game.
    val firstRun = DesktopConfig(File(userdata, "desktop.properties"))
    if (firstRun.get("screensChosen") == null) {
        com.pokedaisy.app.Prefs(android.content.Context()).singleScreen = Monitors.count < 2
        firstRun.set("screensChosen", "true")
    }
    DesktopHost.open(com.pokedaisy.app.LibraryActivity::class.java)
    rom?.let(DesktopHost::startGame)
    // Controllers: the game's buttons in a game, the D-pad / A / B through the Library and Settings.
    Gamepad.listener = { b, down, repeat -> DesktopHost.game?.padButton(b, down, repeat) ?: DesktopHost.libraryPad(b, down, repeat) }
    Gamepad.start()
    application {
        // One window: the screen on top of the stack (Library, then Settings over it, ...), like the phone's screen.
        // Closing it, or Esc, goes back a screen; on the Library it quits.
        val top = DesktopHost.screens.lastOrNull()
        if (DesktopHost.game == null && top != null) {
            val back = { if (DesktopHost.screens.size <= 1) exitApplication() else top.activity.finish() }
            Window(
                onCloseRequest = back,
                title = top.title,
                state = rememberWindowState(
                    size = DpSize(1280.dp, 720.dp), position = WindowPosition(60.dp, 40.dp),
                    placement = if (startFullscreen) androidx.compose.ui.window.WindowPlacement.Fullscreen else androidx.compose.ui.window.WindowPlacement.Floating,
                ),
                onPreviewKeyEvent = {
                    if (it.type == KeyEventType.KeyDown && it.key == Key.Escape && DesktopHost.screens.size > 1) { back(); true } else false
                },
            ) {
                DesktopHost.libraryFocus = LocalFocusManager.current
                DesktopHost.libraryWindow = window
                key(top) { ActivityScreen(top) }
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
        // Pop-ups (OptionOverlay) take the D-pad's focus, keep it in and give it back - as Settings does for itself: on the
        // desktop they're drawn in the same window, so a controller's focus could otherwise land on the list behind them.
        val overlays = remember(screen) { com.pokedaisy.app.companion.ui.OverlayStack() }
        CompositionLocalProvider(
            LocalDensity provides Density(density),
            com.pokedaisy.app.companion.ui.LocalOverlayStack provides overlays,
        ) {
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
    private val prefs = com.pokedaisy.app.Prefs(android.content.Context())

    /** SETTINGS > SINGLE SCREEN (the app's own setting, on the Library's Settings too): off = the companion in a window
     * of its own, for a second monitor; on = beside the game. F2 flips it too. */
    var place by mutableStateOf(
        // Where it was last time (the side panel locked beside the game the first time); a pop-up comes back closed.
        runCatching { CompanionPlace.valueOf(config.get("companionPlace") ?: "") }.getOrDefault(CompanionPlace.DOCKED)
            .let { if (it == CompanionPlace.POPUP) CompanionPlace.HIDDEN else it }
    )
        private set

    /** SETTINGS > SWAP SCREENS: the game on the second monitor and the companion on the main one. */
    var swapScreens by mutableStateOf(prefs.swapScreens)
        private set

    val settings = DesktopSettings(
        emu, config, onClose = { onEnd() }, onHideCompanion = { moveCompanion(CompanionPlace.HIDDEN) },
        companionOwnWindow = { place == CompanionPlace.WINDOW },
        setCompanionOwnWindow = { moveCompanion(if (it) CompanionPlace.WINDOW else CompanionPlace.DOCKED) },
        swap = { swapScreens }, setSwap = { swapScreens = it; prefs.swapScreens = it },
    )
    val slots = DesktopSlots(emu, File(userdata, "states"))

    init {
        println("ROM ${rom.path}, save ${save.path}")
        prepareArt(rom)
        emu.start(sound = true) { crash = it; it.printStackTrace() }
    }

    /** The phone's BACK and the game's buttons for the companion while it pops up over the game (as on a one-screen
     * device: SidePanel's window), and where it was before. */
    val back = CompanionBack()
    val keys = CompanionKeys()
    /** The HUD's own BACK (Esc closes its open panel first). */
    val hudBack = CompanionBack()
    /** Where the game's picture is drawn in its area (px): the HUD keeps to it. */
    var gameRect by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    private var beforePopup = CompanionPlace.DOCKED
    /** Set from inside the game window: its focus (the D-pad moves it in the pop-up) and the window, for A's Enter. */
    var focusManager: FocusManager? = null
    var awtWindow: java.awt.Window? = null
    /** The pop-up's focus is inside the companion: the D-pad moves it, else it first goes to the starting row. */
    var popupFocused = false

    fun moveCompanion(to: CompanionPlace) {
        if (to == CompanionPlace.POPUP && place != CompanionPlace.POPUP) beforePopup = place
        place = to
        if (to == CompanionPlace.DOCKED || to == CompanionPlace.WINDOW) prefs.singleScreen = to == CompanionPlace.DOCKED
        config.set("companionPlace", to.name)
        // Held keys let go, as the phone's window does.
        if (to == CompanionPlace.POPUP) { emu.keys = 0; emu.padKeys = 0; startTries = 0 }
    }

    /** Esc: the pop-up opens over the game; open, it goes back a step (a sub-page, a summary...), then closes. */
    fun togglePopup() {
        when {
            place == CompanionPlace.POPUP -> if (!back.back()) closePopup()
            // Locked beside the game (the side panel): Esc is the companion's back, as BACK is on the phone.
            place == CompanionPlace.DOCKED && sideView -> back.back()
            !hudBack.back() -> moveCompanion(CompanionPlace.POPUP)
        }
    }
    /** COMPANION VIEW is the side panel (not the window in the middle). */
    val sideView get() = settings.companionWindow != true
    fun closePopup() {
        if (place != CompanionPlace.POPUP) return
        moveCompanion(if (sideView) CompanionPlace.HIDDEN else beforePopup.takeIf { it != CompanionPlace.POPUP } ?: CompanionPlace.DOCKED)
    }

    /** The HUD's controller cursor (Y on a pad, H on the keyboard): the D-pad walks its buttons, A presses, B leaves. */
    var hudFocus by mutableStateOf(false)
        private set
    /** An invisible stop in the middle of the picture the HUD cursor starts from (its buttons are around the edges). */
    val hudAnchor = androidx.compose.ui.focus.FocusRequester()
    /** The companion's first stop for the D-pad when its tab has no starting row (an empty PARTY). */
    val companionStart = androidx.compose.ui.focus.FocusRequester()
    /** The HUD is showing (set by GameHud): the cursor can only go to a HUD that's there. */
    var hudShown = false

    fun toggleHudFocus() {
        if (hudFocus) { leaveHud(); return }
        if (!hudShown) return
        if (place == CompanionPlace.POPUP) closePopup()
        hudFocus = true
        emu.keys = 0; emu.padKeys = 0
        runCatching { hudAnchor.requestFocus() }
    }
    fun leaveHud() {
        if (!hudFocus) return
        hudFocus = false
        focusManager?.clearFocus(force = true)
    }

    /** The companion or the HUD has the game's buttons now (they don't reach the game). */
    val steering get() = place == CompanionPlace.POPUP || hudFocus

    /**
     * The keyboard's game keys while the companion pops up or the HUD cursor is on, as on the phone: B = back (then
     * close), START = close, L / R = tabs, the D-pad moves the focus and A presses what has it. False = not a game key.
     */
    fun popupKey(e: KeyEvent): Boolean {
        if (Synthetic.active) return false
        val k = when (e.key) {
            Key.DirectionUp -> MenuKey.UP; Key.DirectionDown -> MenuKey.DOWN
            Key.DirectionLeft -> MenuKey.LEFT; Key.DirectionRight -> MenuKey.RIGHT
            Key.X -> MenuKey.A; Key.Z -> MenuKey.B; Key.A -> MenuKey.L; Key.S -> MenuKey.R
            Key.Enter -> MenuKey.START; Key.Backspace -> MenuKey.SELECT
            else -> return false
        }
        if (e.type == KeyEventType.KeyDown) menuKey(k)
        return true
    }

    /** One of the game's buttons for the companion / HUD (keyboard or controller). */
    fun menuKey(k: MenuKey, repeat: Boolean = false) {
        if (hudFocus) {
            when (k) {
                MenuKey.B -> if (!hudBack.back()) leaveHud()
                MenuKey.START, MenuKey.SELECT -> leaveHud()
                MenuKey.A -> Synthetic.enter(awtWindow)
                else -> moveFocus(k)
            }
            return
        }
        when (k) {
            MenuKey.B -> if (!back.back()) closePopup()
            MenuKey.START -> closePopup()
            else -> keys.key(k, repeat)
        }
    }

    private fun moveFocus(k: MenuKey): Boolean {
        val fm = focusManager ?: return false
        return when (k) {
            MenuKey.UP -> fm.moveFocus(FocusDirection.Up)
            MenuKey.DOWN -> fm.moveFocus(FocusDirection.Down)
            MenuKey.LEFT -> fm.moveFocus(FocusDirection.Left)
            MenuKey.RIGHT -> fm.moveFocus(FocusDirection.Right)
            else -> false
        }
    }

    private var startTries = 0

    /** What nothing in the companion took: the D-pad moves Compose's focus, A presses the focused thing (its Enter). */
    fun focusKey(key: MenuKey, start: () -> Unit): Boolean {
        if (!popupFocused && key != MenuKey.A) {
            // Its starting row first (SETTINGS' cursor, a list's first row); a page without one, the first stop.
            if (startTries++ == 0) start()
            else runCatching { companionStart.requestFocus(); focusManager?.moveFocus(FocusDirection.Next) }
            return true
        }
        startTries = 0
        if (key == MenuKey.A) { Synthetic.enter(awtWindow); return true }
        return moveFocus(key)
    }

    /**
     * A controller's button in the game. Playing: the GBA's buttons (EAST = A, SOUTH = B, like RetroArch), RT held =
     * fast-forward, LT = fast-forward on / off, R3 = EXPAND, SELECT + START = back to the Library. WEST (or GUIDE) = the
     * companion in / out, NORTH = the HUD's cursor. While
     * the companion pops up or the HUD cursor is on, the buttons steer them (EAST = A, SOUTH = B / back).
     */
    fun padButton(b: Gamepad.Btn, down: Boolean, repeat: Boolean) {
        // SELECT + START: back to the Library (Batocera's way out of a game).
        if (b == Gamepad.Btn.EXIT) { emu.padKeys = 0; onEnd(); return }
        when (b) {
            Gamepad.Btn.WEST, Gamepad.Btn.GUIDE -> { if (down && !repeat) padCompanion(); return }
            Gamepad.Btn.NORTH -> { if (down && !repeat) toggleHudFocus(); return }
            Gamepad.Btn.RT -> { emu.ffHeld = down; return }
            Gamepad.Btn.LT -> { if (down) settings.setFfToggled(!settings.ffToggled); return }
            Gamepad.Btn.R3 -> { if (down) settings.setGameAspect(if (settings.gameAspect == GameAspect.WIDE) GameAspect.ORIGINAL else GameAspect.WIDE); return }
            else -> {}
        }
        if (steering) {
            val k = when (b) {
                Gamepad.Btn.UP -> MenuKey.UP; Gamepad.Btn.DOWN -> MenuKey.DOWN
                Gamepad.Btn.LEFT -> MenuKey.LEFT; Gamepad.Btn.RIGHT -> MenuKey.RIGHT
                Gamepad.Btn.EAST -> MenuKey.A; Gamepad.Btn.SOUTH -> MenuKey.B
                Gamepad.Btn.LB -> MenuKey.L; Gamepad.Btn.RB -> MenuKey.R
                Gamepad.Btn.START -> MenuKey.START; Gamepad.Btn.BACK -> MenuKey.SELECT
                else -> return
            }
            if (down) menuKey(k, repeat)
            return
        }
        if (repeat) return
        val bit = when (b) {
            Gamepad.Btn.UP -> MgbaCore.Key.UP; Gamepad.Btn.DOWN -> MgbaCore.Key.DOWN
            Gamepad.Btn.LEFT -> MgbaCore.Key.LEFT; Gamepad.Btn.RIGHT -> MgbaCore.Key.RIGHT
            Gamepad.Btn.EAST -> MgbaCore.Key.A; Gamepad.Btn.SOUTH -> MgbaCore.Key.B
            Gamepad.Btn.LB -> MgbaCore.Key.L; Gamepad.Btn.RB -> MgbaCore.Key.R
            Gamepad.Btn.START -> MgbaCore.Key.START; Gamepad.Btn.BACK -> MgbaCore.Key.SELECT
            else -> return
        }
        emu.padKeys = if (down) emu.padKeys or bit else emu.padKeys and bit.inv()
    }

    /** The pad's companion button: in / out; locked beside the game it unlocks it, so the pad can steer it. */
    private fun padCompanion() {
        leaveHud()
        when (place) {
            CompanionPlace.POPUP -> closePopup()
            else -> moveCompanion(CompanionPlace.POPUP)
        }
    }

    fun stop() = emu.stop()
}

/**
 * The game: one window with the companion beside it by default; View > Companion (or F2) pops the companion out into
 * a window of its own for a second monitor and back, F3 hides / shows it. Closing the game goes back to the Library.
 */
@Composable
fun ApplicationScope.GameWindows(g: GameSession) {
    val snap by g.emu.store.snapshot.collectAsState()
    // Full screen from the start where asked (Batocera): a window with no window manager can't be made so later.
    val gameState = rememberWindowState(
        size = DpSize(1600.dp, 760.dp), position = WindowPosition(30.dp, 30.dp),
        placement = if (g.settings.displayMode == DisplayMode.FULLSCREEN) androidx.compose.ui.window.WindowPlacement.Fullscreen
            else androidx.compose.ui.window.WindowPlacement.Floating,
    )
    val companionState = rememberWindowState(size = DpSize(700.dp, 640.dp), position = WindowPosition(1660.dp, 30.dp))
    fun keysFor(state: androidx.compose.ui.window.WindowState) = { e: KeyEvent ->
        // The desktop's own Enter (a controller's A on the focused button): for Compose, never the game.
        if (Synthetic.active) false
        else if (e.key == Key.F11) {
            if (e.type == KeyEventType.KeyDown) {
                if (state === gameState) g.settings.setDisplayMode(if (g.settings.displayMode == DisplayMode.FULLSCREEN) DisplayMode.WINDOWED else DisplayMode.FULLSCREEN)
                else Monitors.toggleFullscreen(state)
            }
            true
        }
        else if (e.key == Key.F10 && state === gameState) {
            if (e.type == KeyEventType.KeyDown)
                g.settings.setDisplayMode(if (g.settings.displayMode == DisplayMode.BORDERLESS) DisplayMode.WINDOWED else DisplayMode.BORDERLESS)
            true
        }
        else if (e.key == Key.Escape) { if (e.type == KeyEventType.KeyDown) g.togglePopup(); true }
        else if (e.key == Key.H && state === gameState) { if (e.type == KeyEventType.KeyDown) g.toggleHudFocus(); true }
        else if (g.steering && g.popupKey(e)) true
        else gameKey(g.emu, e, g.settings, g.slots, g.place, g::moveCompanion)
    }
    // BORDERLESS has no title bar, which a window only takes when it's made: a new one each time the mode changes.
    val mode = g.settings.displayMode
    key(mode == DisplayMode.BORDERLESS) { Window(
        onCloseRequest = g.onEnd,
        title = "PokeDaisy - ${g.rom.nameWithoutExtension}",
        state = gameState,
        undecorated = mode == DisplayMode.BORDERLESS,
        onPreviewKeyEvent = keysFor(gameState),
    ) {
        // Where the windows go follows SINGLE SCREEN / SWAP SCREENS, whenever either changes (F2, the menus, SETTINGS).
        // (Inside the window: the application's own composition has no frame clock to run effects on.)
        LaunchedEffect(g.place == CompanionPlace.WINDOW, g.swapScreens) {
            Monitors.place(gameState, companionState, g.place == CompanionPlace.WINDOW, g.swapScreens)
        }
        // After placing (which floats the window): BORDERLESS / FULL SCREEN on top of where it went.
        LaunchedEffect(mode, g.place == CompanionPlace.WINDOW, g.swapScreens) { Monitors.display(gameState, mode) }
        // No menu bar in BORDERLESS / FULL SCREEN: the game fills the monitor (F10 / F11 go back, Esc has SETTINGS).
        if (mode == DisplayMode.WINDOWED) GameMenus(g.settings, g.slots, g.place, g::moveCompanion, g.onEnd, g::togglePopup)
        g.focusManager = LocalFocusManager.current
        g.awtWindow = window
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            // The companion's width at its shape (up to half the window; FILL: 40%), docked beside the game or sliding
            // over it as the side panel.
            val shape = g.settings.companionShape.aspect
            val panelW = if (shape == null) maxWidth * 0.4f else minOf(maxWidth * 0.5f, maxHeight * shape)
            val sideView = g.settings.companionWindow != true
            // The side panel's tabs at the companion's own scale.
            val panelPx = with(LocalDensity.current) { panelW.toPx() }
            val tabDensity = Density(sidePanelDensity(panelPx.toInt(), constraints.maxHeight))
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    GameScreen(g.emu, g.crash) { g.gameRect = it }
                    GameHud(g, snap)
                }
                if (g.place == CompanionPlace.DOCKED) Box(Modifier.width(panelW).fillMaxHeight()) {
                    CompanionPanel(snap, g.settings, g.slots, g.back, fill = true)
                }
            }
            if (sideView) CompositionLocalProvider(LocalDensity provides tabDensity) {
                // Locked beside the game: the padlock on its left edge unlocks it (it then slides over the game).
                if (g.place == CompanionPlace.DOCKED) Box(Modifier.fillMaxSize().padding(end = with(tabDensity) { panelPx.toDp() }), contentAlignment = Alignment.TopEnd) {
                    SidePanelHandle(docked = true) { g.moveCompanion(CompanionPlace.POPUP) }
                }
                // Closed: the tab on the right edge, bottom, slides it in (Esc too, or the HUD's COMPANION).
                if (g.place == CompanionPlace.HIDDEN) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    SidePanelOpenTab { g.moveCompanion(CompanionPlace.POPUP) }
                }
            }
            // The side panel, as on a one-screen phone: it slides in over the game's right side (the game keeps running
            // under it); the padlock locks it beside the game, the tab at its bottom left (or Esc) slides it away.
            androidx.compose.animation.AnimatedVisibility(
                visible = sideView && g.place == CompanionPlace.POPUP,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = androidx.compose.animation.slideInHorizontally { it },
                exit = androidx.compose.animation.slideOutHorizontally { it },
            ) {
                Row(Modifier.fillMaxHeight()) {
                    CompositionLocalProvider(LocalDensity provides tabDensity) {
                        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                            SidePanelHandle(docked = false) { g.moveCompanion(CompanionPlace.DOCKED) }
                            SidePanelCloseTab { g.closePopup() }
                        }
                    }
                    Box(Modifier.width(panelW).fillMaxHeight()) {
                        // The game's buttons (keyboard, controller) steer it while it's out: SidePanelHost's focus starts.
                        SidePanelHost(
                            window = false, keysEnabled = true, onClose = g::closePopup, keys = g.keys,
                            focusKeys = { key, _, start -> g.focusKey(key, start) },
                        ) {
                            Box(Modifier.fillMaxSize().onFocusChanged { g.popupFocused = it.hasFocus }.trapFocus(g.steering)) {
                                // Where the D-pad starts on a tab with no starting row of its own: its first stop after this.
                                Box(Modifier.size(1.dp).focusRequester(g.companionStart).focusable(enabled = g.steering))
                                CompanionPanel(snap, g.settings, g.slots, g.back, g.keys, fill = true)
                            }
                        }
                    }
                }
            }
            // The other COMPANION VIEW: the phone's window, framed in the middle over the dimmed game.
            if (!sideView && g.place == CompanionPlace.POPUP) {
                SidePanelHost(
                    window = true, onClose = g::closePopup, keys = g.keys, windowAspect = shape,
                    focusKeys = { key, _, start -> g.focusKey(key, start) },
                ) {
                    Box(Modifier.fillMaxSize().onFocusChanged { g.popupFocused = it.hasFocus }) {
                        CompanionScreen(snap, slots = g.slots, settings = g.settings, back = g.back, keys = g.keys, gamePick = g.emu.store.gamePick)
                    }
                }
            }
        }
    } }
    if (g.place == CompanionPlace.WINDOW) Window(
        // Closing it puts the companion back beside the game, rather than ending the game.
        onCloseRequest = { g.moveCompanion(CompanionPlace.DOCKED) },
        title = "PokeDaisy - companion (close to put it back beside the game)",
        state = companionState,
        onPreviewKeyEvent = keysFor(companionState),
    ) {
        MenuBar {
            Menu("Companion") {
                Item("Put back beside the game (F2)", onClick = { g.moveCompanion(CompanionPlace.DOCKED) })
                Item("Hide (F3)", onClick = { g.moveCompanion(CompanionPlace.HIDDEN) })
                CheckboxItem("Swap screens", checked = g.swapScreens, onCheckedChange = { g.settings.setSwapScreens(it) })
                Item("Full screen on / off (F11)", onClick = { Monitors.toggleFullscreen(companionState) })
            }
        }
        CompanionPanel(snap, g.settings, g.slots)
    }
}

/** The game window's menus: the things the companion's SETTINGS can't reach on a desktop (or that want a key). */
@Composable
fun FrameWindowScope.GameMenus(
    settings: DesktopSettings, slots: DesktopSlots, place: CompanionPlace,
    moveCompanion: (CompanionPlace) -> Unit, exit: () -> Unit, togglePopup: () -> Unit,
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
        RadioButtonItem("Companion over the game (Esc)", selected = place == CompanionPlace.POPUP, onClick = togglePopup)
        RadioButtonItem("Companion hidden (F3)", selected = place == CompanionPlace.HIDDEN, onClick = { moveCompanion(CompanionPlace.HIDDEN) })
        CheckboxItem("Swap screens (game on the second monitor)", checked = settings.swapScreens, enabled = place == CompanionPlace.WINDOW,
            onCheckedChange = { settings.setSwapScreens(it) })
        Separator()
        Menu("Companion shape") {
            CompanionShape.entries.forEach { c ->
                RadioButtonItem(c.label, selected = settings.companionShape == c, onClick = { settings.setCompanionShape(c) })
            }
        }
        CheckboxItem("HUD over the game", checked = settings.fieldHud, onCheckedChange = { settings.setFieldHud(it) })
        Separator()
        GameAspect.entries.forEach { a ->
            RadioButtonItem("Screen: ${a.label}" + if (a == GameAspect.WIDE) " (E)" else "", selected = settings.gameAspect == a,
                enabled = a != GameAspect.WIDE || settings.wideSupported, onClick = { settings.setGameAspect(a) })
        }
        Separator()
        DisplayMode.entries.forEach { d ->
            val key = when (d) { DisplayMode.BORDERLESS -> " (F10)"; DisplayMode.FULLSCREEN -> " (F11)"; else -> "" }
            RadioButtonItem("Window: ${d.label}$key", selected = settings.displayMode == d, onClick = { settings.setDisplayMode(d) })
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
    val scanned = File(dir, "scanned-$crc")
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
fun GameScreen(emu: Emulator, crash: Throwable?, onRect: (androidx.compose.ui.geometry.Rect) -> Unit = {}) {
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
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val img = image
        val bw = constraints.maxWidth.toFloat()
        val bh = constraints.maxHeight.toFloat()
        if (crash != null) Text("The game stopped: ${crash.message}", color = Color.White)
        else if (img != null) {
            val scale = min(bw / img.width, bh / img.height)
            // STRETCH: the GBA's picture made as wide as the area, at the area's height.
            val w = if (emu.stretch) bw.toInt() else (img.width * scale).toInt()
            val h = (img.height * scale).toInt()
            val x = ((bw - w) / 2).toInt()
            val y = ((bh - h) / 2).toInt()
            val rect = androidx.compose.ui.geometry.Rect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())
            LaunchedEffect(rect) { onRect(rect) }
            Canvas(Modifier.fillMaxSize()) {
                drawImage(img, dstOffset = IntOffset(x, y), dstSize = IntSize(w, h), filterQuality = FilterQuality.None)
            }
        }
    }
}

/**
 * The app's FIELD HUD over the game (SETTINGS > HUD): party, minimap, balls, BAG / WILD, and COMPANION, which pops the
 * companion up over the game (Esc). Sized to the picture as on the phone: about half a GBA pixel per HUD pixel.
 */
@Composable
fun GameHud(g: GameSession, snap: SnapshotView) {
    val onField by g.emu.onField.collectAsState()
    val rect = g.gameRect ?: return
    val shown = g.settings.fieldHud && onField && snap.hasData && !snap.unsupported && !snap.inBattle &&
        g.place != CompanionPlace.POPUP
    // The cursor goes with the HUD (a battle, a menu screen); a panel it opened (BAG) keeps it.
    g.hudShown = shown
    LaunchedEffect(shown) { if (!shown) g.leaveHud() }
    // The phone's 1080p screen: a game pixel of 6.75 px, the HUD's base density 2.75 (its GBA pixel 3 px).
    val gamePx = rect.height / Emulator.GBA_H
    val outer = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density((gamePx * 2.75f / 6.75f).coerceAtLeast(1f), outer.fontScale),
        LocalCompanionBack provides g.hudBack,
        // A controller walking the HUD: its buttons show which has the cursor.
        com.pokedaisy.app.companion.ui.LocalFocusRings provides g.hudFocus,
    ) {
        // The cursor stays in the HUD while the pad steers it (moving off its last button does nothing), and starts on
        // its first button: a stop in the middle of the picture takes the focus, then the next stop has it.
        val fm = LocalFocusManager.current
        LaunchedEffect(g.hudFocus) {
            if (g.hudFocus) runCatching { g.hudAnchor.requestFocus(); fm.moveFocus(FocusDirection.Next) }
        }
        Box(Modifier.fillMaxSize().trapFocus(g.hudFocus)) {
        Box(Modifier.offset { IntOffset(rect.center.x.toInt(), rect.center.y.toInt()) }.size(1.dp)
            .focusRequester(g.hudAnchor).focusable(enabled = g.hudFocus))
        FieldHud(
            snap, shown, g.settings.fieldHudStyle, frame = rect,
            // COMPANION while it isn't already beside the game, as the phone shows it while its panel is closed.
            onOpenCompanion = if (g.place == CompanionPlace.DOCKED) null else ({ g.togglePopup() }),
        )
        }
    }
}

/** The app's own companion, sized like the AYN Thor's bottom screen (1240 x 1080 at density 2.625) to fill its area,
 * with the desktop's SETTINGS and STATES bridges. */
@Composable
fun CompanionPanel(
    snap: SnapshotView, settings: DesktopSettings, slots: DesktopSlots, back: CompanionBack? = null,
    keys: CompanionKeys? = null,
    /** Beside the game (docked, the side panel): the companion fills its panel, whose width the shape already set. */
    fill: Boolean = false,
    /** WHICH GAME IS THIS? for a hack it doesn't know (BEST EFFORT): PICK A GAME, PLAYS AS. */
    gamePick: com.pokedaisy.app.companion.GamePickActions? = DesktopHost.game?.emu?.store?.gamePick,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val px = with(LocalDensity.current) { Pair(maxWidth.toPx(), maxHeight.toPx()) }
        // COMPANION SHAPE: the Thor's bottom screen's height in dp, as wide as the shape makes it (FILL: the area's).
        val aspect = (if (fill) null else settings.companionShape.aspect) ?: (px.first / px.second)
        val dpW = COMPANION_DP_H * aspect
        val density = min(px.first / dpW, px.second / COMPANION_DP_H)
        CompositionLocalProvider(LocalDensity provides Density(density)) {
            Box(Modifier.size(dpW.dp, COMPANION_DP_H.dp)) { CompanionScreen(snap, slots = slots, settings = settings, back = back, keys = keys, gamePick = gamePick) }
        }
    }
}

// The Thor's bottom screen in dp: 1240 x 1080 px at density 2.625.
const val COMPANION_DP_W = 1240f / 2.625f
const val COMPANION_DP_H = 1080f / 2.625f

/** Key presses the desktop makes itself (a controller's A = Enter on what has the focus), let through untouched. */
object Synthetic {
    @Volatile var active = false

    fun enter(w: java.awt.Window?) = press(w, java.awt.event.KeyEvent.VK_ENTER, '\n')
    fun escape(w: java.awt.Window?) = press(w, java.awt.event.KeyEvent.VK_ESCAPE, 27.toChar())

    private fun press(w: java.awt.Window?, vk: Int, ch: Char) {
        w ?: return
        val target = w.focusOwner ?: w
        active = true
        try {
            for (id in listOf(java.awt.event.KeyEvent.KEY_PRESSED, java.awt.event.KeyEvent.KEY_RELEASED))
                target.dispatchEvent(java.awt.event.KeyEvent(target, id, System.currentTimeMillis(), 0, vk, ch))
        } finally { active = false }
    }
}

/** While [on], the focus can't leave this box (the D-pad stops at its edge), and its children are one group. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.trapFocus(on: Boolean): Modifier = this
    .focusProperties { if (on) exit = { androidx.compose.ui.focus.FocusRequester.Cancel } }
    .focusGroup()

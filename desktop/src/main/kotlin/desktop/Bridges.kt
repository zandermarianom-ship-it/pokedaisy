package desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pokedaisy.app.GbaControls
import com.pokedaisy.app.Hotkeys
import com.pokedaisy.app.MgbaCore
import com.pokedaisy.app.companion.CompanionSettings
import com.pokedaisy.app.companion.DEFAULT_COMPANION_TABS
import com.pokedaisy.app.companion.FfMode
import com.pokedaisy.app.companion.FfMusicMode
import com.pokedaisy.app.companion.GameAspect
import com.pokedaisy.app.companion.GridStrength
import com.pokedaisy.app.companion.ScreenFilter
import com.pokedaisy.app.companion.Smoothing
import com.pokedaisy.app.companion.StateSlots
import java.io.File
import java.util.Properties
import javax.imageio.ImageIO

/** The desktop's settings, kept in userdata/desktop.properties between runs (Android keeps them in Prefs). */
class DesktopConfig(private val file: File) {
    private val p = Properties().apply { runCatching { file.inputStream().use { load(it) } } }
    fun get(key: String): String? = p.getProperty(key)
    fun set(key: String, value: String) {
        p.setProperty(key, value)
        runCatching { file.parentFile?.mkdirs(); file.outputStream().use { p.store(it, "PokeDaisy desktop") } }
    }
    fun bool(key: String, default: Boolean) = get(key)?.toBooleanStrictOrNull() ?: default
}

/** Where the companion is: beside the game in its window, in a window of its own (a second monitor), hidden, or
 * popped up over the game (the phone's one-screen window: Esc, like the phone's BACK). */
enum class CompanionPlace { DOCKED, WINDOW, HIDDEN, POPUP }

/** The companion's shape (width / height); null = whatever its area is. */
enum class CompanionShape(val label: String, val aspect: Float?) {
    THOR("Thor bottom screen (1240 x 1080)", 1240f / 1080f),
    FOUR_THREE("4:3", 4f / 3f),
    SIXTEEN_TEN("16:10", 16f / 10f),
    WIDE("16:9", 16f / 9f),
    FILL("Fill its area", null),
}

/** How the game's window sits on its monitor. */
enum class DisplayMode(val label: String) { WINDOWED("Windowed"), BORDERLESS("Borderless (fills the monitor)"), FULLSCREEN("Full screen") }

/** The keyboard, as the companion's GAME BUTTONS / HOTKEYS rows show it (fixed for now: no rebinding yet). */
val DESKTOP_BUTTONS = mapOf(
    GbaControls.Btn.A to listOf("X"), GbaControls.Btn.B to listOf("Z"),
    GbaControls.Btn.L to listOf("A"), GbaControls.Btn.R to listOf("S"),
    GbaControls.Btn.START to listOf("ENTER"), GbaControls.Btn.SELECT to listOf("BACKSPACE"),
    GbaControls.Btn.START2 to emptyList(), GbaControls.Btn.SELECT2 to emptyList(),
    GbaControls.Btn.TURBO_A to emptyList(), GbaControls.Btn.TURBO_B to emptyList(),
)
val DESKTOP_HOTKEYS = mapOf(
    Hotkeys.Action.SAVE_STATE to listOf("F5"), Hotkeys.Action.LOAD_STATE to listOf("F7"),
    Hotkeys.Action.SLOT_NEXT to listOf("F8"), Hotkeys.Action.SLOT_PREV to listOf("F6"),
    Hotkeys.Action.FF_HOLD to listOf("SPACE"), Hotkeys.Action.FF_TOGGLE to listOf("TAB"),
)

/**
 * The companion's SETTINGS tab on a desktop: what the Android game screen (PokeDaisyActivity) does for it, for the parts
 * the desktop has - ASPECT (ORIGINAL / STRETCH / EXPAND), fast-forward, the tab bar, hints, RESTART / CLOSE GAME. Every
 * value is Compose state, so a change shows on both windows at once.
 */
class DesktopSettings(
    private val emu: Emulator,
    private val config: DesktopConfig,
    private val onClose: () -> Unit,
    private val onHideCompanion: () -> Unit,
    private val companionOwnWindow: () -> Boolean = { false },
    private val setCompanionOwnWindow: (Boolean) -> Unit = {},
    private val swap: () -> Boolean = { false },
    private val setSwap: (Boolean) -> Unit = {},
) : CompanionSettings {
    private var aspectState by mutableStateOf(
        runCatching { GameAspect.valueOf(config.get("aspect") ?: "") }.getOrDefault(GameAspect.WIDE)
    )
    private var ffToggledState by mutableStateOf(false)
    private var ffSpeedState by mutableStateOf(config.get("ffSpeed")?.toFloatOrNull() ?: 4f)
    private var hints by mutableStateOf(config.bool("hints", true))
    private var tabs by mutableStateOf(config.get("tabs")?.split(',')?.filter { it.isNotBlank() } ?: DEFAULT_COMPANION_TABS)
    private var foeIvs by mutableStateOf(config.bool("foeIvs", false))
    private var foeDetailsOn by mutableStateOf(config.bool("foeDetails", false))

    init { apply() }

    private fun apply() {
        emu.expand = aspectState == GameAspect.WIDE
        emu.stretch = aspectState == GameAspect.STRETCH
        emu.ffToggled = ffToggledState
        emu.ffSpeed = ffSpeedState.toInt().coerceIn(2, 8)
    }

    // ASPECT
    override val gameAspect get() = aspectState
    override fun setGameAspect(aspect: GameAspect) { aspectState = aspect; config.set("aspect", aspect.name); apply() }
    override val wideSupported get() = emu.expandKnown

    // FF / SPEED
    override val ffMaxSpeed get() = ffSpeedState
    override fun setFfMaxSpeed(v: Float) { ffSpeedState = if (v <= 0f) 8f else v; config.set("ffSpeed", ffSpeedState.toString()); apply() }
    override val ffToggled get() = ffToggledState
    override fun setFfToggled(on: Boolean) { ffToggledState = on; apply() }
    override val ffMusicMode get() = FfMusicMode.OFF
    override fun setFfMusicMode(mode: FfMusicMode) {}
    override val ffMode get() = FfMode.NORMAL
    override fun setFfMode(mode: FfMode) {}

    // CONTROLS (shown, not rebindable yet)
    override val touchControlsMode get() = 2
    override fun setTouchControlsMode(v: Int) {}
    override fun gbaControlBindings() = DESKTOP_BUTTONS
    override fun setGbaControlBinding(btn: GbaControls.Btn, keyName: String) {}
    override val hotkeysEnabled get() = true
    override fun setHotkeysEnabled(on: Boolean) {}
    override fun hotkeyBindings() = Hotkeys.Action.entries.associateWith { DESKTOP_HOTKEYS[it] ?: emptyList() }
    override fun setHotkeyBinding(action: Hotkeys.Action, keyNames: List<String>?, takeFrom: List<Hotkeys.Action>) {}

    // GAME
    override fun restartGame() = emu.post { emu.restartNow() }
    override fun closeGame() = onClose()
    override val canCloseCompanion get() = true
    override fun closeCompanion() = onHideCompanion()

    // SCREEN: a PC can always put a window on another monitor, so SINGLE SCREEN (off = the companion in a window of its
    // own) and SWAP SCREENS (the game on the second monitor) always show.
    override val hasSecondScreen get() = true
    override val singleScreen get() = !companionOwnWindow()
    override fun setSingleScreen(on: Boolean) = setCompanionOwnWindow(!on)
    override val swapScreens get() = swap()
    override fun setSwapScreens(on: Boolean) = setSwap(on)

    // HUD: the app's own setting and style (Prefs), so the Library's Settings and the companion's agree.
    private val prefs = com.pokedaisy.app.Prefs(android.content.Context())
    private var hudOn by mutableStateOf(prefs.fieldHud)
    private var hudStyle by mutableStateOf(prefs.fieldHudStyle)
    override val fieldHud get() = hudOn
    override fun setFieldHud(on: Boolean) { hudOn = on; prefs.fieldHud = on }
    override val fieldHudStyle get() = hudStyle
    override fun setFieldHudStyle(style: com.pokedaisy.app.companion.FieldHudStyle) { hudStyle = style; prefs.fieldHudStyle = style }

    /** The desktop's COMPANION SHAPE (View menu): the companion's width / height wherever it is; FILL = its whole area. */
    private var shapeState by mutableStateOf(runCatching { CompanionShape.valueOf(config.get("companionShape") ?: "") }.getOrDefault(CompanionShape.WIDE))
    val companionShape get() = shapeState
    fun setCompanionShape(s: CompanionShape) { shapeState = s; config.set("companionShape", s.name) }

    /** The game window: WINDOWED, BORDERLESS (fills its monitor, no title bar) or FULLSCREEN (View menu, F11). */
    private var modeState by mutableStateOf(runCatching { DisplayMode.valueOf(config.get("displayMode") ?: "") }.getOrDefault(DisplayMode.WINDOWED))
    val displayMode get() = modeState
    fun setDisplayMode(m: DisplayMode) { modeState = m; config.set("displayMode", m.name) }

    /** COMPANION VIEW: the side panel (the desktop's default) or the window in the middle over the game. */
    private var windowView by mutableStateOf(config.bool("companionWindowView", false))
    override val companionWindow: Boolean? get() = windowView
    override fun setCompanionWindow(on: Boolean) { windowView = on; config.set("companionWindowView", on.toString()) }

    // COMPANION
    override val showHints get() = hints
    override fun setShowHints(on: Boolean) { hints = on; config.set("hints", on.toString()) }
    override val showFoeIvs get() = foeIvs
    override fun setShowFoeIvs(on: Boolean) { foeIvs = on; config.set("foeIvs", on.toString()) }
    override val foeDetails get() = foeDetailsOn
    override fun setFoeDetails(on: Boolean) { foeDetailsOn = on; config.set("foeDetails", on.toString()) }
    override val companionTabs get() = tabs
    override fun setCompanionTabs(tabs: List<String>) { this.tabs = tabs; config.set("tabs", tabs.joinToString(",")) }
    override fun guideNoticeAccepted(game: String) = config.bool("guideNotice.$game", false)
    override fun acceptGuideNotice(game: String) = config.set("guideNotice.$game", "true")

    // Android-only for now (no shaders, overlays, status bar, HUD or click sounds on the desktop yet).
    override val clickSound get() = false
    override fun setClickSound(on: Boolean) {}
    override val statusBar get() = false
    override fun setStatusBar(on: Boolean) {}
    override val gbaColors get() = false
    override fun setGbaColors(on: Boolean) {}
    override val screenFilter get() = ScreenFilter.NONE
    override fun setScreenFilter(filter: ScreenFilter) {}
    override val companionShaders get() = false
    override fun setCompanionShaders(on: Boolean) {}
    override val gridStrength get() = GridStrength.MEDIUM
    override fun setGridStrength(strength: GridStrength) {}
    override val smoothing get() = Smoothing.OFF
    override fun setSmoothing(mode: Smoothing) {}
    override val smoothingAvailable get() = false

    override val gameName get() = emu.rom.nameWithoutExtension
    override val romFileName get() = emu.rom.name
}

/**
 * The companion's STATES tab: 10 save-state slots per game in userdata/states (mGBA's own state files, with a
 * screenshot beside each), saved / loaded between frames on the emulator thread. UNDO SAVE puts the slot's previous
 * state back; UNDO LOAD goes back to where the game was just before the load.
 */
class DesktopSlots(private val emu: Emulator, dir: File) : StateSlots {
    private val dir = File(dir, emu.rom.nameWithoutExtension).apply { mkdirs() }
    private var current by mutableStateOf(0)
    private var version by mutableStateOf(0)   // bumped after a save / load: the tab re-lists

    private fun state(i: Int) = File(dir, "slot$i.ss")
    private fun thumb(i: Int) = File(dir, "slot$i.png")
    private val undoSaveFile get() = File(dir, "undo-save.ss")
    private var undoSaveSlot = -1
    private val beforeLoad get() = File(dir, "undo-load.ss")

    override fun list(): List<StateSlots.Slot> {
        version // read, so a change re-lists
        return (0..9).map { i ->
            val s = state(i)
            val t = thumb(i)
            StateSlots.Slot(i, s.isFile, s.lastModified(), t.takeIf { it.isFile }?.absolutePath, t.lastModified())
        }
    }

    override val currentIndex get() = current

    fun select(i: Int) { current = (i + 10) % 10 }

    override fun requestSave(index: Int) {
        current = index
        emu.post {
            val s = state(index)
            if (s.isFile) { s.copyTo(undoSaveFile, overwrite = true); undoSaveSlot = index } else undoSaveSlot = -1
            if (MgbaCore.pkSaveState(s.absolutePath)) emu.lastGbaFrame?.let { ImageIO.write(it, "png", thumb(index)) }
            println("saved state $index: ${s.isFile}")
            version++
        }
    }

    override fun requestLoad(index: Int) {
        current = index
        emu.post {
            val s = state(index)
            if (!s.isFile) return@post
            MgbaCore.pkSaveState(beforeLoad.absolutePath)
            println("loaded state $index: ${MgbaCore.pkLoadState(s.absolutePath)}")
            version++
        }
    }

    override fun requestUndoSave() {
        val slot = undoSaveSlot
        if (slot < 0 || !undoSaveFile.isFile) return
        undoSaveFile.copyTo(state(slot), overwrite = true)
        undoSaveSlot = -1
        version++
    }

    override fun requestUndoLoad() {
        emu.post { if (beforeLoad.isFile) MgbaCore.pkLoadState(beforeLoad.absolutePath); version++ }
    }
}

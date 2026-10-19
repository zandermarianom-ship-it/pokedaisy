package desktop

import androidx.compose.foundation.Canvas
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

/** PokeDaisy desktop prototype: `run -Prom=game.gba [-Psave=game.sav]` opens the game and the companion. */
fun main(args: Array<String>) {
    val rom = File(args.firstOrNull() ?: error("usage: desktop <rom.gba> [save]")).absoluteFile
    require(rom.isFile) { "no ROM at $rom" }
    val save = args.getOrNull(1)?.let(::File) ?: defaultSave(rom)
    prepareArt(rom)
    createSharedUiState()

    val emu = Emulator(rom, save)
    var crash by mutableStateOf<Throwable?>(null)
    emu.start(sound = true) { crash = it; it.printStackTrace() }

    application {
        val exit = { emu.stop(); exitApplication() }
        Window(
            onCloseRequest = exit,
            title = "PokeDaisy - ${rom.nameWithoutExtension}",
            state = rememberWindowState(size = DpSize(1280.dp, 760.dp), position = WindowPosition(40.dp, 40.dp)),
            onPreviewKeyEvent = { gameKey(emu, it) },
        ) {
            GameScreen(emu, crash)
        }
        Window(
            onCloseRequest = exit,
            title = "PokeDaisy - companion",
            state = rememberWindowState(size = DpSize(700.dp, 640.dp), position = WindowPosition(1340.dp, 40.dp)),
            // The companion window passes the game's keys on too, so either window can have focus.
            onPreviewKeyEvent = { gameKey(emu, it) },
        ) {
            val snap by emu.store.snapshot.collectAsState()
            CompanionWindow(snap)
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

/** The save beside the ROM: RetroArch's .srm if there is one, else mGBA's .sav (created on the first save). */
fun defaultSave(rom: File): File {
    val base = rom.nameWithoutExtension
    return listOf("srm", "sav").map { File(rom.parentFile, "$base.$it") }.firstOrNull { it.isFile }
        ?: File(rom.parentFile, "$base.sav")
}

/** The companion's game art (party slots, bag, map...) is rebuilt from the player's own ROM, as on Android. */
fun prepareArt(rom: File) {
    val files = android.content.Context().filesDir
    val dir = RomArt.dir(files)
    if (!RomArt.OUTPUTS.all { File(dir, it).isFile }) {
        val wrote = RomArt.extractTo(rom.readBytes(), dir)
        println("companion art: ${wrote.size} files from ${rom.name}")
    }
    // A FireRed-engine hack's own region map (Unbound, Odyssey...), read from its ROM as the app does at launch.
    RomRegionMap.current = RomRegionMap.loadNow(files, com.pokedaisy.app.SaveStates.crc32(rom), rom)
}

/** Keyboard -> GBA keys: arrows, X = A, Z = B, Enter = START, Backspace = SELECT, A = L, S = R; E toggles EXPAND. */
fun gameKey(emu: Emulator, e: KeyEvent): Boolean {
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
        Key.E -> {
            if (e.type == KeyEventType.KeyDown) emu.expand = !emu.expand
            return true
        }
        else -> return false
    }
    emu.keys = if (e.type == KeyEventType.KeyDown) emu.keys or bit else emu.keys and bit.inv()
    return true
}

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
            val w = (img.width * scale).toInt()
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

/** The app's own companion, sized like the AYN Thor's bottom screen (1240 x 1080 at density 2.625) to fill the window. */
@Composable
fun CompanionWindow(snap: SnapshotView) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val px = with(LocalDensity.current) { Pair(maxWidth.toPx(), maxHeight.toPx()) }
        val density = min(px.first / COMPANION_DP_W, px.second / COMPANION_DP_H)
        CompositionLocalProvider(LocalDensity provides Density(density)) {
            Box(Modifier.fillMaxSize()) { CompanionScreen(snap) }
        }
    }
}

// The Thor's bottom screen in dp: 1240 x 1080 px at density 2.625.
const val COMPANION_DP_W = 1240f / 2.625f
const val COMPANION_DP_H = 1080f / 2.625f

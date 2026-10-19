package desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.pokedaisy.app.MgbaCore
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.ui.CompanionScreen
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.imageio.ImageIO

/**
 * Headless self-test: boots the ROM (and save), plays a script and writes what the windows would show -
 * the game frame (EXPAND) and the companion on each tab - as PNGs, plus what the companion read.
 *
 * Script lines: `wait N`, `press KEY [N]` (held N frames, default 4, then released 12), `hold KEY N`,
 * `shot NAME` (the game frame), `companion NAME [TAB]` (the companion on TAB, default PARTY). Keys:
 * A B START SELECT UP DOWN LEFT RIGHT L R.
 */
fun main(args: Array<String>) {
    val rom = File(args.firstOrNull() ?: error("usage: selftest <rom.gba> [save]")).absoluteFile
    val save = args.getOrNull(1)?.let(::File) ?: defaultSave(rom)
    val out = File(System.getProperty("out") ?: "build/check").apply { mkdirs() }
    val script = System.getProperty("script").orEmpty().takeIf { it.isNotBlank() }?.let { File(it).readText() } ?: DEFAULT_SCRIPT

    prepareArt(rom)
    val emu = Emulator(rom, save)
    emu.boot(sound = false)
    var last: SnapshotView? = null
    var failed = 0

    for (raw in script.lines()) {
        val parts = raw.substringBefore('#').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) continue
        when (parts[0]) {
            "wait" -> repeat(parts[1].toInt()) { emu.step() }
            "press", "hold" -> {
                val bit = keyBit(parts[1])
                val n = parts.getOrNull(2)?.toInt() ?: 4
                emu.keys = bit
                repeat(n) { emu.step() }
                emu.keys = 0
                if (parts[0] == "press") repeat(12) { emu.step() }
            }
            "shot" -> {
                val f = emu.frame.get()
                if (f == null) { println("${parts[1]}: no frame yet"); failed++; continue }
                ImageIO.write(f, "png", File(out, "game-${parts[1]}.png"))
                println("game-${parts[1]}.png: ${f.width}x${f.height} at frame ${emu.frames}, wide ${MgbaCore.pkWideWidth()}x${MgbaCore.pkWideHeight()}, scene ${MgbaCore.pkWideScene()}")
            }
            "companion" -> {
                val snap = emu.store.refresh().also { last = it }
                describe(snap)
                val tab = parts.getOrNull(2) ?: "PARTY"
                runCatching { renderCompanion(snap, tab, File(out, "companion-${parts[1]}.png")) }
                    .onFailure { failed++; println("companion-${parts[1]}: FAILED $it"); it.printStackTrace() }
            }
            else -> error("unknown script line: $raw")
        }
    }
    println("ran ${emu.frames} frames; game ${last?.game}")
    MgbaCore.pkDeinit()
    System.exit(if (failed > 0) 1 else 0)
}

fun keyBit(name: String) = when (name.uppercase()) {
    "A" -> MgbaCore.Key.A; "B" -> MgbaCore.Key.B; "START" -> MgbaCore.Key.START; "SELECT" -> MgbaCore.Key.SELECT
    "UP" -> MgbaCore.Key.UP; "DOWN" -> MgbaCore.Key.DOWN; "LEFT" -> MgbaCore.Key.LEFT; "RIGHT" -> MgbaCore.Key.RIGHT
    "L" -> MgbaCore.Key.L; "R" -> MgbaCore.Key.R
    else -> error("unknown key $name")
}

/** What the companion read from the game's memory, in words. */
fun describe(s: SnapshotView) {
    println("companion: game=${s.game} connected=${s.connected} error=${s.error} unsupported=${s.unsupported}")
    println("  location=${s.location} (section ${s.regionMapSectionId}) at ${s.x},${s.y} inBattle=${s.inBattle} items=${s.items.size}")
    // Independent of the companion's decoding: FireRed's (and CFRU hacks') gPlayerPartyCount, straight from RAM.
    val raw = MgbaCore.pkReadBytes(0x02024029L, 1)?.firstOrNull()?.toInt()
    println("  raw RAM: party count byte = $raw; companion read ${s.party.size} Pokemon")
    s.party.forEachIndexed { i, m -> println("  party $i: ${m.name} (species ${m.species}) Lv${m.level} HP ${m.hp}/${m.maxHp}") }
}

/** The companion at the Thor bottom screen's size and density, after its art and animations have settled. */
fun renderCompanion(snap: SnapshotView, tab: String, file: File) {
    val scene = ImageComposeScene(1240, 1080, Density(2.625f)) { CompanionScreen(snap, initialTab = tab) }
    try {
        var t = 0L
        repeat(30) { scene.render(t); t += 16_666_667L }
        Thread.sleep(400) // bitmaps load on Dispatchers.IO
        repeat(30) { scene.render(t); t += 16_666_667L }
        val img = scene.render(t)
        file.writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        println("${file.name}: written ($tab)")
    } finally {
        scene.close()
    }
}

/** Boot to the title, CONTINUE the save, then walk a little: EXPAND on the field, the companion on its tabs. */
val DEFAULT_SCRIPT = """
    wait 400
    shot intro
    press START
    wait 120
    press A
    wait 150
    shot menu
    press A
    wait 240
    press A
    wait 120
    shot field
    hold RIGHT 48
    wait 20
    shot walked
    companion party PARTY
    companion bag ITEMS
    companion map MAP
""".trimIndent()

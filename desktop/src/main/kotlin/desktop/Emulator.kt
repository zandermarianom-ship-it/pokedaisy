package desktop

import com.pokedaisy.app.MgbaCore
import com.pokedaisy.app.SaveStates
import com.pokedaisy.app.WideScreen
import com.pokedaisy.app.companion.TelemetryStore
import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * The game on a desktop: the app's own native core (libpokedaisy, the same pokedaisy_jni.c) driven
 * from one emulator thread, like the app's EmulatorEngine but much simpler - fixed 1x speed, no FF,
 * no rewind. Every core call happens on that thread; the windows only read [frame] and set [keys].
 */
class Emulator(val rom: File, val save: File?) {
    /** The companion's data, refreshed from the game's memory between frames (TelemetryStore.refresh). */
    val store = TelemetryStore()

    /** The last frame drawn: EXPAND's wide window when the game has it, else the GBA's own 240 x 160. */
    val frame = AtomicReference<BufferedImage?>(null)

    /** GBA keys held now (MgbaCore.Key bits), set by the game window. */
    @Volatile var keys = 0

    /** SETTINGS > ASPECT > EXPAND: the overworld drawn past the GBA's edges, for a 16:9 window. */
    @Volatile var expand = true

    /** Whether pk_wide.c has this ROM's tables (EXPAND does nothing otherwise). */
    var expandKnown = false
        private set

    var frames = 0L
        private set

    @Volatile private var running = false
    private var thread: Thread? = null
    private var audio: SourceDataLine? = null

    /** Loads the ROM and its save. Call before [start] (or [step] for headless runs), on the thread that will run it. */
    fun boot(sound: Boolean) {
        require(MgbaCore.pkInit(rom.absolutePath, save?.absolutePath)) { "the core couldn't load ${rom.name}" }
        val crc = SaveStates.crc32(rom)
        val wide = WideScreen.gameFor(crc)
        expandKnown = wide != WideScreen.OFF
        MgbaCore.pkWideConfigure(wide)
        // The wide window: 16:9 at the GBA's height, the game's own 240 px in the middle.
        val w = WIDE_W
        MgbaCore.pkWideSetView(-(w - GBA_W) / 2, 0, w, GBA_H)
        if (sound) audio = openAudio(MgbaCore.pkSampleRate())
        println("booted ${rom.name}: code ${MgbaCore.pkRomCode()}, CRC32 $crc, EXPAND ${if (expandKnown) "available" else "not available"}")
    }

    /** Runs one frame with [keys], publishes it, plays its sound; samples the companion every [SAMPLE_EVERY] frames. */
    fun step() {
        MgbaCore.pkSetKeys(keys)
        MgbaCore.pkWideSetDraw(true)
        MgbaCore.pkRunFrame()
        frames++
        publish()
        audio?.let { drainAudio(it) } ?: MgbaCore.pkReadAudio(audioBuf)
        if (frames % SAMPLE_EVERY == 0L) runCatching { store.refresh() }.onFailure { println("companion sample failed: $it") }
    }

    /** Real time on its own thread: boot, then a frame every 1 / 59.73 s until [stop]. */
    fun start(sound: Boolean, onError: (Throwable) -> Unit) {
        running = true
        thread = Thread({
            try {
                boot(sound)
                var next = System.nanoTime()
                while (running) {
                    step()
                    next += FRAME_NANOS
                    val wait = next - System.nanoTime()
                    if (wait > 0) Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                    else if (wait < -FRAME_NANOS * 5) next = System.nanoTime() // fell behind: don't race to catch up
                }
                // A save the game is writing must reach the file before the core goes.
                MgbaCore.pkFinishSave(600)
                MgbaCore.pkDeinit()
            } catch (t: Throwable) {
                onError(t)
            } finally {
                audio?.close()
            }
        }, "emulator").apply { isDaemon = false; start() }
    }

    fun stop() {
        running = false
        thread?.join(5000)
    }

    private val audioBuf = ShortArray(8192)
    private val audioBytes = ByteArray(audioBuf.size * 2)

    private fun drainAudio(line: SourceDataLine) {
        val n = MgbaCore.pkReadAudio(audioBuf)
        if (n <= 0) return
        for (i in 0 until n) {
            val s = audioBuf[i].toInt()
            audioBytes[i * 2] = s.toByte()
            audioBytes[i * 2 + 1] = (s shr 8).toByte()
        }
        // Never block the frame loop on the sound card: drop what doesn't fit.
        val len = minOf(n * 2, line.available() and 3.inv())
        if (len > 0) line.write(audioBytes, 0, len)
    }

    private fun openAudio(rate: Int): SourceDataLine? = runCatching {
        val fmt = AudioFormat(rate.toFloat(), 16, 2, true, false)
        AudioSystem.getSourceDataLine(fmt).apply { open(fmt, rate / 5 * 4); start() }
    }.onFailure { println("no sound: ${it.message}") }.getOrNull()

    private fun publish() {
        val ww = MgbaCore.pkWideWidth()
        val wh = MgbaCore.pkWideHeight()
        val wide = expand && ww > 0 && wh > 0
        val buf = (if (wide) MgbaCore.pkWideBuffer() else MgbaCore.pkVideoBuffer()) ?: return
        val w = if (wide) ww else MgbaCore.pkVideoWidth()
        val h = if (wide) wh else MgbaCore.pkVideoHeight()
        val px = buf.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        val out = IntArray(w * h)
        for (i in out.indices) {
            // The core's pixels are R, G, B, then a byte mGBA uses for the layer: opaque ARGB for AWT.
            val p = px.get(i)
            out[i] = (0xFF shl 24) or ((p and 0xFF) shl 16) or (p and 0xFF00) or ((p ushr 16) and 0xFF)
        }
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, w, h, out, 0, w)
        frame.set(img)
    }

    companion object {
        const val GBA_W = 240
        const val GBA_H = 160
        /** 16:9 at 160 tall (284.4), rounded to an even width so the game sits exactly in the middle. */
        const val WIDE_W = 284
        const val FRAME_NANOS = 16_742_706L // 280896 cycles at 16.78 MHz: 59.7275 frames a second
        const val SAMPLE_EVERY = 30L
    }
}

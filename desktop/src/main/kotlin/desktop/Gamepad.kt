package desktop

import com.studiohartman.jamepad.ControllerManager
import javax.swing.SwingUtilities

/**
 * Game controllers (Xbox / PlayStation / 8BitDo / Batocera's pads...), read through SDL2's GameController API
 * (Jamepad, SDL built in for Windows and Linux x64 / arm64). SDL names buttons by position, Xbox style: [Btn.SOUTH] is
 * Xbox's A / Nintendo's B, [Btn.EAST] Xbox's B / Nintendo's A - mapped like RetroArch (EAST = the GBA's A).
 *
 * One thread polls the first connected pad ~120 times a second and hands each change to [listener] on the UI thread;
 * a direction held down repeats (after [REPEAT_DELAY_MS], every [REPEAT_EVERY_MS]), so a cursor runs down a list.
 * The left stick counts as the D-pad.
 */
object Gamepad {
    enum class Btn {
        SOUTH, EAST, WEST, NORTH, LB, RB, LT, RT, START, BACK, GUIDE, L3, R3, UP, DOWN, LEFT, RIGHT,
        /** SELECT + START pressed together (seen where the buttons are read, so a quick release can't miss it). */
        EXIT,
    }

    /** (button, pressed, a held direction repeating) - on the UI thread. */
    @Volatile var listener: ((Btn, Boolean, Boolean) -> Unit)? = null

    /** The connected pad's name, null with none (SETTINGS / the log say which). */
    @Volatile var name: String? = null
        private set

    /** The buttons held now (for combos: SELECT + START): the pad's and the test pipe's. */
    val held: Set<Btn> get() = padHeld + pipeHeld
    @Volatile private var padHeld: Set<Btn> = emptySet()
    @Volatile private var pipeHeld: Set<Btn> = emptySet()


    private val DIRECTIONS = setOf(Btn.UP, Btn.DOWN, Btn.LEFT, Btn.RIGHT)
    private const val REPEAT_DELAY_MS = 380L
    private const val REPEAT_EVERY_MS = 110L
    private const val STICK = 0.55f
    private const val TRIGGER = 0.5f

    fun start() {
        // Testing without a pad: -Dpadfifo=PATH (a named pipe) takes lines like "EAST down" / "UP up".
        System.getProperty("padfifo")?.let { path ->
            Thread({
                while (true) runCatching {
                    java.io.File(path).forEachLine { line ->
                        val (b, d) = line.trim().split(' ').let { it[0] to it.getOrElse(1) { "down" } }
                        runCatching {
                            val btn = Btn.valueOf(b)
                            pipeHeld = if (d == "down") pipeHeld + btn else pipeHeld - btn
                            post(btn, d == "down", false)
                        }
                    }
                }
            }, "padfifo").apply { isDaemon = true; start() }
        }
        Thread({
            val m = runCatching { ControllerManager().also { it.initSDLGamepad() } }
                .onFailure { println("controllers: SDL didn't start ($it); keyboard only") }.getOrNull() ?: return@Thread
            var held = emptySet<Btn>()
            val since = HashMap<Btn, Long>()
            val repeated = HashMap<Btn, Long>()
            while (true) {
                runCatching {
                    m.update()
                    val st = (0 until m.numControllers).map { m.getState(it) }.firstOrNull { it.isConnected }
                    val nowName = st?.controllerType
                    if (nowName != name) {
                        name = nowName
                        println(if (nowName != null) "controller connected: $nowName" else "controller disconnected")
                    }
                    val now = buildSet {
                        if (st != null) {
                            if (st.a) add(Btn.SOUTH); if (st.b) add(Btn.EAST); if (st.x) add(Btn.WEST); if (st.y) add(Btn.NORTH)
                            if (st.lb) add(Btn.LB); if (st.rb) add(Btn.RB)
                            if (st.leftTrigger > TRIGGER) add(Btn.LT); if (st.rightTrigger > TRIGGER) add(Btn.RT)
                            if (st.start) add(Btn.START); if (st.back) add(Btn.BACK); if (st.guide) add(Btn.GUIDE)
                            if (st.leftStickClick) add(Btn.L3); if (st.rightStickClick) add(Btn.R3)
                            // SDL's Y axis runs down.
                            if (st.dpadUp || st.leftStickY < -STICK) add(Btn.UP)
                            if (st.dpadDown || st.leftStickY > STICK) add(Btn.DOWN)
                            if (st.dpadLeft || st.leftStickX < -STICK) add(Btn.LEFT)
                            if (st.dpadRight || st.leftStickX > STICK) add(Btn.RIGHT)
                        }
                    }
                    padHeld = now
                    val t = System.currentTimeMillis()
                    for (b in now - held) { since[b] = t; repeated[b] = t; post(b, true, false) }
                    for (b in held - now) post(b, false, false)
                    for (b in now intersect held) if (b in DIRECTIONS) {
                        if (t - since.getValue(b) >= REPEAT_DELAY_MS && t - repeated.getValue(b) >= REPEAT_EVERY_MS) {
                            repeated[b] = t
                            post(b, true, true)
                        }
                    }
                    held = now
                }.onFailure { println("controllers: $it") }
                Thread.sleep(8)
            }
        }, "gamepad").apply { isDaemon = true; start() }
    }

    /** HOME / GUIDE went down and nothing was pressed with it yet: on its release it's a press of its own. */
    @Volatile private var guideAlone = false

    private fun post(b: Btn, down: Boolean, repeat: Boolean) {
        val l = listener ?: return
        fun send(btn: Btn, d: Boolean) =
            SwingUtilities.invokeLater { runCatching { l(btn, d, false) }.onFailure { it.printStackTrace() } }
        // Batocera's hotkey (HOME on most pads): alone it's its own button, sent when let go; with START, the way out.
        if (b == Btn.GUIDE) {
            if (down && !repeat) guideAlone = true
            else if (!down && guideAlone) { guideAlone = false; send(Btn.GUIDE, true); send(Btn.GUIDE, false) }
            return
        }
        if (down && Btn.GUIDE in held) {
            guideAlone = false
            if (b == Btn.START && !repeat) { send(Btn.EXIT, true); return }
        }
        // SELECT + START: the second one's press is the combo, not a button of its own.
        if (down && !repeat && (b == Btn.START && Btn.BACK in held || b == Btn.BACK && Btn.START in held)) {
            send(Btn.EXIT, true)
            return
        }
        SwingUtilities.invokeLater { runCatching { l(b, down, repeat) }.onFailure { it.printStackTrace() } }
    }
}

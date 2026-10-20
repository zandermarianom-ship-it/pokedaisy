package desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/**
 * The PC's monitors, for SETTINGS > SINGLE SCREEN / SWAP SCREENS: the main one (Windows' primary display) first, then the
 * others. Bounds are in the same units as a window's position and size (Windows' scaled pixels).
 */
object Monitors {
    fun list(): List<Rectangle> = fake ?: runCatching {
        val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val main = env.defaultScreenDevice
        (listOf(main) + env.screenDevices.filter { it != main }).map { usable(it.defaultConfiguration) }
    }.getOrDefault(emptyList())

    /** For testing on one screen: `-Dmonitors=0,0,1600,900;1600,0,1280,800` stands in for the real ones. */
    private val fake: List<Rectangle>? = System.getProperty("monitors")?.split(';')?.mapNotNull { m ->
        m.split(',').mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size == 4 }?.let { Rectangle(it[0], it[1], it[2], it[3]) }
    }?.takeIf { it.isNotEmpty() }

    /** The monitor less its taskbar. */
    private fun usable(c: java.awt.GraphicsConfiguration): Rectangle {
        val b = c.bounds
        val i = runCatching { java.awt.Toolkit.getDefaultToolkit().getScreenInsets(c) }.getOrNull() ?: return b
        return Rectangle(b.x + i.left, b.y + i.top, b.width - i.left - i.right, b.height - i.top - i.bottom)
    }

    val count: Int get() = list().size

    /**
     * Puts the game's and the companion's windows where SINGLE SCREEN / SWAP SCREENS say: with the companion in a window
     * of its own and two monitors, each window fills one (the game on the main one, unless swapped); on one monitor the
     * two sit side by side. With the companion beside the game (or hidden), the game window goes back to the main monitor.
     */
    fun place(game: WindowState, companion: WindowState, ownWindow: Boolean, swap: Boolean) {
        val mons = list()
        val main = mons.firstOrNull() ?: Rectangle(0, 0, 1920, 1080)
        val other = mons.getOrNull(1)
        println("monitors $mons: companion ${if (ownWindow) "in its own window" else "beside the game"}, swap $swap")
        when {
            ownWindow && other != null -> {
                fill(game, if (swap) other else main)
                fill(companion, if (swap) main else other)
            }
            ownWindow -> {
                // One monitor: the game on the left two thirds, the companion beside it.
                val gameW = main.width * 2 / 3
                floating(game, main.x + 10, main.y + 30, gameW - 20, main.height - 80)
                floating(companion, main.x + gameW, main.y + 30, main.width - gameW - 10, main.height - 80)
            }
            else -> floating(game, main.x + 30, main.y + 30, minOf(1600, main.width - 60), minOf(760, main.height - 80))
        }
    }

    /** The window filling [mon] (Windows then maximizes it there, which a window on that monitor's area does). */
    private fun fill(w: WindowState, mon: Rectangle) {
        floating(w, mon.x, mon.y, mon.width, mon.height)
        w.placement = WindowPlacement.Maximized
    }

    private fun floating(w: WindowState, x: Int, y: Int, width: Int, height: Int) {
        w.placement = WindowPlacement.Floating
        w.position = WindowPosition(x.dp, y.dp)
        w.size = DpSize(width.dp, height.dp)
    }

    /** The game window's DISPLAY MODE: windowed (as placed), borderless over its whole monitor, or full screen. */
    fun display(w: WindowState, mode: DisplayMode) {
        when (mode) {
            DisplayMode.FULLSCREEN -> w.placement = WindowPlacement.Fullscreen
            DisplayMode.BORDERLESS -> {
                val mon = monitorAt(w) ?: return
                floating(w, mon.x, mon.y, mon.width, mon.height)
            }
            DisplayMode.WINDOWED -> if (w.placement != WindowPlacement.Floating) w.placement = WindowPlacement.Floating
        }
    }

    /** The whole monitor (taskbar included) the window's middle is on, else the main one. */
    private fun monitorAt(w: WindowState): Rectangle? = runCatching {
        val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val p = w.position
        val cx = if (p is WindowPosition.Absolute) (p.x.value + w.size.width.value / 2).toInt() else 0
        val cy = if (p is WindowPosition.Absolute) (p.y.value + w.size.height.value / 2).toInt() else 0
        env.screenDevices.map { it.defaultConfiguration.bounds }.firstOrNull { it.contains(cx, cy) }
            ?: env.defaultScreenDevice.defaultConfiguration.bounds
    }.getOrNull()

    /** F11: the window fills its monitor with no title bar, and back. */
    fun toggleFullscreen(w: WindowState) {
        w.placement = if (w.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
    }
}

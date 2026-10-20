package desktop

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pokedaisy.app.PokeDaisyActivity
import com.pokedaisy.app.Prefs
import com.pokedaisy.app.SaveBackups
import com.pokedaisy.app.SavesLocation
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/**
 * Runs the app's own activities (LibraryActivity, SettingsActivity, ...) as desktop windows, the way Android stacks
 * them: startActivity opens one on top, finish closes it and the one under it resumes. Starting PokeDaisyActivity
 * opens the game ([GameSession]) instead, with the activities hidden until it ends.
 */
object DesktopHost {
    class Screen(val activity: ComponentActivity, val content: @Composable () -> Unit) {
        val title: String get() = when (activity) {
            is com.pokedaisy.app.SettingsActivity -> "PokeDaisy - Settings"
            else -> "PokeDaisy"
        }
    }

    val screens = mutableStateListOf<Screen>()
    var game by mutableStateOf<GameSession?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    private var noticeId = 0

    fun open(cls: Class<*>, intent: Intent = Intent()) {
        val a = cls.getDeclaredConstructor().newInstance() as ComponentActivity
        a.intent = intent
        screens.lastOrNull()?.activity?.onPause()
        a.onCreate(null)
        val content = androidx.activity.compose.Captured.content ?: return
        screens.add(Screen(a, content))
        a.onStart()
        a.onResume()
    }

    fun start(from: ComponentActivity, intent: Intent) {
        val cls = intent.component
        when {
            intent.action == Intent.ACTION_VIEW && intent.data?.scheme?.startsWith("http") == true -> openLink(intent.data.toString())
            cls == PokeDaisyActivity::class.java -> intent.getStringExtra("rom")?.let { startGame(File(it)) }
            cls != null -> runCatching { open(cls, intent) }.onFailure { toast("Couldn't open that: $it"); it.printStackTrace() }
            else -> println("startActivity ignored: ${intent.action} ${intent.data}")
        }
    }

    fun finish(a: ComponentActivity) {
        val s = screens.firstOrNull { it.activity === a } ?: return
        a.onPause(); a.onStop()
        screens.remove(s)
        if (screens.isEmpty()) System.exit(0)
        screens.last().activity.onResume()
    }

    /** A game from the Library (or the command line): the activities wait under it. */
    fun startGame(rom: File) {
        if (game != null || !rom.isFile) return
        screens.lastOrNull()?.activity?.onPause()
        val save = saveFor(rom)
        SaveBackups.snapshot(save)?.let { println("save backed up to ${it.path}") }
        game = GameSession(rom, save) { endGame() }
    }

    fun endGame() {
        val g = game ?: return
        game = null
        g.stop()
        if (screens.isEmpty()) open(com.pokedaisy.app.LibraryActivity::class.java)
        else screens.last().activity.onResume()
    }

    fun toast(text: String) {
        println("notice: $text")
        val id = ++noticeId
        notice = text
        Thread { Thread.sleep(3500); SwingUtilities.invokeLater { if (noticeId == id) notice = null } }.apply { isDaemon = true }.start()
    }

    fun openLink(url: String) {
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }.onFailure { toast("Open $url in your browser") }
    }

    /**
     * Where [rom]'s save is: the app's own rule (Settings > FOLDERS, ALSO LOOK IN, the game's SAVE FOLDER) when it finds
     * one, else the save beside the ROM (RetroArch's .srm, else .sav). A new save is a .srm - in the folder the player
     * chose, or beside the ROM - so it syncs with RetroArch / Batocera.
     */
    fun saveFor(rom: File): File {
        val ctx = android.content.Context()
        val prefs = Prefs(ctx)
        val chosen = SavesLocation.saveFor(ctx, prefs, rom)
        if (chosen.isFile) return chosen
        val beside = defaultSave(rom)
        if (beside.isFile) return beside
        val picked = !prefs.savesDirOverride.isNullOrBlank() || prefs.romSaveDir(rom) != null
        val fresh = if (picked) chosen else beside
        return if (fresh.extension.equals("sav", true)) File(fresh.parentFile, fresh.nameWithoutExtension + ".srm") else fresh
    }
}

/**
 * The app's pickers (registerForActivityResult / rememberLauncherForActivityResult) as the system's own dialogs: a file
 * comes back as a `file:` URI, which the desktop's ContentResolver, RomUris and DocumentsContract read back.
 */
object DesktopPickers {
    /** The drive root of the last folder picked: Android's StorageAccess.treePath joins paths onto it. */
    @Volatile var lastRoot: File = File(System.getProperty("user.home")).toPath().root?.toFile() ?: File("/")

    fun uriOf(f: File): Uri = Uri.parse("file:" + f.absolutePath)
    fun fileOf(u: Uri?): File? = u?.toString()?.takeIf { it.startsWith("file:") }?.removePrefix("file:")?.let(::File)

    @Suppress("UNCHECKED_CAST")
    fun <I, O> launcher(c: ActivityResultContract<I, O>, cb: (O) -> Unit): ActivityResultLauncher<I> = object : ActivityResultLauncher<I>() {
        override fun launch(i: I) {
            SwingUtilities.invokeLater {
                val result: Any? = when (c) {
                    is ActivityResultContracts.OpenDocumentTree -> pickFolder()?.let(::uriOf)
                    is ActivityResultContracts.OpenMultipleDocuments -> pickFiles(multiple = true).map(::uriOf)
                    is ActivityResultContracts.OpenDocument, is ActivityResultContracts.GetContent -> pickFiles(multiple = false).firstOrNull()?.let(::uriOf)
                    else -> null
                }
                runCatching { cb(result as O) }.onFailure { it.printStackTrace() }
            }
        }
    }

    private var lastDir: File? = null

    private fun pickFiles(multiple: Boolean): List<File> {
        val d = java.awt.FileDialog(null as java.awt.Frame?, "PokeDaisy", java.awt.FileDialog.LOAD)
        d.isMultipleMode = multiple
        lastDir?.let { d.directory = it.path }
        d.isVisible = true
        val files = d.files?.toList().orEmpty()
        files.firstOrNull()?.parentFile?.let { lastDir = it }
        return files
    }

    private fun pickFolder(): File? {
        val chooser = JFileChooser(lastDir).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "PokeDaisy - choose a folder"
            approveButtonText = "Use this folder"
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
        return chooser.selectedFile?.also { lastDir = it }
    }
}

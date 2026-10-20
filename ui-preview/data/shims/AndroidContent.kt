package android.content

import java.io.File
import java.io.InputStream

/** Reads from the process's working directory - the render task points it at app/src/main/assets. */
class AssetManager(private val root: File) {
    fun open(name: String): InputStream = (File(root, name).takeIf { it.isFile }
        // Generated at build time (licenses.txt): app/build/generated/licenses, next to the assets' src dir.
        ?: File(root, "../../../build/generated/licenses/$name")).inputStream()
}

/** Context.applicationInfo's one field the app reads (RomIntake: the private data directory). */
class ContextAppInfo(val dataDir: String? = null)

open class Context {
    val applicationContext: Context get() = this
    val packageName = "com.pokedaisy.app"
    val applicationInfo = ContextAppInfo()
    val resources = android.content.res.Resources()
    val assets = AssetManager(File(System.getProperty("user.dir")))
    val filesDir = File(System.getProperty("scratch") ?: System.getProperty("java.io.tmpdir"), "files").apply { mkdirs() }
    val cacheDir = File(System.getProperty("scratch") ?: System.getProperty("java.io.tmpdir"), "cache").apply { mkdirs() }
    fun getExternalFilesDir(t: String?): File? = filesDir
    fun getSharedPreferences(n: String, mode: Int): SharedPreferences = SharedPreferences.shared
    open fun getSystemService(n: String): Any? = null
    fun checkSelfPermission(p: String) = 0

    companion object {
        const val MODE_PRIVATE = 0
        const val CLIPBOARD_SERVICE = "clipboard"
    }
}

/** In-memory, process-wide - and kept in a file between runs when the `prefsFile` system property names one
 * (the desktop app; ui-preview leaves it unset, so its shots start from the same values every time). */
class SharedPreferences {
    val m = HashMap<String, Any?>()
    fun getString(k: String, d: String?) = (m[k] as String?) ?: d
    fun getInt(k: String, d: Int) = (m[k] as Int?) ?: d
    fun getFloat(k: String, d: Float) = (m[k] as Float?) ?: d
    fun getBoolean(k: String, d: Boolean) = (m[k] as Boolean?) ?: d
    fun getLong(k: String, d: Long) = (m[k] as Long?) ?: d
    fun contains(k: String) = k in m
    fun edit() = Editor(this)

    class Editor(val p: SharedPreferences) {
        fun putString(k: String, v: String?) = apply { p.m[k] = v }
        fun putInt(k: String, v: Int) = apply { p.m[k] = v }
        fun putFloat(k: String, v: Float) = apply { p.m[k] = v }
        fun putBoolean(k: String, v: Boolean) = apply { p.m[k] = v }
        fun putLong(k: String, v: Long) = apply { p.m[k] = v }
        fun remove(k: String) = apply { p.m.remove(k) }
        fun apply() = p.save()
        fun commit() = true.also { p.save() }
    }

    private val file: java.io.File? = System.getProperty("prefsFile")?.let { java.io.File(it) }

    init {
        // One line per key: <type letter><tab><key><tab><value, with \ \n \t escaped>.
        file?.takeIf { it.isFile }?.readLines()?.forEach { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size < 3) return@forEach
            val v = unescape(parts[2])
            m[parts[1]] = when (parts[0]) {
                "s" -> v; "i" -> v.toIntOrNull(); "f" -> v.toFloatOrNull(); "b" -> v.toBoolean(); "l" -> v.toLongOrNull(); else -> null
            }
        }
    }

    @Synchronized fun save() {
        val f = file ?: return
        val text = m.entries.mapNotNull { (k, v) ->
            val type = when (v) { is String -> "s"; is Int -> "i"; is Float -> "f"; is Boolean -> "b"; is Long -> "l"; else -> return@mapNotNull null }
            "$type\t${escape(k)}\t${escape(v.toString())}"
        }.joinToString("\n")
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = java.io.File(f.path + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")
    private fun unescape(s: String): String {
        val b = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                b.append(when (s[i + 1]) { 'n' -> '\n'; 't' -> '\t'; else -> s[i + 1] }); i += 2
            } else { b.append(c); i++ }
        }
        return b.toString()
    }

    companion object { val shared = SharedPreferences() }
}

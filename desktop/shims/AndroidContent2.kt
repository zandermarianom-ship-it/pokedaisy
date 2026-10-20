package android.content

import android.net.Uri

open class Intent() {
    constructor(ctx: Context, cls: Class<*>) : this() { component = cls }
    constructor(action: String, uri: Uri? = null) : this() {
        this.action = action
        this.data = uri
    }
    constructor(o: Intent) : this() {
        action = o.action
        data = o.data
        component = o.component
        extras.putAll(o.extras)
    }
    /** The activity class this intent opens (desktop.DesktopHost reads it). */
    var component: Class<*>? = null
    fun setClass(ctx: Context, cls: Class<*>) = apply { component = cls }
    fun setClassName(pkg: String, cls: String) = this
    fun setFlags(f: Int) = this
    fun addFlags(f: Int) = this
    fun setDataAndType(u: android.net.Uri, type: String) = apply { data = u }

    var action: String? = null
    var data: Uri? = null
    private val extras = HashMap<String, String>()
    fun putExtra(k: String, v: String) = apply { extras[k] = v }
    fun putExtra(k: String, v: Boolean) = apply { extras[k] = v.toString() }
    fun getStringExtra(k: String): String? = extras[k]
    fun getBooleanExtra(k: String, d: Boolean): Boolean = extras[k]?.toBooleanStrictOrNull() ?: d

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val FLAG_GRANT_READ_URI_PERMISSION = 1
        const val FLAG_GRANT_WRITE_URI_PERMISSION = 2
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
    }
}

class ClipData { companion object { fun newPlainText(l: String, t: String) = ClipData() } }

class ClipboardManager { fun setPrimaryClip(c: ClipData) {} }

class ContentResolver {
    /** The desktop's pickers hand out file: URIs (desktop.DesktopPickers). */
    fun openInputStream(u: Uri): java.io.InputStream? = desktop.DesktopPickers.fileOf(u)?.takeIf { it.isFile }?.inputStream()
    fun query(u: Uri, a: Array<String>?, b: String?, c: Array<String>?, d: String?): android.database.Cursor? = null
    fun takePersistableUriPermission(u: Uri, f: Int) {}
}

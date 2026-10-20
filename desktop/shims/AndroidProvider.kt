package android.provider

import android.net.Uri

object DocumentsContract {
    /** A picked folder as Android's "volume:relative path" (StorageAccess.treePath joins it back onto
     * Environment.getExternalStorageDirectory(), which is that folder's drive root here). */
    fun getTreeDocumentId(u: Uri): String {
        val f = desktop.DesktopPickers.fileOf(u) ?: return ""
        val root = f.toPath().root ?: return ""
        desktop.DesktopPickers.lastRoot = root.toFile()
        return "primary:" + root.relativize(f.toPath()).toString()
    }
    fun getDocumentId(u: Uri): String = ""
    fun isDocumentUri(c: android.content.Context, u: Uri) = false
    fun buildDocumentUri(authority: String, id: String): Uri = Uri.parse("content://$authority/document/$id")
}

object OpenableColumns { const val DISPLAY_NAME = "_display_name" }

object Settings {
    const val ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION = "a"
    const val ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION = "b"
    const val ACTION_MANAGE_UNKNOWN_APP_SOURCES = "c"
}

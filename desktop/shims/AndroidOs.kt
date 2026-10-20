package android.os

open class Bundle

object Environment {
    fun isExternalStorageManager() = true
    fun getExternalStorageDirectory() = desktop.DesktopPickers.lastRoot
}

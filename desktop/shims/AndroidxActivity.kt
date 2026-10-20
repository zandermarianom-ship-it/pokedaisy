package androidx.activity

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract

/**
 * PokeDaisy desktop: an activity is a window. onCreate's setContent {} lands in [androidx.activity.compose.Captured]
 * (desktop.DesktopHost picks it up); startActivity / finish open and close windows; pickers are the system's dialogs.
 */
open class ComponentActivity : Context() {
    var intent: Intent = Intent()
    val contentResolver = android.content.ContentResolver()
    val packageManager = android.content.pm.PackageManager()
    open fun onCreate(savedInstanceState: android.os.Bundle?) {}
    open fun onResume() {}
    open fun onPause() {}
    open fun onStart() {}
    open fun onStop() {}
    open fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean = false
    open fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean = false
    open fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean = false
    open fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean = false
    fun finish() = desktop.DesktopHost.finish(this)
    fun startActivity(i: Intent) = desktop.DesktopHost.start(this, i)
    fun startActivity(i: Intent, options: android.os.Bundle?) = desktop.DesktopHost.start(this, i)
    fun requestPermissions(p: Array<String>, code: Int) {}
    fun runOnUiThread(r: () -> Unit) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) r() else javax.swing.SwingUtilities.invokeLater(r)
    }
    override fun getSystemService(n: String): Any? = android.content.ClipboardManager()
    fun <I, O> registerForActivityResult(c: ActivityResultContract<I, O>, cb: (O) -> Unit): ActivityResultLauncher<I> =
        desktop.DesktopPickers.launcher(c, cb)
}

package android.widget

class Toast(private val text: CharSequence = "") {
    fun show() = desktop.DesktopHost.toast(text.toString())
    fun cancel() {}

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        fun makeText(c: android.content.Context, t: CharSequence, d: Int) = Toast(t)
    }
}

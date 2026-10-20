package com.pokedaisy.app

/** Stand-in for the app's Screens (DisplayManager): the Thor, a 1240x1080 second screen. */
object Screens {
    // The desktop: the companion can always have a window of its own (on a second monitor), so Settings shows
    // SINGLE SCREEN / SWAP SCREENS. (The dual-screen launcher stays off: it needs ScreenScraper, which is off.)
    fun hasSecond(context: android.content.Context) = true
    fun secondAspect(context: android.content.Context): Float? = null
    /** The preview is the main display: no options needed. */
    fun mainDisplayLaunch(activity: Any): android.os.Bundle? = null
    fun isOnMainDisplay(activity: Any) = true
    fun reopenOnMainDisplay(activity: Any) = false
    fun bringToMainDisplay(activity: Any) {}
}

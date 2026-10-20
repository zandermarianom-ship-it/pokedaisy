package com.pokedaisy.app

/** Stand-in for the app's Screens (DisplayManager): the Thor, a 1240x1080 second screen. */
object Screens {
    // The desktop: one screen as far as the Library is concerned (the game's companion has its own windows).
    fun hasSecond(context: android.content.Context) = false
    fun secondAspect(context: android.content.Context): Float? = null
    /** The preview is the main display: no options needed. */
    fun mainDisplayLaunch(activity: Any): android.os.Bundle? = null
    fun isOnMainDisplay(activity: Any) = true
    fun reopenOnMainDisplay(activity: Any) = false
    fun bringToMainDisplay(activity: Any) {}
}

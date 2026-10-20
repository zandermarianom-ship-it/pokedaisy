package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.pokedaisy.app.companion.ui.theme.gbaFocusRing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Plays the running game's own menu click (SE_SELECT, rendered from the ROM -
 * see GameClickSound). Silent unless the host provides it, so the
 * top-screen Library / Settings, Paparazzi and ui-preview stay quiet.
 */
val LocalClickSound = staticCompositionLocalOf<() -> Unit> { {} }

/** Whether the game can't be heard right now ([com.pokedaisy.app.companion.BattleInput.gameSilent]): asked on a tap. */
val LocalGameSilent = staticCompositionLocalOf<() -> Boolean> { { false } }

/**
 * [clickable] that plays the game's click on every tap - the companion's
 * buttons use this. Not for buttons that press the game's own buttons
 * (battle FIGHT / moves / BACK): the game plays its own sound for those.
 * The click follows the action, so the CLICK SOUND row itself clicks as it
 * turns on and stays quiet as it turns off.
 */
fun Modifier.soundClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val sound = LocalClickSound.current
    if (!LocalFocusRings.current) clickable(enabled = enabled) { onClick(); sound() }
    else {
        val source = remember { MutableInteractionSource() }
        focusRinged(source).clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled) { onClick(); sound() }
    }
}

/**
 * Draws the D-pad's focus ring on every [soundClickable] under it that has the focus - for screens whose buttons have no
 * focused look of their own, when a controller steers them (the desktop's FIELD HUD). Off by default.
 */
val LocalFocusRings = androidx.compose.runtime.staticCompositionLocalOf { false }

private fun Modifier.focusRinged(source: MutableInteractionSource): Modifier = composed {
    val focused by source.collectIsFocusedAsState()
    gbaFocusRing(focused, androidx.compose.ui.graphics.RectangleShape)
}

/** [soundClickable] with the caller's [interactionSource] / [indication], like [clickable]'s overload. */
fun Modifier.soundClickable(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val sound = LocalClickSound.current
    (if (LocalFocusRings.current) focusRinged(interactionSource) else this)
        .clickable(interactionSource = interactionSource, indication = indication, enabled = enabled) { onClick(); sound() }
}

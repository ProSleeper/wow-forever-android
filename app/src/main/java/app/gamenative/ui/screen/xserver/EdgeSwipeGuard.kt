package app.gamenative.ui.screen.xserver

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private const val PREFS = "wow_forever"
private const val KEY_ENABLED = "edge_guard_enabled"

// The button takes over the place of the screen lock button it replaced, so it stays where it was dragged to.
private const val KEY_BUTTON_X = "lock_button_x"
private const val KEY_BUTTON_Y = "lock_button_y"
private val BUTTON_PLACE = Offset(0.54f, 0.02f)

// The strips are never thinner than this, for devices that report no gesture area at an edge (three-button navigation).
private val MIN_EDGE = 20.dp

/** Whether the edge guard is on. It starts on and is remembered across launches. */
@Stable
internal class EdgeGuardSetting(private val prefs: SharedPreferences) {
    var enabled by mutableStateOf(prefs.getBoolean(KEY_ENABLED, true))
        private set

    fun toggle() {
        enabled = !enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}

@Composable
internal fun rememberEdgeGuardSetting(): EdgeGuardSetting {
    val context = LocalContext.current
    return remember { EdgeGuardSetting(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)) }
}

/**
 * Turns the edge guard on and off, shown with the on-screen controls. A closed lock means it's on. It can be dragged
 * like the keyboard button, for an on-screen control at the edge that the guard would otherwise cover.
 */
@Composable
internal fun EdgeGuardButton(setting: EdgeGuardSetting) {
    FloatingButton(
        keyX = KEY_BUTTON_X,
        keyY = KEY_BUTTON_Y,
        defaultPlace = BUTTON_PLACE,
        icon = if (setting.enabled) Icons.Default.Lock else Icons.Default.LockOpen,
        contentDescription = if (setting.enabled) "Edge swipe guard on" else "Edge swipe guard off",
        onClick = setting::toggle,
    )
}

/**
 * While [enabled], keeps a touch that starts at the top or bottom edge of the screen from reaching the game. Those are
 * the swipes that pull down the status bar or go home, and Android only takes one over once it has begun, so until
 * then the game would read it as a drag and turn the camera. A touch that starts anywhere else works as before.
 *
 * Each strip is as tall as the system's gesture area at that edge. Being hit is enough: Compose gives a touch to the
 * first sibling under it that takes pointer input and keeps the whole gesture there, so nothing below the strip sees it.
 */
@Composable
internal fun BoxScope.EdgeSwipeGuard(enabled: Boolean) {
    if (!enabled) return
    val density = LocalDensity.current
    val gestures = WindowInsets.systemGestures
    val top = with(density) { maxOf(gestures.getTop(density).toDp(), MIN_EDGE) }
    val bottom = with(density) { maxOf(gestures.getBottom(density).toDp(), MIN_EDGE) }
    EdgeStrip(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(top))
    EdgeStrip(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottom))
}

@Composable
private fun EdgeStrip(modifier: Modifier) {
    Box(
        modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent()
            }
        },
    )
}

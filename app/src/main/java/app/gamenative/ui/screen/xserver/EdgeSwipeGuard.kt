package app.gamenative.ui.screen.xserver

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

// The strips are never thinner than this, for devices that report no gesture area at an edge (three-button navigation).
private val MIN_EDGE = 20.dp

/**
 * Keeps a touch that starts at the top or bottom edge of the screen from reaching the game. Those are the swipes that
 * pull down the status bar or go home, and Android only takes one over once it has begun, so until then the game
 * would read it as a drag and turn the camera. A touch that starts anywhere else works as before.
 *
 * Each strip is as tall as the system's gesture area at that edge. Being hit is enough: Compose gives a touch to the
 * first sibling under it that takes pointer input and keeps the whole gesture there, so nothing below the strip sees it.
 */
@Composable
internal fun BoxScope.EdgeSwipeGuard() {
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

package app.gamenative.ui.screen.xserver

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

private const val KEY_LOCK_X = "lock_button_x"
private const val KEY_LOCK_Y = "lock_button_y"
private val LOCK_BUTTON_PLACE = Offset(0.54f, 0.02f)
private const val UNLOCK_BUTTON_SHOWN_MS = 3000L

/**
 * Whether the screen is locked, as in a video player: while it is, no touch reaches the game, its on-screen controls or
 * any button over it, and the back gesture does nothing, until the unlock button is tapped. The game keeps running,
 * and controllers and keyboards plugged into the device keep working.
 */
@Stable
internal class ScreenLock {
    var locked by mutableStateOf(false)
        private set

    /** Goes up each time the unlock button should come up for a while. */
    var unlockButtonRequests by mutableIntStateOf(0)
        private set

    fun lock() {
        locked = true
        // Shows where to unlock straight away.
        unlockButtonRequests++
    }

    fun unlock() {
        locked = false
    }

    fun showUnlockButton() {
        if (locked) unlockButtonRequests++
    }
}

/** The button that locks the screen, shown with the on-screen controls. It can be dragged like the keyboard button. */
@Composable
internal fun LockButton(onLock: () -> Unit) {
    FloatingButton(
        keyX = KEY_LOCK_X,
        keyY = KEY_LOCK_Y,
        defaultPlace = LOCK_BUTTON_PLACE,
        icon = Icons.Default.Lock,
        contentDescription = "Lock screen",
        onClick = onLock,
    )
}

/**
 * Drawn over everything else while the screen is locked, it takes every touch. A touch brings up the unlock button, in
 * the lock button's place, for a few seconds.
 */
@Composable
internal fun ScreenLockOverlay(lock: ScreenLock) {
    if (!lock.locked) return

    var unlockButtonShown by remember { mutableStateOf(false) }
    LaunchedEffect(lock.unlockButtonRequests) {
        // Each new request starts the time over.
        unlockButtonShown = true
        delay(UNLOCK_BUTTON_SHOWN_MS)
        unlockButtonShown = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(lock) {
                // Being hit is what keeps touches from the game: Compose stops looking for a target at the first
                // sibling that takes pointer input, so the game view and the buttons under this never see them. Nothing
                // is consumed here, since that would cancel a tap on the unlock button whenever the finger moves a little.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) lock.showUnlockButton()
                    }
                }
            },
    ) {
        if (unlockButtonShown) {
            FloatingButton(
                keyX = KEY_LOCK_X,
                keyY = KEY_LOCK_Y,
                defaultPlace = LOCK_BUTTON_PLACE,
                icon = Icons.Default.LockOpen,
                contentDescription = "Unlock screen",
                onClick = lock::unlock,
            )
        }
    }
}

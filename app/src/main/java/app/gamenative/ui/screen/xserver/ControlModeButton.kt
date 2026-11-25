package app.gamenative.ui.screen.xserver

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset

private const val KEY_BUTTON_X = "control_mode_button_x"
private const val KEY_BUTTON_Y = "control_mode_button_y"
private val BUTTON_PLACE = Offset(0.46f, 0.02f)

/**
 * Switches between the two ways of playing by touch in one tap, instead of going through the side menu: the
 * on-screen controller, or touchscreen mode, where the screen works as a mouse and the controller is put away. The
 * icon shows the mode a tap switches to. It can be dragged like the keyboard button.
 */
@Composable
internal fun ControlModeButton(touchscreenMode: Boolean, onSwitch: () -> Unit) {
    FloatingButton(
        keyX = KEY_BUTTON_X,
        keyY = KEY_BUTTON_Y,
        defaultPlace = BUTTON_PLACE,
        icon = if (touchscreenMode) Icons.Default.SportsEsports else Icons.Default.TouchApp,
        contentDescription = if (touchscreenMode) "Switch to the on-screen controller" else "Switch to touchscreen mode",
        onClick = onSwitch,
    )
}

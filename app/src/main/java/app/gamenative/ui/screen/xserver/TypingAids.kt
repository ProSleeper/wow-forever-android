package app.gamenative.ui.screen.xserver

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private const val PREFS = "wow_forever"
private const val KEY_BUTTON_X = "keyboard_button_x"
private const val KEY_BUTTON_Y = "keyboard_button_y"
private const val TYPED_LINE_CHARS = 60
private val BUTTON_SIZE = 44.dp

/**
 * Slides the game up by the height of a docked keyboard, so the bottom of the screen, where the chat window is, stays
 * in view while typing. A floating keyboard takes up no room and moves nothing.
 */
@Composable
internal fun Modifier.liftedAboveKeyboard(): Modifier {
    // Held upright, the game only fills a band that the keyboard doesn't reach.
    if (LocalConfiguration.current.orientation != Configuration.ORIENTATION_LANDSCAPE) return this
    val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    return offset { IntOffset(0, -keyboardHeight) }
}

/**
 * Typing aids for touch play, drawn over the game: a button that brings up the Android keyboard in one tap, and the
 * line being typed, for when the keyboard hides the field it goes into.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoxScope.TypingAids(
    showButtons: Boolean,
    line: String,
    onOpenChat: () -> Unit,
    onShowKeyboard: () -> Unit,
    onHideKeyboard: () -> Unit,
    onKeyboardGone: () -> Unit,
) {
    // A floating keyboard is on screen without taking up any of its edge, so its height says nothing about whether
    // it's there.
    val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    val keyboardShown = WindowInsets.isImeVisible
    var keyboardWasShown by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardShown) {
        // The keyboard can also be put away with its own hide key, which nothing else reports.
        if (keyboardWasShown && !keyboardShown) onKeyboardGone()
        keyboardWasShown = keyboardShown
    }

    var lineHidden by remember { mutableStateOf(false) }
    if (line.isNotEmpty()) {
        TypedLine(line, lineHidden, keyboardHeight, onToggleHidden = { lineHidden = !lineHidden })
    }
    if (showButtons) {
        TypingButton(keyboardShown, onOpenChat, onShowKeyboard, onHideKeyboard)
    }
}

@Composable
private fun BoxScope.TypedLine(line: String, hidden: Boolean, keyboardHeight: Int, onToggleHidden: () -> Unit) {
    // Sits on top of a docked keyboard, or at the top of the screen when the keyboard floats.
    val place = if (keyboardHeight > 0) {
        Modifier.align(Alignment.BottomCenter).offset { IntOffset(0, -keyboardHeight) }
    } else {
        Modifier.align(Alignment.TopCenter)
    }
    val shown = line.takeLast(TYPED_LINE_CHARS)
    Text(
        text = if (hidden) "•".repeat(shown.length) else shown,
        color = Color.White,
        fontSize = 16.sp,
        maxLines = 1,
        modifier = place
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            // A tap covers what's typed, for a password with someone looking on.
            .clickable(onClick = onToggleHidden)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/**
 * One button for the keyboard. A tap brings it up for chat, with the game's chat box open, since chat is what it's
 * nearly always for. Holding the button brings up the keyboard alone, for a login or a search field, where the Enter
 * that closes the chat box afterwards would submit the field instead. With the keyboard up, a tap puts it away.
 */
@Composable
private fun TypingButton(keyboardShown: Boolean, onOpenChat: () -> Unit, onShowKeyboard: () -> Unit, onHideKeyboard: () -> Unit) {
    FloatingButton(
        keyX = KEY_BUTTON_X,
        keyY = KEY_BUTTON_Y,
        defaultPlace = Offset(0.62f, 0.02f),
        icon = if (keyboardShown) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
        contentDescription = "Keyboard",
        onClick = if (keyboardShown) onHideKeyboard else onOpenChat,
        onLongClick = if (keyboardShown) null else onShowKeyboard,
    )
}

/**
 * A round button drawn over the game that can be dragged out of the way. Its place is kept, under keyX and keyY, as a
 * fraction of the free space, so it holds across screen sizes and rotations.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FloatingButton(
    keyX: String,
    keyY: String,
    defaultPlace: Offset,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var place by remember { mutableStateOf(Offset(prefs.getFloat(keyX, defaultPlace.x), prefs.getFloat(keyY, defaultPlace.y))) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val buttonPx = with(LocalDensity.current) { BUTTON_SIZE.toPx() }
        val free = Offset(
            (constraints.maxWidth - buttonPx).coerceAtLeast(1f),
            (constraints.maxHeight - buttonPx).coerceAtLeast(1f),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset { IntOffset((place.x * free.x).roundToInt(), (place.y * free.y).roundToInt()) }
                .size(BUTTON_SIZE)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                .pointerInput(free) {
                    detectDragGestures(
                        onDragEnd = {
                            prefs.edit().putFloat(keyX, place.x).putFloat(keyY, place.y).apply()
                        },
                    ) { change, drag ->
                        change.consume()
                        place = Offset(
                            (place.x + drag.x / free.x).coerceIn(0f, 1f),
                            (place.y + drag.y / free.y).coerceIn(0f, 1f),
                        )
                    }
                }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

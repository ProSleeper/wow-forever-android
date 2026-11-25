package app.gamenative.externaldisplay

import android.content.Context
import android.text.Editable
import android.text.Selection
import android.text.SpannableStringBuilder
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import com.winlator.xserver.Pointer
import com.winlator.xserver.XKeycode
import com.winlator.xserver.XServer
import com.winlator.xserver.unicodeKeysym
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Invisible view that receives IME (soft keyboard) input from Android system keyboard
 * and forwards it to XServer as keyboard events for the game.
 *
 * This is needed when the system keyboard is pinned to the external display
 */
class IMEInputReceiver(
    context: Context,
    private val xServer: XServer,
    private val displayContext: Context = context,
) : FrameLayout(context) {

    val screenWidth: Int get() = xServer.screenInfo.width.toInt()
    val screenHeight: Int get() = xServer.screenInfo.height.toInt()

    suspend fun clickAt(x: Int, y: Int) {
        xServer.injectPointerMove(x, y)
        delay(60)
        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
        delay(80)
        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
        delay(60)
    }

    fun movePointerTo(x: Int, y: Int) {
        xServer.injectPointerMove(x, y)
    }

    companion object {
        private val SHIFTED_SYMBOLS = setOf(
            ')', '!', '@', '#', '$', '%', '^', '&', '*', '(',
            '_', '+', '{', '}', '|', ':', '"', '<', '>', '?', '~',
        )

        private const val BACKSPACE = '\b'
        private const val ENTER = '\n'
        private const val TAB = '\t'

        // Not a key: a wait in the queue of keystrokes, for when the game needs a moment to act on the one before.
        private const val PAUSE = '\u0000'
        private const val PAUSE_MS = 200L

        // Wine reads a spare key's character when the game gets round to the keystroke, so the same spare key must not
        // be pointed at another character before then. This gap spaces out a burst, such as a paste.
        private const val SPARE_KEY_GAP_MS = 12L

        // Far past any chat line. A line that never ends would otherwise keep growing.
        private const val MAX_LINE_LENGTH = 400
    }

    /** Called with the line being typed, to show it while the keyboard covers the game's own text field. */
    var onLineChanged: ((String) -> Unit)? = null

    private var imeSessionActive = false

    // Whether the keyboard was brought up for chat, which keeps the game's chat box open for as long as it's up.
    private var chatting = false

    // The line the keyboard is editing, and the keystrokes that keep the game's text field in step with it.
    private val line = SpannableStringBuilder()
    private val mirror = ImeTextMirror(
        object : ImeTextMirror.Keys {
            // Enter and Tab act on the game's field instead of adding to it, so they are never part of a line.
            override fun canType(char: Char) =
                char != ENTER && char != TAB && (charToKeyDispatch(char) != null || unicodeKeysym(char.code) != 0)

            override fun type(char: Char) = sendKey(char)

            override fun backspace() = sendKey(BACKSPACE)
        },
    )
    private val pendingKeys = ArrayDeque<Char>()
    private var sendingKeys = false
    private val resumeKeys = Runnable { drainKeys() }

    init {
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun onCheckIsTextEditor(): Boolean {
        val isEditor = imeSessionActive && hasFocus()
        Timber.d("IMEInputReceiver: onCheckIsTextEditor called - returning $isEditor")
        return isEditor
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        if (!imeSessionActive || !hasFocus()) {
            Timber.d(
                "IMEInputReceiver: onCreateInputConnection ignored (active=$imeSessionActive, hasFocus=${hasFocus()})",
            )
            return BaseInputConnection(this, false)
        }

        Timber.d("IMEInputReceiver: onCreateInputConnection called!")
        // Plain text, so the keyboard offers every language it has: a password field is often held to ASCII. What's
        // typed here includes logins and names no dictionary knows, so suggestions and learning are off.
        outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
            EditorInfo.IME_ACTION_NONE
        outAttrs.initialSelStart = Selection.getSelectionStart(line).coerceAtLeast(0)
        outAttrs.initialSelEnd = Selection.getSelectionEnd(line).coerceAtLeast(0)

        return GameInputConnection()
    }

    /**
     * Lets the keyboard edit [line] like any text field and replays each edit in the game as keystrokes. Editing a
     * real line, composing text included, is what makes languages that build characters as you type come out right.
     */
    private inner class GameInputConnection : BaseInputConnection(this@IMEInputReceiver, true) {
        override fun getEditable(): Editable = line

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            var rest = text?.toString().orEmpty()
            while (true) {
                val end = rest.indexOfFirst { it == ENTER || it == TAB }
                if (end < 0) break
                super.commitText(rest.substring(0, end), 1)
                endLine(rest[end])
                rest = rest.substring(end + 1)
            }
            return super.commitText(rest, newCursorPosition).also { lineEdited() }
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean =
            super.setComposingText(text, newCursorPosition).also { lineEdited() }

        override fun finishComposingText(): Boolean = super.finishComposingText().also { lineEdited() }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val beforeCursor = Selection.getSelectionStart(line).coerceAtLeast(0)
            return super.deleteSurroundingText(beforeLength, afterLength).also {
                lineEdited()
                backspacePastLineStart(beforeLength - beforeCursor)
            }
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
            val beforeCursor = Character.codePointCount(line, 0, Selection.getSelectionStart(line).coerceAtLeast(0))
            return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength).also {
                lineEdited()
                backspacePastLineStart(beforeLength - beforeCursor)
            }
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            endLine(ENTER)
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            val pressed = event.action == KeyEvent.ACTION_DOWN
            when (event.keyCode) {
                KeyEvent.KEYCODE_DEL -> if (pressed) deleteSurroundingText(1, 0)
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (pressed) endLine(ENTER)
                KeyEvent.KEYCODE_TAB -> if (pressed) endLine(TAB)
                else -> {
                    // Keyboards send some characters, digits among them, as key presses. Those join the line like any
                    // other text. Keys with no character go to the game as they are.
                    val char = event.unicodeChar
                    if (char <= 0 || event.isCtrlPressed || event.isAltPressed) return super.sendKeyEvent(event)
                    if (pressed) commitText(String(Character.toChars(char)), 1)
                }
            }
            return true
        }
    }

    private fun lineEdited() {
        mirror.sync(line)
        onLineChanged?.invoke(line.toString())
        if (line.length > MAX_LINE_LENGTH) startNewLine()
    }

    /** Backspace at the start of the line reaches text that was in the game's field before this line began. */
    private fun backspacePastLineStart(count: Int) {
        repeat(count.coerceAtLeast(0)) { sendKey(BACKSPACE) }
    }

    /** Presses [key] on the line as it stands (Enter sends it, Tab moves to the next field) and starts a new one. */
    private fun endLine(key: Char) {
        BaseInputConnection.removeComposingSpans(line)
        mirror.sync(line)
        sendKey(key)
        startNewLine()
        // Sending a chat line closes the chat box, and the next one needs it open again. The game has to finish with
        // the first Enter before the second one means "open".
        if (chatting && key == ENTER) {
            sendKey(PAUSE)
            openChatBox()
        }
    }

    private fun openChatBox() {
        sendKey(ENTER)
    }

    /**
     * Closes the chat box that [openChat] kept open: deletes what wasn't sent, then Enter on the empty box closes it
     * without sending anything. Escape would do it in one key, but it opens the game menu whenever no box is open.
     */
    private fun closeChatBox() {
        if (!chatting) return
        chatting = false
        mirror.sync("")
        sendKey(PAUSE)
        sendKey(ENTER)
    }

    private fun startNewLine() {
        clearLine()
        // The keyboard still remembers the old line. Restarting makes it read the empty one.
        inputMethodManager()?.restartInput(this)
    }

    private fun clearLine() {
        line.clear()
        Selection.setSelection(line, 0)
        mirror.reset()
        onLineChanged?.invoke("")
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        Timber.d("IMEInputReceiver: onWindowFocusChanged: $hasWindowFocus")
        if (hasWindowFocus && imeSessionActive && !hasFocus()) {
            post { requestFocus() }
        }
    }

    private data class KeyDispatch(
        val keyCode: Int,
        val requiresShift: Boolean,
    )

    fun typeText(text: CharSequence) {
        text.forEach(::sendKey)
    }

    // Keystrokes go out in order, one queue for every kind, so a character that borrows a spare key can't be overtaken
    // by the ones typed after it.
    private fun sendKey(key: Char) {
        pendingKeys.addLast(key)
        if (!sendingKeys) drainKeys()
    }

    private fun drainKeys() {
        sendingKeys = true
        while (pendingKeys.isNotEmpty()) {
            val key = pendingKeys.removeFirst()
            if (key == PAUSE) {
                postDelayed(resumeKeys, PAUSE_MS)
                return
            }
            val usedSpareKey = pressKey(key)
            if (usedSpareKey && pendingKeys.isNotEmpty()) {
                postDelayed(resumeKeys, SPARE_KEY_GAP_MS)
                return
            }
        }
        sendingKeys = false
    }

    /** Sends one keystroke to the game. Returns whether it borrowed a spare key, which calls for a pause after it. */
    private fun pressKey(key: Char): Boolean {
        when {
            key == BACKSPACE -> {
                xServer.injectKeyPress(XKeycode.KEY_BKSP, 0)
                xServer.injectKeyRelease(XKeycode.KEY_BKSP)
            }
            charToKeyDispatch(key) != null -> sendCharacterToGame(key)
            else -> return xServer.injectUnicodeChar(key.code)
        }
        return false
    }

    private fun sendCharacterToGame(char: Char) {
        val keyDispatch = charToKeyDispatch(char)
        if (keyDispatch != null) {
            val metaState = if (keyDispatch.requiresShift) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
            val downEvent = KeyEvent(
                0L,
                0L,
                KeyEvent.ACTION_DOWN,
                keyDispatch.keyCode,
                0,
                metaState,
            )
            xServer.keyboard.onKeyEvent(downEvent)

            val upEvent = KeyEvent(
                0L,
                0L,
                KeyEvent.ACTION_UP,
                keyDispatch.keyCode,
                0,
                metaState,
            )
            xServer.keyboard.onKeyEvent(upEvent)

            Timber.v(
                "IMEInputReceiver: Sent char '$char' as keyCode ${keyDispatch.keyCode} (shift=${keyDispatch.requiresShift})",
            )
        } else {
            Timber.w("IMEInputReceiver: Could not map character '$char' to keyCode")
        }
    }

    private fun charToKeyDispatch(char: Char): KeyDispatch? {
        val isUppercaseLetter = char in 'A'..'Z'
        val normalizedChar = if (isUppercaseLetter) char.lowercaseChar() else char

        val keyCode = when (normalizedChar) {
            'a' -> KeyEvent.KEYCODE_A
            'b' -> KeyEvent.KEYCODE_B
            'c' -> KeyEvent.KEYCODE_C
            'd' -> KeyEvent.KEYCODE_D
            'e' -> KeyEvent.KEYCODE_E
            'f' -> KeyEvent.KEYCODE_F
            'g' -> KeyEvent.KEYCODE_G
            'h' -> KeyEvent.KEYCODE_H
            'i' -> KeyEvent.KEYCODE_I
            'j' -> KeyEvent.KEYCODE_J
            'k' -> KeyEvent.KEYCODE_K
            'l' -> KeyEvent.KEYCODE_L
            'm' -> KeyEvent.KEYCODE_M
            'n' -> KeyEvent.KEYCODE_N
            'o' -> KeyEvent.KEYCODE_O
            'p' -> KeyEvent.KEYCODE_P
            'q' -> KeyEvent.KEYCODE_Q
            'r' -> KeyEvent.KEYCODE_R
            's' -> KeyEvent.KEYCODE_S
            't' -> KeyEvent.KEYCODE_T
            'u' -> KeyEvent.KEYCODE_U
            'v' -> KeyEvent.KEYCODE_V
            'w' -> KeyEvent.KEYCODE_W
            'x' -> KeyEvent.KEYCODE_X
            'y' -> KeyEvent.KEYCODE_Y
            'z' -> KeyEvent.KEYCODE_Z
            '0', ')' -> KeyEvent.KEYCODE_0
            '1', '!' -> KeyEvent.KEYCODE_1
            '2', '@' -> KeyEvent.KEYCODE_2
            '3', '#' -> KeyEvent.KEYCODE_3
            '4', '$' -> KeyEvent.KEYCODE_4
            '5', '%' -> KeyEvent.KEYCODE_5
            '6', '^' -> KeyEvent.KEYCODE_6
            '7', '&' -> KeyEvent.KEYCODE_7
            '8', '*' -> KeyEvent.KEYCODE_8
            '9', '(' -> KeyEvent.KEYCODE_9
            ' ' -> KeyEvent.KEYCODE_SPACE
            '\n' -> KeyEvent.KEYCODE_ENTER
            '\t' -> KeyEvent.KEYCODE_TAB
            '-', '_' -> KeyEvent.KEYCODE_MINUS
            '=', '+' -> KeyEvent.KEYCODE_EQUALS
            '[', '{' -> KeyEvent.KEYCODE_LEFT_BRACKET
            ']', '}' -> KeyEvent.KEYCODE_RIGHT_BRACKET
            '\\', '|' -> KeyEvent.KEYCODE_BACKSLASH
            ';', ':' -> KeyEvent.KEYCODE_SEMICOLON
            '\'', '"' -> KeyEvent.KEYCODE_APOSTROPHE
            ',', '<' -> KeyEvent.KEYCODE_COMMA
            '.', '>' -> KeyEvent.KEYCODE_PERIOD
            '/', '?' -> KeyEvent.KEYCODE_SLASH
            '`', '~' -> KeyEvent.KEYCODE_GRAVE
            else -> null
        } ?: return null

        val requiresShift = isUppercaseLetter || char in SHIFTED_SYMBOLS

        return KeyDispatch(keyCode = keyCode, requiresShift = requiresShift)
    }

    private fun inputMethodManager() =
        displayContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    fun showKeyboard() {
        post {
            if (!imeSessionActive) clearLine()
            imeSessionActive = true
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            inputMethodManager()?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            Timber.d("IMEInputReceiver: Requested to show keyboard")
        }
    }

    /** Shows the keyboard with the game's chat box open, and keeps the box open until the keyboard is put away. */
    fun openChat() {
        showKeyboard()
        post {
            chatting = true
            openChatBox()
        }
    }

    fun hideKeyboard() {
        post {
            inputMethodManager()?.hideSoftInputFromWindow(windowToken, 0)
            clearFocus()
            imeSessionActive = false
            isFocusable = false
            isFocusableInTouchMode = false
            closeChatBox()
            clearLine()
            Timber.d("IMEInputReceiver: Requested to hide keyboard")
        }
    }

    /** For a keyboard that was put away with its own hide key: the line it was editing is over. */
    fun onKeyboardGone() {
        post {
            closeChatBox()
            clearLine()
        }
    }
}

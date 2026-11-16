package app.gamenative.externaldisplay

/**
 * Keeps a text field inside the game in step with the line the Android keyboard is editing.
 *
 * The game only understands keystrokes, while a keyboard edits text: it rewrites the syllable being composed (ㅎ, 하,
 * 한), swaps in a correction, or deletes in the middle of the line. After every edit the line is compared with what has
 * already been typed into the game: the part that no longer matches is backspaced away and the rest is typed again.
 * Characters the game can't receive are left out on both sides, so the two never drift apart.
 */
internal class ImeTextMirror(private val keys: Keys) {
    interface Keys {
        fun canType(char: Char): Boolean

        fun type(char: Char)

        fun backspace()
    }

    private val typed = StringBuilder()

    fun sync(line: CharSequence) {
        val wanted = line.filter(keys::canType)
        var kept = 0
        while (kept < typed.length && kept < wanted.length && typed[kept] == wanted[kept]) kept++

        repeat(typed.length - kept) { keys.backspace() }
        typed.setLength(kept)
        for (i in kept until wanted.length) {
            keys.type(wanted[i])
            typed.append(wanted[i])
        }
    }

    /** Starts a new line. What was typed so far stays in the game and is no longer this mirror's to edit. */
    fun reset() {
        typed.setLength(0)
    }
}

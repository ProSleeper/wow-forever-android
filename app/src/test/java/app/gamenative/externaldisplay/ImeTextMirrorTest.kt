package app.gamenative.externaldisplay

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeTextMirrorTest {
    /** Stands in for the game's text field: typing appends, backspace removes the last character. */
    private class Field : ImeTextMirror.Keys {
        val text = StringBuilder()
        var keystrokes = 0

        override fun canType(char: Char) = !char.isSurrogate()

        override fun type(char: Char) {
            text.append(char)
            keystrokes++
        }

        override fun backspace() {
            text.setLength(text.length - 1)
            keystrokes++
        }
    }

    private val field = Field()
    private val mirror = ImeTextMirror(field)

    private fun edits(vararg lines: String) = lines.forEach(mirror::sync)

    @Test
    fun hangulSyllable_isRetypedAsItComposes() {
        edits("ㅎ", "하", "한")
        assertEquals("한", field.text.toString())

        // The next consonant starts a new syllable, then moves over when a vowel follows.
        edits("한ㄱ", "한그", "한글")
        assertEquals("한글", field.text.toString())
        edits("한글ㅇ", "한글이", "한그리")
        assertEquals("한그리", field.text.toString())
    }

    @Test
    fun appendingText_onlyTypesWhatIsNew() {
        edits("hello", "hello w", "hello world")
        assertEquals("hello world", field.text.toString())
        assertEquals("hello world".length, field.keystrokes)
    }

    @Test
    fun deletingAndCorrecting_backspacesToTheFirstChange() {
        edits("teh quick")
        field.keystrokes = 0

        edits("the quick")
        assertEquals("the quick", field.text.toString())
        // "eh quick" goes, "he quick" comes back: the shared "t" is left alone.
        assertEquals(16, field.keystrokes)

        edits("the")
        assertEquals("the", field.text.toString())
    }

    @Test
    fun charactersTheGameCannotTake_areLeftOutWithoutLosingStep() {
        edits("gg 😀", "gg 😀!")
        assertEquals("gg !", field.text.toString())

        edits("gg 😀")
        assertEquals("gg ", field.text.toString())
    }

    @Test
    fun reset_leavesWhatWasTypedInTheGame() {
        edits("first")
        mirror.reset()
        edits("second")
        assertEquals("firstsecond", field.text.toString())

        edits("")
        assertEquals("first", field.text.toString())
    }
}

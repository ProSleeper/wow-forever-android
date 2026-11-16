package com.winlator.xserver

import org.junit.Assert.assertEquals
import org.junit.Test

class UnicodeKeysymTest {
    @Test
    fun hangulAndOtherBmpCharacters_useTheUnicodeKeysymRange() {
        assertEquals(0x0100D55C, unicodeKeysym('한'.code))
        assertEquals(0x0100314E, unicodeKeysym('ㅎ'.code))
        assertEquals(0x01000100, unicodeKeysym(0x100))
        assertEquals(0x0100FFFD, unicodeKeysym(0xFFFD))
    }

    @Test
    fun latin1Characters_keepTheirOwnKeysyms() {
        assertEquals(0xE9, unicodeKeysym('é'.code))
        assertEquals(0xA0, unicodeKeysym(0xA0))
    }

    @Test
    fun charactersTheMappingCannotCarry_haveNoKeysym() {
        // Plain ASCII has real keys, and control characters aren't text.
        assertEquals(0, unicodeKeysym('a'.code))
        assertEquals(0, unicodeKeysym('\n'.code))
        // Half of a surrogate pair, and a code point past the Basic Multilingual Plane.
        assertEquals(0, unicodeKeysym(0xD83D))
        assertEquals(0, unicodeKeysym(0x1F600))
    }
}

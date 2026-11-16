@file:JvmName("UnicodeKeysym")

package com.winlator.xserver

/**
 * The X keysym for a character that no key on the keyboard produces, or 0 when the keyboard mapping can't carry it.
 *
 * Characters past Latin-1 use the Unicode keysym range (0x01000000 + code point), which Wine turns straight into the
 * character. That range stops at the Basic Multilingual Plane, so emoji and other surrogate pairs don't fit.
 */
fun unicodeKeysym(codePoint: Int): Int = when (codePoint) {
    in 0xA0..0xFF -> codePoint
    in 0x100..0xD7FF, in 0xE000..0xFFFD -> 0x01000000 or codePoint
    else -> 0
}

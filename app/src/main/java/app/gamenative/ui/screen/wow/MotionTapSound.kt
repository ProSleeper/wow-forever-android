package app.gamenative.ui.screen.wow

import android.content.Context

/**
 * "MotionTap sound": hands the game's sound to the MotionTap helper app on this phone, so it can hear the
 * fishing splash even when the phone is muted or the volume is down.
 *
 * Turning it on switches the WoW container from PulseAudio to the ALSA audio driver. PulseAudio sends the
 * game sound straight to the speaker over AAudio MMAP, where no other app can hear it; the ALSA driver
 * plays through an AudioTrack in this app, so the sound passes our code first
 * ([com.winlator.alsaserver.GameAudioTap]). Takes effect on the next launch.
 */
internal object MotionTapSound {
    private const val PREFS = "motiontap_sound"

    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean("enabled", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
    }

    fun audioDriver(context: Context): String = if (enabled(context)) "alsa" else "pulseaudio"
}

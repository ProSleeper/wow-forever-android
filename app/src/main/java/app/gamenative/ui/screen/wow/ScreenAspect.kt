package app.gamenative.ui.screen.wow

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowManager

private const val PREFS = "wow_forever"
private const val KEY_MODE = "aspect_mode"
private const val KEY_UNFOLDED = "unfolded_aspect"

// A screen wider than this (long side over short side) isn't a foldable's unfolded inner screen.
private const val MAX_UNFOLDED_ASPECT = 1.6f

/**
 * The shape the game's picture is given at launch. The game can't be reshaped while it runs, so on a foldable a game
 * started on one screen is letterboxed on the other: started folded, it's a thin strip across the unfolded screen.
 */
enum class AspectMode(val label: String) {
    /** The screen the game is started on. */
    SCREEN("This screen"),

    /**
     * The foldable's unfolded inner screen, wherever the game is started. Folded, it shows with bars at the sides,
     * which only cover scenery; unfolded, it fills the screen (user request, 2026-11-26).
     */
    UNFOLDED("Unfolded");

    companion object {
        fun current(context: Context): AspectMode =
            prefs(context).getString(KEY_MODE, null)?.let { saved -> entries.firstOrNull { it.name == saved } } ?: SCREEN

        fun save(context: Context, mode: AspectMode) {
            prefs(context).edit().putString(KEY_MODE, mode.name).apply()
        }
    }
}

/**
 * Remembers the shape of a foldable's unfolded inner screen, so a game started folded can be shaped for it. It's
 * learned whenever the app's window is that screen: near square, and not shared with another app.
 */
object UnfoldedAspect {
    fun saved(context: Context): Float? = prefs(context).getFloat(KEY_UNFOLDED, 0f).takeIf { it >= 1f }

    fun record(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || activity.isInMultiWindowMode) return
        val bounds = activity.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
        val aspect = aspectOf(bounds.width(), bounds.height()) ?: return
        if (aspect <= MAX_UNFOLDED_ASPECT && aspect != saved(activity)) {
            prefs(activity).edit().putFloat(KEY_UNFOLDED, aspect).apply()
        }
    }

    /** Long side over short side, or null for a window with no size yet. */
    internal fun aspectOf(width: Int, height: Int): Float? =
        if (width <= 0 || height <= 0) null else maxOf(width, height).toFloat() / minOf(width, height)
}

private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

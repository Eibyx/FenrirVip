package dev.ragnarok.fenrir.settings

import android.animation.ValueAnimator
import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import de.maxr1998.modernpreferences.PreferenceScreen.Companion.getPreferences

/** Read at screen attachment, never on each scroll frame. Android's animator switch wins. */
object ModernAppearance {
    fun flag(context: Context, key: String, default: Boolean = false): Boolean =
        getPreferences(context).getBoolean(key, default)

    fun motion(context: Context): String =
        if (!ValueAnimator.areAnimatorsEnabled()) "off"
        else getPreferences(context).getString("modern_motion", "smooth") ?: "smooth"

    fun applyPalette(activity: android.app.Activity) {
        if (!flag(activity, "modern_custom_palette") ||
            !com.google.android.material.color.DynamicColors.isDynamicColorAvailable()) return
        val color = getPreferences(activity).getInt("modern_seed", 0xFF346096.toInt())
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(
            activity,
            com.google.android.material.color.DynamicColorsOptions.Builder()
                .setContentBasedSource(color)
                .build()
        )
        // Legacy Fenrir views use additional color attributes. Bind them to the same
        // Material roles so a custom accent never leaves preset-colored icons/bubbles.
        activity.theme.applyStyle(dev.ragnarok.fenrir.R.style.Modern_CustomPalette, true)
    }

    fun applyTo(list: RecyclerView) {
        val mode = motion(list.context)
        if (mode == "off") {
            list.itemAnimator = null
            list.layoutAnimation = null
        } else {
            (list.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            list.itemAnimator?.apply {
                val duration = if (mode == "reduced") 80L else 180L
                addDuration = duration
                removeDuration = duration
                moveDuration = duration
                changeDuration = duration
            }
            if (mode == "reduced") list.layoutAnimation = null
        }
    }
}

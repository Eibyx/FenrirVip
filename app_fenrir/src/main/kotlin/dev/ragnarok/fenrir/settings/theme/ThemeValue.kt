package dev.ragnarok.fenrir.settings.theme

import android.content.Context
import dev.ragnarok.fenrir.R
import androidx.annotation.StyleRes
import dev.ragnarok.fenrir.toColor

class ThemeValue {
    fun displayName(context: Context): String = when (id) {
        "cold" -> context.getString(R.string.modern_theme_cold)
        "cold_gold" -> context.getString(R.string.modern_theme_cold_gold)
        "cold_green" -> context.getString(R.string.modern_theme_cold_green)
        "coffee_pistachio" -> context.getString(R.string.modern_theme_coffee_pistachio)
        "ice" -> context.getString(R.string.modern_theme_ice)
        "old_ice" -> context.getString(R.string.modern_theme_old_ice)
        "fire" -> context.getString(R.string.modern_theme_fire)
        "red" -> context.getString(R.string.modern_theme_red)
        "violet" -> context.getString(R.string.modern_theme_violet)
        "gray" -> context.getString(R.string.modern_theme_gray)
        "yellow_violet" -> context.getString(R.string.modern_theme_yellow_violet)
        "red_violet" -> context.getString(R.string.modern_theme_red_violet)
        "contrast" -> context.getString(R.string.modern_theme_contrast)
        "orange" -> context.getString(R.string.modern_theme_orange)
        "orange_gray" -> context.getString(R.string.modern_theme_orange_gray)
        "violet_gray" -> context.getString(R.string.modern_theme_violet_gray)
        "pink_gray" -> context.getString(R.string.modern_theme_pink_gray)
        "violet_ice" -> context.getString(R.string.modern_theme_violet_ice)
        "ice_green" -> context.getString(R.string.modern_theme_ice_green)
        "blue_red" -> context.getString(R.string.modern_theme_blue_red)
        "green" -> context.getString(R.string.modern_theme_green)
        "lineage" -> context.getString(R.string.modern_theme_lineage)
        "fuxia_neon_yellow" -> context.getString(R.string.modern_theme_fuxia_neon_yellow)
        "fuxia_neon_violet" -> context.getString(R.string.modern_theme_fuxia_neon_violet)
        "neon_yellow_ice" -> context.getString(R.string.modern_theme_neon_yellow_ice)
        "random" -> context.getString(R.string.modern_theme_random)
        "dynamic" -> context.getString(R.string.modern_theme_dynamic)
        else -> name
    }

    val id: String
    val name: String
    val colorDayPrimary: Int
    val colorDaySecondary: Int
    val colorNightPrimary: Int
    val colorNightSecondary: Int
    var colorToast: Int
    var colorReadToast: Int
    var disabled: Boolean
    var special: Boolean

    @StyleRes
    val themeRes: Int

    @StyleRes
    val themeAmoledRes: Int

    @StyleRes
    val themeMD1Res: Int

    constructor(
        id: String,
        colorPrimary: String,
        colorSecondary: String,
        name: String,
        @StyleRes themeRes: Int,
        @StyleRes themeAmoledRes: Int,
        @StyleRes themeMD1Res: Int
    ) {
        colorDayPrimary = colorPrimary.toColor()
        colorDaySecondary = colorSecondary.toColor()
        colorNightPrimary = colorDayPrimary
        colorNightSecondary = colorDaySecondary
        colorToast = colorPrimary.toColor()
        colorReadToast = colorPrimary.toColor()
        disabled = false
        special = false
        this.id = id
        this.name = name
        this.themeRes = themeRes
        this.themeAmoledRes = themeAmoledRes
        this.themeMD1Res = themeMD1Res
    }

    constructor(
        id: String,
        colorDayPrimary: String,
        colorDaySecondary: String,
        colorNightPrimary: String,
        colorNightSecondary: String,
        name: String,
        @StyleRes themeRes: Int,
        @StyleRes themeAmoledRes: Int,
        @StyleRes themeMD1Res: Int
    ) {
        this.colorDayPrimary = colorDayPrimary.toColor()
        this.colorDaySecondary = colorDaySecondary.toColor()
        this.colorNightPrimary = colorNightPrimary.toColor()
        this.colorNightSecondary = colorNightSecondary.toColor()
        colorToast = colorDayPrimary.toColor()
        colorReadToast = colorDayPrimary.toColor()
        disabled = false
        special = false
        this.id = id
        this.name = name
        this.themeRes = themeRes
        this.themeAmoledRes = themeAmoledRes
        this.themeMD1Res = themeMD1Res
    }

    fun toast(colorToast: String): ThemeValue {
        this.colorToast = colorToast.toColor()
        this.colorReadToast = colorToast.toColor()
        return this
    }

    fun toast(colorToast: Int): ThemeValue {
        this.colorToast = colorToast
        this.colorReadToast = colorToast
        return this
    }

    fun toast(colorToast: String, colorReadToast: String): ThemeValue {
        this.colorToast = colorToast.toColor()
        this.colorReadToast = colorReadToast.toColor()
        return this
    }

    fun toast(colorToast: Int, colorReadToast: Int): ThemeValue {
        this.colorToast = colorToast
        this.colorReadToast = colorReadToast
        return this
    }

    fun enable(bEnable: Boolean): ThemeValue {
        disabled = !bEnable
        return this
    }

    fun specialised(bSpecial: Boolean): ThemeValue {
        special = bSpecial
        return this
    }
}

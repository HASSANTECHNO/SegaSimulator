package ir.segasim.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import ir.segasim.R

/**
 * توکن‌های رنگ برنامه — هویت «آبی سگا»:
 *  • تم تاریک: مشکی + آبی (مثل لوگوی آبی روی مشکی)
 *  • تم روشن: سفید + آبی
 * توکن‌های glass* مخصوص نوار ناوبری شیشه‌ای پایین صفحه‌اند.
 */
data class AppColors(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val cardAlt: Color,
    val txt: Color,
    val sub: Color,
    val line: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentOn: Color,
    val second: Color,
    val good: Color,
    val warn: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassBorder: Color,
)

/** تاریک: مشکی + آبی. */
private val DarkColors = AppColors(
    bg = Color(0xFF04060A),
    surface = Color(0xFF0A0E15),
    card = Color(0xFF0F151F),
    cardAlt = Color(0xFF17202E),
    txt = Color(0xFFE8F1FF),
    sub = Color(0xFF7C8BA3),
    line = Color(0xFF1B2533),
    accent = Color(0xFF2F7DF6),
    accentSoft = Color(0x332F7DF6),
    accentOn = Color(0xFFFFFFFF),
    second = Color(0xFF38BDF8),
    good = Color(0xFF34D399),
    warn = Color(0xFFF59E0B),
    glassTop = Color(0xE60C121B),
    glassBottom = Color(0xD9070B12),
    glassBorder = Color(0x26FFFFFF),
)

/** روشن: سفید + آبی. */
private val LightColors = AppColors(
    bg = Color(0xFFF4F8FF),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFEDF4FF),
    txt = Color(0xFF071A33),
    sub = Color(0xFF5A6B85),
    line = Color(0xFFD9E5F7),
    accent = Color(0xFF0B5CAB),
    accentSoft = Color(0x220B5CAB),
    accentOn = Color(0xFFFFFFFF),
    second = Color(0xFF0A7BC0),
    good = Color(0xFF12855A),
    warn = Color(0xFFA96A0E),
    glassTop = Color(0xF2FFFFFF),
    glassBottom = Color(0xE6EFF8FF),
    glassBorder = Color(0x14000000),
)

val LocalAppColors = staticCompositionLocalOf { DarkColors }

/** فونت وزیرمتن — خوانا برای فارسی، با سه وزن سبک/متوسط/بولد. */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

@Composable
fun AppTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val c = if (dark) DarkColors else LightColors

    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent,
            onPrimary = c.accentOn,
            secondary = c.second,
            background = c.bg,
            onBackground = c.txt,
            surface = c.surface,
            onSurface = c.txt,
            surfaceVariant = c.cardAlt,
            onSurfaceVariant = c.sub,
            outline = c.line,
        )
    } else {
        lightColorScheme(
            primary = c.accent,
            onPrimary = c.accentOn,
            secondary = c.second,
            background = c.bg,
            onBackground = c.txt,
            surface = c.surface,
            onSurface = c.txt,
            surfaceVariant = c.cardAlt,
            onSurfaceVariant = c.sub,
            outline = c.line,
        )
    }

    // آیکون‌های نوار وضعیت/ناوبری سیستم با تم هماهنگ شوند
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val ctrl = WindowCompat.getInsetsController(window, view)
            ctrl.isAppearanceLightStatusBars = !dark
            ctrl.isAppearanceLightNavigationBars = !dark
            window.statusBarColor = c.bg.value.toInt()
            window.navigationBarColor = c.bg.value.toInt()
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = appTypography(),
            content = content,
        )
    }
}

/** همه‌ی سبک‌های Material 3 را با فونت وزیرمتن بازمی‌سازد. */
private fun appTypography(): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = Vazirmatn),
        displayMedium = b.displayMedium.copy(fontFamily = Vazirmatn),
        displaySmall = b.displaySmall.copy(fontFamily = Vazirmatn),
        headlineLarge = b.headlineLarge.copy(fontFamily = Vazirmatn),
        headlineMedium = b.headlineMedium.copy(fontFamily = Vazirmatn),
        headlineSmall = b.headlineSmall.copy(fontFamily = Vazirmatn),
        titleLarge = b.titleLarge.copy(fontFamily = Vazirmatn),
        titleMedium = b.titleMedium.copy(fontFamily = Vazirmatn),
        titleSmall = b.titleSmall.copy(fontFamily = Vazirmatn),
        bodyLarge = b.bodyLarge.copy(fontFamily = Vazirmatn),
        bodyMedium = b.bodyMedium.copy(fontFamily = Vazirmatn),
        bodySmall = b.bodySmall.copy(fontFamily = Vazirmatn),
        labelLarge = b.labelLarge.copy(fontFamily = Vazirmatn),
        labelMedium = b.labelMedium.copy(fontFamily = Vazirmatn),
        labelSmall = b.labelSmall.copy(fontFamily = Vazirmatn),
    )
}

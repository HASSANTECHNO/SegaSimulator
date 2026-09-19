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
 * توکن‌های رنگ برنامه. یک پالت آرام و «کنسولی» (کهربایی + فیروزه‌ای روی
 * خاکستری خنثی) — بدون گرادیان‌های بنفش/آبیِ تکراری که ظاهر «هوش مصنوعی»
 * می‌دهد. توکن‌های glass* مخصوص نوار ناوبری شیشه‌ای پایین صفحه هستند.
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

private val DarkColors = AppColors(
    bg = Color(0xFF0E1013),
    surface = Color(0xFF14171C),
    card = Color(0xFF191D24),
    cardAlt = Color(0xFF212630),
    txt = Color(0xFFEDEFF3),
    sub = Color(0xFF8B94A3),
    line = Color(0xFF262C35),
    accent = Color(0xFFE9B44C),
    accentSoft = Color(0x2EE9B44C),
    accentOn = Color(0xFF20180A),
    second = Color(0xFF57B8C9),
    good = Color(0xFF4ECB8B),
    warn = Color(0xFFE9A23B),
    glassTop = Color(0xE61B1F27),
    glassBottom = Color(0xD913161C),
    glassBorder = Color(0x1FFFFFFF),
)

private val LightColors = AppColors(
    bg = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFF1F3F6),
    txt = Color(0xFF14171C),
    sub = Color(0xFF6A7280),
    line = Color(0xFFE2E5EA),
    accent = Color(0xFFB0741A),
    accentSoft = Color(0x22B0741A),
    accentOn = Color(0xFFFFFFFF),
    second = Color(0xFF1F7A8C),
    good = Color(0xFF1E9E63),
    warn = Color(0xFFB87411),
    glassTop = Color(0xF2FFFFFF),
    glassBottom = Color(0xE6F4F5F7),
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

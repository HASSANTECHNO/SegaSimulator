package ir.segasim.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.segasim.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

/**
 * اسپلش ورود: نشانِ اختصاصی برنامه روی پس‌زمینه‌ی مشکی + یک زنگ کوتاه.
 *
 * ⚠️ لوگو و «آوای ماندگار» رسمی سگا علائم تجاری و دارای حق نشر سگا است و
 * بازتولید آن مجاز نیست؛ اینجا یک نشانِ اصلی و یک زنگِ اختصاصی ساخته شده
 * که فقط حال‌وهوای آبیِ همان برند را دارد.
 */
@Composable
fun SegaLakSplash(onDone: () -> Unit) {
    val context = LocalContext.current
    val c = LocalAppColors.current
    var appear by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (appear) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "splashAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (appear) 1f else 0.86f,
        animationSpec = tween(durationMillis = 900),
        label = "splashScale",
    )

    // زنگ اختصاصی (بایت‌های WAV داخل res/raw — دارایی اصلی همین پروژه)
    DisposableEffect(Unit) {
        val mp = try {
            android.media.MediaPlayer.create(context, ir.segasim.R.raw.segalak_chime)
        } catch (_: Exception) {
            null
        }
        try {
            mp?.start()
        } catch (_: Exception) {
        }
        onDispose {
            try {
                mp?.release()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(Unit) {
        appear = true
        delay(2100)
        onDone()
    }

    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale),
        ) {
            SegaLakMark(size = 148.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                "سگالاک",
                color = c.txt,
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "SEGA · MASTER SYSTEM · MEGA DRIVE",
                color = c.sub,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
            )
        }
    }
}

/**
 * نشان اختصاصی «سگالاک»: صفحه‌ی آبیِ گردگوشه با نشانه‌ی دسته (دی‌پد + دو دکمه).
 * کاملاً برداری و اصلی — هیچ عنصری از لوگوی رسمی سگا کپی نشده است.
 */
@Composable
fun SegaLakMark(size: Dp) {
    val c = LocalAppColors.current
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        // صفحه‌ی آبی
        drawRoundRect(
            color = c.accent,
            cornerRadius = CornerRadius(s * 0.24f, s * 0.24f),
            size = Size(s, s),
        )
        // براقیِ ملایم بالای صفحه
        drawRoundRect(
            color = Color.White.copy(alpha = 0.15f),
            topLeft = Offset(s * 0.07f, s * 0.07f),
            size = Size(s * 0.86f, s * 0.38f),
            cornerRadius = CornerRadius(s * 0.18f, s * 0.18f),
        )
        // نشانه‌ی دی‌پد (سفید)
        val arm = s * 0.145f
        val cx = s * 0.45f
        val cy = s * 0.54f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(cx - arm * 0.5f, cy - arm * 1.55f),
            size = Size(arm, arm * 3.1f),
            cornerRadius = CornerRadius(arm * 0.25f, arm * 0.25f),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(cx - arm * 1.55f, cy - arm * 0.5f),
            size = Size(arm * 3.1f, arm),
            cornerRadius = CornerRadius(arm * 0.25f, arm * 0.25f),
        )
        // دو دکمه‌ی کنش (سفید)
        drawCircle(Color.White, radius = s * 0.075f, center = Offset(s * 0.73f, s * 0.33f))
        drawCircle(
            Color.White.copy(alpha = 0.78f),
            radius = s * 0.058f,
            center = Offset(s * 0.855f, s * 0.47f),
        )
    }
}

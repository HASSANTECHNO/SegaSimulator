package ir.segasim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.segasim.ui.theme.LocalAppColors

/**
 * نشانِ برنامه «سگالاک» — برداری و کاملاً اصلی:
 * یک صفحه‌ی آبی گردگوشه با نشانه‌ی دی‌پد و دو دکمه‌ی کنش.
 * در هدر تب خانه استفاده می‌شود.
 */
@Composable
fun SegaLakMark(size: Dp) {
    val c = LocalAppColors.current
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(
            color = c.accent,
            cornerRadius = CornerRadius(s * 0.24f, s * 0.24f),
            size = Size(s, s),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.15f),
            topLeft = Offset(s * 0.07f, s * 0.07f),
            size = Size(s * 0.86f, s * 0.38f),
            cornerRadius = CornerRadius(s * 0.18f, s * 0.18f),
        )
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
        drawCircle(Color.White, radius = s * 0.075f, center = Offset(s * 0.73f, s * 0.33f))
        drawCircle(
            Color.White.copy(alpha = 0.78f),
            radius = s * 0.058f,
            center = Offset(s * 0.855f, s * 0.47f),
        )
    }
}

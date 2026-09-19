package ir.segasim.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.segasim.ui.theme.LocalAppColors

/**
 * تب‌های ممکن نوار ناوبری پایین. چون کل برنامه راست‌به‌رچپ است و لیست به
 * ترتیب پیمایش می‌شود، «خانه» در سمت راست قرار می‌گیرد (مثل تلگرام فارسی).
 *
 * ترتیب خوانده‌شده از راست به چپ: خانه، بازی‌های من، پریمیوم، حساب.
 * تب «پریمیوم» فقط تا وقتی خرید انجام نشده نمایش داده می‌شود.
 */
enum class Tab(val label: String, val icon: ImageVector) {
    Home("خانه", Icons.Filled.Home),
    Mine("بازی‌های من", Icons.Filled.List),
    Premium("پریمیوم", Icons.Filled.Star),
    Account("حساب", Icons.Filled.AccountCircle),
}

/**
 * نوار ناوبری شیشه‌ای پایین صفحه — سبک تلگرام:
 *  • یک قرص شناور با گوشه‌های گرد
 *  • پس‌زمینه‌ی نیمه‌شفاف با گرادیان ملایم (حس شیشه/بلور)
 *  • خط مویی روشن روی لبه‌ها + سایه‌ی نرم
 *  • تب فعال با قرص آبی ملایم و متن بولد
 *
 * [tabs] عمداً پارامتر است تا پس از خرید پریمیوم، تبِ اضافه از نوار حذف شود.
 */
@Composable
fun GlassNavBar(
    selected: Tab,
    tabs: List<Tab>,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(26.dp), clip = false)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(c.glassTop, c.glassBottom)))
                .border(1.dp, c.glassBorder, RoundedCornerShape(26.dp))
                .padding(horizontal = 6.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { tab ->
                NavItem(
                    tab = tab,
                    active = selected == tab,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: Tab,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    val tint by animateColorAsState(if (active) c.accent else c.sub, label = "navTint")
    val pill by animateColorAsState(
        if (active) c.accentSoft else Color.Transparent, label = "navPill",
    )
    val scale by animateFloatAsState(if (active) 1f else 0.93f, label = "navScale")

    Column(
        modifier = modifier
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(pill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(21.dp).scale(scale),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

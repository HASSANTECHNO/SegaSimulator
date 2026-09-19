package ir.segasim.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.segasim.catalog.Game
import ir.segasim.catalog.PlayerMode
import ir.segasim.ui.theme.LocalAppColors

/* اجزای مشترک رابط — تمیز و تخت، بدون گرادیان‌های پرزرق‌وبرق. */

@Composable
fun SectionTitle(text: String, hint: String? = null, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Row(
        modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 3.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.accent)
        )
        Spacer(Modifier.width(9.dp))
        Text(text, color = c.txt, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
        if (hint != null) {
            Spacer(Modifier.width(8.dp))
            Text(hint, color = c.sub, fontSize = 11.5.sp)
        }
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(18.dp)
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(c.card)
        .border(1.dp, c.line, shape)

    Box(
        if (onClick != null) {
            base.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
        } else base
    ) { content() }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(14.dp)
    val bg = when {
        !enabled -> c.cardAlt
        filled -> c.accent
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> c.sub
        filled -> c.accentOn
        else -> c.txt
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(bg)
            .then(if (filled) Modifier else Modifier.border(1.dp, c.line, shape))
            .clickable(enabled = enabled) { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Pill(text: String, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = tint, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
    }
}

/** دو دکمه‌ی چیپی برای انتخاب یک‌نفره/دونفره. */
@Composable
fun SegChips(
    leftLabel: String,
    rightLabel: String,
    twoPlayer: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniChip(leftLabel, !twoPlayer) { onSelect(false) }
        MiniChip(rightLabel, twoPlayer) { onSelect(true) }
    }
}

@Composable
private fun MiniChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalAppColors.current
    val bg by animateColorAsState(if (selected) c.accentSoft else Color.Transparent, label = "chipBg")
    val fg by animateColorAsState(if (selected) c.accent else c.sub, label = "chipFg")
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) c.accent.copy(alpha = 0.5f) else c.line, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/**
 * ردیف بازی — برای کاتالوگ‌ها. بازی قفل با دکمه‌ی خرید نمایش داده می‌شود.
 */
@Composable
fun GameRow(
    game: Game,
    unlocked: Boolean,
    onPlay: (PlayerMode) -> Unit,
    onUnlock: (() -> Unit)? = null,
    defaultTwoPlayer: Boolean = false,
) {
    val c = LocalAppColors.current
    var twoPlayer by remember(game.id) { androidx.compose.runtime.mutableStateOf(defaultTwoPlayer) }
    val playable = game.isFree || unlocked

    AppCard {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (playable) c.accentSoft else c.cardAlt),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playable) androidx.compose.material.icons.Icons.Filled.PlayArrow
                        else androidx.compose.material.icons.Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (playable) c.accent else c.sub,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(game.title, color = c.txt, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(game.author, color = c.sub, fontSize = 11.5.sp)
                }
                if (game.supportsTwoPlayer) Pill("دونفره", c.second)
            }

            Spacer(Modifier.height(8.dp))
            Text(game.description, color = c.sub, fontSize = 12.sp)

            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (game.supportsTwoPlayer && playable) {
                    SegChips("۱ نفر", "دونفره", twoPlayer, { twoPlayer = it })
                    Spacer(Modifier.width(10.dp))
                }
                Spacer(Modifier.weight(1f))
                if (playable) {
                    SmallAction("شروع", c.accent, c.accentOn) {
                        onPlay(if (twoPlayer) PlayerMode.HotSeat else PlayerMode.Single)
                    }
                } else {
                    SmallAction("باز کن با خرید", c.warn, Color(0xFF20180A)) { onUnlock?.invoke() }
                }
            }
        }
    }
}

@Composable
fun SmallAction(
    text: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(text, color = fg, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EmptyHint(text: String, sub: String) {
    val c = LocalAppColors.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = c.txt, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(sub, color = c.sub, fontSize = 12.sp)
    }
}

/** نوار وضعیت کوچک بالای صفحه‌ها (پیام‌های کوتاه). */
@Composable
fun InlineStatus(text: String) {
    val c = LocalAppColors.current
    if (text.isBlank()) return
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(c.accentSoft).padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(text, color = c.accent, fontSize = 12.sp)
    }
}

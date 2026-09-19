package ir.segasim.ui

import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.segasim.catalog.PlayerMode
import ir.segasim.emu.EmulatorEngine
import ir.segasim.ui.theme.AppColors
import ir.segasim.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ShortBuffer

/* ------------------------------------------------------------------ *
 * بیت‌های ورودی — دقیقاً بر اساس ایندکس‌های استاندارد libretro         *
 * (RETRO_DEVICE_ID_JOYPAD_*).                                          *
 *                                                                     *
 * باگ نسخه‌ی قبل: این اعداد با «بیت‌های خام جنسیس» یکی گرفته شده بود  *
 * (UP=0x1، B=0x10 …) در حالی که هسته‌ی Genesis Plus GX بیتِ n را به    *
 * عنوان RETRO_DEVICE_ID_JOYPAD_n می‌خواند. نتیجه: دی‌پد روی دکمه‌های    *
 * B/C/A می‌افتاد و کل دسته جابه‌جا/برعکس کار می‌کرد.                  *
 *                                                                     *
 * نقشه‌ی واقعی هسته (از جدول retro_input_descriptor در libretro.c):   *
 *   RETRO B(0) → B سگا   |  RETRO Y(1) → A سگا                       *
 *   START(3)             |  UP(4) DOWN(5) LEFT(6) RIGHT(7)             *
 *   RETRO A(8) → C سگا   |  RETRO X(9) → Y سگا                        *
 *   RETRO L(10) → X سگا  |  RETRO R(11) → Z سگا                       *
 * ------------------------------------------------------------------ */
private const val BIT_B = 1 shl 0        // 0x0001 — دکمه‌ی B سگا
private const val BIT_A = 1 shl 1        // 0x0002 — دکمه‌ی A سگا
private const val BIT_START = 1 shl 3    // 0x0008 — START
private const val BIT_UP = 1 shl 4       // 0x0010 — بالا
private const val BIT_DOWN = 1 shl 5     // 0x0020 — پایین
private const val BIT_LEFT = 1 shl 6     // 0x0040 — چپ
private const val BIT_RIGHT = 1 shl 7    // 0x0080 — راست
private const val BIT_C = 1 shl 8        // 0x0100 — دکمه‌ی C سگا
private const val BIT_Y = 1 shl 9        // 0x0200 — Y سگا (ردیف بالا)
private const val BIT_X = 1 shl 10       // 0x0400 — X سگا
private const val BIT_Z = 1 shl 11       // 0x0800 — Z سگا

/**
 * صفحه‌ی اجرای بازی: ویدیو (RGB565) در بالا، دسته‌ی لمسی ۶ دکمه‌ای
 * (چیدمان سگا ستورن) در پایین. در حالت دونفره، بالای صفحه دسته‌ی
 * بازیکن ۲ و پایین دسته‌ی بازیکن ۱ است — هر دو با همان چیدمان.
 */
@Composable
fun EmulatorScreen(session: PlaySession, onExit: () -> Unit) {
    val context = LocalContext.current
    val frameTick = remember { mutableIntStateOf(0) }
    var desync by remember { mutableStateOf(false) }

    val engine = remember {
        EmulatorEngine(
            net = session.net, mode = session.mode,
            listener = object : EmulatorEngine.Listener {
                override fun onFrameReady(width: Int, height: Int) { frameTick.intValue++ }
                override fun onDesync(frame: Long) { desync = true }
            },
        )
    }

    var videoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var bmpW by remember { mutableIntStateOf(0) }
    var bmpH by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loaded = withContext(Dispatchers.IO) {
            engine.loadRom(session.rom, context.filesDir.absolutePath)
        }
        if (loaded) {
            engine.start()
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                val track = AudioTrack(
                    AudioManager.STREAM_MUSIC, 44100,
                    AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                    44100 * 4, AudioTrack.MODE_STREAM,
                )
                track.play()
                while (true) {
                    val data = engine.drainAudio()
                    if (data.isNotEmpty()) track.write(data, 0, data.size)
                    else Thread.sleep(4)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            engine.stop()
            session.net?.disconnect()
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // ── ویدیو ───────────────────────────────────────────────
        Box(
            Modifier.fillMaxWidth().weight(0.56f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (loaded) {
                frameTick.intValue
                val src = engine.lastFrame
                val w = engine.lastWidth
                val h = engine.lastHeight
                if (src != null && w > 0 && h > 0) {
                    var bmp = videoBitmap
                    if (bmp == null || bmpW != w || bmpH != h) {
                        bmp = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                        videoBitmap = bmp; bmpW = w; bmpH = h
                    }
                    synchronized(bmp) {
                        val sb = ShortBuffer.wrap(src, 0, w * h)
                        bmp.copyPixelsFromBuffer(sb)
                    }
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "نمایش بازی",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                Text("در حال بارگذاری…", color = Color.White, fontSize = 13.sp)
            }

            if (desync) {
                Text(
                    "ناهماهنگی — اتصال را دوباره برقرار کن",
                    color = Color(0xFFFF6B6B), fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExitChip(onExit)
                Spacer(Modifier.weight(1f))
                Text(
                    session.title,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                )
            }
        }

        // ── دسته ────────────────────────────────────────────────
        when (session.mode) {
            PlayerMode.Single -> SegaPad(
                label = null,
                onBits = { engine.padBits[0] = it },
                modifier = Modifier.weight(0.44f).fillMaxWidth(),
            )
            PlayerMode.HotSeat -> Column(Modifier.weight(0.44f).fillMaxWidth()) {
                SegaPad(
                    label = "بازیکن ۲",
                    onBits = { engine.padBits[1] = it },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    compact = true,
                )
                HorizontalDivider(color = Color(0xFF11161F))
                SegaPad(
                    label = "بازیکن ۱",
                    onBits = { engine.padBits[0] = it },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun ExitChip(onExit: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
    ) {
        TextButton(onClick = onExit) {
            Text("بازگشت", color = Color.White, fontSize = 12.5.sp)
        }
    }
}

/**
 * دسته‌ی لمسی ۶ دکمه‌ای به سبک سگا ستورن (مطابق تصویری که کاربر فرستاد):
 *  • چپ : یک صفحه‌ی گرد با دی‌پد (+) و فلش‌های ▲ ◀ ▶ ▼
 *  • راست: شش دکمه‌ی گرد در دو ردیفِ کج‌شده —
 *          ردیف پایین A B C (A پایین‌ترین، C بالاتر)
 *          ردیف بالا  X Y Z (X پایین‌ترین، Z بالاترین)
 *  • وسطِ پایین: دکمه‌ی بیضی START
 *
 * چیدمان داخل دسته با LayoutDirection.Ltr قفل شده تا در برنامه‌ی
 * راست‌به‌چپ آینه نشود؛ دسته‌ی واقعی هم همین شکل است.
 * هر دو دسته (بازیکن ۱ و ۲) دقیقاً همین چیدمان را دارند.
 */
@Composable
fun SegaPad(
    label: String?,
    onBits: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val c = LocalAppColors.current
    val held = remember { mutableStateMapOf<Int, Boolean>() }
    val key = if (compact) 30.dp else 46.dp

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier) {
            if (label != null) {
                Text(
                    label, color = c.sub, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (label != null) 14.dp else 2.dp,
                        start = 12.dp, end = 12.dp, bottom = 2.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegaDPad(held, onBits, key)
                Spacer(Modifier.weight(1f))
                FaceCluster(held, onBits, key)
            }
            StartKey(
                held, onBits,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (compact) 0.dp else 4.dp),
                key = key,
            )
        }
    }
}

/** صفحه‌ی گرد دی‌پد با بازوهای صلیبی و چهار فلش. */
@Composable
private fun SegaDPad(
    held: MutableMap<Int, Boolean>,
    onBits: (Int) -> Unit,
    key: Dp,
) {
    val c = LocalAppColors.current
    val d = key * 3.3f
    Box(Modifier.size(d), contentAlignment = Alignment.Center) {
        // صفحه‌ی گرد
        Box(
            Modifier.fillMaxSize().clip(CircleShape)
                .background(Brush.verticalGradient(listOf(c.cardAlt, c.card)))
                .border(1.dp, c.line, CircleShape)
        )
        // بازوهای صلیبی
        Box(
            Modifier.size(width = key * 1.12f, height = d * 0.88f)
                .clip(RoundedCornerShape(key * 0.2f))
                .background(Brush.verticalGradient(listOf(c.card, c.bg)))
        )
        Box(
            Modifier.size(width = d * 0.88f, height = key * 1.12f)
                .clip(RoundedCornerShape(key * 0.2f))
                .background(Brush.verticalGradient(listOf(c.card, c.bg)))
        )
        // مرکز
        Box(Modifier.size(key * 0.46f).clip(CircleShape).background(c.bg.copy(alpha = 0.9f)))
        // چهار جهت
        PadKey("▲", BIT_UP, held, onBits, Modifier.align(Alignment.TopCenter).size(key), flat = true)
        PadKey("▼", BIT_DOWN, held, onBits, Modifier.align(Alignment.BottomCenter).size(key), flat = true)
        PadKey("◀", BIT_LEFT, held, onBits, Modifier.align(Alignment.CenterStart).size(key), flat = true)
        PadKey("▶", BIT_RIGHT, held, onBits, Modifier.align(Alignment.CenterEnd).size(key), flat = true)
    }
}

/**
 * شش دکمه‌ی کنش در دو ردیفِ کج‌شده (بالا-راست).
 * ردیف پایین: A B C — ردیف بالا: X Y Z
 */
@Composable
private fun FaceCluster(
    held: MutableMap<Int, Boolean>,
    onBits: (Int) -> Unit,
    key: Dp,
) {
    val g = key * 0.16f
    val w = key * 3.6f + g * 2f
    val h = key * 2.25f
    Box(Modifier.size(width = w, height = h)) {
        // ردیف بالا: X Y Z
        PadKey("X", BIT_X, held, onBits, Modifier.offset(x = key * 0.55f, y = key * 0.34f).size(key))
        PadKey("Y", BIT_Y, held, onBits, Modifier.offset(x = key * 1.55f + g, y = key * 0.18f).size(key))
        PadKey("Z", BIT_Z, held, onBits, Modifier.offset(x = key * 2.55f + g * 2f, y = 0.dp).size(key))
        // ردیف پایین: A B C
        PadKey("A", BIT_A, held, onBits, Modifier.offset(x = 0.dp, y = key * 1.20f).size(key))
        PadKey("B", BIT_B, held, onBits, Modifier.offset(x = key + g, y = key * 0.96f).size(key))
        PadKey("C", BIT_C, held, onBits, Modifier.offset(x = key * 2f + g * 2f, y = key * 0.72f).size(key))
    }
}

/** دکمه‌ی START: بیضی کوچک وسطِ پایین. */
@Composable
private fun StartKey(
    held: MutableMap<Int, Boolean>,
    onBits: (Int) -> Unit,
    modifier: Modifier,
    key: Dp,
) {
    PadKey(
        label = "START",
        key = BIT_START,
        held = held,
        onBits = onBits,
        modifier = modifier.width(key * 2f).height(key * 0.62f),
        pill = true,
    )
}

private fun computeBits(held: Map<Int, Boolean>): Int {
    var b = 0
    if (held[BIT_UP] == true) b = b or BIT_UP
    if (held[BIT_DOWN] == true) b = b or BIT_DOWN
    if (held[BIT_LEFT] == true) b = b or BIT_LEFT
    if (held[BIT_RIGHT] == true) b = b or BIT_RIGHT
    if (held[BIT_A] == true) b = b or BIT_A
    if (held[BIT_B] == true) b = b or BIT_B
    if (held[BIT_C] == true) b = b or BIT_C
    if (held[BIT_X] == true) b = b or BIT_X
    if (held[BIT_Y] == true) b = b or BIT_Y
    if (held[BIT_Z] == true) b = b or BIT_Z
    if (held[BIT_START] == true) b = b or BIT_START
    return b
}

/**
 * یک دکمه‌ی دسته با «نگه‌داشتن» درست: تا وقتی انگشت روی دکمه است بیت آن
 * روشن می‌ماند و با برداشتن انگشت خاموش می‌شود.
 */
@Composable
private fun PadKey(
    label: String,
    key: Int,
    held: MutableMap<Int, Boolean>,
    onBits: (Int) -> Unit,
    modifier: Modifier = Modifier,
    flat: Boolean = false,
    pill: Boolean = false,
) {
    val c = LocalAppColors.current
    var pressed by remember { mutableStateOf(false) }
    val shape: Shape = if (pill) RoundedCornerShape(999.dp) else CircleShape

    Box(
        modifier
            .clip(shape)
            .then(
                if (!flat) {
                    Modifier.background(
                        if (pressed) Brush.verticalGradient(listOf(c.accent, c.accent.copy(alpha = 0.82f)))
                        else Brush.verticalGradient(listOf(c.cardAlt, c.card))
                    ).border(1.dp, if (pressed) c.accent else c.line, shape)
                } else Modifier
            )
            .then(
                if (flat && pressed) Modifier.background(c.accent.copy(alpha = 0.55f), shape)
                else Modifier
            )
            .pointerInput(key) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    held[key] = true
                    onBits(computeBits(held))
                    waitForUpOrCancellation()
                    pressed = false
                    held[key] = false
                    onBits(computeBits(held))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (pressed) (if (flat) c.txt else c.accentOn) else (if (flat) c.sub else c.txt),
            fontSize = if (pill) 10.sp else if (key >= 40) 15.sp else 12.sp,
            fontWeight = if (pill) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** دیالوگ اتصال شبکه‌ای LAN. */
@Composable
fun NetDialog(onConnect: (Boolean, String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalAppColors.current
    var ip by remember { mutableStateOf("192.168.1.100") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text("بازی شبکه‌ای (LAN)", color = c.txt, fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    "هر دو گوشی روی یک وای‌فای باشند و ROM یکسان داشته باشند. " +
                        "میزبان منتظر می‌ماند و مهمان IP میزبان را وارد می‌کند.",
                    color = c.sub, fontSize = 12.5.sp,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = { Text("IP میزبان") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onConnect(true, "") }) { Text("میزبان", color = c.good) }
                TextButton(onClick = { onConnect(false, ip) }) { Text("مهمان", color = c.second) }
                TextButton(onClick = onDismiss) { Text("بستن", color = c.sub) }
            }
        },
    )
}

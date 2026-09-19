package ir.segasim.ui

import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import ir.segasim.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ShortBuffer

/* ------------------------------------------------------------------ *
 * بیت‌های ورودی — دقیقاً بر اساس ایندکس‌های استاندارد libretro         *
 * (RETRO_DEVICE_ID_JOYPAD_*).                                          *
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

/** جهت‌های مورب D-pad: هر ناحیه هر دو بیت را با هم می‌فرستد. */
private const val BIT_UP_LEFT = BIT_UP or BIT_LEFT
private const val BIT_UP_RIGHT = BIT_UP or BIT_RIGHT
private const val BIT_DOWN_LEFT = BIT_DOWN or BIT_LEFT
private const val BIT_DOWN_RIGHT = BIT_DOWN or BIT_RIGHT

/**
 * صفحه‌ی اجرای بازی: ویدیو (RGB565) در بالا، دسته‌ی لمسی ۶ دکمه‌ای
 * (چیدمان سگا ستورن) در پایین.
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
 * دسته‌ی لمسی ۶ دکمه‌ای به سبک سگا ستورن:
 *  • چپ : صفحه‌ی گرد با دی‌پد ۸ جهته
 *  • راست: شش دکمه‌ی گرد در دو ردیفِ کج‌شده (پایین A B C، بالا X Y Z)
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

/**
 * دی‌پد ۸ جهته روی یک صفحه‌ی گرد (سبک دسته‌ی شش‌دکمه‌ای).
 *
 * چهار ناحیه‌ی مورب گوشه‌ها — همان جاهایی که در تصویر کاربر با دایره‌ی
 * قرمز مشخص شده بودند — هرکدام جهت مورب خودشان را می‌فرستند
 * (بالا‑چپ، بالا‑راست، پایین‑چپ، پایین‑راست) و فلشِ مثلثی هم رو به بیرون
 * همان گوشه اشاره می‌کند. چهار ناحیه‌ی میانی بازوها، جهت‌های اصلی
 * (بالا/پایین/چپ/راست) را می‌دهند؛ چون همه‌ی بیت‌های فعال OR می‌شوند،
 * ترکیب‌ها به‌طور طبیعی ۸ جهته کار می‌کنند.
 */
@Composable
private fun SegaDPad(
    held: MutableMap<Int, Boolean>,
    onBits: (Int) -> Unit,
    key: Dp,
) {
    val c = LocalAppColors.current
    val d = key * 3.3f
    val corner = key * 1.12f
    val mid = key * 0.98f

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
        Box(Modifier.size(key * 0.42f).clip(CircleShape).background(c.bg.copy(alpha = 0.9f)))

        // ── چهار جهت اصلی، روی بازوها ─────────────────────────
        DirKey(
            bits = BIT_UP, held = held, onBits = onBits,
            arrow = Arrow.Up, active = c.accent,
            modifier = Modifier.align(Alignment.TopCenter).size(key, mid),
        )
        DirKey(
            bits = BIT_DOWN, held = held, onBits = onBits,
            arrow = Arrow.Down, active = c.accent,
            modifier = Modifier.align(Alignment.BottomCenter).size(key, mid),
        )
        DirKey(
            bits = BIT_LEFT, held = held, onBits = onBits,
            arrow = Arrow.Left, active = c.accent,
            modifier = Modifier.align(Alignment.CenterStart).size(mid, key),
        )
        DirKey(
            bits = BIT_RIGHT, held = held, onBits = onBits,
            arrow = Arrow.Right, active = c.accent,
            modifier = Modifier.align(Alignment.CenterEnd).size(mid, key),
        )

        // ── چهار ناحیه‌ی مورب، در گوشه‌های صفحه ───────────────
        DirKey(
            bits = BIT_UP_LEFT, held = held, onBits = onBits,
            arrow = Arrow.UpLeft, active = c.accent,
            modifier = Modifier.align(Alignment.TopStart).size(corner, corner),
        )
        DirKey(
            bits = BIT_UP_RIGHT, held = held, onBits = onBits,
            arrow = Arrow.UpRight, active = c.accent,
            modifier = Modifier.align(Alignment.TopEnd).size(corner, corner),
        )
        DirKey(
            bits = BIT_DOWN_LEFT, held = held, onBits = onBits,
            arrow = Arrow.DownLeft, active = c.accent,
            modifier = Modifier.align(Alignment.BottomStart).size(corner, corner),
        )
        DirKey(
            bits = BIT_DOWN_RIGHT, held = held, onBits = onBits,
            arrow = Arrow.DownRight, active = c.accent,
            modifier = Modifier.align(Alignment.BottomEnd).size(corner, corner),
        )
    }
}

/** جهتِ فلشِ ناحیه‌ها. */
private enum class Arrow { Up, Down, Left, Right, UpLeft, UpRight, DownLeft, DownRight }

/**
 * یک ناحیه‌ی جهت روی دی‌پد: با نگه‌داشتن انگشت، بیت‌های آن ناحیه روشن
 * می‌مانند و مثلثِ آن به سمت بیرونِ دی‌پد اشاره می‌کند.
 */
@Composable
private fun DirKey(
    bits: Int,
    held: MutableMap<Int, Boolean>,
    onBits: (Int) -> Unit,
    arrow: Arrow,
    active: Color,
    modifier: Modifier,
) {
    val c = LocalAppColors.current
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(bits) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    held[bits] = true
                    onBits(computeBits(held))
                    waitForUpOrCancellation()
                    pressed = false
                    held.remove(bits)
                    onBits(computeBits(held))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val w = size.width
            val h = size.height
            val path = Path()
            when (arrow) {
                Arrow.Up -> {
                    path.moveTo(w / 2f, 0f); path.lineTo(w, h); path.lineTo(0f, h)
                }
                Arrow.Down -> {
                    path.moveTo(w / 2f, h); path.lineTo(0f, 0f); path.lineTo(w, 0f)
                }
                Arrow.Left -> {
                    path.moveTo(0f, h / 2f); path.lineTo(w, 0f); path.lineTo(w, h)
                }
                Arrow.Right -> {
                    path.moveTo(w, h / 2f); path.lineTo(0f, 0f); path.lineTo(0f, h)
                }
                Arrow.UpLeft -> {
                    path.moveTo(0f, 0f); path.lineTo(w, 0f); path.lineTo(0f, h)
                }
                Arrow.UpRight -> {
                    path.moveTo(w, 0f); path.lineTo(0f, 0f); path.lineTo(w, h)
                }
                Arrow.DownLeft -> {
                    path.moveTo(0f, h); path.lineTo(w, h); path.lineTo(0f, 0f)
                }
                Arrow.DownRight -> {
                    path.moveTo(w, h); path.lineTo(0f, h); path.lineTo(w, 0f)
                }
            }
            path.close()
            drawPath(path, color = if (pressed) active else c.sub.copy(alpha = 0.62f))
        }
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
    for (k in held.keys) if (held[k] == true) b = b or k
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
    pill: Boolean = false,
) {
    val c = LocalAppColors.current
    var pressed by remember { mutableStateOf(false) }
    val shape: Shape = if (pill) RoundedCornerShape(999.dp) else CircleShape

    Box(
        modifier
            .clip(shape)
            .background(
                if (pressed) Brush.verticalGradient(listOf(c.accent, c.accent.copy(alpha = 0.82f)))
                else Brush.verticalGradient(listOf(c.cardAlt, c.card))
            )
            .border(1.dp, if (pressed) c.accent else c.line, shape)
            .pointerInput(key) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    held[key] = true
                    onBits(computeBits(held))
                    waitForUpOrCancellation()
                    pressed = false
                    held.remove(key)
                    onBits(computeBits(held))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (pressed) c.accentOn else c.txt,
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

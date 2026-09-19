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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.segasim.catalog.PlayerMode
import ir.segasim.emu.EmulatorEngine
import ir.segasim.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ShortBuffer

/* بیت‌های ورودی دسته‌ی مگا درایو (۳ دکمه) */
private const val BIT_UP = 0x0001
private const val BIT_DOWN = 0x0002
private const val BIT_LEFT = 0x0004
private const val BIT_RIGHT = 0x0008
private const val BIT_B = 0x0010
private const val BIT_C = 0x0020
private const val BIT_A = 0x0040
private const val BIT_START = 0x0080

/**
 * صفحه‌ی اجرای بازی: ویدیو (RGB565) در بالا، دسته‌ی لمسی سگا در پایین.
 * در حالت دونفره، بالای صفحه دسته‌ی بازیکن ۲ و پایین دسته‌ی بازیکن ۱.
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
                HorizontalDivider(color = Color(0xFF1D222B))
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
 * دسته‌ی لمسی سگا مگا درایو (۳ دکمه):
 *  • چپ: دی‌پد (+)
 *  • راست: دکمه‌های A B C روی یک قوس بالارونده + START
 * چیدمان داخل دسته همیشه چپ‌به‌راست است تا شبیه دسته‌ی واقعی بماند،
 * حتی وقتی کل برنامه راست‌به‌چپ است.
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
    val key = if (compact) 42.dp else 54.dp

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
                        top = if (label != null) 15.dp else 4.dp,
                        start = 16.dp, end = 16.dp, bottom = 4.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DPad(held, onBits, key)
                Spacer(Modifier.weight(1f))
                RightCluster(held, onBits, key, compact)
            }
        }
    }
}

@Composable
private fun DPad(held: MutableMap<Int, Boolean>, onBits: (Int) -> Unit, key: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(key * 3)) {
        PadKey("▲", BIT_UP, held, onBits, Modifier.align(Alignment.TopCenter).size(key))
        PadKey("◀", BIT_LEFT, held, onBits, Modifier.align(Alignment.CenterStart).size(key))
        PadKey("▶", BIT_RIGHT, held, onBits, Modifier.align(Alignment.CenterEnd).size(key))
        PadKey("▼", BIT_DOWN, held, onBits, Modifier.align(Alignment.BottomCenter).size(key))
    }
}

@Composable
private fun RightCluster(
    held: MutableMap<Int, Boolean>,
    onBits: (Int) -> Unit,
    key: androidx.compose.ui.unit.Dp,
    compact: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            // قوس بالارونده: A پایین‌چپ، B وسط، C بالاراست — مثل دسته‌ی واقعی
            PadKey("A", BIT_A, held, onBits, Modifier.offset(y = key / 3).size(key))
            Spacer(Modifier.width(9.dp))
            PadKey("B", BIT_B, held, onBits, Modifier.offset(y = key / 6).size(key))
            Spacer(Modifier.width(9.dp))
            PadKey("C", BIT_C, held, onBits, Modifier.size(key))
        }
        Spacer(Modifier.height(12.dp))
        PadKey(
            "START", BIT_START, held, onBits,
            Modifier.width(key * 2).height(if (compact) 30.dp else 36.dp),
            round = true, compact = true,
        )
    }
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
    if (held[BIT_START] == true) b = b or BIT_START
    return b
}

/**
 * یک دکمه‌ی دسته با نگه‌داشتن درست: تا وقتی انگشت روی دکمه است بیت آن
 * روشن می‌ماند و با برداشتن انگشت خاموش می‌شود. این همان چیزی است که
 * قبلاً کار نمی‌کرد (بیت بلافاصله آزاد می‌شد و حرکت/شلیک ثبت نمی‌شد).
 */
@Composable
private fun PadKey(
    label: String,
    key: Int,
    held: MutableMap<Int, Boolean>,
    onBits: (Int) -> Unit,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    compact: Boolean = false,
) {
    val c = LocalAppColors.current
    var pressed by remember { mutableStateOf(false) }
    val shape = if (round) CircleShape else RoundedCornerShape(if (compact) 12.dp else 16.dp)

    Box(
        modifier
            .clip(shape)
            .background(if (pressed) c.accent else c.cardAlt)
            .border(1.dp, if (pressed) c.accent else c.line, shape)
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
            color = if (pressed) c.accentOn else c.txt,
            fontSize = if (compact) 11.sp else 14.sp,
            fontWeight = FontWeight.Bold,
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

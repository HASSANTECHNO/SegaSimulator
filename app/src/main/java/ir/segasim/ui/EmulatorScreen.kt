package ir.segasim.ui

import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import ir.segasim.catalog.PlayerMode
import ir.segasim.emu.EmulatorEngine
import ir.segasim.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ShortBuffer

/**
 * صفحه‌ی اجرای بازی: ویدیو (RGB565) در بالا، کنترل لمسی در پایین.
 * در حالت دونفره، نیمه‌ی بالایی صفحه دسته‌ی بازیکن ۲ و نیمه‌ی پایینی
 * دسته‌ی بازیکن ۱ است.
 */
@Composable
fun EmulatorScreen(session: PlaySession, onExit: () -> Unit) {
    val context = LocalContext.current
    val c = LocalAppColors.current
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

            // نوار بالای صفحه: خروج + نام بازی + حالت
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

        // ── کنترل‌ها ────────────────────────────────────────────
        when (session.mode) {
            PlayerMode.Single -> TouchControls(
                label = null,
                onPad = { bits -> engine.padBits[0] = bits },
                modifier = Modifier.weight(0.44f).fillMaxWidth(),
            )
            PlayerMode.HotSeat -> Column(Modifier.weight(0.44f).fillMaxWidth()) {
                TouchControls(
                    label = "بازیکن ۲",
                    onPad = { bits -> engine.padBits[1] = bits },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    compact = true,
                )
                HorizontalDivider(color = Color(0xFF1D222B))
                TouchControls(
                    label = "بازیکن ۱",
                    onPad = { bits -> engine.padBits[0] = bits },
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
 * دسته‌ی لمسی: D-pad در سمت راست (به دلیل راست‌به‌چپ بودن چیدمان،
 * از دید کاربر در سمت راست صفحه است) و دکمه‌های A/B/C و Start در سمت چپ.
 */
@Composable
fun TouchControls(
    label: String?,
    onPad: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val c = LocalAppColors.current
    val held = remember { mutableStateMapOf<String, Boolean>() }

    fun recompute() {
        var bits = 0
        if (held["up"] == true) bits = bits or 0x0001
        if (held["down"] == true) bits = bits or 0x0002
        if (held["left"] == true) bits = bits or 0x0004
        if (held["right"] == true) bits = bits or 0x0008
        if (held["b"] == true) bits = bits or 0x0010
        if (held["c"] == true) bits = bits or 0x0020
        if (held["a"] == true) bits = bits or 0x0040
        if (held["start"] == true) bits = bits or 0x0080
        onPad(bits)
    }

    Box(modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
        if (label != null) {
            Text(
                label, color = c.sub, fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 1.dp),
            )
        }
        Row(
            Modifier.fillMaxSize().padding(top = if (label != null) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // دکمه‌های عملکردی
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PadButton("C", compact, Modifier.fillMaxWidth(0.9f)) {
                    held["c"] = true; recompute()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PadButton("B", compact, Modifier.weight(1f)) { held["b"] = true; recompute() }
                    PadButton("A", compact, Modifier.weight(1f)) { held["a"] = true; recompute() }
                }
            }

            Spacer(Modifier.weight(1f))

            // دی‌پد
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PadButton("▲", compact, Modifier.width(48.dp)) { held["up"] = true; recompute() }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PadButton("◀", compact, Modifier.width(48.dp)) { held["left"] = true; recompute() }
                    PadButton("▶", compact, Modifier.width(48.dp)) { held["right"] = true; recompute() }
                }
                Spacer(Modifier.height(4.dp))
                PadButton("▼", compact, Modifier.width(48.dp)) { held["down"] = true; recompute() }
            }
        }

        // Start در گوشه
        Box(Modifier.align(Alignment.BottomStart)) {
            PadButton("Start", true, Modifier.width(70.dp)) { held["start"] = true; recompute() }
        }
    }
}

@Composable
private fun PadButton(
    label: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onPress: suspend (awaitRelease: suspend () -> Unit) -> Unit,
) {
    val c = LocalAppColors.current
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(if (compact) 34.dp else 42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed) c.accent else c.card)
            .border(1.dp, if (pressed) c.accent else c.line, RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onPress { }
                        pressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (pressed) c.accentOn else c.sub,
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = if (pressed) FontWeight.Bold else FontWeight.Normal,
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

package ir.segasim.ui

import android.app.Activity
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.segasim.billing.EntitlementRepository
import ir.segasim.catalog.Game
import ir.segasim.catalog.GameRegistry
import ir.segasim.catalog.PlayerMode
import ir.segasim.emu.EmulatorEngine
import ir.segasim.net.NetSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ShortBuffer

/* ===================== پالت ===================== */

private val Bg = Color(0xFF0E1116)
private val Card1 = Color(0xFF171C24)
private val Accent = Color(0xFF7C5CFF)
private val Accent2 = Color(0xFF38BDF8)
private val Good = Color(0xFF34D399)
private val Warn = Color(0xFFF59E0B)
private val Txt = Color(0xFFE7EAF2)
private val Sub = Color(0xFF9AA3B2)

private sealed interface Screen {
    data object Menu : Screen
    data class Play(val title: String, val rom: ByteArray, val mode: PlayerMode, val net: NetSync?) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SegaSimApp() }
    }
}

@Composable
fun SegaSimApp() {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    val billing = remember { EntitlementRepository(context) { v -> unlocked = v } }

    LaunchedEffect(Unit) {
        billing.selectProvider()
        kotlinx.coroutines.withContext(Dispatchers.IO) { billing.refreshFromStore() }
    }
    DisposableEffect(Unit) { onDispose { billing.disconnect() } }

    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg, surface = Card1, primary = Accent, secondary = Accent2,
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            when (val s = screen) {
                is Screen.Play -> GameScreen(
                    title = s.title, rom = s.rom, mode = s.mode, net = s.net,
                    onExit = { screen = Screen.Menu },
                )
                is Screen.Menu -> MenuScreen(
                    unlocked = unlocked,
                    storeName = billing.providerDisplayName(),
                    onUnlockClick = { (context as? Activity)?.let { billing.launchPurchase(it) } },
                    onPlay = { title, rom, mode, net -> screen = Screen.Play(title, rom, mode, net) },
                )
            }
        }
    }
}

/* ===================== منوی اصلی ===================== */

@Composable
fun MenuScreen(
    unlocked: Boolean,
    storeName: String,
    onUnlockClick: () -> Unit,
    onPlay: (String, ByteArray, PlayerMode, NetSync?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var showNetDialog by remember { mutableStateOf(false) }
    var importedRom by remember { mutableStateOf<ByteArray?>(null) }
    var importedName by remember { mutableStateOf("") }
    var twoPlayerImport by remember { mutableStateOf(false) }

    val romPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            status = "در حال خواندن…"
            val (bytes, name) = withContext(Dispatchers.IO) { readRom(context, uri) }
            importedRom = bytes
            importedName = name
            status = if (bytes != null) "ROM آماده است: $name" else "خطا در خواندن فایل"
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(28.dp))
                Box(
                    Modifier.fillMaxWidth().height(140.dp)
                        .background(
                            Brush.linearGradient(listOf(Accent, Accent2)),
                            RoundedCornerShape(20.dp),
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text("سیمولاتور سگا", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("مگا درایو · مستر سیستم · گیم گیر — آفلاین و آنلاین",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(8.dp).background(
                                    if (storeName.contains("بازار") || storeName.contains("مایکت"))
                                        Good else Warn,
                                    RoundedCornerShape(50),
                                )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(storeName, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                        }
                    }
                }
            }

            item { SectionTitle("🎮 بازی‌های رایگان", Good) }
            items(GameRegistry.freeGames(), key = { it.id }) { g ->
                GameCard(game = g, unlocked = true,
                    onPlay = { mode -> loadAndPlay(g, context, onPlay, { status = it }, mode) })
            }

            item { SectionTitle("⭐ بسته ویژه (پرداخت از $storeName)", Warn) }
            items(GameRegistry.lockedGames(), key = { it.id }) { g ->
                GameCard(game = g, unlocked = unlocked,
                    onPlay = { mode -> loadAndPlay(g, context, onPlay, { status = it }, mode) },
                    onUnlock = onUnlockClick)
            }

            item { SectionTitle("📁 ROM خودت", Accent2) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TwoChips(
                        left = Pair("یک‌نفره", !twoPlayerImport),
                        right = Pair("دونفره (همین گوشی)", twoPlayerImport),
                        onLeft = { twoPlayerImport = false },
                        onRight = { twoPlayerImport = true },
                    )
                    PrimaryButton("انتخاب فایل ROM از دستگاه") { romPicker.launch(arrayOf("*/*")) }
                    val romNow = importedRom
                    if (romNow != null) {
                        PrimaryButton("شروع بازی: $importedName") {
                            onPlay(importedName, romNow,
                                if (twoPlayerImport) PlayerMode.HotSeat else PlayerMode.Single, null)
                        }
                    }
                    PrimaryButton("بازی آنلاین (LAN) با دو گوشی") { showNetDialog = true }
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    "هسته: Genesis Plus GX (مجوز غیرتجاری) · پرداخت فقط از بازار/مایکت · ROM تجاری همراه اپ توزیع نمی‌شود",
                    color = Sub, fontSize = 11.sp,
                )
                Spacer(Modifier.height(34.dp))
            }
        }

        if (status.isNotEmpty()) {
            Card(
                Modifier.align(Alignment.BottomCenter).padding(18.dp),
                colors = CardDefaults.cardColors(containerColor = Card1),
            ) {
                Text(status, Modifier.padding(14.dp), color = Accent2, fontSize = 13.sp)
            }
        }
    }

    if (showNetDialog) {
        NetDialog(
            onConnect = { isHost, ip ->
                showNetDialog = false
                scope.launch {
                    status = "در حال اتصال…"
                    val rom = importedRom
                    if (rom == null) { status = "اول ROM را انتخاب کن"; return@launch }
                    val sync = NetSync(isHost = isHost, host = if (isHost) null else ip)
                    val ok = withContext(Dispatchers.IO) { sync.connect() }
                    if (ok) {
                        withContext(Dispatchers.IO) { sync.handshake() }
                        onPlay(importedName, rom, PlayerMode.Single, sync)
                    } else status = "اتصال ناموفق — IP/پورت را چک کن"
                }
            },
            onDismiss = { showNetDialog = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(text, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun GameCard(
    game: Game, unlocked: Boolean,
    onPlay: (PlayerMode) -> Unit, onUnlock: (() -> Unit)? = null,
) {
    var twoPlayer by remember(game.id) { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Card1),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).background(
                        Brush.linearGradient(listOf(Accent, Accent2)), RoundedCornerShape(12.dp)
                    ), contentAlignment = Alignment.Center,
                ) {
                    Text(if (game.isFree || unlocked) "▶" else "🔒", color = Color.White, fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(game.title, color = Txt, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(game.author, color = Sub, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(game.description, color = Sub, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (game.supportsTwoPlayer) {
                    TwoChips(
                        left = Pair("۱ نفر", !twoPlayer),
                        right = Pair("دونفره", twoPlayer),
                        onLeft = { twoPlayer = false },
                        onRight = { twoPlayer = true },
                    )
                }
                if (game.isFree || unlocked) {
                    Button(
                        onClick = { onPlay(if (twoPlayer) PlayerMode.HotSeat else PlayerMode.Single) },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    ) { Text("شروع", color = Color.White) }
                } else {
                    Button(
                        onClick = onUnlock ?: {},
                        colors = ButtonDefaults.buttonColors(containerColor = Warn),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    ) { Text("باز کردن با خرید (فروشگاه)", color = Color(0xFF1F1400)) }
                }
            }
        }
    }
}

private fun loadAndPlay(
    g: Game, context: android.content.Context,
    onPlay: (String, ByteArray, PlayerMode, NetSync?) -> Unit,
    statusUpdater: (String) -> Unit, mode: PlayerMode,
) {
    Thread {
        try {
            val rom: ByteArray? = when {
                g.assetPath != null -> context.assets.open(g.assetPath).use { it.readBytes() }
                g.remoteUrl != null -> download(g.remoteUrl)
                else -> null
            }
            if (rom != null) onPlay(g.title, rom, mode, null)
            else statusUpdater("ROM در دسترس نیست")
        } catch (e: Exception) {
            statusUpdater("خطا در بارگذاری: ${e.message}")
        }
    }.start()
}

private fun download(url: String): ByteArray? = try {
    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = 15000; conn.readTimeout = 60000
    conn.instanceFollowRedirects = true
    conn.inputStream.use { ins ->
        val out = ByteArrayOutputStream(); val buf = ByteArray(64 * 1024)
        while (true) { val r = ins.read(buf); if (r < 0) break; out.write(buf, 0, r) }
        out.toByteArray()
    }
} catch (e: Exception) { null }

/* ===================== کنترل‌های لمسی ===================== */

@Composable
private fun TwoChips(
    left: Pair<String, Boolean>, right: Pair<String, Boolean>,
    onLeft: () -> Unit, onRight: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(left.first, left.second, onLeft)
        Chip(right.first, right.second, onRight)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Accent else Card1, label = "chip")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, if (selected) Color.Transparent else Sub.copy(alpha = 0.4f)),
    ) {
        Text(label, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (selected) Color.White else Sub, fontSize = 13.sp)
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Card1),
        contentPadding = PaddingValues(12.dp),
    ) { Text(text, color = Txt, fontSize = 14.sp) }
}

@Composable
fun NetDialog(onConnect: (Boolean, String) -> Unit, onDismiss: () -> Unit) {
    var ip by remember { mutableStateOf("192.168.1.100") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Card1,
        title = { Text("بازی آنلاین (LAN)", color = Txt) },
        text = {
            Column {
                Text("هر دو گوشی باید یک Wi-Fi مشترک داشته باشند.",
                    color = Sub, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("IP میزبان") })
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onConnect(true, "") }) { Text("میزبان", color = Good) }
                TextButton(onClick = { onConnect(false, ip) }) { Text("مهمان", color = Accent2) }
                TextButton(onClick = onDismiss) { Text("بستن", color = Sub) }
            }
        },
    )
}

/* ===================== صفحه بازی ===================== */

@Composable
fun GameScreen(
    title: String, rom: ByteArray, mode: PlayerMode, net: NetSync?, onExit: () -> Unit,
) {
    val context = LocalContext.current
    val frameTick = remember { mutableIntStateOf(0) }
    val desync = remember { mutableStateOf(false) }

    val engine = remember {
        EmulatorEngine(net = net, mode = mode,
            listener = object : EmulatorEngine.Listener {
                override fun onFrameReady(width: Int, height: Int) { frameTick.intValue++ }
                override fun onDesync(frame: Long) { desync.value = true }
            })
    }

    var videoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var bmpW by remember { mutableIntStateOf(0) }
    var bmpH by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loaded = withContext(Dispatchers.IO) {
            engine.loadRom(rom, context.filesDir.absolutePath)
        }
        if (loaded) {
            engine.start()
            withContext(Dispatchers.IO) {
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

    DisposableEffect(Unit) { onDispose { engine.stop(); net?.disconnect() } }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxWidth().weight(0.58f), contentAlignment = Alignment.Center) {
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
                        contentDescription = "video",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else Text("در حال بارگذاری…", color = Color.White)

            if (desync.value) Text("دیسنک! اتصال را دوباره برقرار کنید", color = Color.Red)
            TextButton(onClick = onExit, modifier = Modifier.align(Alignment.TopStart)) {
                Text("✕ خروج", color = Color.White)
            }
            Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        }

        when (mode) {
            PlayerMode.Single -> TouchControls(
                label = null,
                onPad = { bits -> engine.padBits[0] = bits },
                modifier = Modifier.weight(0.42f).fillMaxWidth(),
            )
            PlayerMode.HotSeat -> Column(Modifier.weight(0.42f).fillMaxWidth()) {
                TouchControls(label = "بازیکن ۱", compact = true,
                    onPad = { bits -> engine.padBits[0] = bits },
                    modifier = Modifier.weight(1f).fillMaxWidth())
                HorizontalDivider(color = Color(0xFF22272F))
                TouchControls(label = "بازیکن ۲", compact = true,
                    onPad = { bits -> engine.padBits[1] = bits },
                    modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
fun TouchControls(
    label: String?, onPad: (Int) -> Unit,
    modifier: Modifier = Modifier, compact: Boolean = false,
) {
    val held = remember { mutableStateMapOf<String, Boolean>() }
    fun recompute() {
        fun b(k: String, id: Int) = if (held[k] == true) (1 shl id) else 0
        onPad(
            b("UP", 4) or b("DOWN", 5) or b("LEFT", 6) or b("RIGHT", 7) or
                b("A", 8) or b("B", 0) or b("C", 1) or b("START", 3)
        )
    }

    Column(
        modifier = modifier.background(Color(0xFF0A0D12)).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (label != null) Text(label, color = Sub, fontSize = 11.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PadButton("◀", held, "LEFT", ::recompute, compact)
            PadButton("▶", held, "RIGHT", ::recompute, compact)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PadButton("▲", held, "UP", ::recompute, compact)
            PadButton("▼", held, "DOWN", ::recompute, compact)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PadButton("START", held, "START", ::recompute, compact)
            PadButton("A", held, "A", ::recompute, compact)
            PadButton("B", held, "B", ::recompute, compact)
            PadButton("C", held, "C", ::recompute, compact)
        }
    }
}

@Composable
fun PadButton(
    label: String,
    held: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Boolean>,
    key: String, recompute: () -> Unit, compact: Boolean,
) {
    val pressed = held[key] == true
    val bg by animateColorAsState(if (pressed) Accent else Color(0xFF1C222C), label = "pad")
    Surface(
        shape = if (key.length > 2) RoundedCornerShape(10.dp) else RoundedCornerShape(999.dp),
        color = bg,
        modifier = Modifier
            .size(
                width = if (key == "START") (if (compact) 68.dp else 92.dp)
                else (if (compact) 52.dp else 68.dp),
                height = if (compact) 44.dp else 52.dp,
            )
            .pointerInput(key) {
                detectTapGestures(
                    onPress = {
                        held[key] = true; recompute()
                        try { awaitRelease() } finally { held[key] = false; recompute() }
                    },
                )
            },
        border = BorderStroke(1.dp, Color(0xFF2A3140)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (pressed) Color.White else Sub,
                fontSize = if (compact) 12.sp else 14.sp)
        }
    }
}

/* ===================== helpers ===================== */

private fun readRom(context: android.content.Context, uri: Uri): Pair<ByteArray?, String> {
    return try {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else "game"
        } ?: "game"
        val bytes = context.contentResolver.openInputStream(uri)?.use { ins ->
            val out = ByteArrayOutputStream(); val buf = ByteArray(64 * 1024)
            while (true) { val r = ins.read(buf); if (r < 0) break; out.write(buf, 0, r) }
            out.toByteArray()
        }
        Pair(bytes, name)
    } catch (e: Exception) { Pair(null, "game") }
}

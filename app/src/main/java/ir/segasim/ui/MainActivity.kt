package ir.segasim.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.segasim.billing.EntitlementRepository
import ir.segasim.catalog.Game
import ir.segasim.catalog.GameRegistry
import ir.segasim.catalog.PlayerMode
import ir.segasim.data.MyGamesStore
import ir.segasim.data.SettingsStore
import ir.segasim.net.NetSync
import ir.segasim.ui.theme.AppTheme
import ir.segasim.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** یک نشست بازی (ROM بارگذاری‌شده + حالت). */
data class PlaySession(
    val title: String,
    val rom: ByteArray,
    val mode: PlayerMode,
    val net: NetSync?,
)

class MainActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var dark by remember { mutableStateOf(SettingsStore(context).isDark()) }
    var tab by remember { mutableStateOf(Tab.Home) }
    var playing by remember { mutableStateOf<PlaySession?>(null) }

    var unlocked by remember { mutableStateOf(false) }
    val billing = remember { EntitlementRepository(context) { v -> unlocked = v } }
    LaunchedEffect(Unit) {
        billing.selectProvider()
        withContext(Dispatchers.IO) { billing.refreshFromStore() }
    }

    AppTheme(dark = dark) {
        // کل برنامه راست‌به‌رچپ — «خانه»ی اول در سمت راست نوار ناوبری
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val c = LocalAppColors.current
            val session = playing
            if (session != null) {
                EmulatorScreen(
                    session = session,
                    onExit = {
                        playing = null
                        billing_refresh(billing)
                    },
                )
            } else {
                Box(Modifier.fillMaxSize().background(c.bg)) {
                    Column(
                        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                Tab.Home -> HomeTab(
                                    unlocked = unlocked,
                                    storeName = billing.providerDisplayName(),
                                    onGoTab = { tab = it },
                                )
                                Tab.Mine -> MyGamesTab(
                                    unlocked = unlocked,
                                    onPlay = { playing = it },
                                    onUnlock = {
                                        (context as? Activity)?.let { billing.launchPurchase(it) }
                                    },
                                )
                                Tab.Premium -> PremiumTab(
                                    unlocked = unlocked,
                                    storeName = billing.providerDisplayName(),
                                    onPlay = { playing = it },
                                    onUnlock = {
                                        (context as? Activity)?.let { billing.launchPurchase(it) }
                                    },
                                )
                                Tab.Account -> AccountTab(
                                    unlocked = unlocked,
                                    storeName = billing.providerDisplayName(),
                                    dark = dark,
                                    onToggleDark = {
                                        dark = it
                                        SettingsStore(context).setDark(it)
                                    },
                                    onUnlockClick = {
                                        (context as? Activity)?.let { billing.launchPurchase(it) }
                                    },
                                )
                            }
                        }
                        GlassNavBar(selected = tab, onSelect = { tab = it })
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { billing.disconnect() }
    }
}

private fun billing_refresh(repo: EntitlementRepository) {
    // پس از بازگشت از بازی، وضعیت خرید را تازه می‌کنیم
    GlobalScope.launch(Dispatchers.IO) { repo.refreshFromStore() }
}

/* ===================== تب خانه ===================== */

@Composable
private fun HomeTab(
    unlocked: Boolean,
    storeName: String,
    onGoTab: (Tab) -> Unit,
) {
    val c = LocalAppColors.current

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            Column {
                Text("سیمولاتور سگا", color = c.txt, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    "مگا درایو · مستر سیستم · گیم گیر · سگا سی‌دی",
                    color = c.sub, fontSize = 12.sp,
                )
            }
        }

        item {
            StatusRow(
                label = if (unlocked) "بسته‌ی پریمیوم فعال است" else "بسته‌ی پریمیوم قفل است",
                ok = unlocked,
                storeName = storeName,
            )
        }

        item {
            SectionTitle("شروع سریع")
            Spacer(Modifier.height(9.dp))
            AppCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    PrimaryButton("★  بازی‌های پریمیوم", filled = true) { onGoTab(Tab.Premium) }
                    PrimaryButton("＋  افزودن ROM از دستگاه", filled = false) { onGoTab(Tab.Mine) }
                    PrimaryButton("👤  حساب و تنظیمات", filled = false) { onGoTab(Tab.Account) }
                }
            }
        }

        item {
            SectionTitle("راهنمای سریع", "چند نکته")
            Spacer(Modifier.height(9.dp))
            AppCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Tip("۱", "بازی‌های پریمیوم از پیش آماده‌اند؛ کافی است بسته را یک‌بار بخری — نیازی به افزودن ROM نیست.")
                    Tip("۲", "پس از خرید، بازی‌ها در تب «بازی‌های من» زیر بخش پریمیوم باز می‌شوند.")
                    Tip("۳", "ROMهای خودت را از «بازی‌های من» اضافه کن؛ دسته‌ی لمسی سگا روی همه‌ی بازی‌ها کار می‌کند.")
                    Tip("۴", "خرید از بازار یا مایکت انجام می‌شود؛ پس از حذف و نصب مجدد، خرید خودکار برمی‌گردد.")
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text(
                "هسته: Genesis Plus GX (مجوز غیرتجاری) · ROM تجاری همراه اپ توزیع نمی‌شود",
                color = c.sub, fontSize = 10.5.sp,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun Tip(n: String, text: String) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(20.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Text(n, color = c.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(9.dp))
        Text(text, color = c.sub, fontSize = 12.sp)
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, storeName: String) {
    val c = LocalAppColors.current
    AppCard {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (ok) c.good else c.warn)
            )
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = c.txt, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(storeName, color = c.sub, fontSize = 11.sp)
            }
        }
    }
}

/* ===================== تب پریمیوم ===================== */

@Composable
private fun PremiumTab(
    unlocked: Boolean,
    storeName: String,
    onPlay: (PlaySession) -> Unit,
    onUnlock: () -> Unit,
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            Text("بازی‌های پریمیوم", color = c.txt, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(
                "نوستالژیک و دونفره — با یک خرید، خودکار وارد برنامه می‌شوند",
                color = c.sub, fontSize = 12.sp,
            )
        }

        item {
            AppCard {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (unlocked) "بسته فعال است — همه‌ی بازی‌ها باز است"
                        else "برای باز شدن بازی‌ها، بسته‌ی پریمیوم را خریداری کن",
                        color = if (unlocked) c.good else c.txt,
                        fontSize = 13.5.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "زحمت افزودن ROM را از دوشت برمی‌داریم: بازی‌های پرطرفدار " +
                            "دونفره (و تک‌نفره) از پیش در برنامه باندل شده‌اند و بعد از پرداخت " +
                            "از طریق «$storeName» بلافاصله باز و قابل‌اجرا می‌شوند.",
                        color = c.sub, fontSize = 12.sp,
                    )
                    if (!unlocked) {
                        Spacer(Modifier.height(11.dp))
                        PrimaryButton("خرید بسته‌ی پریمیوم", filled = true) { onUnlock() }
                    }
                }
            }
        }

        item { InlineStatus(status) }

        items(GameRegistry.premiumGames, key = { it.id }) { g ->
            GameRow(
                game = g,
                unlocked = unlocked,
                onPlay = { mode -> loadAndPlay(g, context, onPlay, { status = it }, mode) },
                onUnlock = onUnlock,
            )
        }

        if (unlocked) {
            item {
                AppCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("بازی‌ها کجاست؟", color = c.txt, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "همین بازی‌ها در تب «بازی‌های من» زیر بخش «پریمیوم شما» هم " +
                                "نمایش داده می‌شوند تا همه‌ی بازی‌هایت یک‌جا باشند.",
                            color = c.sub, fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

/* ===================== تب بازی‌های من ===================== */

@Composable
private fun MyGamesTab(
    unlocked: Boolean,
    onPlay: (PlaySession) -> Unit,
    onUnlock: () -> Unit,
) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val store = remember { MyGamesStore(context) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    val games = remember(refresh) { store.list() }
    var showNet by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            status = "در حال افزودن…"
            val name = queryName(context, uri)
            val entry = withContext(Dispatchers.IO) { store.import(uri, name) }
            status = if (entry != null) "افزوده شد: ${entry.name}" else "افزودن ناموفق بود"
            refresh++
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            Text("بازی‌های من", color = c.txt, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text("ROMهای خودت + بازی‌های پریمیومی که خریدی", color = c.sub, fontSize = 12.sp)
        }
        item { InlineStatus(status) }

        item {
            PrimaryButton("＋  افزودن ROM از دستگاه", filled = true) {
                picker.launch(arrayOf("*/*"))
            }
        }

        // ── بخش پریمیوم: پس از خرید خودکار این‌جا باز می‌شود ─────────────
        item { SectionTitle("پریمیوم شما", GameRegistry.premiumGames.size.toString()) }
        items(GameRegistry.premiumGames, key = { "prem_${it.id}" }) { g ->
            GameRow(
                game = g,
                unlocked = unlocked,
                onPlay = { mode -> loadAndPlay(g, context, onPlay, { status = it }, mode) },
                onUnlock = onUnlock,
            )
        }
        if (!unlocked) {
            item {
                Text(
                    "این بازی‌ها با خرید بسته‌ی پریمیوم باز می‌شوند.",
                    color = c.sub, fontSize = 11.5.sp,
                )
            }
        }

        // ── ROMهای خودم ────────────────────────────────────────────────
        item { SectionTitle("ROMهای خودم", games.size.toString()) }
        if (games.isEmpty()) {
            item {
                AppCard {
                    EmptyHint(
                        "هنوز بازی‌ای اضافه نکرده‌ای",
                        "فایل ROM (پسوند .bin، .gen، .md، .smd، .iso) را از حافظه انتخاب کن",
                    )
                }
            }
        } else {
            items(games, key = { it.id }) { e ->
                AppCard {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name, color = c.txt, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                                Text(readableSize(e.sizeBytes), color = c.sub, fontSize = 11.5.sp)
                            }
                            Box(
                                Modifier.size(30.dp).clickable {
                                    store.delete(e); status = "حذف شد"; refresh++
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Delete, contentDescription = "حذف",
                                    tint = c.sub, modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        SegChips("۱ نفر", "دونفره", e.twoPlayer, { v ->
                            store.setTwoPlayer(e, v); refresh++
                        })
                        Spacer(Modifier.height(11.dp))
                        Row {
                            Spacer(Modifier.weight(1f))
                            SmallAction("شروع", c.accent, c.accentOn) {
                                val bytes = store.readBytes(e)
                                if (bytes != null) {
                                    onPlay(
                                        PlaySession(
                                            e.name, bytes,
                                            if (e.twoPlayer) PlayerMode.HotSeat else PlayerMode.Single,
                                            null,
                                        )
                                    )
                                } else status = "خواندن فایل ناموفق بود"
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("بازی شبکه‌ای", "دو گوشی")
            Spacer(Modifier.height(9.dp))
            AppCard {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "هر دو گوشی روی یک وای‌فای باشند و ROM یکسان داشته باشند.",
                        color = c.sub, fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(11.dp))
                    PrimaryButton("اتصال به بازی شبکه‌ای", filled = false) {
                        if (games.isEmpty()) status = "اول یک ROM اضافه کن"
                        else showNet = true
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    if (showNet) {
        NetDialog(
            onConnect = { isHost, ip ->
                showNet = false
                scope.launch {
                    status = "در حال اتصال…"
                    val e = games.firstOrNull()
                    if (e == null) { status = "اول یک ROM اضافه کن"; return@launch }
                    val bytes = store.readBytes(e)
                    if (bytes == null) { status = "خواندن فایل ناموفق بود"; return@launch }
                    val sync = NetSync(isHost = isHost, host = if (isHost) null else ip)
                    val ok = withContext(Dispatchers.IO) { sync.connect() }
                    if (ok) {
                        withContext(Dispatchers.IO) { sync.handshake() }
                        onPlay(PlaySession(e.name, bytes, PlayerMode.Single, sync))
                    } else status = "اتصال ناموفق — IP/پورت را بررسی کن"
                }
            },
            onDismiss = { showNet = false },
        )
    }
}

/* ===================== تب حساب کاربری ===================== */

@Composable
private fun AccountTab(
    unlocked: Boolean,
    storeName: String,
    dark: Boolean,
    onToggleDark: (Boolean) -> Unit,
    onUnlockClick: () -> Unit,
) {
    val c = LocalAppColors.current
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            Text("حساب کاربری", color = c.txt, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text("وضعیت خرید و تنظیمات برنامه", color = c.sub, fontSize = 12.sp)
        }

        item {
            SectionTitle("وضعیت خرید")
            Spacer(Modifier.height(9.dp))
            AppCard {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (unlocked) c.good.copy(alpha = 0.16f) else c.warn.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.ShoppingCart,
                                contentDescription = null,
                                tint = if (unlocked) c.good else c.warn,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (unlocked) "بسته‌ی پریمیوم فعال است" else "بسته‌ی پریمیوم خریداری نشده",
                                color = c.txt, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            )
                            Text(storeName, color = c.sub, fontSize = 11.5.sp)
                        }
                    }
                    Spacer(Modifier.height(11.dp))
                    if (!unlocked) {
                        PrimaryButton("خرید بسته‌ی پریمیوم", filled = true) { onUnlockClick() }
                    } else {
                        Text(
                            "همه‌ی بازی‌ها باز است. اگر برنامه را حذف کنی و دوباره از همین فروشگاه " +
                                "نصب کنی، خرید خودکار بازمی‌گردد.",
                            color = c.sub, fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        item {
            SectionTitle("نمایش")
            Spacer(Modifier.height(9.dp))
            AppCard {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Settings, contentDescription = null,
                        tint = c.sub, modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("تم تاریک", color = c.txt, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                        Text(
                            if (dark) "فعال" else "غیرفعال (تم روشن)",
                            color = c.sub, fontSize = 11.5.sp,
                        )
                    }
                    Switch(
                        checked = dark,
                        onCheckedChange = onToggleDark,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.accentOn,
                            checkedTrackColor = c.accent,
                        ),
                    )
                }
            }
        }

        item {
            SectionTitle("درباره")
            Spacer(Modifier.height(9.dp))
            AppCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    InfoLine("نسخه", "۰.۵.۰")
                    InfoLine("هسته", "Genesis Plus GX")
                    InfoLine("پرداخت", "بازار / مایکت")
                    InfoLine("مجوز هسته", "غیرتجاری — جزئیات در NOTICE.md")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InfoLine(k: String, v: String) {
    val c = LocalAppColors.current
    Row {
        Text(k, color = c.sub, fontSize = 12.sp, modifier = Modifier.width(74.dp))
        Text(v, color = c.txt, fontSize = 12.sp)
    }
}

/* ===================== ابزارها ===================== */

private fun readableSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format("%.1f مگابایت", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format("%.0f کیلوبایت", bytes / 1024.0)
    else -> "$bytes بایت"
}

private fun queryName(context: android.content.Context, uri: Uri): String = try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cur ->
        val idx = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cur.moveToFirst() && idx >= 0) cur.getString(idx) else "rom.bin"
    } ?: "rom.bin"
} catch (_: Exception) { "rom.bin" }

/** ROM یک بازی پریمیوم را می‌خواند (asset یا دانلود) و نشست بازی می‌سازد. */
fun loadAndPlay(
    g: Game,
    context: android.content.Context,
    onPlay: (PlaySession) -> Unit,
    statusUpdater: (String) -> Unit,
    mode: PlayerMode,
) {
    Thread {
        try {
            val rom: ByteArray? = when {
                g.assetPath != null -> context.assets.open(g.assetPath).use { it.readBytes() }
                g.remoteUrl != null -> downloadBytes(g.remoteUrl)
                else -> null
            }
            if (rom != null) onPlay(PlaySession(g.title, rom, mode, null))
            else statusUpdater("ROM در دسترس نیست")
        } catch (e: Exception) {
            statusUpdater("خطا در بارگذاری: ${e.message}")
        }
    }.start()
}

private fun downloadBytes(url: String): ByteArray? = try {
    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = 15000; conn.readTimeout = 60000
    conn.instanceFollowRedirects = true
    conn.inputStream.use { ins ->
        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(64 * 1024)
        while (true) { val r = ins.read(buf); if (r < 0) break; out.write(buf, 0, r) }
        out.toByteArray()
    }
} catch (e: Exception) { null }

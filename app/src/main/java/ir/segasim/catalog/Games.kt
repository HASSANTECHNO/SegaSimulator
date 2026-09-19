package ir.segasim.catalog

/**
 * PlayerMode: چند نفر بازی می‌کنند و چطور.
 *  - Single : یک بازیکن، یک دسته.
 *  - HotSeat: دو بازیکن روی یک گوشی — هر طرف صفحه دسته‌ی خودش را دارد.
 */
enum class PlayerMode { Single, HotSeat }

/**
 * یک بازی پریمیوم. تا وقتی کاربر بسته‌ی کامل را نخریده باشد قفل است؛
 * پس از خرید، بدون هیچ کار اضافه‌ای از سمت کاربر باز و آماده‌ی اجرا می‌شود.
 *
 *  assetPath → ROM داخل خود APK باندل شده (کاربر زحمت افزودن نمی‌کشد)
 *  remoteUrl → در صورت نبود asset، هنگام اولین اجرا دانلود می‌شود
 */
data class Game(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val players: Int = 1,
    val assetPath: String? = null,
    val remoteUrl: String? = null,
    val infoUrl: String? = null,
    val licenseNote: String = "",
) {
    val supportsTwoPlayer: Boolean get() = players >= 2
}

/**
 * تنها منبع حقیقت برای فهرست بازی‌ها. همه‌ی صفحه‌ها از این رجیستری
 * داده می‌گیرند — منطق قفل/باز هرگز داخل صفحه‌ها هاردکد نمی‌شود.
 */
object GameRegistry {

    /** محصول غیرمصرفی که کل بسته‌ی پریمیوم را باز می‌کند. */
    const val PRODUCT_UNLOCK_ALL = "unlock_all"

    /**
     * بازی‌های پریمیوم — نوستالژیک و عمدتاً دونفره. کاربر این‌ها را
     * دستی اضافه نمی‌کند؛ با خرید بسته، خودشان باز می‌شوند و در تب
     * «بازی‌های من» زیر بخش «پریمیوم» ظاهر می‌شوند.
     */
    val premiumGames: List<Game> = listOf(
        Game(
            id = "sor3",
            title = "Streets of Rage 3",
            author = "Sega — 1994",
            description = "مبارزه‌ای کلاسیک و نوستالژیک؛ تا دو بازیکن هم‌زمان روی یک گوشی.",
            players = 2,
            assetPath = "games/premium/streets_of_rage_3.md",
            infoUrl = "https://en.wikipedia.org/wiki/Streets_of_Rage_3",
            licenseNote = "بازی تجاری سگا — مسئولیت حقوق توزیع با توسعه‌دهنده است.",
        ),
        // ── جای بازی‌های بعدی بسته‌ی پریمیوم ────────────────────────────
        // هر بازی نوستالژیکِ خوبی که حق توزیعش را داری این‌جا اضافه کن:
        //
        // Game(
        //     id = "gunstar_heroes",
        //     title = "Gunstar Heroes",
        //     author = "Treasure — 1993",
        //     description = "اکشن دونفره‌ی معروف مگا درایو.",
        //     players = 2,
        //     assetPath = "games/premium/gunstar_heroes.md",
        // ),
    )

    fun twoPlayerPremium(): List<Game> = premiumGames.filter { it.supportsTwoPlayer }
}

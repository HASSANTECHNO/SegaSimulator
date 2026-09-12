package ir.segasim.catalog

/**
 * PlayerMode: how many humans play and how.
 *  - Single: one player, one pad.
 *  - HotSeat: two players sharing ONE device — a turn toggle swaps which
 *    on-screen pad feeds the core (no duplicated game code).
 */
enum class PlayerMode { Single, HotSeat }

/**
 * Single source of truth for the game list. Every screen is data-driven
 * from this registry — lock logic is NEVER hardcoded per screen.
 *
 * Bundled  = ROM ships inside the APK assets (free games).
 * Remote   = free game downloaded on demand from the author's release.
 * Locked   = unlocked via the non-consumable in-app product "unlock_all".
 *
 * Only legally redistributable games are listed:
 *  - KleleAtoms MD: MIT licensed, original assets.
 *  - Cave Story MD: port of the freeware classic; source public.
 *  - Mega Tetris:   open SGDK example (educational) — see license note.
 *  - TownQuest:     AGPL-3.0 — distributed under its license terms.
 */
data class Game(
    val id: String,
    val title: String,
    val author: String,
    val description: String,
    val isFree: Boolean,
    val supportsTwoPlayer: Boolean = false,
    val assetPath: String? = null,   // "games/xxx.bin" inside APK
    val remoteUrl: String? = null,   // direct ROM url (free downloads)
    val infoUrl: String? = null,
    val licenseNote: String = "",
)

object GameRegistry {

    /** The single non-consumable product that unlocks every locked game. */
    const val PRODUCT_UNLOCK_ALL = "unlock_all"

    val games: List<Game> = listOf(
        // ------------------- رایگان (باندل‌شده داخل APK) -------------------
        Game(
            id = "kleleatoms",
            title = "KłełeAtoms MD",
            author = "Nightwolf-47 (MIT)",
            description = "پازل اتمی کله‌ای — بازی اصلی، مجوز MIT، کاملاً رایگان.",
            isFree = true,
            supportsTwoPlayer = false,
            assetPath = "games/kleleatoms.md.bin",
            infoUrl = "https://github.com/Nightwolf-47/KleleAtoms-MD",
            licenseNote = "MIT License",
        ),
        Game(
            id = "cavestory",
            title = "Cave Story (نسخه مگا درایو)",
            author = "andwn + جامعه متن‌باز",
            description = "پورت کامل بازی فریم‌ور محبوب Cave Story برای مگا درایو.",
            isFree = true,
            supportsTwoPlayer = false,
            assetPath = "games/cavestory.gen",
            infoUrl = "https://github.com/andwn/cave-story-md",
            licenseNote = "بازی اصلی فریم‌ور (Studio Pixel) — پورت متن‌باز",
        ),
        Game(
            id = "megatetris",
            title = "Mega Tetris",
            author = "kikutano (SGDK)",
            description = "تتریس کامل برای جنسیسیس — پروژه آموزشی SGDK با سورس باز.",
            isFree = true,
            supportsTwoPlayer = false,
            assetPath = "games/megatetris.bin",
            infoUrl = "https://github.com/kikutano/Mega-Tetris-for-SEGA-Genesis",
            licenseNote = "نمونه آموزشی SGDK — گرافیک/موسیقی الهام‌گرفته از بازی‌های تجاری؛ برای انتشار تجاری جایگزین شود",
        ),

        // ------------------- قفل‌شده (با خرید باز می‌شوند) -------------------
        Game(
            id = "townquest",
            title = "TownQuest",
            author = "sixteenbits (AGPL-3.0)",
            description = "RPG شهری مگا درایو — با خرید بسته ویژه باز می‌شود.",
            isFree = false,
            supportsTwoPlayer = false,
            remoteUrl = "https://github.com/sixteenbits/TownQuest/releases/download/0.0.2finalalpha/rom.bin",
            infoUrl = "https://github.com/sixteenbits/TownQuest",
            licenseNote = "AGPL-3.0 — توزیع با رعایت شرایط مجوز",
        ),
        // جای بازی‌های بعدی: هر بازی‌ای که حقوق توزیعش را داری این‌جا
        // با isFree=false اضافه می‌شود — بقیه منطق اپ خودکار از این لیست
        // پیروی می‌کند (بدون تغییر در صفحه‌ها).
    )

    fun freeGames(): List<Game> = games.filter { it.isFree }
    fun lockedGames(): List<Game> = games.filter { !it.isFree }
}

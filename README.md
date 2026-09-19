# سیمولاتور سگا (Sega Simulator) — Android / Kotlin

اپ اندروید شبیه‌ساز کنسول‌های سگا (Mega Drive / Genesis، Master System،
Game Gear، Sega CD، SG-1000) با بازی آفلاین و آنلاین (LAN).

**نسخه فعلی: ۰.۵.۰** — چهار تب: **خانه · بازی‌های من · پریمیوم · حساب**

## معماری

```
┌────────────────────────────────────────────────────────────┐
│  UI (Jetpack Compose)                                      │
│  تب‌ها: HomeTab · MyGamesTab · PremiumTab · AccountTab      │
│  اجزا: GlassNavBar · SegaPad (دسته‌ی لمسی) · Common         │
├────────────────────────────────────────────────────────────┤
│  EmulatorEngine (Kotlin)                                   │
│  حلقه فریم ~59.92fps · ادغام input محلی/دور · تشخیص desync  │
│  AudioTrack (44.1kHz stereo s16) · Bitmap RGB565           │
├───────────────────────────┬────────────────────────────────┤
│  NetSync (Kotlin, TCP)    │  GameRegistry (Kotlin)         │
│  netplay LAN lockstep     │  بسته‌ی پریمیوم + ROM کاربر     │
│  تبادل ۱۶بیت input/فریم   │                                │
├───────────────────────────┴────────────────────────────────┤
│  JNI: native-lib.c  (app/src/main/cpp)                     │
│  callbacks: environment / video / audio / input            │
├────────────────────────────────────────────────────────────┤
│  هسته: Genesis Plus GX (libretro) — vendored در core-src/  │
│  libsegacore.so — لینک استاتیک، بدون dlopen/RetroArch      │
└────────────────────────────────────────────────────────────┘
```

- **هسته:** Genesis Plus GX نسخه libretro (کامیت `c2838c7`) به‌صورت
  vendored در `app/src/main/cpp/core-src/`.
- **آنلاین (netplay):** مدل lockstep با frame-delay مثل RetroArch —
  هر طرف در هر فریم ورودی ۱۶بیتی خودش را می‌فرستد؛ تأخیر =
  `ping ÷ 16.6ms` (بین ۱ تا ۸ فریم)؛ تشخیص desync با هش save-state
  هر ۶۰۰ فریم. (Rollback کامل در نقشه راه است.)

## پیش‌نیازهای بیلد

- Android Studio (Koala یا جدیدتر) یا CLI با:
  - JDK 17
  - Android SDK: Platform 34, Build-Tools 34
  - NDK r26+ و CMake 3.22.1
- حداقل 4GB فضا برای SDK/NDK

## مراحل بیلد

```bash
git clone https://github.com/HASSANTECHNO/SegaSimulator.git
cd SegaSimulator
./gradlew assembleBazaarDebug        # APK تست
./gradlew assembleBazaarRelease assembleMyketRelease   # APK فروشگاه‌ها
```

خروجی‌ها در `app/build/outputs/apk/{bazaar,myket}/**` (+ نسخه‌ی universal).

## ساختار رابط (نسخه ۰.۵.۰)

```
ui/
├── MainActivity.kt     ← AppRoot + چهار تب (خانه/بازی‌های من/پریمیوم/حساب)
├── GlassNavBar.kt      ← نوار ناوبری شیشه‌ای + enum چهار عضوی Tab
├── EmulatorScreen.kt   ← اجرای بازی + دسته‌ی لمسی سگا + دیالوگ LAN
├── Common.kt           ← AppCard / PrimaryButton / GameRow / SectionTitle …
└── theme/AppTheme.kt   ← AppColors (روشن+تاریک) + فونت وزیرمتن
data/
├── MyGamesStore.kt     ← کپی و فهرست ROMهای کاربر
└── SettingsStore.kt    ← ماندگاری حالت تم
catalog/
└── Games.kt            ← Game + GameRegistry.premiumGames (تنها منبع حقیقت)
```

## استفاده

1. **بازی‌های پریمیوم:** از تب «پریمیوم» بسته را یک‌بار بخر؛ همه‌ی بازی‌ها
   خودکار باز می‌شوند — نیازی به افزودن ROM نداری.
2. **ROM خودت:** از تب «بازی‌های من» فایل ROM را با SAF انتخاب کن.
3. **آنلاین LAN:** هر دو نفر همان ROM را دارند؛ یکی «میزبان» و
   دیگری با IP آن «مهمان» روی پورت `24879`.

## مجوز و ملاحظات قانونی

⚠️ هسته Genesis Plus GX **غیرتجاری** است (شرط عدم فروش + انتشار
سورس کامل). این اپ باید رایگان و متن‌باز منتشر شود، مگر اینکه
مجوز کتبی از نگارنده هسته (Eke-Eke) بگیرید. جزئیات: `NOTICE.md`.

⚠️ **ROMهای تجاری پریمیوم در مخزن گیت‌هاب نیستند.** پوشه‌ی
`app/src/main/assets/games/premium/` در `.gitignore` است. برای دیدن آن
بازی‌ها در خروجی، فایل ROM را دستی در همان پوشه بگذارید.

## قرارداد فروشگاه‌های ایرانی

```
app/src/main/java/ir/segasim/billing/
├── BillingProvider.kt        ← اینترفیس مشترک (connect / queryOwnedSkus / launchPurchase / disconnect)
├── EntitlementRepository.kt  ← نقطه‌ی مرکزی «آیا کاربر خرید کرده؟» + کش محلی
└── OfflineProvider.kt        ← نصب از فروشگاه دیگر / sideload

app/src/bazaar/java/ir/segasim/billing/   ← BazaarProvider (Poolakey 2.2.0) + BillingFactory
app/src/myket/java/ir/segasim/billing/    ← MyketProvider (myket-billing-client 1.19) + BillingFactory
```

### چرا دو طعم (Flavor)؟
هر دو SDK کلاس AIDL `com.android.vending.billing.IInAppBillingService` را داخل
خودشان باندل می‌کنند و کنار هم `Duplicate class` می‌دهند:

- `assembleBazaarRelease` → APK نسخه‌ی بازار (فقط Poolakey)
- `assembleMyketRelease` → APK نسخه‌ی مایکت (فقط myket-billing-client)

انتخاب ارائه‌دهنده در زمان اجرا بر اساس `getInstallerPackageName` است؛ اگر اپ
از فروشگاه دیگری نصب شده باشد، بدون کرش به حالت آفلاین می‌رود.

### ریستور خرید پس از حذف و نصب مجدد
محصول `unlock_all` غیرمصرفی (Non-Consumable) است و هرگز consume نمی‌شود. در هر
راه‌اندازی، `EntitlementRepository.refreshFromStore()` موجودی واقعی را از سرور
فروشگاه می‌پرسد، کش محلی (`SharedPreferences`) را به‌روز می‌کند و خرید قبلی
به‌طور خودکار برمی‌گردد.

### قبل از انتشار عمومی
1. در `BazaarProvider.kt` مقدار `BAZAAR_RSA_KEY` و در `MyketProvider.kt` مقدار
   `MYKET_RSA_KEY` را با کلیدهای عمومی پنل‌ها جایگزین کنید.
2. در پنل هر فروشگاه محصول `unlock_all` را از نوع غیرمصرفی بسازید.
3. SHA-256 کلید امضا (`app/segasim-release.jks`) را در هر دو پنل ثبت کنید.

> ⚠️ کلید امضا برای راحتی بیلد داخل ریپو است؛ پیش از انتشار تجاری آن را
> بچرخانید و مسیرش را از `app/build.gradle.kts` به بیرون از ریپو ببرید.

---

## تاریخچه‌ی نسخه‌ها

### ۰.۵.۰ — بسته‌ی پریمیوم و دسته‌ی لمسی سگا
- **بازی‌های رایگان کلاً حذف شدند:** سه ROM رایگان باندل‌شده
  (`kleleatoms.md.bin`، `cavestory.gen`، `megatetris.bin`) از assets پاک
  شدند و تب «رایگان» به‌همراه `isFree` / `freeGames()` / `lockedGames()`
  از کد حذف شد. مدل داده اکنون تنها یک دسته دارد: بسته‌ی پریمیوم.
- **نوار ناوبری چهار تب شد:** خانه · بازی‌های من · پریمیوم · حساب
  (از راست به چپ). تب «دونفره» هم کلاً حذف شد.
- **بسته‌ی پریمیوم:** بازی‌های نوستالژیک و عمدتاً دونفره از پیش داخل APK
  باندل می‌شوند؛ کاربر هیچ ROMی اضافه نمی‌کند. با خرید محصول غیرمصرفی
  `unlock_all` همه‌ی آن‌ها خودکار باز و قابل‌اجرا می‌شوند.
- **تب «بازی‌های من» دو بخشی شد:** بخش «پریمیوم شما» (بازی‌های خریداری‌شده،
  خودکار پس از خرید این‌جا باز می‌شوند) + بخش «ROMهای خودم».
- **دسته‌ی لمسی دقیقاً شبیه دسته‌ی سگا مگا درایو:** دی‌پد چهارجهته در چپ و
  دکمه‌های **A / B / C** روی یک قوس بالارونده + دکمه‌ی **START** در راست.
  چیدمان داخل دسته با `LayoutDirection.Ltr` قفل شده تا در حالت راست‌به‌چپ
  آینه نشود. در حالت دونفره، بالای صفحه دسته‌ی بازیکن ۲ و پایین دسته‌ی
  بازیکن ۱ نمایش داده می‌شود.
- **رفع باگ کار نکردن دسته:** کد قبلی با `detectTapGestures` فقط لحظه‌ی
  رها کردن انگشت را ثبت می‌کرد و بیت ورودی بلافاصله آزاد می‌شد، پس حرکت و
  شلیک به هسته نمی‌رسید. اکنون با `awaitEachGesture` +
  `awaitFirstDown` + `waitForUpOrCancellation` تا زمانی که انگشت روی دکمه
  باشد بیت روشن می‌ماند (نگه‌داشتن درست).
- **افزودن بازی جدید به بسته:** فقط یک `Game(...)` در
  `GameRegistry.premiumGames` اضافه کنید — بقیه‌ی منطق خودکار از همان
  لیست پیروی می‌کند.

### ۰.۴.۰ — بازطراحی رابط کاربری
- نوار ناوبری شیشه‌ای به سبک تلگرام: قرص شناور، پس‌زمینه‌ی نیمه‌شفاف،
  خط مویی روشن، سایه‌ی نرم و قرص رنگی برای تب فعال.
- راست‌به‌چپ کامل (`LocalLayoutDirection = Rtl`).
- تم روشن و تاریک با دکمه‌ی تغییر در تب حساب (`SettingsStore`).
- فونت وزیرمتن (سه وزن) روی همه‌ی سبک‌های Material 3.
- پالت کهربایی/فیروزه‌ای روی خاکستری خنثی.
- «بازی‌های من» ماندگار (`MyGamesStore`) + آیکون اختصاصی برنامه.

### ۰.۳.۰ — پرداخت درون‌برنامه‌ای بازار و مایکت
- حذف پرداخت گوگل‌پلی؛ خرید فقط از بازار (Poolakey) یا مایکت.
- دو طعم فروشگاه برای رفع تداخل AIDL؛ ریستور خودکار خرید.

# سیمولاتور سگا (Sega Simulator) — Android / Kotlin

اپ اندروید شبیه‌ساز کنسول‌های سگا (Mega Drive / Genesis، Master System،
Game Gear، Sega CD، SG-1000) که **بازی آفلاین و آنلاین (LAN)** دارد و
«بازی‌های آنلاین داخلش» از طریق یک کاتالوگ بازی‌های آزاد (homebrew /
freely-licensed) + ورود فایل ROM کاربر تأمین می‌شود.

## معماری

```
┌────────────────────────────────────────────────────────────┐
│  UI (Jetpack Compose)                                      │
│  MenuScreen · CatalogScreen · GameScreen · TouchControls    │
├────────────────────────────────────────────────────────────┤
│  EmulatorEngine (Kotlin)                                   │
│  حلقه فریم ~59.92fps · ادغام input محلی/دور · تشخیص desync  │
│  AudioTrack (44.1kHz stereo s16) · Bitmap RGB565           │
├───────────────────────────┬────────────────────────────────┤
│  NetSync (Kotlin, TCP)    │  GameCatalog (Kotlin)          │
│  netplay LAN lockstep     │  ROMهای آزاد + دانلود SAF     │
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
  vendored در `app/src/main/cpp/core-src/` — همان ۱۱۷ فایل سورسی که
  با `Makefile.libretro` روی دسکتاپ کامپایل و تأیید شده است.
  `CMakeLists.txt` از فهرست واقعی آبجکت‌های همان بیلد تولید شده.
- **آنلاین (netplay):** مدل lockstep با frame-delay مثل RetroArch —
  هر طرف در هر فریم ورودی ۱۶بیتی خودش را می‌فرستد؛ تأخیر =
  `ping ÷ 16.6ms` (بین ۱ تا ۸ فریم)؛ تشخیص desync با هش save-state
  هر ۶۰۰ فریم. (Rollback کامل در نقشه راه است.)
- **کاتالوگ آنلاین:** فقط بازی‌های آزاد، مثلاً پورت سورس‌باز
  Cave Story برای مگا درایو (`github.com/andwn/cave-story-md`).

## پیش‌نیازهای بیلد

- Android Studio (Koala یا جدیدتر) یا CLI با:
  - JDK 17
  - Android SDK: Platform 34, Build-Tools 34
  - NDK r26+ و CMake 3.22.1
- حداقل 4GB فضا برای SDK/NDK

## مراحل بیلد

```bash
# 1) باز کردن پروژه در Android Studio → Build ▸ Make Project
# یا از خط فرمان:
./gradlew assembleDebug
# خروجی: app/build/outputs/apk/debug/app-debug.apk
```

اگر `app/src/main/cpp/core-src/` خالی بود (مثلاً بعد از clone بدون
submodule)، هسته را واکشی کنید:

```bash
./scripts/fetch-core.sh c2838c7
```

## استفاده

1. **آفلاین:** دکمه «انتخاب ROM از دستگاه» — فایل ROM خودتان
   (استفاده شخصی از کارتریج قانونی) با SAF انتخاب می‌شود.
2. **کاتالوگ:** بازی‌های آزاد دانلود و مستقیم اجرا می‌شوند.
3. **آنلاین LAN:** هر دو نفر همان ROM را دارند؛ یکی «میزبان» و
   دیگری با IP آن «مهمان» روی پورت `24879`.

## مجوز و ملاحظات قانونی

⚠️ هسته Genesis Plus GX **غیرتجاری** است (شرط عدم فروش + انتشار
سورس کامل). این اپ باید رایگان و متن‌باز منتشر شود، مگر اینکه
مجوز کتبی از نگارنده هسته (Eke-Eke) بگیرید. جزئیات: `NOTICE.md`.

هیچ ROM تجاری سگا همراه اپ توزیع نمی‌شود.

## نقشه راه

- [x] هسته vendored + CMake + JNI (video/audio/input/state)
- [x] UI Compose، کنترل لمسی، کاتالوگ آزاد
- [x] netplay LAN پایه (lockstep + frame delay + desync check)
- [ ] rollback netcode (حلقه save-state و شبیه‌سازی مجدد)
- [ ] signaling سرور عمومی (فعلاً LAN/Direct-IP)
- [ ] GamePad بلوتوثی، shader/فیلتر تصویر، rewind
- [ ] support پیش‌فرض پورت 2 و حالت تماشاچی

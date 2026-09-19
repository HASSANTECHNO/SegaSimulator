package ir.segasim.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.VideoView
import ir.segasim.R

/**
 * اسپلش ورودی: ویدیوی کاربر را تمام‌صفحه پخش می‌کند و پس از پایان،
 * برنامه‌ی اصلی را باز می‌کند.
 *
 * چرا اکتیویتی جدا؟ Android 12 SplashScreen API فقط می‌تواند یک آیکون
 * ثابت نشان دهد و از پخش ویدیو پشتیبانی نمی‌کند؛ پس یک اکتیویتی سبک با
 * VideoView ساخته شده که همان تجربه را می‌دهد.
 *
 * مسیرهای خروج (هر کدام اول اتفاق بیفتد):
 *  • پایان طبیعی ویدیو  → برنامه
 *  • خطای پخش/نبود فایل  → برنامه (بدون گیر کردن روی صفحه‌ی سیاه)
 *  • تور اطمینان ۳۰ ثانیه → برنامه
 */
class SplashActivity : android.app.Activity() {

    private var moved = false
    private val advance = Runnable { goToApp() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )

        val video = VideoView(this)
        video.setBackgroundColor(Color.BLACK)
        video.setOnPreparedListener { mp ->
            mp.isLooping = false
            video.start()
        }
        video.setOnCompletionListener { goToApp() }
        video.setOnErrorListener { _, _, _ ->
            goToApp()
            true
        }
        video.setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.segacomplete}"))

        setContentView(
            video,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        // تور اطمینان: اگر ویدیو به هر دلیلی تمام نشد، برنامه باز شود
        Handler(Looper.getMainLooper()).postDelayed(advance, 30_000)
    }

    private fun goToApp() {
        if (moved) return
        moved = true
        Handler(Looper.getMainLooper()).removeCallbacks(advance)
        try {
            startActivity(Intent(this, MainActivity::class.java))
        } catch (_: Exception) {
        }
        finish()
    }

    override fun onDestroy() {
        Handler(Looper.getMainLooper()).removeCallbacks(advance)
        super.onDestroy()
    }
}

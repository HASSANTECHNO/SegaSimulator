package ir.segasim.billing

import android.content.Context

/**
 * انتخاب ارائه‌دهنده‌ی پرداخت برای نسخه‌ی «بازار».
 * این فایل در سورس‌ست flavor بازار است تا Poolakey فقط در همین APK باشد.
 */
object BillingFactory {
    private const val PKG_BAZAAR = "com.farsitel.bazaar"

    fun fromInstaller(ctx: Context, sku: String): BillingProvider {
        val installer = try {
            ctx.packageManager.getInstallerPackageName(ctx.packageName)
        } catch (_: Exception) { null }
        return if (installer == PKG_BAZAAR) BazaarProvider(ctx, sku)
        else OfflineProvider(sku, installer ?: "ناشناس")
    }
}

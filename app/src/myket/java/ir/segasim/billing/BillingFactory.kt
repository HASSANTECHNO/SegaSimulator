package ir.segasim.billing

import android.content.Context

/**
 * انتخاب ارائه‌دهنده‌ی پرداخت برای نسخه‌ی «مایکت».
 * این فایل در سورس‌ست flavor مایکت است تا myket-billing-client فقط در همین APK باشد.
 */
object BillingFactory {
    private const val PKG_MYKET = "ir.mservices.market"

    fun fromInstaller(ctx: Context, sku: String): BillingProvider {
        val installer = try {
            ctx.packageManager.getInstallerPackageName(ctx.packageName)
        } catch (_: Exception) { null }
        return if (installer == PKG_MYKET) MyketProvider(ctx, sku)
        else OfflineProvider(sku, installer ?: "ناشناس")
    }
}

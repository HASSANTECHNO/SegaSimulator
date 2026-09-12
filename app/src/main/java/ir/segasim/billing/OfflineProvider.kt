package ir.segasim.billing

import android.app.Activity
import android.content.Context

/**
 * Fallback زمانی که کاربر برنامه را از جایی غیر از بازار/مایکت نصب کرده
 * (مثلاً sideload یا گوگل‌پلی). در این حالت فقط کش محلی معتبر است؛
 * برای خرید، کاربر باید اپ را از فروشگاه مناسب نصب کند و در آنجا خرید
 * کند — پس از نصب مجدد، کش محلی و ریستور سروری فعال می‌شود.
 */
class OfflineProvider(
    private val sku: String,
    private val installer: String,
) : BillingProvider {

    override val displayName: String =
        "نصب فعلی: $installer (خرید فقط از بازار/مایکت)"

    override fun isAvailable() = false
    override fun connect() = false
    override suspend fun queryOwnedSkus(): Set<String> = emptySet()
    override fun launchPurchase(activity: Activity, sku: String) = false
    override fun disconnect() = Unit
}

package ir.segasim.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import ir.segasim.catalog.GameRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * یک نقطه‌ی مرکزی برای «آیا کاربر قبلاً خرید کرده؟» که با فروشگاه
 * ایرانی که نصب شده صحبت می‌کند. مسؤولية اصلی:
 *
 *  1) در لحظه‌ی راه‌اندازی اپ، SKUهای غیرمصرفی فعلی را به‌صورت
 *     restore از سرورِ فروشگاه می‌پرسد (نه از کش). به این ترتیب اگر
 *     کاربر اپ را حذف کند و دوباره از همان فروشگاه نصب کند، خرید قبلی
 *     به‌طور خودکار بازمی‌گردد.
 *  2) کش محلی (SharedPreferences) را هم‌زمان با سرور به‌روز می‌کند؛
 *     کش فقط برای cold-start سریع است، ولی حقیقت سرور است.
 *  3) آداپتور باز/بسته رویدادهای خرید را به UI اطلاع می‌دهد تا بدون
 *     نیاز به راه‌اندازی مجدد صفحه قفل باز شود.
 */
class EntitlementRepository(
    private val context: Context,
    private val onEntitlementChanged: (Boolean) -> Unit,
) {

    companion object {
        private const val TAG = "Entitlement"
        private const val PREFS = "entitlements"
        private const val KEY_UNLOCKED = "unlock_all_owned"
        private const val KEY_INSTALLER = "last_installer"
    }

    @Volatile var unlocked: Boolean = false
        private set

    @Volatile private var currentProvider: BillingProvider = OfflineFallback

    /**
     * ارائه‌دهنده‌ی پرداخت بر اساس آن‌که اپ از کدام فروشگاه نصب شده.
     * هر بار که اپ راه‌اندازی می‌شود فراخوانی کنید.
     */
    fun selectProvider(): BillingProvider {
        val p = BillingFactory.fromInstaller(context, GameRegistry.PRODUCT_UNLOCK_ALL)
        currentProvider = p
        Log.d(TAG, "ارائه‌دهنده پرداخت: ${p.displayName}")
        // یادآور کش قبلی برای حالت‌هایی که اتصال اولیه شکست می‌خورد
        val cached = prefs().getBoolean(KEY_UNLOCKED, false)
        val persistedInstaller = prefs().getString(KEY_INSTALLER, null)
        if (cached && !unlocked) {
            unlocked = true
            onEntitlementChanged(true)
        }
        prefs().edit().putString(KEY_INSTALLER, currentProvider.javaClass.simpleName).apply()
        return p
    }

    /**
     * عملیات restore سروری. در UI به‌صورت یک «پرده‌ی صبر کن» کوتاه نشان
     * می‌دهیم تا در شبکه‌های ضعیف تجربه‌ی بد پیش نیاید.
     */
    suspend fun refreshFromStore(): Boolean = withContext(Dispatchers.IO) {
        val p = currentProvider
        if (!p.isAvailable() || !p.connect()) return@withContext false
        val owned = p.queryOwnedSkus()
        val ownsUnlock = owned.contains(GameRegistry.PRODUCT_UNLOCK_ALL)
        if (ownsUnlock != unlocked) {
            unlocked = ownsUnlock
            prefs().edit().putBoolean(KEY_UNLOCKED, ownsUnlock).apply()
            withContext(Dispatchers.Main) { onEntitlementChanged(ownsUnlock) }
            Log.d(TAG, "restore: ${if (ownsUnlock) "خرید قبلی پیدا شد" else "خریدی نیست"}")
        }
        ownsUnlock
    }

    fun launchPurchase(activity: Activity): Boolean =
        currentProvider.launchPurchase(activity, GameRegistry.PRODUCT_UNLOCK_ALL)

    fun providerDisplayName(): String = currentProvider.displayName

    fun disconnect() {
        currentProvider.disconnect()
    }

    // ------------------ helpers -------------------------------------------
    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** برای زمانی که تا هنوز انتخاب ارائه‌دهنده تکمیل نشده (مثلاً شروع خیلی زود). */
    private object OfflineFallback : BillingProvider {
        override val displayName = "در حال شناسایی فروشگاه…"
        override fun isAvailable() = false
        override fun connect() = false
        override suspend fun queryOwnedSkus(): Set<String> = emptySet()
        override fun launchPurchase(activity: Activity, sku: String) = false
        override fun disconnect() = Unit
    }
}

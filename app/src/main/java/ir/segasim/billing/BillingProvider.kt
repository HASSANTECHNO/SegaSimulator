package ir.segasim.billing

import android.app.Activity

/**
 * Abstraction over Iranian in-app billing SDKs. The concrete provider
 * (Bazaar via Poolakey / Myket via myket-billing-client) lives in the
 * matching product flavor's source set (app/src/bazaar, app/src/myket)
 * because each SDK bundles its own copy of the shared AIDL stub and the
 * two must never ship in the same APK.
 *
 * Restore semantics: the SKU is non-consumable (we never call consume),
 * so the purchase survives uninstall/reinstall. On every app launch we
 * re-query the store and refresh the local cache — after reinstalling
 * from the same store the user automatically gets everything back.
 */
interface BillingProvider {
    /** نام فارسی برای نمایش در رابط کاربری. */
    val displayName: String

    /** true اگر فروشگاه روی گوشی نصب و در دسترس باشد. */
    fun isAvailable(): Boolean

    /** راه‌اندازی اتصال؛ غیرهمگام است و بلافاصله برمی‌گردد. */
    fun connect(): Boolean

    /** SKUهای غیرمصرفی که در سرورِ فروشگاه متعلق به کاربر فعلی ثبت شده است. */
    suspend fun queryOwnedSkus(): Set<String>

    /** شیت/دیالوگ خرید فروشگاه را باز می‌کند. true اگر درخواست ارسال شد. */
    fun launchPurchase(activity: Activity, sku: String): Boolean

    fun disconnect()
}

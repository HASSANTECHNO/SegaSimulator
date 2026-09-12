package ir.segasim.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import ir.myket.billingclient.IabHelper
import ir.myket.billingclient.util.IabResult
import ir.myket.billingclient.util.Inventory
import ir.myket.billingclient.util.Purchase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * مایکت — Myket In-App Billing — با myket-billing-client 1.19
 *
 * مستند رسمی: https://myket.ir/kb/en/pages/adding-in-app-purchase-in-java-2/
 * سورس SDK:   https://github.com/myketstore/myket-billing-client
 *
 * پیش‌نیازهای پنل مایکت (یک‌بار قبل از انتشار):
 *  1. اپ را در پنل توسعه‌دهندگان مایکت ثبت کنید.
 *  2. محصول «unlock_all» را با نوع غیرمصرفی تعریف کنید.
 *  3. مقدار MYKET_RSA_KEY را با کلید عمومی RSA پنل جایگزین کنید — بدون آن
 *     صحت‌سنجی امضای رسیدها شکست می‌خورد و restore پیدا نمی‌شود.
 *
 * API واقعی SDK (تأییدشده از سورس رسمی):
 *  - IabHelper(context, base64PublicKey)           → ir.myket.billingclient
 *  - startSetup(OnIabSetupFinishedListener)
 *  - queryInventoryAsync(boolean, List<String>, QueryInventoryFinishedListener)
 *  - launchPurchaseFlow(Activity, String, OnIabPurchaseFinishedListener, String?)
 *  - Inventory.getPurchase(sku): Purchase?          |   IabResult.isSuccess
 *
 * نکته: اندروید ۱۱ به بعد برای دیدن مایکت باید پکیج آن در <queries> باشد؛
 * manifest کتابخانه با manifestPlaceholders پر می‌شود (ببینید build.gradle.kts).
 */
class MyketProvider(
    private val context: Context,
    private val sku: String,
) : BillingProvider {

    override val displayName: String = "مایکت"

    @Volatile private var helper: IabHelper? = null

    override fun isAvailable(): Boolean =
        isAppInstalled(context, MYKET_PKG)

    override fun connect(): Boolean {
        if (!isAvailable()) return false
        if (helper != null) return true
        synchronized(this) {
            if (helper != null) return true
            return try {
                val h = IabHelper(context, MYKET_RSA_KEY)
                val latch = CountDownLatch(1)
                var ok = false
                h.startSetup { result: IabResult ->
                    ok = result.isSuccess
                    if (!ok) Log.w(TAG, "مایکت: setup ناموفق — ${result.message}")
                    latch.countDown()
                }
                latch.await(15, TimeUnit.SECONDS)
                if (ok) {
                    helper = h
                    Log.d(TAG, "مایکت: اتصال موفق")
                } else {
                    h.dispose()
                }
                ok
            } catch (e: Throwable) {
                Log.w(TAG, "مایکت: خطای اتصال — ${e.javaClass.simpleName}: ${e.message}")
                false
            }
        }
    }

    override suspend fun queryOwnedSkus(): Set<String> = withContext(Dispatchers.IO) {
        val h = helper ?: return@withContext emptySet()
        val latch = CountDownLatch(1)
        var owned = false
        try {
            h.queryInventoryAsync(false, listOf(sku)) { result: IabResult, inv: Inventory? ->
                if (result.isSuccess && inv?.getPurchase(sku) != null) {
                    owned = true
                    Log.d(TAG, "مایکت: خرید قبلی یافت شد")
                } else if (!result.isSuccess) {
                    Log.w(TAG, "مایکت: query ناموفق — ${result.message}")
                }
                latch.countDown()
            }
            latch.await(10, TimeUnit.SECONDS)
        } catch (e: Throwable) {
            Log.w(TAG, "مایکت: queryOwnedSkus استثنا — ${e.message}")
        }
        if (owned) setOf(sku) else emptySet()
    }

    override fun launchPurchase(activity: Activity, sku: String): Boolean {
        val h = helper ?: return false
        return try {
            h.launchPurchaseFlow(
                activity, sku,
                object : IabHelper.OnIabPurchaseFinishedListener {
                    override fun onIabPurchaseFinished(result: IabResult, info: Purchase?) {
                        if (result.isSuccess) {
                            Log.d(TAG, "مایکت: خرید موفق — ${info?.sku}")
                        } else {
                            Log.w(TAG, "مایکت: خرید ناموفق — ${result.message}")
                        }
                    }
                },
                null,
            )
            Log.d(TAG, "مایکت: درخواست خرید $sku ارسال شد")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "مایکت: شروع خرید ناموفق — ${e.message}")
            false
        }
    }

    override fun disconnect() {
        try { helper?.dispose() } catch (_: Throwable) {}
        helper = null
    }

    companion object {
        private const val TAG = "MyketProvider"
        private const val MYKET_PKG = "ir.mservices.market"

        /** آیا فروشگاه روی گوشی نصب است؟ */
        private fun isAppInstalled(ctx: Context, pkg: String): Boolean = try {
            ctx.packageManager.getPackageInfo(pkg, 0); true
        } catch (_: Exception) { false
        }

        /**
         * کلید عمومی RSA مایکت (Base64) — از پنل توسعه‌دهندگان مایکت.
         * قبل از انتشار عمومی جایگزین شود.
         */
        private const val MYKET_RSA_KEY = "PASTE_YOUR_MYKET_RSA_PUBLIC_KEY_HERE"
    }
}

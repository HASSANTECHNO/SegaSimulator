package ir.segasim.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.entity.PurchaseInfo
import ir.cafebazaar.poolakey.request.PurchaseRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * بازار — Cafe Bazaar In-App Billing — با Poolakey 2.2.0
 *
 * مستند رسمی: https://github.com/cafebazaar/Poolakey
 * راهنمای پنل:  https://developers.cafebazaar.ir/fa/guidelines/in-app-billing
 *
 * پیش‌نیازهای پنل بازار (یک‌بار قبل از انتشار):
 *  1. اپ را در https://developers.cafebazaar.ir اضافه کنید.
 *  2. در بخش «کلید امضا»، SHA-256 کلید release (app/segasim-release.jks) را ثبت کنید.
 *  3. محصولی با شناسه‌ی «unlock_all» و نوع «غیرمصرفی» بسازید.
 *  4. مقدار BAZAAR_RSA_KEY را با کلید عمومی RSA پنل جایگزین کنید.
 *
 * API واقعی Poolakey 2.2.0 (استخراج‌شده از باینری AAR با javap):
 *  - Payment(context, PaymentConfiguration(localSecurityCheck))
 *  - connect(Function1<ConnectionCallback, Unit>): Connection
 *      ConnectionCallback DSL: connectionSucceed { } / connectionFailed { } / disconnected { }
 *  - getPurchasedProducts(Function1<PurchaseQueryCallback, Unit>)
 *      PurchaseQueryCallback DSL: querySucceed { List<PurchaseInfo> } / queryFailed { Throwable }
 *  - purchaseProduct(ActivityResultRegistry, PurchaseRequest, Function1<PurchaseCallback, Unit>)
 *      PurchaseCallback DSL: purchaseSucceed { PurchaseInfo } / purchaseCanceled { } / purchaseFailed { }
 *  - SecurityCheck.Enable(rsaPublicKey: String) / SecurityCheck.Disable
 *  - Connection.disconnect()
 */
class BazaarProvider(
    private val context: Context,
    private val sku: String,
) : BillingProvider {

    override val displayName: String = "بازار"

    private var payment: Payment? = null
    private var connection: Connection? = null
    private val connected = AtomicBoolean(false)

    override fun isAvailable(): Boolean =
        isAppInstalled(context, BAZAAR_PKG)

    override fun connect(): Boolean {
        if (!isAvailable()) return false
        if (connected.get()) return true
        return try {
            val config = PaymentConfiguration(
                // اگر کلید RSA هنوز جایگزین نشده، صحت‌سنجی محلی موقتاً خاموش
                // می‌شود تا تست خرید/restore کار کند؛ قبل از انتشار عمومی حتماً
                // کلید واقعی پنل را قرار دهید تا رسیدهای جعلی رد شوند.
                localSecurityCheck = if (BAZAAR_RSA_KEY.startsWith("PASTE_")) {
                    Log.w(TAG, "کلید RSA بازار تنظیم نشده — صحت‌سنجی محلی خاموش است")
                    SecurityCheck.Disable
                } else {
                    SecurityCheck.Enable(BAZAAR_RSA_KEY)
                }
            )
            val p = Payment(context, config).also { payment = it }
            connection = p.connect {
                connectionSucceed {
                    connected.set(true)
                    Log.d(TAG, "Poolakey: اتصال موفق")
                }
                connectionFailed { e ->
                    Log.w(TAG, "Poolakey: اتصال ناموفق — ${e.message}")
                }
                disconnected {
                    connected.set(false)
                }
            }
            true // اتصال غیرهمگام است؛ نتیجه در callback ثبت می‌شود
        } catch (e: Throwable) {
            Log.w(TAG, "Poolakey: خطای اتصال — ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    override suspend fun queryOwnedSkus(): Set<String> = withContext(Dispatchers.IO) {
        val p = payment ?: return@withContext emptySet()
        // تا ۱۵ ثانیه منتظر برقراری اتصال غیرهمگام می‌مانیم
        val ok = withTimeoutOrNull(15_000L) {
            while (!connected.get()) delay(200)
            true
        } ?: false
        if (!ok) {
            Log.w(TAG, "Poolakey: اتصال برای query آماده نشد")
            return@withContext emptySet()
        }
        try {
            suspendCancellableCoroutine<Set<String>> { cont ->
                p.getPurchasedProducts {
                    querySucceed { list: List<PurchaseInfo> ->
                        val ids = list.map { it.productId }.toSet()
                        Log.d(TAG, "Poolakey: محصولات متعلق = $ids")
                        cont.resume(ids.filter { it == sku }.toSet())
                    }
                    queryFailed { e ->
                        Log.w(TAG, "Poolakey: query ناموفق — ${e.message}")
                        cont.resume(emptySet())
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Poolakey: queryOwnedSkus استثنا — ${e.message}")
            emptySet()
        }
    }

    override fun launchPurchase(activity: Activity, sku: String): Boolean {
        val p = payment ?: return false
        // Poolakey 2.x خرید را با ActivityResult API شروع می‌کند
        val registry = (activity as? ComponentActivity)?.activityResultRegistry ?: run {
            Log.w(TAG, "Poolakey: ActivityResultRegistry در دسترس نیست")
            return false
        }
        return try {
            p.purchaseProduct(registry, PurchaseRequest(productId = sku)) {
                purchaseSucceed { info ->
                    Log.d(TAG, "Poolakey: خرید موفق — ${info.productId}")
                }
                purchaseCanceled {
                    Log.d(TAG, "Poolakey: خرید لغو شد")
                }
                purchaseFailed { e ->
                    Log.w(TAG, "Poolakey: خرید ناموفق — ${e.message}")
                }
            }
            Log.d(TAG, "Poolakey: درخواست خرید $sku ارسال شد")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Poolakey: شروع خرید ناموفق — ${e.message}")
            false
        }
    }

    override fun disconnect() {
        try { connection?.disconnect() } catch (_: Throwable) {}
        connection = null
        payment = null
        connected.set(false)
    }

    companion object {
        private const val TAG = "BazaarProvider"
        private const val BAZAAR_PKG = "com.farsitel.bazaar"

        /** آیا فروشگاه روی گوشی نصب است؟ */
        private fun isAppInstalled(ctx: Context, pkg: String): Boolean = try {
            ctx.packageManager.getPackageInfo(pkg, 0); true
        } catch (_: Exception) { false
        }

        /**
         * کلید عمومی RSA بازار (Base64) — از پنل توسعه‌دهندگان بازار.
         * قبل از انتشار عمومی جایگزین شود؛ بدون آن، صحت‌سنجی امضای
         * رسیدها شکست می‌خورد و restore کار نمی‌کند.
         */
        private const val BAZAAR_RSA_KEY = "PASTE_YOUR_BAZAAR_RSA_PUBLIC_KEY_HERE"
    }
}

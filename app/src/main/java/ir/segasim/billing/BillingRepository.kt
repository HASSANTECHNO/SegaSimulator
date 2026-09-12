package ir.segasim.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import ir.segasim.catalog.GameRegistry

/**
 * Google Play Billing v7 wrapper for the single non-consumable product
 * "unlock_all". Per Play policy this is the ONLY payment path — no custom
 * or alternative payment flows.
 *
 * Entitlement is cached locally (SharedPreferences) so locked/unlocked UI
 * is instant on cold start, then re-validated with queryPurchasesAsync.
 */
class BillingRepository(
    private val context: Context,
    private val onEntitlementChanged: (Boolean) -> Unit,
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "Billing"
        private const val PREFS = "entitlements"
        private const val KEY_UNLOCKED = "unlock_all_owned"
    }

    @Volatile var unlocked: Boolean = false
        private set

    private var productDetails: ProductDetails? = null
    private var playAvailable: Boolean = false

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    // ---------------- local cache (instant cold-start state) -------------
    private fun loadCached(): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_UNLOCKED, false)

    private fun saveCached(v: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_UNLOCKED, v).apply()
    }

    // ---------------- lifecycle ------------------------------------------
    fun start() {
        unlocked = loadCached()
        onEntitlementChanged(unlocked)

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                playAvailable = result.responseCode == BillingClient.BillingResponseCode.OK
                if (!playAvailable) {
                    Log.w(TAG, "Play Billing unavailable: ${result.debugMessage}")
                    return
                }
                refreshPurchases()
                queryProduct()
            }
            override fun onBillingServiceDisconnected() {
                playAvailable = false
            }
        })
    }

    /** Re-validate owned purchases from Play (handles reinstalls, refunds). */
    fun refreshPurchases() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any { p ->
                p.products.contains(GameRegistry.PRODUCT_UNLOCK_ALL) &&
                    p.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (owned != unlocked) {
                unlocked = owned
                saveCached(owned)
                onEntitlementChanged(owned)
            }
            if (owned) acknowledge(purchases.first {
                it.products.contains(GameRegistry.PRODUCT_UNLOCK_ALL)
            })
        }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(GameRegistry.PRODUCT_UNLOCK_ALL)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = details.firstOrNull()
            }
        }
    }

    /** Opens the Google Play purchase sheet. Call from UI (Activity context). */
    fun launchPurchase(activity: Activity): Boolean {
        val pd = productDetails
        if (!playAvailable || pd == null) return false
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .build()
                )
            )
            .build()
        client.launchBillingFlow(activity, flow)
        return true
    }

    private fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
        ) { result ->
            Log.i(TAG, "acknowledge: ${result.responseCode}")
        }
    }

    /** Callback مناندات: نتیجه شیت خرید گوگل‌پلی */
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> refreshPurchases()
            BillingClient.BillingResponseCode.USER_CANCELED ->
                Log.i(TAG, "purchase canceled by user")
            else -> Log.w(TAG, "purchase failed: ${result.debugMessage}")
        }
    }

    fun release() {
        client.endConnection()
    }
}

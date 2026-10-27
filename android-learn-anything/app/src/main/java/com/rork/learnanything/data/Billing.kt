package com.rork.learnanything.data

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.rork.learnanything.Config

/** RevenueCat wrapper. The "learn_anything_pro" entitlement unlocks Premium. */
object Billing {
    const val ENTITLEMENT = "learn_anything_pro"
    private const val TAG = "Billing"

    var configured = false
        private set

    sealed interface Result {
        data object Success : Result
        data object Cancelled : Result
        data class Failed(val message: String) : Result
    }

    fun init(context: Context) {
        val key = Config.EXPO_PUBLIC_REVENUECAT_TEST_API_KEY
        if (key.isBlank()) {
            Log.w(TAG, "RevenueCat key missing; purchases disabled")
            return
        }
        runCatching {
            Purchases.configure(PurchasesConfiguration.Builder(context.applicationContext, key).build())
            Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { apply(it) }
            configured = true
        }.onFailure { AppStore.logError("billing_init", it) }
    }

    private fun apply(info: CustomerInfo) {
        val active = info.entitlements[ENTITLEMENT]?.isActive == true
        if (AppStore.state.value.isPremium != active) AppStore.update { it.copy(isPremium = active) }
    }

    suspend fun refresh() {
        if (!configured) {
            if (AppStore.state.value.isPremium) AppStore.update { it.copy(isPremium = false) }
            return
        }
        try { apply(Purchases.sharedInstance.awaitCustomerInfo()) } catch (e: PurchasesException) { Log.w(TAG, "customerInfo: ${e.message}") }
    }

    suspend fun currentOffering(): Offering? {
        check(configured) { "Payments aren't set up on this build." }
        return Purchases.sharedInstance.awaitOfferings().current
    }

    suspend fun purchase(activity: Activity, pkg: Package): Result = try {
        val res = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
        apply(res.customerInfo)
        AppStore.track("purchase_completed", mapOf("package" to pkg.identifier))
        if (res.customerInfo.entitlements[ENTITLEMENT]?.isActive == true) Result.Success
        else Result.Failed("Purchase finished but Premium isn't active yet. Try Restore.")
    } catch (e: PurchasesTransactionException) {
        if (e.userCancelled) Result.Cancelled else Result.Failed(e.error.message)
    } catch (e: PurchasesException) {
        Result.Failed(e.error.message)
    }

    suspend fun restore(): Result = try {
        val info = Purchases.sharedInstance.awaitRestore()
        apply(info)
        if (info.entitlements[ENTITLEMENT]?.isActive == true) Result.Success
        else Result.Failed("No previous purchases found for this account.")
    } catch (e: PurchasesException) {
        Result.Failed(e.error.message)
    }
}

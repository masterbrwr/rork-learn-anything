package com.rork.learnanything.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.rork.learnanything.Config
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** PayPal subscription wrapper. PayPal secrets stay server-side in Supabase. */
object Billing {
    private const val TAG = "Billing"
    private const val CREATE = "paypal-create-subscription"
    private const val STATUS = "paypal-status"

    var configured = false
        private set

    sealed interface Result {
        data object Success : Result
        data object Started : Result
        data object Cancelled : Result
        data class Failed(val message: String) : Result
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client by lazy {
        HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
    }

    private val base get() = Config.EXPO_PUBLIC_SUPABASE_URL.trimEnd('/')

    fun init(context: Context) {
        configured = base.isNotBlank() && Config.EXPO_PUBLIC_SUPABASE_ANON_KEY.isNotBlank()
    }

    private fun token(): String? = Auth.session.value?.accessToken?.takeIf { it.isNotBlank() }

    private fun functionUrl(name: String) = "$base/functions/v1/$name"

    private fun friendly(body: String, fallback: String): String {
        return runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
    }

    suspend fun refresh() {
        if (!configured || token() == null) {
            if (AppStore.state.value.isPremium) AppStore.update { it.copy(isPremium = false) }
            return
        }
        try {
            val r = client.post(functionUrl(STATUS)) {
                header("apikey", Config.EXPO_PUBLIC_SUPABASE_ANON_KEY)
                header("Authorization", "Bearer ${token()}")
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            val body = r.bodyAsText()
            if (!r.status.isSuccess()) throw IllegalStateException(friendly(body, "Unable to check Premium status."))
            val active = json.parseToJsonElement(body).jsonObject["active"]?.jsonPrimitive?.contentOrNull == "true"
            if (AppStore.state.value.isPremium != active) AppStore.update { it.copy(isPremium = active) }
        } catch (e: Exception) {
            Log.w(TAG, "status check failed: ${e.message}")
        }
    }

    suspend fun startSubscription(activity: Activity): Result {
        if (!configured) return Result.Failed("Payments aren't configured on this build yet.")
        val access = token() ?: return Result.Failed("Sign in before starting Premium.")
        return try {
            val r = client.post(functionUrl(CREATE)) {
                header("apikey", Config.EXPO_PUBLIC_SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $access")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("source", "android") }.toString())
            }
            val body = r.bodyAsText()
            if (!r.status.isSuccess()) return Result.Failed(friendly(body, "Unable to start PayPal checkout."))
            val approval = json.parseToJsonElement(body).jsonObject["approve_url"]?.jsonPrimitive?.contentOrNull
                ?: return Result.Failed("PayPal did not return a checkout link.")
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(approval)))
            AppStore.track("subscription_started", mapOf("provider" to "paypal"))
            Result.Started
        } catch (e: Exception) {
            Log.w(TAG, "subscription start failed", e)
            Result.Failed(e.message ?: "Unable to start PayPal checkout.")
        }
    }

    suspend fun restore(): Result {
        refresh()
        return if (AppStore.state.value.isPremium) Result.Success
        else Result.Failed("No active Premium subscription was found for this account.")
    }
}

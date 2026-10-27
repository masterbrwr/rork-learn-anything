package com.rork.learnanything.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.rork.learnanything.Config
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest
import java.security.SecureRandom

@Serializable
data class AuthUser(val id: String, val email: String? = null, val phone: String? = null, val name: String? = null, val avatarUrl: String? = null, val provider: String = "email")

@Serializable
data class AuthSession(val accessToken: String, val refreshToken: String, val expiresAt: Long, val user: AuthUser)

class AuthException(message: String) : Exception(message)

/**
 * Accounts via Supabase Auth (email + password, phone code, Google).
 * Talks to the REST API directly and keeps learning progress backed up to the user's profile row.
 */
@OptIn(FlowPreview::class)
object Auth {
    const val REDIRECT = "learnanything://auth/callback"
    private const val TAG = "Auth"
    private const val PREFS = "la_auth"

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http by lazy { HttpClient(Android) { install(HttpTimeout) { requestTimeoutMillis = 20_000 } } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()
    private var appContext: Context? = null
    private var syncJob: Job? = null

    private val _session = MutableStateFlow<AuthSession?>(null)
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    /** "idle" | "syncing" | "synced" | "error" */
    val syncStatus = MutableStateFlow("idle")
    val lastSyncedAt = MutableStateFlow(0L)

    /** Result of a Google round-trip that finished while the app was in the background. */
    val oauthError = MutableStateFlow<String?>(null)

    private val base get() = Config.EXPO_PUBLIC_SUPABASE_URL.trimEnd('/')
    private val anon get() = Config.EXPO_PUBLIC_SUPABASE_ANON_KEY
    val available get() = base.isNotBlank() && anon.isNotBlank()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString("session", null)?.let { raw ->
            runCatching { json.decodeFromString(AuthSession.serializer(), raw) }.getOrNull()?.let { _session.value = it }
        }
        if (_session.value != null) {
            startAutoSync()
            scope.launch { runCatching { validToken() } }
        }
    }

    private fun save(s: AuthSession?) {
        _session.value = s
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.apply {
            if (s == null) remove("session") else putString("session", json.encodeToString(AuthSession.serializer(), s))
            apply()
        }
    }

    // ---------- HTTP helpers ----------
    private fun HttpRequestBuilder.common(token: String? = null) {
        header("apikey", anon)
        header("Authorization", "Bearer ${token ?: anon}")
        contentType(ContentType.Application.Json)
    }

    private suspend fun readError(r: HttpResponse): String {
        val body = runCatching { r.bodyAsText() }.getOrDefault("")
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val raw = listOf("error_description", "msg", "message", "error").firstNotNullOfOrNull { obj?.get(it)?.jsonPrimitive?.contentOrNull }
        return friendly(raw ?: "Something went wrong (${r.status.value}).")
    }

    private fun friendly(raw: String): String = when {
        raw.contains("Invalid login credentials", true) -> "That email and password don't match."
        raw.contains("already registered", true) || raw.contains("already exists", true) -> "An account with this email already exists. Try signing in."
        raw.contains("Password should be", true) -> "Use a password with at least 8 characters."
        raw.contains("Token has expired", true) || raw.contains("invalid", true) && raw.contains("otp", true) -> "That code is wrong or expired. Request a new one."
        raw.contains("provider is not enabled", true) -> "This sign-in method isn't switched on yet."
        raw.contains("sms", true) && raw.contains("provider", true) -> "Text messages can't be sent yet. Try email or Google."
        raw.contains("rate limit", true) -> "Too many attempts. Wait a minute and try again."
        else -> raw
    }

    private fun parseSession(body: String, provider: String): AuthSession {
        val o = json.parseToJsonElement(body).jsonObject
        val access = o["access_token"]?.jsonPrimitive?.contentOrNull ?: throw AuthException("No session returned.")
        val refresh = o["refresh_token"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val expiresIn = o["expires_in"]?.jsonPrimitive?.intOrNull ?: 3600
        val expiresAt = o["expires_at"]?.jsonPrimitive?.longOrNull?.times(1000) ?: (System.currentTimeMillis() + expiresIn * 1000L)
        val u = o["user"]?.jsonObject ?: throw AuthException("No user returned.")
        return AuthSession(access, refresh, expiresAt, userFrom(u, provider))
    }

    private fun userFrom(u: JsonObject, fallbackProvider: String): AuthUser {
        val meta = (u["user_metadata"] as? JsonObject)
        val appMeta = (u["app_metadata"] as? JsonObject)
        fun JsonObject?.s(k: String) = (this?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        return AuthUser(
            id = u.s("id") ?: throw AuthException("No user id."),
            email = u.s("email"),
            phone = u.s("phone"),
            name = meta.s("full_name") ?: meta.s("name"),
            avatarUrl = meta.s("avatar_url") ?: meta.s("picture"),
            provider = appMeta.s("provider") ?: fallbackProvider,
        )
    }

    private suspend fun completeSignIn(s: AuthSession) {
        save(s)
        AppStore.track("sign_in", mapOf("provider" to s.user.provider))
        // Adopt the account name on this device if the learner hasn't set one.
        s.user.name?.let { n -> if (AppStore.state.value.profile.name.isBlank()) AppStore.update { it.copy(profile = it.profile.copy(name = n)) } }
        runCatching { pullOrPush() }.onFailure { Log.w(TAG, "initial sync", it) }
        startAutoSync()
    }

    private fun ensureAvailable() { if (!available) throw AuthException("Accounts aren't set up on this build.") }

    /** Which sign-in methods the server has switched on (google, phone, email). */
    suspend fun enabledProviders(): Set<String> = withContext(Dispatchers.IO) {
        if (!available) return@withContext emptySet()
        runCatching {
            val r = http.get("$base/auth/v1/settings") { common() }
            val ext = json.parseToJsonElement(r.bodyAsText()).jsonObject["external"]?.jsonObject ?: return@runCatching emptySet<String>()
            ext.filter { (_, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull == "true" }.keys
        }.getOrDefault(setOf("email", "phone"))
    }

    // ---------- Email ----------
    suspend fun signUpEmail(name: String, email: String, password: String) = withContext(Dispatchers.IO) {
        ensureAvailable()
        val r = http.post("$base/auth/v1/signup") {
            common()
            setBody(buildJsonObject {
                put("email", email.trim()); put("password", password)
                putJsonObject("data") { put("full_name", name.trim()) }
            }.toString())
        }
        if (!r.status.isSuccess()) throw AuthException(readError(r))
        val body = r.bodyAsText()
        if (!body.contains("access_token")) throw AuthException("Check your inbox to confirm your email, then sign in.")
        completeSignIn(parseSession(body, "email"))
    }

    suspend fun signInEmail(email: String, password: String) = withContext(Dispatchers.IO) {
        ensureAvailable()
        val r = http.post("$base/auth/v1/token?grant_type=password") {
            common(); setBody(buildJsonObject { put("email", email.trim()); put("password", password) }.toString())
        }
        if (!r.status.isSuccess()) throw AuthException(readError(r))
        completeSignIn(parseSession(r.bodyAsText(), "email"))
    }

    // ---------- Phone ----------
    suspend fun sendPhoneCode(phone: String) = withContext(Dispatchers.IO) {
        ensureAvailable()
        val r = http.post("$base/auth/v1/otp") { common(); setBody(buildJsonObject { put("phone", phone) }.toString()) }
        if (!r.status.isSuccess()) throw AuthException(readError(r))
    }

    suspend fun verifyPhoneCode(phone: String, code: String) = withContext(Dispatchers.IO) {
        ensureAvailable()
        val r = http.post("$base/auth/v1/verify") {
            common(); setBody(buildJsonObject { put("phone", phone); put("token", code.trim()); put("type", "sms") }.toString())
        }
        if (!r.status.isSuccess()) throw AuthException(readError(r))
        completeSignIn(parseSession(r.bodyAsText(), "phone"))
    }

    // ---------- Google (PKCE through the browser) ----------
    private fun verifierStore() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun startOAuth(context: Context, provider: String = "google") {
        ensureAvailable()
        val bytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
        val verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        verifierStore()?.edit()?.putString("pkce", verifier)?.putString("pkce_provider", provider)?.apply()
        val url = Uri.parse("$base/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider", provider)
            .appendQueryParameter("redirect_to", REDIRECT)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "s256")
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Called from MainActivity when the browser redirects back. Returns true if handled. */
    fun handleRedirect(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "learnanything" || uri.host != "auth") return false
        val err = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
        if (err != null) { oauthError.value = friendly(err); return true }
        val code = uri.getQueryParameter("code") ?: return true
        val verifier = verifierStore()?.getString("pkce", null) ?: return true
        val provider = verifierStore()?.getString("pkce_provider", null) ?: "google"
        scope.launch {
            runCatching {
                val r = http.post("$base/auth/v1/token?grant_type=pkce") {
                    common(); setBody(buildJsonObject { put("auth_code", code); put("code_verifier", verifier) }.toString())
                }
                if (!r.status.isSuccess()) throw AuthException(readError(r))
                verifierStore()?.edit()?.remove("pkce")?.apply()
                completeSignIn(parseSession(r.bodyAsText(), provider))
            }.onFailure { oauthError.value = it.message ?: "Google sign-in didn't finish." }
        }
        return true
    }

    // ---------- Session upkeep ----------
    suspend fun validToken(): String = refreshLock.withLock {
        val s = _session.value ?: throw AuthException("Not signed in.")
        if (System.currentTimeMillis() < s.expiresAt - 60_000) return@withLock s.accessToken
        val r = http.post("$base/auth/v1/token?grant_type=refresh_token") {
            common(); setBody(buildJsonObject { put("refresh_token", s.refreshToken) }.toString())
        }
        if (!r.status.isSuccess()) {
            if (r.status.value in 400..499) save(null) // refresh token revoked or expired
            throw AuthException("Your session expired. Sign in again.")
        }
        val fresh = parseSession(r.bodyAsText(), s.user.provider).let { it.copy(user = it.user.copy(provider = s.user.provider)) }
        save(fresh)
        fresh.accessToken
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { pushNow() }
        val token = _session.value?.accessToken
        syncJob?.cancel(); syncJob = null
        save(null)
        syncStatus.value = "idle"
        if (token != null) runCatching { http.post("$base/auth/v1/logout") { common(token) } }
        AppStore.track("sign_out")
    }

    suspend fun deleteAccount() = withContext(Dispatchers.IO) {
        val token = validToken()
        val r = http.post("$base/functions/v1/delete-account") { common(token); setBody("{}") }
        if (!r.status.isSuccess()) throw AuthException(readError(r))
        syncJob?.cancel(); syncJob = null
        save(null)
        syncStatus.value = "idle"
        AppStore.track("account_deleted")
    }

    // ---------- Progress backup ----------
    private fun startAutoSync() {
        syncJob?.cancel()
        syncJob = AppStore.state.drop(1).debounce(8_000).onEach { runCatching { pushNow() } }.launchIn(scope)
    }

    /** On sign-in: restore a richer cloud copy, otherwise back up this device. */
    private suspend fun pullOrPush() {
        val s = _session.value ?: return
        val token = validToken()
        val r = http.get("$base/rest/v1/profiles?id=eq.${s.user.id}&select=learning_state") { common(token) }
        val remote: AppState? = if (r.status.isSuccess()) {
            runCatching { json.parseToJsonElement(r.bodyAsText()).jsonArray.firstOrNull()?.jsonObject?.get("learning_state") }
                .getOrNull()?.takeIf { it !is JsonNull }?.let { AppStore.importJson(it) }
        } else null
        val local = AppStore.state.value
        if (remote != null && (remote.xp > local.xp || (remote.xp == local.xp && remote.lessonsCompleted > local.lessonsCompleted))) {
            AppStore.replaceState(remote)
            syncStatus.value = "synced"; lastSyncedAt.value = System.currentTimeMillis()
        } else pushNow()
    }

    suspend fun pushNow() = withContext(Dispatchers.IO) {
        val s = _session.value ?: return@withContext
        syncStatus.value = "syncing"
        try {
            val token = validToken()
            val st = AppStore.state.value
            val payload = buildJsonObject {
                put("id", s.user.id)
                s.user.email?.let { put("email", it) }
                s.user.phone?.let { put("phone", it) }
                put("name", st.profile.name.ifBlank { s.user.name ?: "" })
                put("learning_state", AppStore.exportJson(st))
                put("updated_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()))
            }
            val r = http.post("$base/rest/v1/profiles?on_conflict=id") {
                common(token); header("Prefer", "resolution=merge-duplicates,return=minimal"); setBody(payload.toString())
            }
            if (!r.status.isSuccess()) throw AuthException(readError(r))
            syncStatus.value = "synced"; lastSyncedAt.value = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "sync failed", e)
            syncStatus.value = "error"
        }
    }

    @Suppress("unused")
    private fun JsonElement.str(): String? = (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
}

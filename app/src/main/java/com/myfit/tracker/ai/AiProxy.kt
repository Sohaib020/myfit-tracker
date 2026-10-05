package com.myfit.tracker.ai

import com.myfit.tracker.BuildConfig
import java.net.HttpURLConnection

/**
 * MyFit's AI proxy (a Cloudflare Worker — see server/ai-proxy). The app ships with NO provider keys: when the
 * user hasn't entered their own key, requests go to the proxy, which holds the keys as server secrets, checks
 * the caller's Firebase sign-in and rate-limits each user.
 *
 * Providers receive [KEY] as their "key"; [open] swaps the real endpoint for the proxy and adds the
 * signed-in user's ID token instead of a provider key.
 */
object AiProxy {
    /** Sentinel "key" meaning "use the MyFit proxy". Never sent anywhere. */
    const val KEY = "myfit-proxy"

    private val url: String = BuildConfig.AI_PROXY_URL.trim().trimEnd('/')

    /** True when a proxy is configured for this build (the CI secret AI_PROXY_URL is set). */
    val configured: Boolean get() = url.isNotEmpty() && BuildConfig.SOCIAL

    /** Signed in to MyFit (Firebase) — the proxy only serves signed-in users. */
    val signedIn: Boolean get() = runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null }.getOrDefault(false)

    /** The key a provider should use: the user's own, else the proxy sentinel when it can be used, else "". */
    fun keyOr(userKey: String): String = userKey.ifBlank { if (configured && signedIn) KEY else "" }

    fun isProxy(key: String) = key == KEY

    /** Proxy URL for [provider] + [path] (e.g. "gemini", "/v1beta/models"). */
    fun url(provider: String, path: String) = "$url/$provider$path"

    /** Current Firebase ID token (cached by the SDK; refreshed when near expiry). Call off the main thread. */
    fun token(): String? = runCatching {
        val u = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return null
        com.google.android.gms.tasks.Tasks.await(u.getIdToken(false), 15, java.util.concurrent.TimeUnit.SECONDS).token
    }.getOrNull()

    /** Adds the proxy auth header. Throws a clear error when the user isn't signed in. */
    fun authorize(c: HttpURLConnection) {
        val t = token() ?: throw IllegalStateException("Sign in (Me → Account) to use online AI, or add your own key in Settings → AI")
        c.setRequestProperty("Authorization", "Bearer $t")
    }
}

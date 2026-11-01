package com.kalai73.lifemapp.widgets.core.data

import kotlinx.serialization.json.Json

/** Lenient JSON shared by the API client and the local cache: unknown fields from newer servers are ignored. */
val LifeMappJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/** Build-time client configuration (local.properties / CI env, see core/data/build.gradle.kts). */
data class LifeMappConfig(
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/'),
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    val webUrl: String = BuildConfig.WEB_URL.trimEnd('/'),
    /** OAuth redirect, matching the intent filter of AuthCallbackActivity */
    val authRedirectUri: String = "lifemappwidgets://auth/callback",
)

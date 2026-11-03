package com.kalai73.lifemapp.widgets.core.data

/** Build-time client configuration (local.properties / CI env, see core/data/build.gradle.kts). */
data class LifeMappConfig(
    val supabaseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/'),
    val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    val webUrl: String = BuildConfig.WEB_URL.trimEnd('/'),
    /** OAuth redirect, matching the intent filter of AuthCallbackActivity */
    val authRedirectUri: String = "lifemappwidgets://auth/callback",
)

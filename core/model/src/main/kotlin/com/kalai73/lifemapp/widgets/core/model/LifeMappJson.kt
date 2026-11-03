package com.kalai73.lifemapp.widgets.core.model

import kotlinx.serialization.json.Json

/** Lenient JSON shared by the API client, the local cache and the presenter: unknown fields from newer servers are ignored. */
val LifeMappJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

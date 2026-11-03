package com.kalai73.lifemapp.widgets.core.data.api

import com.kalai73.lifemapp.widgets.core.model.Snapshot
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/** The widget snapshot: everything the widgets and the Today screen show, in one request. */
class SnapshotApi internal constructor(client: ApiClient) : ApiArea(client) {
    suspend fun snapshot(timezone: String, daysAhead: Int = 7): Snapshot {
        val response = authorized { token ->
            http.get("${config.webUrl}/api/widget/snapshot") {
                bearerAuth(token)
                parameter("tz", timezone)
                parameter("daysAhead", daysAhead)
                // Also the Task List Widget's Tomorrow / Tomorrow ++ 1 tasks, shown dimmed as on the web
                parameter("upcomingTasks", 1)
            }
        }
        return response.body()
    }
}

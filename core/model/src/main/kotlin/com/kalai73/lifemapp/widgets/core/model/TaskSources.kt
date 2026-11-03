package com.kalai73.lifemapp.widgets.core.model

/** What the app can do with a task, by its [Task.source] ("local" for LIFE MAPP's own, else the provider). */
object TaskSources {
    const val LOCAL = "local"
    const val TODOIST = "todoist"

    /** Tasks the app can check off: LIFE MAPP's own, and Todoist's with the user's API key */
    fun canComplete(source: String): Boolean = source == LOCAL || source == TODOIST

    /** Tasks whose title and description the app can edit: LIFE MAPP's own */
    fun canEdit(source: String): Boolean = source == LOCAL
}

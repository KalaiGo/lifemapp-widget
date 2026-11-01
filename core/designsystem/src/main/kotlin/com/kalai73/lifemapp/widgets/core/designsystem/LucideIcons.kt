package com.kalai73.lifemapp.widgets.core.designsystem

/**
 * Bundled Lucide icons (stroke vectors, tinted at render time). RemoteViews can't load arbitrary images
 * cheaply, so quick-link icons outside this set fall back to their label's first letter on a colored tile.
 * Generated with scratchpad/gen_icons.py — add icons there and re-run.
 */
object LucideIcons {
    private val drawables: Map<String, Int> = mapOf(
        "calendar" to R.drawable.lm_ic_calendar,
        "list-checks" to R.drawable.lm_ic_list_checks,
        "list-todo" to R.drawable.lm_ic_list_todo,
        "mail" to R.drawable.lm_ic_mail,
        "inbox" to R.drawable.lm_ic_inbox,
        "book-open" to R.drawable.lm_ic_book_open,
        "book" to R.drawable.lm_ic_book,
        "wallet" to R.drawable.lm_ic_wallet,
        "refresh-cw" to R.drawable.lm_ic_refresh_cw,
        "plus" to R.drawable.lm_ic_plus,
        "lock" to R.drawable.lm_ic_lock,
        "circle" to R.drawable.lm_ic_circle,
        "circle-check" to R.drawable.lm_ic_circle_check,
        "repeat" to R.drawable.lm_ic_repeat,
        "triangle-alert" to R.drawable.lm_ic_triangle_alert,
        "target" to R.drawable.lm_ic_target,
        "hexagon" to R.drawable.lm_ic_hexagon,
        "star" to R.drawable.lm_ic_star,
        "sun" to R.drawable.lm_ic_sun,
        "moon" to R.drawable.lm_ic_moon,
        "cloud" to R.drawable.lm_ic_cloud,
        "cloud-sun" to R.drawable.lm_ic_cloud_sun,
        "cloud-moon" to R.drawable.lm_ic_cloud_moon,
        "cloud-rain" to R.drawable.lm_ic_cloud_rain,
        "cloud-snow" to R.drawable.lm_ic_cloud_snow,
        "cloud-lightning" to R.drawable.lm_ic_cloud_lightning,
        "cloud-fog" to R.drawable.lm_ic_cloud_fog,
        "wind" to R.drawable.lm_ic_wind,
        "snowflake" to R.drawable.lm_ic_snowflake,
        "link" to R.drawable.lm_ic_link,
        "globe" to R.drawable.lm_ic_globe,
        "search" to R.drawable.lm_ic_search,
        "bookmark" to R.drawable.lm_ic_bookmark,
        "send" to R.drawable.lm_ic_send,
        "message-circle" to R.drawable.lm_ic_message_circle,
        "message-square" to R.drawable.lm_ic_message_square,
        "phone" to R.drawable.lm_ic_phone,
        "bell" to R.drawable.lm_ic_bell,
        "youtube" to R.drawable.lm_ic_youtube,
        "music" to R.drawable.lm_ic_music,
        "video" to R.drawable.lm_ic_video,
        "camera" to R.drawable.lm_ic_camera,
        "mic" to R.drawable.lm_ic_mic,
        "headphones" to R.drawable.lm_ic_headphones,
        "image" to R.drawable.lm_ic_image,
        "tv" to R.drawable.lm_ic_tv,
        "file" to R.drawable.lm_ic_file,
        "file-text" to R.drawable.lm_ic_file_text,
        "folder" to R.drawable.lm_ic_folder,
        "clock" to R.drawable.lm_ic_clock,
        "user" to R.drawable.lm_ic_user,
        "users" to R.drawable.lm_ic_users,
        "briefcase" to R.drawable.lm_ic_briefcase,
        "graduation-cap" to R.drawable.lm_ic_graduation_cap,
        "house" to R.drawable.lm_ic_house,
        "smartphone" to R.drawable.lm_ic_smartphone,
        "monitor" to R.drawable.lm_ic_monitor,
        "code" to R.drawable.lm_ic_code,
        "terminal" to R.drawable.lm_ic_terminal,
        "shopping-cart" to R.drawable.lm_ic_shopping_cart,
        "shopping-bag" to R.drawable.lm_ic_shopping_bag,
        "credit-card" to R.drawable.lm_ic_credit_card,
        "dollar-sign" to R.drawable.lm_ic_dollar_sign,
        "trending-up" to R.drawable.lm_ic_trending_up,
        "plane" to R.drawable.lm_ic_plane,
        "car" to R.drawable.lm_ic_car,
        "map" to R.drawable.lm_ic_map,
        "map-pin" to R.drawable.lm_ic_map_pin,
        "coffee" to R.drawable.lm_ic_coffee,
        "heart" to R.drawable.lm_ic_heart,
        "activity" to R.drawable.lm_ic_activity,
        "leaf" to R.drawable.lm_ic_leaf,
        "flame" to R.drawable.lm_ic_flame,
        "zap" to R.drawable.lm_ic_zap,
        "shield" to R.drawable.lm_ic_shield,
        "key" to R.drawable.lm_ic_key,
        "lightbulb" to R.drawable.lm_ic_lightbulb,
        "pencil" to R.drawable.lm_ic_pencil,
        "flag" to R.drawable.lm_ic_flag,
    )

    /** lucide-react names (kebab-cased) drawn with a close match */
    private val aliases: Map<String, String> = mapOf(
        "alarm-clock" to "clock",
        "apple" to "leaf",
        "archive" to "folder",
        "at-sign" to "mail",
        "atom" to "zap",
        "banknote" to "credit-card",
        "bar-chart-2" to "trending-up",
        "battery" to "zap",
        "bike" to "activity",
        "building-2" to "house",
        "bus" to "car",
        "check-circle" to "circle-check",
        "check-circle-2" to "circle-check",
        "chef-hat" to "coffee",
        "clipboard" to "file-text",
        "clipboard-list" to "list-checks",
        "cloud-rain" to "cloud-rain",
        "compass" to "map",
        "cpu" to "monitor",
        "database" to "folder",
        "dumbbell" to "activity",
        "external-link" to "link",
        "file-spreadsheet" to "file-text",
        "film" to "video",
        "flower-2" to "leaf",
        "folder-open" to "folder",
        "gamepad-2" to "monitor",
        "hard-drive" to "folder",
        "hash" to "message-square",
        "heart-pulse" to "heart",
        "home" to "house",
        "hotel" to "house",
        "landmark" to "house",
        "layers" to "list-todo",
        "layout-dashboard" to "list-todo",
        "link-2" to "link",
        "mailbox" to "inbox",
        "mountain" to "map",
        "navigation" to "map-pin",
        "newspaper" to "file-text",
        "package" to "shopping-bag",
        "palette" to "pencil",
        "phone-call" to "phone",
        "piggy-bank" to "wallet",
        "podcast" to "mic",
        "power" to "zap",
        "printer" to "file",
        "radio" to "music",
        "receipt" to "file-text",
        "rocket" to "zap",
        "rss" to "wind",
        "school" to "graduation-cap",
        "settings" to "key",
        "settings-2" to "key",
        "share-2" to "link",
        "sparkles" to "star",
        "store" to "shopping-bag",
        "tablet" to "smartphone",
        "tag" to "bookmark",
        "tent" to "map-pin",
        "train" to "car",
        "tree-pine" to "leaf",
        "trophy" to "star",
        "tv" to "tv",
        "umbrella" to "cloud-rain",
        "users" to "users",
        "utensils-crossed" to "coffee",
        "watch" to "clock",
        "waves" to "wind",
        "wifi" to "globe",
        "wine" to "coffee",
        "wrench" to "key",
    )

    /** Kebab-case of a lucide-react name: "ShoppingCart" → "shopping-cart", "Share2" → "share-2". */
    fun kebab(name: String): String = buildString {
        name.trim().forEachIndexed { i, ch ->
            val prev = name.getOrNull(i - 1)
            if (i > 0 && ((ch.isUpperCase() && prev?.isDigit() != true) || (ch.isDigit() && prev?.isDigit() != true))) append('-')
            append(ch.lowercaseChar())
        }
    }

    /** Drawable for a lucide-react (or kebab-case) icon name, or null when it isn't bundled. */
    fun resolve(name: String?): Int? {
        if (name.isNullOrBlank()) return null
        val key = if (name.contains('-')) name.lowercase() else kebab(name)
        return drawables[key] ?: aliases[key]?.let(drawables::get)
    }

    /** An emoji (or other non-name text) is drawn as text rather than looked up. */
    fun isEmoji(icon: String?): Boolean = !icon.isNullOrBlank() && icon.none { it.isLetterOrDigit() && it.code < 128 }

    val CALENDAR = R.drawable.lm_ic_calendar
    val TASKS = R.drawable.lm_ic_list_checks
    val MAIL = R.drawable.lm_ic_mail
    val JOURNAL = R.drawable.lm_ic_book_open
    val BUDGET = R.drawable.lm_ic_wallet
    val RELOAD = R.drawable.lm_ic_refresh_cw
    val ADD = R.drawable.lm_ic_plus
    val MIC = R.drawable.lm_ic_mic
    val PENCIL = R.drawable.lm_ic_pencil
    val LOCK = R.drawable.lm_ic_lock
    val CHECK_OFF = R.drawable.lm_ic_circle
    val CHECK_ON = R.drawable.lm_ic_circle_check
    val REPEAT = R.drawable.lm_ic_repeat
    val WARNING = R.drawable.lm_ic_triangle_alert
    val TARGET = R.drawable.lm_ic_target
    val HEXAGON = R.drawable.lm_ic_hexagon
    val STAR = R.drawable.lm_ic_star
    val LINK = R.drawable.lm_ic_link
    val ZAP = R.drawable.lm_ic_zap
    val INBOX = R.drawable.lm_ic_inbox
    val CLOCK = R.drawable.lm_ic_clock
    val CHEVRON_DOWN = R.drawable.lm_ic_chevron_down
    val CHEVRON_RIGHT = R.drawable.lm_ic_chevron_right
    val FOLDER = R.drawable.lm_ic_folder
    val SMARTPHONE = R.drawable.lm_ic_smartphone
    val PANEL =R.drawable.lm_ic_panel_left

    /** Visual Crossing icon ids (weather.icon) → bundled weather glyphs */
    fun weather(icon: String?): Int = when (icon) {
        "clear-day" -> R.drawable.lm_ic_sun
        "clear-night" -> R.drawable.lm_ic_moon
        "partly-cloudy-day" -> R.drawable.lm_ic_cloud_sun
        "partly-cloudy-night" -> R.drawable.lm_ic_cloud_moon
        "rain", "showers-day", "showers-night" -> R.drawable.lm_ic_cloud_rain
        "snow", "snow-showers-day", "snow-showers-night", "sleet" -> R.drawable.lm_ic_cloud_snow
        "thunder-rain", "thunder-showers-day", "thunder-showers-night", "thunder" -> R.drawable.lm_ic_cloud_lightning
        "fog" -> R.drawable.lm_ic_cloud_fog
        "wind" -> R.drawable.lm_ic_wind
        else -> R.drawable.lm_ic_cloud
    }
}

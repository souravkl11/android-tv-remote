package com.ryzen.tvremote.apps

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A button on the remote that opens an app on the TV.
 *
 * @param launchTarget either a deep link (e.g. `https://www.youtube.com`, `spotify://`)
 * or a plain package name (e.g. `org.xbmc.kodi`), which the v2 client turns into
 * `market://launch?id=<package>`.
 */
data class AppShortcut(
    val id: String,
    val name: String,
    val launchTarget: String,
    val colorArgb: Long,
    val presetId: String? = null
)

data class AppPreset(
    val id: String,
    val name: String,
    val launchTarget: String,
    val colorArgb: Long
) {
    fun toShortcut() = AppShortcut(
        id = UUID.randomUUID().toString(),
        name = name,
        launchTarget = launchTarget,
        colorArgb = colorArgb,
        presetId = id
    )
}

object AppPresets {
    val all: List<AppPreset> = listOf(
        AppPreset("youtube", "YouTube", "https://www.youtube.com", 0xFFFF0033),
        AppPreset("netflix", "Netflix", "https://www.netflix.com/title", 0xFFE50914),
        AppPreset("primevideo", "Prime Video", "https://app.primevideo.com", 0xFF1A98FF),
        AppPreset("spotify", "Spotify", "spotify://", 0xFF1DB954),
        AppPreset("disneyplus", "Disney+", "https://www.disneyplus.com", 0xFF113CCF),
        AppPreset("ytmusic", "YT Music", "https://music.youtube.com", 0xFFD32F2F),
        AppPreset("hotstar", "JioHotstar", "in.startv.hotstar", 0xFF5A31F4),
        AppPreset("twitch", "Twitch", "twitch://home", 0xFF9146FF),
        AppPreset("kodi", "Kodi", "market://launch?id=org.xbmc.kodi", 0xFF17B2E7)
    )

    val defaultIds = listOf("youtube", "netflix", "primevideo", "spotify")

    /** Swatches offered when creating a custom app. */
    val swatches: List<Long> = listOf(
        0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047,
        0xFF00ACC1, 0xFF1E88E5, 0xFF5E35B1, 0xFFD81B60, 0xFF546E7A
    )
}

/** Pure JSON (de)serialization so it can be unit-tested without Android. */
object AppShortcutCodec {
    fun encode(list: List<AppShortcut>): String {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("target", s.launchTarget)
                put("color", s.colorArgb)
                s.presetId?.let { put("preset", it) }
            })
        }
        return arr.toString()
    }

    fun decode(json: String): List<AppShortcut> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("name")
            val target = o.optString("target")
            if (name.isBlank() || target.isBlank()) return@mapNotNull null
            AppShortcut(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = name,
                launchTarget = target,
                colorArgb = o.optLong("color", 0xFF546E7A),
                presetId = if (o.has("preset")) o.optString("preset") else null
            )
        }
    }
}

class AppShortcutRepository(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_SHORTCUTS = "app_shortcuts"
    }

    private val _shortcuts = MutableStateFlow(load())
    val shortcuts: StateFlow<List<AppShortcut>> = _shortcuts.asStateFlow()

    private fun load(): List<AppShortcut> {
        val json = prefs.getString(KEY_SHORTCUTS, null)
        if (json == null) {
            val seeded = AppPresets.all.filter { it.id in AppPresets.defaultIds }.map { it.toShortcut() }
            persist(seeded)
            return seeded
        }
        return try {
            AppShortcutCodec.decode(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(list: List<AppShortcut>) {
        prefs.edit().putString(KEY_SHORTCUTS, AppShortcutCodec.encode(list)).apply()
    }

    private fun set(list: List<AppShortcut>) {
        _shortcuts.value = list
        persist(list)
    }

    fun add(shortcut: AppShortcut) = set(_shortcuts.value + shortcut)

    fun update(shortcut: AppShortcut) =
        set(_shortcuts.value.map { if (it.id == shortcut.id) shortcut else it })

    fun remove(id: String) = set(_shortcuts.value.filterNot { it.id == id })

    /** Moves the shortcut by [delta] positions (negative = left). */
    fun move(id: String, delta: Int) = set(moveItem(_shortcuts.value, id, delta))
}

fun moveItem(list: List<AppShortcut>, id: String, delta: Int): List<AppShortcut> {
    val from = list.indexOfFirst { it.id == id }
    if (from < 0) return list
    val to = (from + delta).coerceIn(0, list.lastIndex)
    if (to == from) return list
    return list.toMutableList().apply { add(to, removeAt(from)) }
}

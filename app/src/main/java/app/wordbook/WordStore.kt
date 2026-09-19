package app.wordbook

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class Entry(
    val id: String,
    val word: String,
    val pos: String,
    val meaning: String,
    val example: String
)

data class WidgetSettings(
    val showPos: Boolean,
    val showMeaning: Boolean,
    val showExample: Boolean,
    val size: String,
    val color: String,
    val theme: String
)

/**
 * The web app pushes its whole state (words, settings, current pick) here as one JSON string.
 * The widget reads it, and can rotate the pick on its own (new day, shuffle button).
 * The web app adopts the rotated pick next time it opens.
 */
object WordStore {
    private const val PREFS = "wordbook"
    private const val KEY = "state"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun raw(ctx: Context): String? = prefs(ctx).getString(KEY, null)

    fun saveRaw(ctx: Context, json: String) {
        prefs(ctx).edit().putString(KEY, json).apply()
    }

    private fun state(ctx: Context): JSONObject? =
        try {
            raw(ctx)?.let { JSONObject(it) }
        } catch (e: Exception) {
            null
        }

    fun settings(ctx: Context): WidgetSettings {
        val s = state(ctx)?.optJSONObject("settings")
        return WidgetSettings(
            showPos = s?.optBoolean("showPos", true) ?: true,
            showMeaning = s?.optBoolean("showMeaning", true) ?: true,
            showExample = s?.optBoolean("showExample", true) ?: true,
            size = s?.optString("size", "m") ?: "m",
            color = s?.optString("color", "coral") ?: "coral",
            theme = s?.optString("theme", "auto") ?: "auto"
        )
    }

    private fun today(): String = LocalDate.now().toString()

    // Same rule as the web app: prefer words that have a meaning.
    private fun pool(words: JSONArray): List<JSONObject> {
        val all = ArrayList<JSONObject>()
        for (i in 0 until words.length()) {
            val w = words.optJSONObject(i) ?: continue
            if (w.optString("id").isNotEmpty() && w.optString("word").isNotBlank()) all.add(w)
        }
        val withMeaning = all.filter { it.optString("meaning").isNotBlank() }
        return if (withMeaning.isNotEmpty()) withMeaning else all
    }

    private fun toEntry(w: JSONObject) = Entry(
        id = w.optString("id"),
        word = w.optString("word"),
        pos = w.optString("pos"),
        meaning = w.optString("meaning"),
        example = w.optString("example")
    )

    /**
     * Returns the word to show. Keeps today's pick if there is one,
     * otherwise picks a new word (every word shows once before any repeats) and saves it.
     */
    @Synchronized
    fun current(ctx: Context, forceNew: Boolean = false): Entry? {
        val st = state(ctx) ?: return null
        val words = st.optJSONArray("words") ?: return null
        val pool = pool(words)
        if (pool.isEmpty()) return null

        val pick = st.optJSONObject("pick")
        val curId = pick?.optString("id") ?: ""
        val cur = pool.firstOrNull { it.optString("id") == curId }
        if (!forceNew && cur != null && pick?.optString("date") == today()) return toEntry(cur)

        val seen = ArrayList<String>()
        st.optJSONArray("seen")?.let { arr ->
            for (i in 0 until arr.length()) seen.add(arr.optString(i))
        }

        var fresh = pool.filter { it.optString("id") !in seen && it.optString("id") != curId }
        if (fresh.isEmpty()) {
            seen.clear()
            if (cur != null) seen.add(curId)
            fresh = pool.filter { it.optString("id") != curId }
            if (fresh.isEmpty()) fresh = pool
        }

        val chosen = fresh.random()
        val id = chosen.optString("id")
        seen.add(id)

        st.put("pick", JSONObject().put("date", today()).put("id", id))
        st.put("seen", JSONArray(seen))
        saveRaw(ctx, st.toString())
        return toEntry(chosen)
    }
}

package app.wordbook

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlin.math.min

class WordWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) render(context, appWidgetManager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        render(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SHUFFLE) {
            WordStore.current(context, forceNew = true)
            updateAll(context)
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_SHUFFLE = "app.wordbook.SHUFFLE"

        private val WORD_COLORS = mapOf(
            "coral" to ("#FF6B57" to "#C93A26"),
            "amber" to ("#FFB547" to "#A56300"),
            "mint" to ("#4FD1A5" to "#0B7A5B"),
            "pink" to ("#FF7EB6" to "#B92A68"),
            "violet" to ("#B18CFF" to "#6B3FD1")
        )

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, WordWidget::class.java))
            for (id in ids) render(context, mgr, id)
        }

        private fun isDark(context: Context, theme: String): Boolean = when (theme) {
            "dark" -> true
            "light" -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }

        private fun render(context: Context, mgr: AppWidgetManager, id: Int) {
            val s = WordStore.settings(context)
            val dark = isDark(context, s.theme)

            val textColor = Color.parseColor(if (dark) "#ECEEF4" else "#141926")
            val mutedColor = Color.parseColor(if (dark) "#98A0B5" else "#566079")
            val posColor = Color.parseColor(if (dark) "#7BA3FF" else "#2A58D0")
            val pair = WORD_COLORS[s.color] ?: WORD_COLORS.getValue("coral")
            val wordColor = Color.parseColor(if (dark) pair.first else pair.second)

            val opts = mgr.getAppWidgetOptions(id)
            val widthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val heightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

            val views = RemoteViews(context.packageName, R.layout.widget_word)
            views.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light
            )
            views.setInt(R.id.w_shuffle, "setColorFilter", mutedColor)
            views.setTextColor(R.id.w_word, wordColor)
            views.setTextColor(R.id.w_pos, posColor)
            views.setTextColor(R.id.w_meaning, textColor)
            views.setTextColor(R.id.w_example, mutedColor)

            val entry = WordStore.current(context)
            if (entry == null) {
                views.setTextViewText(R.id.w_word, "No words yet")
                views.setTextViewTextSize(R.id.w_word, TypedValue.COMPLEX_UNIT_SP, 22f)
                views.setViewVisibility(R.id.w_pos, View.GONE)
                views.setViewVisibility(R.id.w_meaning, View.VISIBLE)
                views.setTextViewText(R.id.w_meaning, "Open Wordbook and add a word to see it here.")
                views.setInt(R.id.w_meaning, "setMaxLines", 3)
                views.setViewVisibility(R.id.w_example, View.GONE)
            } else {
                // Shrink long words so they stay on one line.
                val base = when (s.size) {
                    "s" -> 22f
                    "l" -> 36f
                    else -> 28f
                }
                val availableDp = (widthDp - 36 - 34).coerceAtLeast(120)
                val fitSp = availableDp / (entry.word.length * 0.6f)
                val sp = min(base, fitSp).coerceAtLeast(16f)
                views.setTextViewText(R.id.w_word, entry.word)
                views.setTextViewTextSize(R.id.w_word, TypedValue.COMPLEX_UNIT_SP, sp)

                val showPos = s.showPos && entry.pos.isNotEmpty()
                views.setViewVisibility(R.id.w_pos, if (showPos) View.VISIBLE else View.GONE)
                if (showPos) views.setTextViewText(R.id.w_pos, entry.pos)

                views.setViewVisibility(R.id.w_meaning, if (s.showMeaning) View.VISIBLE else View.GONE)
                if (s.showMeaning) {
                    views.setTextViewText(
                        R.id.w_meaning,
                        entry.meaning.ifBlank { "No meaning yet. Add one in Wordbook." }
                    )
                    val lines = when {
                        heightDp < 125 -> 1
                        heightDp < 170 -> 2
                        heightDp < 230 -> 4
                        else -> 7
                    }
                    views.setInt(R.id.w_meaning, "setMaxLines", lines)
                }

                val showExample = s.showExample && entry.example.isNotBlank() && heightDp >= 170
                views.setViewVisibility(R.id.w_example, if (showExample) View.VISIBLE else View.GONE)
                if (showExample) views.setTextViewText(R.id.w_example, entry.example)
            }

            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), flags
            )
            val shuffle = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, WordWidget::class.java).setAction(ACTION_SHUFFLE),
                flags
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setOnClickPendingIntent(R.id.w_shuffle, shuffle)

            mgr.updateAppWidget(id, views)
        }
    }
}

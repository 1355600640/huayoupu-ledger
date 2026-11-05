package net.ankio.ledger.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ankio.ledger.R
import net.ankio.ledger.core.Money
import net.ankio.ledger.data.DayTotals
import net.ankio.ledger.ledger
import net.ankio.ledger.ui.MainActivity
import java.time.LocalDate

class TodayLedgerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                updateAll(context)
            }
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val componentName = ComponentName(context, TodayLedgerWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName) ?: return
            if (appWidgetIds.isEmpty()) return

            val app = context.ledger
            app.scope.launch {
                val totals = runCatching { app.store.todayTotals() }.getOrDefault(DayTotals(0L, 0L))
                withContext(Dispatchers.Main) {
                    val views = buildViews(context, totals)
                    for (id in appWidgetIds) {
                        appWidgetManager.updateAppWidget(id, views)
                    }
                }
            }
        }

        private fun buildViews(context: Context, totals: DayTotals): RemoteViews {
            val now = LocalDate.now()
            val dateText = "${now.monthValue}月${now.dayOfMonth}日 · 今天"
            val expenseText = "¥ " + Money.format(totals.expenseCents)
            val incomeText = "¥ " + Money.format(totals.incomeCents)

            val intent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_ADD_RECORD
                putExtra(MainActivity.EXTRA_OPEN_MANUAL, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return RemoteViews(context.packageName, R.layout.widget_today_ledger).apply {
                setTextViewText(R.id.widget_date, dateText)
                setTextViewText(R.id.widget_expense_amount, expenseText)
                setTextViewText(R.id.widget_income_amount, incomeText)
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
                setOnClickPendingIntent(R.id.widget_btn_add, pendingIntent)
            }
        }
    }
}

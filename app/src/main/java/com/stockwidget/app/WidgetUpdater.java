package com.stockwidget.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class WidgetUpdater {
    private static final AtomicBoolean IS_UPDATING = new AtomicBoolean(false);

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, StockWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        update(context, manager, ids);
    }

    public static void update(Context context, AppWidgetManager manager, int[] ids) {
        if (ids == null || ids.length == 0) {
            return;
        }
        if (!IS_UPDATING.compareAndSet(false, true)) {
            return;
        }

        try {
            List<StockItem> stocks = StockRepository.loadStocks(context);
            if (!MarketHours.isRegularMarketOpen()) {
                List<StockQuote> quotes = StockRepository.loadQuoteCache(context, stocks);
                if (MarketHours.isAfterRegularMarketClose() || quotes.isEmpty()) {
                    quotes = NaverFinanceClient.fetchQuotes(stocks);
                    if (NaverFinanceClient.hasSuccessfulQuote(quotes)) {
                        StockRepository.saveQuoteCache(context, quotes);
                    }
                }
                if (!NaverFinanceClient.hasSuccessfulQuote(quotes)) {
                    quotes = StockRepository.loadQuoteCache(context, stocks);
                }
                if (!quotes.isEmpty()) {
                    String updated = new SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(new Date());
                    String suffix = MarketHours.isAfterRegularMarketClose() ? "마감 정보" : "저장된 정보";
                    updateWidgetShell(context, manager, ids, MarketHours.statusLabel(), updated + " " + suffix);
                    return;
                }
                updatePaused(context, manager, ids, stocks);
                return;
            }

            List<StockQuote> quotes = NaverFinanceClient.fetchQuotes(stocks);
            if (NaverFinanceClient.hasSuccessfulQuote(quotes)) {
                StockRepository.saveQuoteCache(context, quotes);
            }
            String updated = new SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(new Date());
            updateWidgetShell(context, manager, ids, "장중", updated + " 갱신");
        } finally {
            IS_UPDATING.set(false);
        }
    }

    private static void updateWidgetShell(Context context, AppWidgetManager manager, int[] ids, String marketLabel, String updatedText) {
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stock_list);
            views.setTextViewText(R.id.widget_title, "주식 위젯 · " + marketLabel);
            views.setTextViewText(R.id.widget_updated, updatedText);
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context));
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context));
            views.setRemoteAdapter(R.id.widget_rows, rowsIntent(context, id));
            views.setEmptyView(R.id.widget_rows, R.id.widget_empty);
            manager.updateAppWidget(id, views);
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_rows);
        }
    }

    private static void updatePaused(Context context, AppWidgetManager manager, int[] ids, List<StockItem> stocks) {
        updateWidgetShell(context, manager, ids, MarketHours.statusLabel(), MarketHours.detailText());
    }

    private static Intent rowsIntent(Context context, int widgetId) {
        Intent intent = new Intent(context, StockWidgetRemoteViewsService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        intent.setData(android.net.Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        return intent;
    }

    private static PendingIntent refreshIntent(Context context) {
        Intent intent = new Intent(context, StockWidgetProvider.class);
        intent.setAction(StockWidgetProvider.ACTION_REFRESH);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, 1001, intent, flags);
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, 1003, intent, flags);
    }
}

package com.stockwidget.app;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StockWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "com.stockwidget.app.REFRESH_WIDGET";
    public static final String ACTION_AUTO_REFRESH = "com.stockwidget.app.AUTO_REFRESH_WIDGET";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        UpdateScheduler.applySettings(context.getApplicationContext());
        updateAsync(context.getApplicationContext(), appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_REFRESH.equals(action) || ACTION_AUTO_REFRESH.equals(action)) {
            BroadcastReceiver.PendingResult pendingResult = goAsync();
            Context appContext = context.getApplicationContext();
            new Thread(() -> {
                try {
                    WidgetUpdater.updateAll(appContext);
                } finally {
                    UpdateScheduler.applySettings(appContext);
                    pendingResult.finish();
                }
            }).start();
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            UpdateScheduler.applySettings(context.getApplicationContext());
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onEnabled(Context context) {
        UpdateScheduler.applySettings(context.getApplicationContext());
    }

    @Override
    public void onDisabled(Context context) {
        UpdateScheduler.cancel(context.getApplicationContext());
    }

    private void updateAsync(Context context, AppWidgetManager manager, int[] ids) {
        new Thread(() -> WidgetUpdater.update(context, manager, ids)).start();
    }
}

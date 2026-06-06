package com.stockwidget.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public class UpdateScheduler {
    private static final int REQUEST_AUTO_REFRESH = 1002;
    private static final long MINUTE_MILLIS = 60_000L;

    public static void applySettings(Context context) {
        int minutes = StockRepository.loadRefreshIntervalMinutes(context);
        if (minutes <= 0 || !hasWidgets(context)) {
            cancel(context);
            return;
        }
        if (MarketHours.isPreMarketActive()
                || MarketHours.isRegularMarketOpen()
                || MarketHours.isNxtMarketActive()) {
            schedule(context, minutes);
            return;
        }
        scheduleNextOpen(context);
    }

    public static void schedule(Context context, int minutes) {
        if (minutes <= 0 || !hasWidgets(context)) {
            cancel(context);
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        alarmManager.cancel(pendingIntent(context));
        long intervalMillis = Math.max(1, minutes) * MINUTE_MILLIS;
        long firstTrigger = SystemClock.elapsedRealtime() + intervalMillis;
        alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                firstTrigger,
                intervalMillis,
                pendingIntent(context)
        );
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent(context));
        }
    }

    private static void scheduleNextOpen(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent intent = pendingIntent(context);
        alarmManager.cancel(intent);
        long trigger = SystemClock.elapsedRealtime() + MarketHours.millisUntilNextOpen();
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, intent);
    }

    private static boolean hasWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, StockWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        return ids != null && ids.length > 0;
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, StockWidgetProvider.class);
        intent.setAction(StockWidgetProvider.ACTION_AUTO_REFRESH);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, REQUEST_AUTO_REFRESH, intent, flags);
    }
}

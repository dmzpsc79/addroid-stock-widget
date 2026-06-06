package com.stockwidget.app;

import java.util.Calendar;
import java.util.TimeZone;

public class MarketHours {
    private static final TimeZone KOREA_TIME_ZONE = TimeZone.getTimeZone("Asia/Seoul");
    private static final int OPEN_MINUTES = 9 * 60;
    private static final int CLOSE_MINUTES = 15 * 60 + 30;

    public static boolean isRegularMarketOpen() {
        Calendar now = nowInKorea();
        if (!isWeekday(now)) {
            return false;
        }
        int minutes = minutesOfDay(now);
        return minutes >= OPEN_MINUTES && minutes < CLOSE_MINUTES;
    }

    public static boolean isAfterRegularMarketClose() {
        Calendar now = nowInKorea();
        return isWeekday(now) && minutesOfDay(now) >= CLOSE_MINUTES;
    }

    public static String statusLabel() {
        Calendar now = nowInKorea();
        if (!isWeekday(now)) {
            return "휴장";
        }
        int minutes = minutesOfDay(now);
        if (minutes < OPEN_MINUTES) {
            return "장 시작 전";
        }
        if (minutes < CLOSE_MINUTES) {
            return "장중";
        }
        return "장 종료";
    }

    public static String detailText() {
        Calendar now = nowInKorea();
        if (!isWeekday(now)) {
            return "평일 09:00 시작";
        }
        int minutes = minutesOfDay(now);
        if (minutes < OPEN_MINUTES) {
            return "09:00 시작";
        }
        if (minutes < CLOSE_MINUTES) {
            return "정규장 09:00~15:30";
        }
        return "마감 정보";
    }

    public static String appStatusText() {
        return statusLabel() + " · " + detailText();
    }

    public static long millisUntilNextOpen() {
        Calendar now = nowInKorea();
        Calendar nextOpen = (Calendar) now.clone();
        nextOpen.set(Calendar.HOUR_OF_DAY, 9);
        nextOpen.set(Calendar.MINUTE, 0);
        nextOpen.set(Calendar.SECOND, 0);
        nextOpen.set(Calendar.MILLISECOND, 0);

        if (!isWeekday(nextOpen) || now.getTimeInMillis() >= nextOpen.getTimeInMillis()) {
            do {
                nextOpen.add(Calendar.DAY_OF_MONTH, 1);
                nextOpen.set(Calendar.HOUR_OF_DAY, 9);
                nextOpen.set(Calendar.MINUTE, 0);
                nextOpen.set(Calendar.SECOND, 0);
                nextOpen.set(Calendar.MILLISECOND, 0);
            } while (!isWeekday(nextOpen));
        }

        return Math.max(60_000L, nextOpen.getTimeInMillis() - now.getTimeInMillis());
    }

    private static Calendar nowInKorea() {
        return Calendar.getInstance(KOREA_TIME_ZONE);
    }

    private static boolean isWeekday(Calendar calendar) {
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        return day != Calendar.SATURDAY && day != Calendar.SUNDAY;
    }

    private static int minutesOfDay(Calendar calendar) {
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    }
}

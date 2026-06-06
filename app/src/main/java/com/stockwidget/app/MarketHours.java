// 한국 주식시장(KRX) 장 시간 및 공휴일 판별 유틸리티
package com.stockwidget.app;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

public class MarketHours {
    private static final TimeZone KOREA_TIME_ZONE = TimeZone.getTimeZone("Asia/Seoul");
    private static final int OPEN_MINUTES = 9 * 60;
    private static final int CLOSE_MINUTES = 15 * 60 + 30;

    // KRX 공휴일 (MMDD 형식, 매년 업데이트 필요)
    // 2025~2026 기준 고정 공휴일 + 주요 대체 공휴일
    private static final Set<String> HOLIDAYS = new HashSet<>(Arrays.asList(
        // 2025
        "0101", // 신정
        "0127", "0128", "0129", "0130", // 설날 연휴
        "0301", // 삼일절
        "0505", // 어린이날
        "0506", // 어린이날 대체
        "0603", // 현충일
        "0815", // 광복절
        "1003", "1004", "1005", "1007", // 추석 연휴 + 개천절
        "1009", // 한글날
        "1225", // 성탄절
        "1231", // 연말 휴장
        // 2026
        "20260101",
        "20260216", "20260217", "20260218", "20260219", // 설날 연휴
        "20260301",
        "20260505",
        "20260606",
        "20260815",
        "20260924", "20260925", "20260926", "20260927", // 추석 연휴
        "20261009",
        "20261225",
        "20261231"
    ));

    public static boolean isRegularMarketOpen() {
        Calendar now = nowInKorea();
        if (!isTradingDay(now)) {
            return false;
        }
        int minutes = minutesOfDay(now);
        return minutes >= OPEN_MINUTES && minutes < CLOSE_MINUTES;
    }

    public static boolean isAfterRegularMarketClose() {
        Calendar now = nowInKorea();
        return isTradingDay(now) && minutesOfDay(now) >= CLOSE_MINUTES;
    }

    public static String statusLabel() {
        Calendar now = nowInKorea();
        if (!isTradingDay(now)) {
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
        if (!isTradingDay(now)) {
            return isWeekday(now) ? "공휴일 휴장" : "평일 09:00 시작";
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

        if (!isTradingDay(nextOpen) || now.getTimeInMillis() >= nextOpen.getTimeInMillis()) {
            do {
                nextOpen.add(Calendar.DAY_OF_MONTH, 1);
                nextOpen.set(Calendar.HOUR_OF_DAY, 9);
                nextOpen.set(Calendar.MINUTE, 0);
                nextOpen.set(Calendar.SECOND, 0);
                nextOpen.set(Calendar.MILLISECOND, 0);
            } while (!isTradingDay(nextOpen));
        }

        return Math.max(60_000L, nextOpen.getTimeInMillis() - now.getTimeInMillis());
    }

    private static boolean isTradingDay(Calendar calendar) {
        return isWeekday(calendar) && !isHoliday(calendar);
    }

    private static boolean isHoliday(Calendar calendar) {
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        String mmdd = String.format("%02d%02d", month, day);
        String yyyymmdd = String.format("%04d%02d%02d", year, month, day);
        return HOLIDAYS.contains(mmdd) || HOLIDAYS.contains(yyyymmdd);
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

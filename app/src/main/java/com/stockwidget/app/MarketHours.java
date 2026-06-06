// 한국 주식시장(KRX) 장 시간 및 공휴일 판별 유틸리티
package com.stockwidget.app;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

public class MarketHours {
    private static final TimeZone KOREA_TIME_ZONE = TimeZone.getTimeZone("Asia/Seoul");
    private static final int PRE_MARKET_OPEN_MINUTES = 8 * 60;       // 장전 시간외 시작 (08:00)
    private static final int OPEN_MINUTES             = 9 * 60;       // 정규장 시작 (09:00)
    private static final int CLOSE_MINUTES            = 15 * 60 + 30; // 정규장 종료 (15:30)
    private static final int NXT_CLOSE_MINUTES        = 20 * 60;      // NXT 시간외 종료 (20:00)

    // KRX 공휴일 목록
    // MMDD: 매년 고정 공휴일 (신정·삼일절·어린이날·현충일·광복절·한글날·성탄절·연말)
    // YYYYMMDD: 연도별 변동 공휴일 (설날·추석 등 음력 기반, 대체공휴일)
    private static final Set<String> HOLIDAYS = new HashSet<>(Arrays.asList(
        // 고정 연간 공휴일 (MMDD)
        "0101", // 신정
        "0301", // 삼일절
        "0505", // 어린이날
        "0606", // 현충일
        "0815", // 광복절
        "1003", // 개천절
        "1009", // 한글날
        "1225", // 성탄절
        "1231", // 연말 휴장
        // 2025 변동 공휴일 (YYYYMMDD)
        "20250127", "20250128", "20250129", "20250130", // 설날 연휴
        "20250506", // 어린이날 대체
        "20251003", "20251004", "20251005", "20251007", // 추석 연휴 + 개천절 대체
        // 2026 변동 공휴일 (YYYYMMDD)
        "20260216", "20260217", "20260218", "20260219", // 설날 연휴
        "20260924", "20260925", "20260926", "20260927"  // 추석 연휴
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

    // 장전 시간외 활성 여부 (08:00~09:00)
    public static boolean isPreMarketActive() {
        Calendar now = nowInKorea();
        if (!isTradingDay(now)) return false;
        int minutes = minutesOfDay(now);
        return minutes >= PRE_MARKET_OPEN_MINUTES && minutes < OPEN_MINUTES;
    }

    // NXT 시간외 거래 활성 여부 (15:30~20:00)
    public static boolean isNxtMarketActive() {
        Calendar now = nowInKorea();
        if (!isTradingDay(now)) return false;
        int minutes = minutesOfDay(now);
        return minutes >= CLOSE_MINUTES && minutes < NXT_CLOSE_MINUTES;
    }

    public static String statusLabel() {
        Calendar now = nowInKorea();
        if (!isTradingDay(now)) {
            return "휴장";
        }
        int minutes = minutesOfDay(now);
        if (minutes < PRE_MARKET_OPEN_MINUTES) {
            return "장 시작 전";
        }
        if (minutes < OPEN_MINUTES) {
            return "장전";
        }
        if (minutes < CLOSE_MINUTES) {
            return "장중";
        }
        if (minutes < NXT_CLOSE_MINUTES) {
            return "NXT";
        }
        return "장 종료";
    }

    public static String detailText() {
        Calendar now = nowInKorea();
        if (!isTradingDay(now)) {
            return isWeekday(now) ? "공휴일 휴장" : "주말 휴장";
        }
        int minutes = minutesOfDay(now);
        if (minutes < PRE_MARKET_OPEN_MINUTES) {
            return "08:00 장전 시작";
        }
        if (minutes < OPEN_MINUTES) {
            return "장전 시간외 08:00~09:00";
        }
        if (minutes < CLOSE_MINUTES) {
            return "정규장 09:00~15:30";
        }
        if (minutes < NXT_CLOSE_MINUTES) {
            return "NXT 시간외 ~20:00";
        }
        return "마감 정보";
    }

    public static String appStatusText() {
        return statusLabel() + " · " + detailText();
    }

    // 다음 거래 세션(장전 08:00) 시작까지 남은 밀리초
    public static long millisUntilNextOpen() {
        Calendar now = nowInKorea();
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.HOUR_OF_DAY, 8);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);

        if (!isTradingDay(next) || now.getTimeInMillis() >= next.getTimeInMillis()) {
            do {
                next.add(Calendar.DAY_OF_MONTH, 1);
                next.set(Calendar.HOUR_OF_DAY, 8);
                next.set(Calendar.MINUTE, 0);
                next.set(Calendar.SECOND, 0);
                next.set(Calendar.MILLISECOND, 0);
            } while (!isTradingDay(next));
        }

        return Math.max(60_000L, next.getTimeInMillis() - now.getTimeInMillis());
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

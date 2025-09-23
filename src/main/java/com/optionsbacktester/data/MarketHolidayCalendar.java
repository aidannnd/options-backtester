package com.optionsbacktester.data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Set;

/**
 * Calendar for US stock market holidays (NYSE/NASDAQ)
 * Handles major holidays when markets are closed
 */
public class MarketHolidayCalendar {

    private static final Set<LocalDate> FIXED_HOLIDAYS_CACHE = new HashSet<>();
    private static int lastCachedYear = -1;

    /**
     * Check if a given date is a US market holiday
     */
    public static boolean isMarketHoliday(LocalDate date) {
        // weekend check
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return true;
        }

        // cache holidays for the current year if needed
        int year = date.getYear();
        if (year != lastCachedYear) {
            FIXED_HOLIDAYS_CACHE.clear();
            FIXED_HOLIDAYS_CACHE.addAll(calculateHolidaysForYear(year));
            lastCachedYear = year;
        }

        return FIXED_HOLIDAYS_CACHE.contains(date);
    }

    /**
     * Check if a date is a valid trading day (not weekend, not holiday)
     */
    public static boolean isTradingDay(LocalDate date) {
        return !isMarketHoliday(date);
    }

    /**
     * Get the next trading day (skipping weekends and holidays)
     */
    public static LocalDate getNextTradingDay(LocalDate date) {
        LocalDate nextDay = date.plusDays(1);
        while (isMarketHoliday(nextDay)) {
            nextDay = nextDay.plusDays(1);
        }
        return nextDay;
    }

    /**
     * Get the previous trading day (skipping weekends and holidays)
     */
    public static LocalDate getPreviousTradingDay(LocalDate date) {
        LocalDate prevDay = date.minusDays(1);
        while (isMarketHoliday(prevDay)) {
            prevDay = prevDay.minusDays(1);
        }
        return prevDay;
    }

    private static Set<LocalDate> calculateHolidaysForYear(int year) {
        Set<LocalDate> holidays = new HashSet<>();

        // new Year's Day (observed rules apply)
        LocalDate newYears = LocalDate.of(year, Month.JANUARY, 1);
        holidays.add(observedHoliday(newYears));

        // martin Luther King Jr. Day (3rd Monday in January)
        LocalDate mlkDay = LocalDate.of(year, Month.JANUARY, 1)
                .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
                .plusWeeks(2);
        holidays.add(mlkDay);

        // presidents' Day (3rd Monday in February)
        LocalDate presidentsDay = LocalDate.of(year, Month.FEBRUARY, 1)
                .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
                .plusWeeks(2);
        holidays.add(presidentsDay);

        // good Friday (calculated from Easter)
        LocalDate goodFriday = calculateGoodFriday(year);
        holidays.add(goodFriday);

        // memorial Day (last Monday in May)
        LocalDate memorialDay = LocalDate.of(year, Month.MAY, 31)
                .with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY));
        holidays.add(memorialDay);

        // juneteenth (June 19th, observed since 2021)
        if (year >= 2021) {
            LocalDate juneteenth = LocalDate.of(year, Month.JUNE, 19);
            holidays.add(observedHoliday(juneteenth));
        }

        // independence Day (July 4th)
        LocalDate independenceDay = LocalDate.of(year, Month.JULY, 4);
        holidays.add(observedHoliday(independenceDay));

        // labor Day (1st Monday in September)
        LocalDate laborDay = LocalDate.of(year, Month.SEPTEMBER, 1)
                .with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        holidays.add(laborDay);

        // thanksgiving (4th Thursday in November)
        LocalDate thanksgiving = LocalDate.of(year, Month.NOVEMBER, 1)
                .with(TemporalAdjusters.firstInMonth(DayOfWeek.THURSDAY))
                .plusWeeks(3);
        holidays.add(thanksgiving);

        // christmas Day (December 25th)
        LocalDate christmas = LocalDate.of(year, Month.DECEMBER, 25);
        holidays.add(observedHoliday(christmas));

        return holidays;
    }

    /**
     * Apply observed holiday rules:
     * - If holiday falls on Saturday, observe on Friday
     * - If holiday falls on Sunday, observe on Monday
     */
    private static LocalDate observedHoliday(LocalDate holiday) {
        DayOfWeek dayOfWeek = holiday.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY) {
            return holiday.minusDays(1); // Friday
        } else if (dayOfWeek == DayOfWeek.SUNDAY) {
            return holiday.plusDays(1); // Monday
        }
        return holiday;
    }

    /**
     * Calculate Good Friday for a given year
     * Based on Easter Sunday calculation
     */
    private static LocalDate calculateGoodFriday(int year) {
        LocalDate easter = calculateEaster(year);
        return easter.minusDays(2); // Friday before Easter
    }

    /**
     * Calculate Easter Sunday using the Western Christian algorithm
     */
    private static LocalDate calculateEaster(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;

        return LocalDate.of(year, month, day);
    }
}
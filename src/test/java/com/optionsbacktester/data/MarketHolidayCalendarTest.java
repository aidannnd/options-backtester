package com.optionsbacktester.data;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MarketHolidayCalendarTest {

    @Test
    void shouldIdentifyWeekendsAsHolidays() {
        LocalDate saturday = LocalDate.of(2024, 1, 6);
        LocalDate sunday = LocalDate.of(2024, 1, 7);

        assertThat(MarketHolidayCalendar.isMarketHoliday(saturday)).isTrue();
        assertThat(MarketHolidayCalendar.isMarketHoliday(sunday)).isTrue();
        assertThat(MarketHolidayCalendar.isTradingDay(saturday)).isFalse();
        assertThat(MarketHolidayCalendar.isTradingDay(sunday)).isFalse();
    }

    @Test
    void shouldIdentifyMajorHolidays2024() {
        // New Year's Day (January 1, 2024 - Monday)
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2024, 1, 1))).isTrue();

        // MLK Day (January 15, 2024 - 3rd Monday in January)
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2024, 1, 15))).isTrue();

        // Presidents Day (February 19, 2024 - 3rd Monday in February)
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2024, 2, 19))).isTrue();

        // Good Friday (March 29, 2024)
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2024, 3, 29))).isTrue();

        // Memorial Day (May 27, 2024 - last Monday in May)
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2024, 5, 27))).isTrue();

        // Juneteenth (June 19, 2024)
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2024, 6, 19))).isTrue();

        // Independence Day (July 4, 2024)
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2024, 7, 4))).isTrue();

        // Labor Day (September 2, 2024 - 1st Monday in September)
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2024, 9, 2))).isTrue();

        // Thanksgiving (November 28, 2024 - 4th Thursday in November)
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2024, 11, 28))).isTrue();

        // Christmas (December 25, 2024)
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2024, 12, 25))).isTrue();
    }

    @Test
    void shouldIdentifyRegularTradingDays() {
        // Regular weekdays that are not holidays
        LocalDate tuesday = LocalDate.of(2024, 1, 2);
        LocalDate wednesday = LocalDate.of(2024, 1, 3);
        LocalDate thursday = LocalDate.of(2024, 1, 4);

        assertThat(MarketHolidayCalendar.isMarketHoliday(tuesday)).isFalse();
        assertThat(MarketHolidayCalendar.isMarketHoliday(wednesday)).isFalse();
        assertThat(MarketHolidayCalendar.isMarketHoliday(thursday)).isFalse();

        assertThat(MarketHolidayCalendar.isTradingDay(tuesday)).isTrue();
        assertThat(MarketHolidayCalendar.isTradingDay(wednesday)).isTrue();
        assertThat(MarketHolidayCalendar.isTradingDay(thursday)).isTrue();
    }

    @Test
    void shouldHandleObservedHolidays() {
        // Test when holiday falls on weekend and is observed on different day
        // Independence Day 2021 fell on Sunday, observed on Monday July 5th
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2021, 7, 4))).isTrue(); // Sunday
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2021, 7, 5))).isTrue(); // Observed Monday

        // Christmas 2021 fell on Saturday, observed on Friday December 24th
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2021, 12, 25))).isTrue(); // Saturday
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2021, 12, 24))).isTrue(); // Observed Friday
    }

    @Test
    void shouldGetNextTradingDay() {
        // Friday January 12, 2024 -> next trading day should skip MLK Day
        LocalDate friday = LocalDate.of(2024, 1, 12);
        LocalDate nextTrading = MarketHolidayCalendar.getNextTradingDay(friday);

        // Should skip weekend (13th, 14th) and MLK Day (15th), go to Tuesday 16th
        assertThat(nextTrading).isEqualTo(LocalDate.of(2024, 1, 16));
    }

    @Test
    void shouldGetPreviousTradingDay() {
        // Tuesday January 16, 2024 -> previous trading day should skip MLK Day
        LocalDate tuesday = LocalDate.of(2024, 1, 16);
        LocalDate prevTrading = MarketHolidayCalendar.getPreviousTradingDay(tuesday);

        // Should skip MLK Day (15th) and weekend (13th, 14th), go to Friday 12th
        assertThat(prevTrading).isEqualTo(LocalDate.of(2024, 1, 12));
    }

    @Test
    void shouldHandleJuneteenthStartingFrom2021() {
        // Juneteenth should not be a holiday before 2021
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2020, 6, 19))).isFalse();

        // Juneteenth should be a holiday from 2021 onwards
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2021, 6, 19))).isTrue();
        assertThat(MarketHolidayCalendar.isMarketHoliday(LocalDate.of(2024, 6, 19))).isTrue();
    }
}
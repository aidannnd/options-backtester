package com.optionsbacktester.data;

import com.optionsbacktester.model.MarketData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvDataProviderTest {

    private CsvDataProvider dataProvider;

    @BeforeEach
    void setUp() {
        dataProvider = new CsvDataProvider();
    }

    @Test
    void shouldLoadDataFromCsvFile() {
        LocalDate startDate = LocalDate.of(2024, 1, 2);
        LocalDate endDate = LocalDate.of(2024, 1, 5);

        List<MarketData> data = dataProvider.getMarketData("SPY", startDate, endDate);

        assertThat(data).hasSize(4);
        assertThat(data.get(0).getSymbol()).isEqualTo("SPY");
        assertThat(data.get(0).getPrice()).isEqualTo(new BigDecimal("476.28"));
        assertThat(data.get(0).getTimestamp().toLocalDate()).isEqualTo(LocalDate.of(2024, 1, 2));
    }

    @Test
    void shouldCalculateBidAskFromPrice() {
        LocalDate startDate = LocalDate.of(2024, 1, 2);
        LocalDate endDate = LocalDate.of(2024, 1, 2);

        List<MarketData> data = dataProvider.getMarketData("SPY", startDate, endDate);

        MarketData marketData = data.get(0);
        BigDecimal price = marketData.getPrice();
        BigDecimal bid = marketData.getBid();
        BigDecimal ask = marketData.getAsk();

        // bid should be slightly less than price, ask should be slightly more
        assertThat(bid).isLessThan(price);
        assertThat(ask).isGreaterThan(price);
        assertThat(marketData.getSpread()).isPositive();
    }

    @Test
    void shouldThrowExceptionForMissingSymbol() {
        LocalDate startDate = LocalDate.of(2024, 1, 2);
        LocalDate endDate = LocalDate.of(2024, 1, 5);

        assertThatThrownBy(() -> dataProvider.getMarketData("NONEXISTENT", startDate, endDate))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("CSV file not found in resources");
    }

    @Test
    void shouldHandleMarketHolidays() {
        LocalDate startDate = LocalDate.of(2024, 1, 2);
        LocalDate endDate = LocalDate.of(2024, 1, 16); // includes MLK Day (Jan 15)

        // this should NOT throw an exception because Jan 15 is MLK Day (market holiday)
        // the holiday calendar should recognize it as a non-trading day
        List<MarketData> data = dataProvider.getMarketData("SPY", startDate, endDate);

        // should get data for trading days only, excluding MLK Day
        assertThat(data).isNotEmpty();

        // verify no data for MLK Day (Jan 15)
        boolean hasDataForMLKDay = data.stream()
            .anyMatch(md -> md.getTimestamp().toLocalDate().equals(LocalDate.of(2024, 1, 15)));
        assertThat(hasDataForMLKDay).isFalse();
    }

    @Test
    void shouldThrowExceptionForMissingTradingDays() {
        // create a date range that includes a real trading day not in our CSV
        LocalDate startDate = LocalDate.of(2024, 2, 1); // February 1st - should be a trading day
        LocalDate endDate = LocalDate.of(2024, 2, 1);

        // this should throw an exception because Feb 1 is a real trading day missing from our CSV
        assertThatThrownBy(() -> dataProvider.getMarketData("SPY", startDate, endDate))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing data for dates");
    }

    @Test
    void shouldCheckIfSymbolDataExists() {
        assertThat(dataProvider.hasDataForSymbol("SPY")).isTrue();
        assertThat(dataProvider.hasDataForSymbol("NONEXISTENT")).isFalse();
    }
}
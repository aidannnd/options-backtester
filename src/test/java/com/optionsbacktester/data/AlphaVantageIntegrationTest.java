package com.optionsbacktester.data;

import com.optionsbacktester.model.MarketData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Alpha Vantage API - requires internet connection and valid API key
 * Run manually to test real API connectivity
 */
class AlphaVantageIntegrationTest {

    @Test
    void testRealApiCall() {
        AlphaVantageDataProvider provider = new AlphaVantageDataProvider();

        // Get recent data (last few days)
        LocalDate endDate = LocalDate.now().minusDays(1); // Yesterday
        LocalDate startDate = endDate.minusDays(2); // 3 days of data

        List<MarketData> data = provider.getMarketData("SPY", startDate, endDate);

        assertThat(data).isNotEmpty();
        assertThat(data.get(0).getSymbol()).isEqualTo("SPY");
        assertThat(data.get(0).getPrice()).isPositive();

        System.out.println("Successfully retrieved " + data.size() + " data points:");
        data.forEach(d -> System.out.println(d.getTimestamp().toLocalDate() + ": $" + d.getPrice()));
    }
}
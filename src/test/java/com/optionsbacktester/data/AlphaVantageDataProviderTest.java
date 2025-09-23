package com.optionsbacktester.data;

import com.optionsbacktester.model.MarketData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AlphaVantageDataProviderTest {

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockResponse;

    private AlphaVantageDataProvider dataProvider;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dataProvider = new AlphaVantageDataProvider(mockHttpClient, new BigDecimal("0.001"), "test-api-key");
    }

    @Test
    void shouldParseValidApiResponse() throws IOException, InterruptedException {
        String validApiResponse = """
            {
                "Meta Data": {
                    "1. Information": "Daily Prices (open, high, low, close) and Volumes",
                    "2. Symbol": "SPY",
                    "3. Last Refreshed": "2024-01-05",
                    "4. Output Size": "Full size",
                    "5. Time Zone": "US/Eastern"
                },
                "Time Series (Daily)": {
                    "2024-01-05": {
                        "1. open": "478.90",
                        "2. high": "480.25",
                        "3. low": "478.45",
                        "4. close": "479.58",
                        "5. volume": "41245600"
                    },
                    "2024-01-04": {
                        "1. open": "477.00",
                        "2. high": "478.45",
                        "3. low": "476.22",
                        "4. close": "477.89",
                        "5. volume": "42156800"
                    }
                }
            }
            """;

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(validApiResponse);
        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        LocalDate startDate = LocalDate.of(2024, 1, 4);
        LocalDate endDate = LocalDate.of(2024, 1, 5);

        List<MarketData> data = dataProvider.getMarketData("SPY", startDate, endDate);

        assertThat(data).hasSize(2);

        MarketData firstDay = data.get(0); // 2024-01-04 (sorted by date)
        assertThat(firstDay.getSymbol()).isEqualTo("SPY");
        assertThat(firstDay.getPrice()).isEqualTo(new BigDecimal("477.89"));
        assertThat(firstDay.getTimestamp().toLocalDate()).isEqualTo(LocalDate.of(2024, 1, 4));

        // test bid/ask calculation with 0.1% spread
        BigDecimal price = firstDay.getPrice();
        assertThat(firstDay.getBid()).isLessThan(price);
        assertThat(firstDay.getAsk()).isGreaterThan(price);

        // check that spread is approximately 0.1% of price (allowing for rounding)
        BigDecimal expectedSpread = price.multiply(new BigDecimal("0.001"));
        BigDecimal actualSpread = firstDay.getSpread();
        assertThat(actualSpread).isCloseTo(expectedSpread, org.assertj.core.data.Percentage.withPercentage(1));
    }

    @Test
    void shouldThrowExceptionForApiError() throws IOException, InterruptedException {
        String errorResponse = """
            {
                "Error Message": "Invalid API call. Please retry or visit the documentation for TIME_SERIES_DAILY."
            }
            """;

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(errorResponse);
        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        LocalDate startDate = LocalDate.of(2024, 1, 4);
        LocalDate endDate = LocalDate.of(2024, 1, 5);

        assertThatThrownBy(() -> dataProvider.getMarketData("INVALID", startDate, endDate))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("API Error");
    }

    @Test
    void shouldThrowExceptionForRateLimit() throws IOException, InterruptedException {
        String rateLimitResponse = """
            {
                "Note": "Thank you for using Alpha Vantage! Our standard API call frequency is 5 calls per minute and 500 calls per day."
            }
            """;

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(rateLimitResponse);
        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        LocalDate startDate = LocalDate.of(2024, 1, 4);
        LocalDate endDate = LocalDate.of(2024, 1, 5);

        assertThatThrownBy(() -> dataProvider.getMarketData("SPY", startDate, endDate))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("API Rate Limit");
    }

    @Test
    void shouldThrowExceptionForHttpError() throws IOException, InterruptedException {
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        LocalDate startDate = LocalDate.of(2024, 1, 4);
        LocalDate endDate = LocalDate.of(2024, 1, 5);

        assertThatThrownBy(() -> dataProvider.getMarketData("SPY", startDate, endDate))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("API request failed with status: 500");
    }

    @Test
    void shouldThrowExceptionForMissingDates() throws IOException, InterruptedException {
        String responseWithMissingDate = """
            {
                "Meta Data": {
                    "2. Symbol": "SPY"
                },
                "Time Series (Daily)": {
                    "2024-01-04": {
                        "1. open": "477.00",
                        "2. high": "478.45",
                        "3. low": "476.22",
                        "4. close": "477.89",
                        "5. volume": "42156800"
                    }
                }
            }
            """;

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(responseWithMissingDate);
        when(mockHttpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenReturn(mockResponse);

        LocalDate startDate = LocalDate.of(2024, 1, 4);
        LocalDate endDate = LocalDate.of(2024, 1, 5); // Jan 5 is missing

        assertThatThrownBy(() -> dataProvider.getMarketData("SPY", startDate, endDate))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing data for dates");
    }
}
package com.optionsbacktester.data;

import com.optionsbacktester.model.MarketData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AlphaVantageDataProvider implements DataProvider {
    private static final Logger logger = LoggerFactory.getLogger(AlphaVantageDataProvider.class);
    private static final String BASE_URL = "https://www.alphavantage.co/query";
    private static final BigDecimal DEFAULT_SPREAD_PERCENTAGE = new BigDecimal("0.0005"); // 0.05%

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final BigDecimal spreadPercentage;
    private final String apiKey;

    public AlphaVantageDataProvider() {
        this(HttpClient.newHttpClient(), DEFAULT_SPREAD_PERCENTAGE);
    }

    public AlphaVantageDataProvider(BigDecimal spreadPercentage) {
        this(HttpClient.newHttpClient(), spreadPercentage);
    }

    public AlphaVantageDataProvider(HttpClient httpClient, BigDecimal spreadPercentage) {
        this(httpClient, spreadPercentage, getApiKeyFromEnvironment());
    }

    public AlphaVantageDataProvider(HttpClient httpClient, BigDecimal spreadPercentage, String apiKey) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.spreadPercentage = spreadPercentage;
        this.apiKey = apiKey;

        if (this.apiKey == null || this.apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Alpha Vantage API key is required. " +
                "Set the ALPHA_VANTAGE_API_KEY environment variable or " +
                "alpha.vantage.api.key system property, or provide it directly.");
        }
    }

    @Override
    public List<MarketData> getMarketData(LocalDate startDate, LocalDate endDate) {
        return getMarketData("SPY", startDate, endDate);
    }

    @Override
    public List<MarketData> getMarketData(String symbol, LocalDate startDate, LocalDate endDate) {
        try {
            String url = buildApiUrl(symbol);
            logger.info("Fetching data for {} from {} to {}", symbol, startDate, endDate);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("API request failed with status: " + response.statusCode());
            }

            return parseApiResponse(response.body(), symbol, startDate, endDate);

        } catch (IOException | InterruptedException e) {
            logger.error("Failed to fetch data for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to fetch market data for " + symbol, e);
        }
    }

    private String buildApiUrl(String symbol) {
        return String.format("%s?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s&outputsize=full",
                BASE_URL, symbol, apiKey);
    }

    private static String getApiKeyFromEnvironment() {
        String apiKey = System.getenv("ALPHA_VANTAGE_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = System.getProperty("alpha.vantage.api.key");
        }
        return apiKey;
    }

    private List<MarketData> parseApiResponse(String responseBody, String symbol,
                                            LocalDate startDate, LocalDate endDate) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

        // Check for API errors
        if (root.has("Error Message")) {
            throw new RuntimeException("API Error: " + root.get("Error Message").asText());
        }

        if (root.has("Note")) {
            throw new RuntimeException("API Rate Limit: " + root.get("Note").asText());
        }

        JsonNode timeSeries = root.get("Time Series (Daily)");
        if (timeSeries == null) {
            throw new RuntimeException("No time series data found in API response");
        }

        Map<LocalDate, MarketData> dataMap = new TreeMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        timeSeries.fields().forEachRemaining(entry -> {
            try {
                LocalDate date = LocalDate.parse(entry.getKey(), formatter);
                if (!date.isBefore(startDate) && !date.isAfter(endDate)) {
                    JsonNode dayData = entry.getValue();
                    MarketData marketData = createMarketData(symbol, date, dayData);
                    dataMap.put(date, marketData);
                }
            } catch (Exception e) {
                logger.warn("Failed to parse data for date: {}", entry.getKey(), e);
            }
        });

        // Check for missing dates and report them
        List<LocalDate> missingDates = findMissingDates(dataMap, startDate, endDate);
        if (!missingDates.isEmpty()) {
            throw new RuntimeException("Missing data for dates: " + missingDates);
        }

        return new ArrayList<>(dataMap.values());
    }

    private MarketData createMarketData(String symbol, LocalDate date, JsonNode dayData) {
        BigDecimal open = new BigDecimal(dayData.get("1. open").asText());
        BigDecimal high = new BigDecimal(dayData.get("2. high").asText());
        BigDecimal low = new BigDecimal(dayData.get("3. low").asText());
        BigDecimal close = new BigDecimal(dayData.get("4. close").asText());
        long volume = dayData.get("5. volume").asLong();

        // Calculate bid/ask from close price with spread
        BigDecimal halfSpread = close.multiply(spreadPercentage).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        BigDecimal bid = close.subtract(halfSpread);
        BigDecimal ask = close.add(halfSpread);

        LocalDateTime timestamp = date.atStartOfDay();

        return new MarketData(symbol, timestamp, close, bid, ask, volume);
    }

    private List<LocalDate> findMissingDates(Map<LocalDate, MarketData> dataMap,
                                           LocalDate startDate, LocalDate endDate) {
        List<LocalDate> missingDates = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            // Only check for trading days (exclude weekends and holidays)
            if (MarketHolidayCalendar.isTradingDay(current) && !dataMap.containsKey(current)) {
                missingDates.add(current);
            }
            current = current.plusDays(1);
        }

        return missingDates;
    }
}
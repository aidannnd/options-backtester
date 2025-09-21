package com.optionsbacktester.data;

import com.optionsbacktester.model.MarketData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CsvDataProvider implements DataProvider {
    private static final Logger logger = LoggerFactory.getLogger(CsvDataProvider.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final BigDecimal DEFAULT_SPREAD_PERCENTAGE = new BigDecimal("0.0005"); // 0.05%

    private final BigDecimal spreadPercentage;

    public CsvDataProvider() {
        this(DEFAULT_SPREAD_PERCENTAGE);
    }

    public CsvDataProvider(BigDecimal spreadPercentage) {
        this.spreadPercentage = spreadPercentage;
    }

    @Override
    public List<MarketData> getMarketData(LocalDate startDate, LocalDate endDate) {
        return getMarketData("SPY", startDate, endDate);
    }

    @Override
    public List<MarketData> getMarketData(String symbol, LocalDate startDate, LocalDate endDate) {
        String csvFileName = symbol + ".csv";

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(csvFileName)) {
            if (inputStream == null) {
                throw new RuntimeException("CSV file not found in resources: " + csvFileName);
            }

            return loadDataFromCsv(inputStream, symbol, startDate, endDate);
        } catch (IOException e) {
            logger.error("Error reading CSV file for symbol: {}", symbol, e);
            throw new RuntimeException("Failed to load data for symbol: " + symbol, e);
        }
    }

    private List<MarketData> loadDataFromCsv(InputStream inputStream, String symbol,
                                           LocalDate startDate, LocalDate endDate) throws IOException {
        Map<LocalDate, MarketData> dataMap = new TreeMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new RuntimeException("Empty CSV file for symbol: " + symbol);
            }

            validateCsvHeader(headerLine);

            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    MarketData marketData = parseMarketDataLine(line, symbol);
                    LocalDate dataDate = marketData.getTimestamp().toLocalDate();

                    if (!dataDate.isBefore(startDate) && !dataDate.isAfter(endDate)) {
                        dataMap.put(dataDate, marketData);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse line: {}", line, e);
                }
            }
        }

        // Check for missing dates and report them
        List<LocalDate> missingDates = findMissingDates(dataMap, startDate, endDate);
        if (!missingDates.isEmpty()) {
            throw new RuntimeException("Missing data for dates: " + missingDates);
        }

        return new ArrayList<>(dataMap.values());
    }

    private void validateCsvHeader(String headerLine) {
        String[] expectedHeaders = {"Date", "Open", "High", "Low", "Close", "Volume"};
        String[] actualHeaders = headerLine.split(",");

        if (actualHeaders.length < expectedHeaders.length) {
            throw new RuntimeException("Invalid CSV format - expected headers: Date,Open,High,Low,Close,Volume");
        }

        for (int i = 0; i < expectedHeaders.length; i++) {
            if (!actualHeaders[i].trim().equalsIgnoreCase(expectedHeaders[i])) {
                logger.warn("Header mismatch at position {}: expected '{}', got '{}'",
                           i, expectedHeaders[i], actualHeaders[i].trim());
            }
        }
    }

    private MarketData parseMarketDataLine(String line, String symbol) {
        String[] fields = line.split(",");

        if (fields.length < 6) {
            throw new IllegalArgumentException("Invalid CSV format - expected at least 6 fields, got " + fields.length);
        }

        LocalDate date = LocalDate.parse(fields[0].trim(), DATE_FORMATTER);
        LocalDateTime timestamp = date.atStartOfDay();

        BigDecimal open = new BigDecimal(fields[1].trim());
        BigDecimal high = new BigDecimal(fields[2].trim());
        BigDecimal low = new BigDecimal(fields[3].trim());
        BigDecimal close = new BigDecimal(fields[4].trim());
        long volume = Long.parseLong(fields[5].trim());

        // Calculate bid/ask from close price with spread
        BigDecimal halfSpread = close.multiply(spreadPercentage).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        BigDecimal bid = close.subtract(halfSpread);
        BigDecimal ask = close.add(halfSpread);

        return new MarketData(symbol, timestamp, close, bid, ask, volume);
    }

    private List<LocalDate> findMissingDates(Map<LocalDate, MarketData> dataMap,
                                           LocalDate startDate, LocalDate endDate) {
        List<LocalDate> missingDates = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            // Only check for weekdays (markets are closed on weekends)
            if (current.getDayOfWeek().getValue() <= 5 && !dataMap.containsKey(current)) {
                missingDates.add(current);
            }
            current = current.plusDays(1);
        }

        return missingDates;
    }

    public boolean hasDataForSymbol(String symbol) {
        String csvFileName = symbol + ".csv";
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(csvFileName);
        if (inputStream != null) {
            try {
                inputStream.close();
                return true;
            } catch (IOException e) {
                logger.warn("Error checking for symbol data: {}", symbol, e);
            }
        }
        return false;
    }
}
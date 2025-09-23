package com.optionsbacktester;

import com.optionsbacktester.backtester.BacktestEngine;
import com.optionsbacktester.backtester.BacktestResult;
import com.optionsbacktester.data.AlphaVantageDataProvider;
import com.optionsbacktester.data.CsvDataProvider;
import com.optionsbacktester.data.DataProvider;
import com.optionsbacktester.model.MarketData;
import com.optionsbacktester.strategy.BuyAndHoldStrategy;
import com.optionsbacktester.strategy.CoveredCallStrategy;
import com.optionsbacktester.strategy.ProtectivePutStrategy;
import com.optionsbacktester.strategy.LongStraddleStrategy;
import com.optionsbacktester.strategy.OptionsStrategy;
import com.optionsbacktester.util.CapitalRequirementChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        logger.info("Options Backtester starting...");

        if (args.length > 0 && "demo".equals(args[0])) {
            runDemo();
        } else {
            runInteractiveCli();
        }
    }

    private static void runDemo() {
        System.out.println("=== Options Backtester Demo ===");
        System.out.println();

        try {
            // demo configuration - use higher capital to ensure all strategies can execute
            String symbol = "SPY";
            LocalDate startDate = LocalDate.of(2024, 1, 2);
            LocalDate endDate = LocalDate.of(2024, 1, 30);
            BigDecimal initialCapital = new BigDecimal("50000"); // Increased to accommodate all strategies

            System.out.println("Running demo with:");
            System.out.println("Symbol: " + symbol);
            System.out.println("Period: " + startDate + " to " + endDate);
            System.out.println("Initial Capital: $" + initialCapital);
            System.out.println();

            // use CSV data provider for demo
            DataProvider dataProvider = new CsvDataProvider();

            // get first market data point to determine stock price for adaptive strike offsets
            List<MarketData> sampleData = dataProvider.getMarketData(symbol, startDate, startDate);
            BigDecimal stockPrice = sampleData.get(0).getPrice();

            // calculate strike offset as percentage of stock price (5% for reasonable OTM options)
            BigDecimal strikeOffsetPercentage = new BigDecimal("0.05"); // 5%
            BigDecimal strikeOffset = stockPrice.multiply(strikeOffsetPercentage);

            // ensure minimum $1 offset and round to reasonable increments
            strikeOffset = strikeOffset.max(new BigDecimal("1.00"));

            System.out.println("Stock Price: $" + stockPrice + ", Strike Offset: $" + strikeOffset);

            OptionsStrategy[] strategies = {
                new BuyAndHoldStrategy(symbol, 500, endDate), // Allow up to 500 shares
                new CoveredCallStrategy(symbol, 30, strikeOffset, 500), // Price-based strike offset
                new ProtectivePutStrategy(symbol, 30, strikeOffset, 500), // Price-based strike offset
                new LongStraddleStrategy(symbol, 30, 500, new BigDecimal("50")) // Allow up to 500 contracts
            };

            for (OptionsStrategy strategy : strategies) {
                System.out.println("=== " + strategy.getName() + " ===");
                try {
                    BacktestEngine engine = new BacktestEngine(dataProvider, strategy, initialCapital);
                    BacktestResult result = engine.runBacktest(symbol, startDate, endDate);
                    printResults(result);
                    System.out.println();
                } catch (Exception e) {
                    System.out.println("Failed to run strategy: " + e.getMessage());
                    System.out.println();
                }
            }

        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            logger.error("Demo execution failed", e);
        }
    }

    private static void runInteractiveCli() {
        System.out.println("=== Options Backtester ===");
        System.out.println("Interactive Command Line Interface");
        System.out.println();

        try {
            // get user inputs
            String symbol = getSymbolInput();
            LocalDate[] dateRange = getDateRangeInput();
            BigDecimal initialCapital = getCapitalInput();
            DataProvider dataProvider = getDataProviderInput();
            OptionsStrategy strategy = getStrategyInput(symbol, dateRange[1]);

            // check capital requirements and prompt user if insufficient
            BigDecimal adjustedCapital = CapitalRequirementChecker.checkAndPromptForCapital(
                    strategy, symbol, initialCapital, dataProvider, dateRange[0], scanner);

            if (adjustedCapital == null) {
                System.out.println("Backtest cancelled.");
                return;
            }

            System.out.println();
            System.out.println("Final Configuration:");
            System.out.println("Symbol: " + symbol);
            System.out.println("Date Range: " + dateRange[0] + " to " + dateRange[1]);
            System.out.println("Capital: $" + adjustedCapital);
            System.out.println("Strategy: " + strategy.getName());

            System.out.println();
            System.out.println("Running backtest...");

            // run the backtest with adjusted capital
            BacktestEngine engine = new BacktestEngine(dataProvider, strategy, adjustedCapital);
            BacktestResult result = engine.runBacktest(symbol, dateRange[0], dateRange[1]);

            System.out.println();
            System.out.println("=== BACKTEST RESULTS ===");
            printResults(result);

        } catch (Exception e) {
            System.err.println("Backtest failed: " + e.getMessage());
            logger.error("Interactive CLI execution failed", e);
        } finally {
            scanner.close();
        }
    }

    private static String getSymbolInput() {
        System.out.print("Enter symbol (default: SPY): ");
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? "SPY" : input.toUpperCase();
    }

    private static LocalDate[] getDateRangeInput() {
        LocalDate startDate, endDate;

        while (true) {
            try {
                System.out.print("Enter start date (YYYY-MM-DD, default: 2024-01-02): ");
                String startInput = scanner.nextLine().trim();
                startDate = startInput.isEmpty() ?
                    LocalDate.of(2024, 1, 2) :
                    LocalDate.parse(startInput, DATE_FORMATTER);
                break;
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date format. Please use YYYY-MM-DD");
            }
        }

        while (true) {
            try {
                System.out.print("Enter end date (YYYY-MM-DD, default: 2024-01-30): ");
                String endInput = scanner.nextLine().trim();
                endDate = endInput.isEmpty() ?
                    LocalDate.of(2024, 1, 30) :
                    LocalDate.parse(endInput, DATE_FORMATTER);

                if (endDate.isBefore(startDate)) {
                    System.out.println("End date must be after start date");
                    continue;
                }
                break;
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date format. Please use YYYY-MM-DD");
            }
        }

        return new LocalDate[]{startDate, endDate};
    }

    private static BigDecimal getCapitalInput() {
        while (true) {
            try {
                System.out.print("Enter initial capital (default: 10000): ");
                String input = scanner.nextLine().trim();
                return input.isEmpty() ?
                    new BigDecimal("10000") :
                    new BigDecimal(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number format");
            }
        }
    }

    private static DataProvider getDataProviderInput() {
        System.out.println();
        System.out.println("Data Provider Options:");
        System.out.println("1. CSV (uses local test data)");
        System.out.println("2. Alpha Vantage API (requires internet)");

        while (true) {
            System.out.print("Choose data provider (1-2, default: 1): ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty() || "1".equals(input)) {
                return new CsvDataProvider();
            } else if ("2".equals(input)) {
                try {
                    return new AlphaVantageDataProvider();
                } catch (IllegalArgumentException e) {
                    System.out.println("Error: " + e.getMessage());
                    System.out.println("Please set your Alpha Vantage API key:");
                    System.out.println("  - Environment variable: ALPHA_VANTAGE_API_KEY=your_key_here");
                    System.out.println("  - System property: -Dalpha.vantage.api.key=your_key_here");
                    System.out.println("Falling back to CSV data provider.");
                    return new CsvDataProvider();
                }
            } else {
                System.out.println("Invalid choice. Please enter 1 or 2");
            }
        }
    }

    private static OptionsStrategy getStrategyInput(String symbol, LocalDate endDate) {
        System.out.println();
        System.out.println("Strategy Options:");
        System.out.println("1. Buy and Hold");
        System.out.println("2. Covered Call");
        System.out.println("3. Protective Put");
        System.out.println("4. Long Straddle");

        while (true) {
            System.out.print("Choose strategy (1-4, default: 1): ");
            String input = scanner.nextLine().trim();

            switch (input.isEmpty() ? "1" : input) {
                case "1":
                    return new BuyAndHoldStrategy(symbol.toUpperCase(), 500, endDate); // Allow up to 500 shares
                case "2":
                    BigDecimal coveredCallOffset = calculateDynamicStrikeOffset(symbol);
                    return new CoveredCallStrategy(symbol.toUpperCase(), 30, coveredCallOffset, 500);
                case "3":
                    BigDecimal protectivePutOffset = calculateDynamicStrikeOffset(symbol);
                    return new ProtectivePutStrategy(symbol.toUpperCase(), 30, protectivePutOffset, 500);
                case "4":
                    return new LongStraddleStrategy(symbol.toUpperCase(), 30, 500, new BigDecimal("50"));
                default:
                    System.out.println("Invalid choice. Please enter 1-4");
            }
        }
    }

    private static BigDecimal calculateDynamicStrikeOffset(String symbol) {
        try {
            // use CSV data provider to get sample price
            DataProvider dataProvider = new CsvDataProvider();
            LocalDate sampleDate = LocalDate.of(2024, 1, 2);
            List<MarketData> sampleData = dataProvider.getMarketData(symbol, sampleDate, sampleDate);
            BigDecimal stockPrice = sampleData.get(0).getPrice();

            // calculate 5% offset with $1 minimum
            BigDecimal strikeOffsetPercentage = new BigDecimal("0.05");
            BigDecimal strikeOffset = stockPrice.multiply(strikeOffsetPercentage);
            return strikeOffset.max(new BigDecimal("1.00"));
        } catch (Exception e) {
            // fallback to reasonable default
            return new BigDecimal("5.00");
        }
    }

    private static void printResults(BacktestResult result) {
        System.out.println("Strategy: " + result.getStrategyName());
        System.out.println("Period: " + result.getStartDate() + " to " + result.getEndDate());
        System.out.println("Initial Capital: $" + result.getInitialCapital());
        System.out.println("Final Value: $" + result.getFinalValue());
        System.out.println("Total Return: $" + result.getTotalReturn());
        System.out.println("Return Percentage: " + String.format("%.2f%%", result.getReturnPercentage().doubleValue()));
        System.out.println("Number of Trades: " + result.getTotalTrades());
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar options-backtester.jar          # Interactive mode");
        System.out.println("  java -jar options-backtester.jar demo     # Demo mode");
        System.out.println();
        System.out.println("Demo mode runs all strategies with sample data for comparison.");
        System.out.println("Interactive mode allows you to configure all parameters.");
    }
}
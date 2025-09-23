package com.optionsbacktester.util;

import com.optionsbacktester.data.DataProvider;
import com.optionsbacktester.model.MarketData;
import com.optionsbacktester.strategy.OptionsStrategy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class CapitalRequirementChecker {

    public static class CapitalCheckResult {
        private final boolean sufficient;
        private final BigDecimal required;
        private final BigDecimal available;
        private final String reason;

        public CapitalCheckResult(boolean sufficient, BigDecimal required, BigDecimal available, String reason) {
            this.sufficient = sufficient;
            this.required = required;
            this.available = available;
            this.reason = reason;
        }

        public boolean isSufficient() { return sufficient; }
        public BigDecimal getRequired() { return required; }
        public BigDecimal getAvailable() { return available; }
        public String getReason() { return reason; }
    }

    /**
     * Check if the current capital is sufficient for the strategy and offer to increase if not
     */
    public static BigDecimal checkAndPromptForCapital(OptionsStrategy strategy, String symbol,
                                                    BigDecimal currentCapital, DataProvider dataProvider,
                                                    LocalDate startDate, Scanner scanner) {
        try {
            // get initial market data to calculate minimum capital
            List<MarketData> sampleData = dataProvider.getMarketData(symbol, startDate, startDate);
            if (sampleData.isEmpty()) {
                System.out.println("Warning: No market data available for " + symbol + " on " + startDate);
                return currentCapital;
            }

            MarketData firstData = sampleData.get(0);
            CapitalCheckResult result = checkCapitalRequirement(strategy, currentCapital, firstData);

            if (result.isSufficient()) {
                return currentCapital; // All good, proceed
            }

            // capital is insufficient, prompt user
            System.out.println();
            System.out.println("INSUFFICIENT CAPITAL WARNING");
            System.out.println("Strategy: " + strategy.getName());
            System.out.println("Symbol: " + symbol + " (Price: $" + firstData.getPrice() + ")");
            System.out.println("Your Capital: $" + currentCapital);
            System.out.println("Minimum Required: $" + result.getRequired());
            System.out.println("Reason: " + result.getReason());
            System.out.println();

            System.out.print("Would you like to increase your capital to $" + result.getRequired() + "? (y/n): ");
            String response = scanner.nextLine().trim().toLowerCase();

            if ("y".equals(response) || "yes".equals(response)) {
                System.out.println("Capital increased to $" + result.getRequired());
                return result.getRequired();
            } else {
                System.out.println("Backtest cancelled due to insufficient capital.");
                return null; // Signal to cancel
            }

        } catch (Exception e) {
            System.out.println("Warning: Could not check capital requirements: " + e.getMessage());
            return currentCapital; // Proceed with original capital if check fails
        }
    }

    /**
     * Check if current capital meets minimum requirements for the strategy
     */
    public static CapitalCheckResult checkCapitalRequirement(OptionsStrategy strategy, BigDecimal currentCapital,
                                                           MarketData marketData) {
        BigDecimal required = strategy.getMinimumCapitalRequired(marketData);

        if (required == null) {
            return new CapitalCheckResult(true, currentCapital, currentCapital, "No minimum requirement");
        }

        if (currentCapital.compareTo(required) >= 0) {
            return new CapitalCheckResult(true, required, currentCapital, "Capital is sufficient");
        }

        String reason = buildReasonMessage(strategy, marketData, required);
        return new CapitalCheckResult(false, required, currentCapital, reason);
    }

    private static String buildReasonMessage(OptionsStrategy strategy, MarketData marketData, BigDecimal required) {
        String strategyName = strategy.getName();
        BigDecimal price = marketData.getPrice();

        if (strategyName.contains("Covered Call") || strategyName.contains("Protective Put")) {
            return String.format("Requires 100+ shares for options trading (%s * 100 = $%.2f)",
                                price, required.doubleValue());
        } else if (strategyName.contains("Straddle")) {
            return String.format("Requires at least 1 call + 1 put contract (estimated $%.2f)",
                                required.doubleValue());
        } else {
            return String.format("Requires at least $%.2f for this strategy", required.doubleValue());
        }
    }
}
package com.optionsbacktester.backtester;

import com.optionsbacktester.model.Trade;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class BacktestResult {
    private final String strategyName;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final List<Trade> trades;
    private final BigDecimal finalValue;
    private final BigDecimal initialCapital;

    public BacktestResult(String strategyName, LocalDate startDate, LocalDate endDate,
                          List<Trade> trades, BigDecimal finalValue, BigDecimal initialCapital) {
        this.strategyName = strategyName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.trades = trades;
        this.finalValue = finalValue;
        this.initialCapital = initialCapital;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public List<Trade> getTrades() {
        return trades;
    }

    public BigDecimal getFinalValue() {
        return finalValue;
    }

    public BigDecimal getInitialCapital() {
        return initialCapital;
    }

    public BigDecimal getTotalReturn() {
        return finalValue.subtract(initialCapital);
    }

    public BigDecimal getReturnPercentage() {
        if (initialCapital.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getTotalReturn().divide(initialCapital, 4, BigDecimal.ROUND_HALF_UP)
                              .multiply(BigDecimal.valueOf(100));
    }

    public int getTotalTrades() {
        return trades.size();
    }

    @Override
    public String toString() {
        return String.format(
            "Strategy: %s\nPeriod: %s to %s\nTrades: %d\nInitial Capital: $%.2f\n" +
            "Final Value: $%.2f\nTotal Return: $%.2f (%.2f%%)",
            strategyName, startDate, endDate, getTotalTrades(),
            initialCapital, finalValue, getTotalReturn(), getReturnPercentage()
        );
    }
}
package com.optionsbacktester.strategy;

import com.optionsbacktester.model.MarketData;
import com.optionsbacktester.model.Trade;

import java.math.BigDecimal;
import java.util.List;

public interface OptionsStrategy {
    String getName();

    List<Trade> generateTrades(MarketData marketData);

    void reset();

    /**
     * Calculate the minimum capital required for this strategy to execute at least one trade
     * @param marketData Current market data to base calculations on
     * @return Minimum capital needed, or null if strategy can work with any amount
     */
    BigDecimal getMinimumCapitalRequired(MarketData marketData);

    /**
     * Set the actual available capital for this strategy to use
     * @param availableCapital The actual capital available in the portfolio
     */
    void setAvailableCapital(BigDecimal availableCapital);
}
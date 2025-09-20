package com.optionsbacktester.strategy;

import com.optionsbacktester.model.MarketData;
import com.optionsbacktester.model.Trade;

import java.util.List;

public interface OptionsStrategy {
    String getName();

    List<Trade> generateTrades(MarketData marketData);

    void reset();
}
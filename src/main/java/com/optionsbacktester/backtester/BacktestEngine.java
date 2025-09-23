package com.optionsbacktester.backtester;

import com.optionsbacktester.data.DataProvider;
import com.optionsbacktester.model.MarketData;
import com.optionsbacktester.model.Trade;
import com.optionsbacktester.strategy.OptionsStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BacktestEngine {
    private static final Logger logger = LoggerFactory.getLogger(BacktestEngine.class);

    private final DataProvider dataProvider;
    private final OptionsStrategy strategy;
    private final Portfolio portfolio;
    private final List<Trade> executedTrades;

    public BacktestEngine(DataProvider dataProvider, OptionsStrategy strategy,
                          BigDecimal initialCapital) {
        this.dataProvider = dataProvider;
        this.strategy = strategy;
        this.portfolio = new Portfolio(initialCapital);
        this.executedTrades = new ArrayList<>();
    }

    public BacktestResult runBacktest(String symbol, LocalDate startDate, LocalDate endDate) {
        logger.info("Starting backtest from {} to {} using strategy: {} for symbol: {}",
            startDate, endDate, strategy.getName(), symbol);

        strategy.reset();
        portfolio.reset();
        executedTrades.clear();

        // Inject portfolio capital into strategy
        strategy.setAvailableCapital(portfolio.getTotalValue());

        List<MarketData> marketDataList = dataProvider.getMarketData(symbol, startDate, endDate);

        for (MarketData marketData : marketDataList) {
            List<Trade> trades = strategy.generateTrades(marketData);

            for (Trade trade : trades) {
                if (portfolio.canExecuteTrade(trade)) {
                    portfolio.executeTrade(trade);
                    executedTrades.add(trade);
                    logger.debug("Executed trade: {}", trade);
                } else {
                    logger.warn("Cannot execute trade (insufficient funds/position): {}", trade);
                }
            }

            portfolio.updateValue(marketData);
        }

        BacktestResult result = new BacktestResult(
            strategy.getName(),
            startDate,
            endDate,
            new ArrayList<>(executedTrades),
            portfolio.getTotalValue(),
            portfolio.getInitialCapital()
        );

        logger.info("Backtest completed. Final portfolio value: ${}",
            portfolio.getTotalValue());

        return result;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public List<Trade> getExecutedTrades() {
        return new ArrayList<>(executedTrades);
    }
}
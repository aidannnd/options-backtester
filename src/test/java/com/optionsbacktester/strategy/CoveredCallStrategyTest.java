package com.optionsbacktester.strategy;

import com.optionsbacktester.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoveredCallStrategyTest {

    private CoveredCallStrategy strategy;
    private MarketData marketData;

    @BeforeEach
    void setUp() {
        strategy = new CoveredCallStrategy("AAPL", 30, new BigDecimal("5.00"), 100);
        marketData = new MarketData(
            "AAPL",
            LocalDateTime.now(),
            new BigDecimal("150.00"),
            new BigDecimal("149.50"),
            new BigDecimal("150.50"),
            1000L
        );
    }

    @Test
    void testGetName() {
        assertEquals("Covered Call Strategy", strategy.getName());
    }

    @Test
    void testInitialTradeGeneration() {
        List<Trade> trades = strategy.generateTrades(marketData);

        assertEquals(2, trades.size());

        Trade stockTrade = trades.get(0);
        assertEquals("AAPL", stockTrade.getSymbol());
        assertEquals(TradeAction.BUY, stockTrade.getAction());
        assertEquals(100, stockTrade.getQuantity());
        assertEquals(new BigDecimal("150.00"), stockTrade.getPrice());

        Trade optionTrade = trades.get(1);
        assertEquals(TradeAction.SELL, optionTrade.getAction());
        assertEquals(1, optionTrade.getQuantity());
    }

    @Test
    void testNoTradesForDifferentSymbol() {
        MarketData wrongSymbol = new MarketData(
            "GOOGL",
            LocalDateTime.now(),
            new BigDecimal("2500.00"),
            new BigDecimal("2499.00"),
            new BigDecimal("2501.00"),
            500L
        );

        List<Trade> trades = strategy.generateTrades(wrongSymbol);
        assertTrue(trades.isEmpty());
    }

    @Test
    void testReset() {
        strategy.generateTrades(marketData);
        strategy.reset();

        List<Trade> trades = strategy.generateTrades(marketData);
        assertEquals(2, trades.size());
    }
}
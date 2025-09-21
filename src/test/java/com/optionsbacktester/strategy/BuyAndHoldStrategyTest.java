package com.optionsbacktester.strategy;

import com.optionsbacktester.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuyAndHoldStrategyTest {

    private BuyAndHoldStrategy strategy;
    private MarketData marketData;

    @BeforeEach
    void setUp() {
        LocalDate sellDate = LocalDate.now().plusDays(30);
        strategy = new BuyAndHoldStrategy("AAPL", 100, sellDate);
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
        assertEquals("Buy and Hold Strategy", strategy.getName());
    }

    @Test
    void testInitialBuyTrade() {
        List<Trade> trades = strategy.generateTrades(marketData);

        assertEquals(1, trades.size());

        Trade trade = trades.get(0);
        assertEquals("AAPL", trade.getSymbol());
        assertEquals(TradeAction.BUY, trade.getAction());
        assertEquals(100, trade.getQuantity());
        assertEquals(new BigDecimal("150.00"), trade.getPrice());
    }

    @Test
    void testNoSecondBuyTrade() {
        strategy.generateTrades(marketData);
        List<Trade> secondTrades = strategy.generateTrades(marketData);

        assertTrue(secondTrades.isEmpty());
    }

    @Test
    void testSellAtTargetDate() {
        LocalDate sellDate = LocalDate.now().minusDays(1);
        BuyAndHoldStrategy strategyWithPastSellDate =
            new BuyAndHoldStrategy("AAPL", 100, sellDate);

        strategyWithPastSellDate.generateTrades(marketData);

        MarketData futureMarketData = new MarketData(
            "AAPL",
            LocalDateTime.now(),
            new BigDecimal("160.00"),
            new BigDecimal("159.50"),
            new BigDecimal("160.50"),
            1000L
        );

        List<Trade> trades = strategyWithPastSellDate.generateTrades(futureMarketData);

        assertEquals(1, trades.size());
        Trade trade = trades.get(0);
        assertEquals(TradeAction.SELL, trade.getAction());
        assertEquals(new BigDecimal("160.00"), trade.getPrice());
    }

    @Test
    void testReset() {
        strategy.generateTrades(marketData);
        strategy.reset();

        List<Trade> trades = strategy.generateTrades(marketData);
        assertEquals(1, trades.size());
        assertEquals(TradeAction.BUY, trades.get(0).getAction());
    }
}
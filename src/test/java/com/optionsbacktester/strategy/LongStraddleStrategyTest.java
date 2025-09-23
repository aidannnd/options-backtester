package com.optionsbacktester.strategy;

import com.optionsbacktester.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LongStraddleStrategyTest {

    private LongStraddleStrategy strategy;
    private MarketData marketData;

    @BeforeEach
    void setUp() {
        strategy = new LongStraddleStrategy("AAPL", 30, 10, new BigDecimal("50.00"));
        strategy.setAvailableCapital(new BigDecimal("10000"));
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
        assertEquals("Long Straddle Strategy", strategy.getName());
    }

    @Test
    void testInitialStraddleEntry() {
        List<Trade> trades = strategy.generateTrades(marketData);

        assertEquals(2, trades.size());

        Trade callTrade = trades.get(0);
        assertEquals(TradeAction.BUY, callTrade.getAction());
        // with safety limits and 50% capital allocation, expect reasonable quantities
        assertTrue(callTrade.getQuantity() >= 1 && callTrade.getQuantity() <= 10); // Reasonable contract quantity
        assertTrue(callTrade.getSymbol().contains("AAPL"));
        assertTrue(callTrade.getSymbol().contains("C")); // Call option

        Trade putTrade = trades.get(1);
        assertEquals(TradeAction.BUY, putTrade.getAction());
        assertEquals(callTrade.getQuantity(), putTrade.getQuantity()); // Should match call trade quantity
        assertTrue(putTrade.getSymbol().contains("AAPL"));
        assertTrue(putTrade.getSymbol().contains("P")); // Put option
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
    void testPositionManagementAfterEntry() {
        // enter position
        List<Trade> entryTrades = strategy.generateTrades(marketData);
        assertEquals(2, entryTrades.size());

        // test management with profitable move (large price change)
        MarketData profitableData = new MarketData(
            "AAPL",
            LocalDateTime.now().plusDays(15),
            new BigDecimal("210.00"), // Very big move up to ensure profit threshold is met
            new BigDecimal("209.50"),
            new BigDecimal("210.50"),
            1000L
        );

        List<Trade> managementTrades = strategy.generateTrades(profitableData);
        assertEquals(2, managementTrades.size()); // Should close position due to profit

        Trade callSell = managementTrades.get(0);
        assertEquals(TradeAction.SELL, callSell.getAction());

        Trade putSell = managementTrades.get(1);
        assertEquals(TradeAction.SELL, putSell.getAction());
    }

    @Test
    void testExpirationClose() {
        // enter position
        strategy.generateTrades(marketData);

        // test at expiration (31+ days later)
        MarketData expirationData = new MarketData(
            "AAPL",
            LocalDateTime.now().plusDays(31),
            new BigDecimal("155.00"),
            new BigDecimal("154.50"),
            new BigDecimal("155.50"),
            1000L
        );

        List<Trade> expirationTrades = strategy.generateTrades(expirationData);
        assertEquals(2, expirationTrades.size()); // Should close at expiration

        // verify both are sell trades
        assertTrue(expirationTrades.stream().allMatch(t -> t.getAction() == TradeAction.SELL));
    }

    @Test
    void testIntrinsicValueCalculation() {
        // enter position at 150
        strategy.generateTrades(marketData);

        // price moves significantly up
        MarketData upMoveData = new MarketData(
            "AAPL",
            LocalDateTime.now().plusDays(31),
            new BigDecimal("170.00"), // 20 points up
            new BigDecimal("169.50"),
            new BigDecimal("170.50"),
            1000L
        );

        List<Trade> trades = strategy.generateTrades(upMoveData);
        Trade callTrade = trades.get(0); // Call should have intrinsic value
        assertEquals(new BigDecimal("20.00"), callTrade.getPrice()); // 170 - 150

        Trade putTrade = trades.get(1); // Put should be worthless
        assertEquals(BigDecimal.ZERO, putTrade.getPrice());
    }

    @Test
    void testPutIntrinsicValue() {
        // enter position at 150
        strategy.generateTrades(marketData);

        // price moves significantly down
        MarketData downMoveData = new MarketData(
            "AAPL",
            LocalDateTime.now().plusDays(31),
            new BigDecimal("130.00"), // 20 points down
            new BigDecimal("129.50"),
            new BigDecimal("130.50"),
            1000L
        );

        List<Trade> trades = strategy.generateTrades(downMoveData);
        Trade callTrade = trades.get(0); // Call should be worthless
        assertEquals(BigDecimal.ZERO, callTrade.getPrice());

        Trade putTrade = trades.get(1); // Put should have intrinsic value
        assertEquals(new BigDecimal("20.00"), putTrade.getPrice()); // 150 - 130
    }

    @Test
    void testReset() {
        // enter position
        strategy.generateTrades(marketData);

        // reset strategy
        strategy.reset();

        // should be able to enter new position
        List<Trade> newTrades = strategy.generateTrades(marketData);
        assertEquals(2, newTrades.size());
        assertTrue(newTrades.stream().allMatch(t -> t.getAction() == TradeAction.BUY));
    }

    @Test
    void testMultipleContractsQuantity() {
        LongStraddleStrategy multiContractStrategy = new LongStraddleStrategy(
            "AAPL", 30, 5, new BigDecimal("100.00"));
        multiContractStrategy.setAvailableCapital(new BigDecimal("50000")); // Higher capital for more contracts

        List<Trade> trades = multiContractStrategy.generateTrades(marketData);

        assertEquals(2, trades.size());
        // with safety limits and 50% capital allocation, expect up to 5 contracts max
        assertTrue(trades.get(0).getQuantity() >= 1 && trades.get(0).getQuantity() <= 5); // Call
        assertTrue(trades.get(1).getQuantity() >= 1 && trades.get(1).getQuantity() <= 5); // Put
        assertEquals(trades.get(0).getQuantity(), trades.get(1).getQuantity()); // Same quantity for both
    }
}
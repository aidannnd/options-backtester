package com.optionsbacktester.strategy;

import com.optionsbacktester.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtectivePutStrategyTest {

    private ProtectivePutStrategy strategy;
    private MarketData marketData;

    @BeforeEach
    void setUp() {
        strategy = new ProtectivePutStrategy("AAPL", 30, new BigDecimal("5.00"), 100);
        marketData = new MarketData(
            "AAPL",
            LocalDateTime.now(),
            new BigDecimal("90.00"), // Lower price so we can afford 100+ shares ($9500 / $90 = 105 shares)
            new BigDecimal("89.50"),
            new BigDecimal("90.50"),
            1000L
        );
    }

    @Test
    void testGetName() {
        assertEquals("Protective Put Strategy", strategy.getName());
    }

    @Test
    void testInitialProtectivePutEntry() {
        List<Trade> trades = strategy.generateTrades(marketData);

        assertEquals(2, trades.size());

        Trade stockTrade = trades.get(0);
        assertEquals("AAPL", stockTrade.getSymbol());
        assertEquals(TradeAction.BUY, stockTrade.getAction());
        assertEquals(100, stockTrade.getQuantity());
        assertEquals(new BigDecimal("90.00"), stockTrade.getPrice());

        Trade putTrade = trades.get(1);
        assertEquals(TradeAction.BUY, putTrade.getAction());
        assertEquals(1, putTrade.getQuantity()); // 100 shares / 100 = 1 contract
        assertTrue(putTrade.getSymbol().contains("AAPL"));
        assertTrue(putTrade.getSymbol().contains("P")); // Put option
    }

    @Test
    void testPutStrikeCalculation() {
        List<Trade> trades = strategy.generateTrades(marketData);

        Trade putTrade = trades.get(1);
        // Strike should be current price - offset (90 - 5 = 85)
        assertTrue(putTrade.getSymbol().contains("85"));
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
    void testPositionManagementBeforeExpiration() {
        // Enter position
        List<Trade> entryTrades = strategy.generateTrades(marketData);
        assertEquals(2, entryTrades.size());

        // Test management before expiration (15 days later)
        MarketData midTermData = new MarketData(
            "AAPL",
            LocalDateTime.now().plusDays(15),
            new BigDecimal("95.00"),
            new BigDecimal("94.50"),
            new BigDecimal("95.50"),
            1000L
        );

        List<Trade> managementTrades = strategy.generateTrades(midTermData);
        assertTrue(managementTrades.isEmpty()); // Should not close before expiration
    }

    @Test
    void testExpirationClose() {
        // Enter position
        strategy.generateTrades(marketData);

        // Test at expiration (31+ days later)
        MarketData expirationData = new MarketData(
            "AAPL",
            LocalDateTime.now().plusDays(31),
            new BigDecimal("92.00"),
            new BigDecimal("91.50"),
            new BigDecimal("92.50"),
            1000L
        );

        List<Trade> expirationTrades = strategy.generateTrades(expirationData);
        assertEquals(2, expirationTrades.size()); // Should close at expiration

        Trade stockSell = expirationTrades.get(0);
        assertEquals("AAPL", stockSell.getSymbol());
        assertEquals(TradeAction.SELL, stockSell.getAction());
        assertEquals(100, stockSell.getQuantity());

        Trade putSell = expirationTrades.get(1);
        assertEquals(TradeAction.SELL, putSell.getAction());
        assertEquals(1, putSell.getQuantity());
    }

    @Test
    void testPutIntrinsicValueWhenInTheMoney() {
        // Enter position at 90 (put strike at 85)
        strategy.generateTrades(marketData);

        // Price drops below put strike
        MarketData lowPriceData = new MarketData(
            "AAPL",
            LocalDateTime.now().plusDays(31),
            new BigDecimal("80.00"), // Below 85 strike
            new BigDecimal("79.50"),
            new BigDecimal("80.50"),
            1000L
        );

        List<Trade> trades = strategy.generateTrades(lowPriceData);
        Trade putTrade = trades.get(1);
        assertEquals(new BigDecimal("5.00"), putTrade.getPrice()); // 85 - 80 = 5
    }

    @Test
    void testPutWorthlessWhenOutOfTheMoney() {
        // Enter position at 90 (put strike at 85)
        strategy.generateTrades(marketData);

        // Price stays above put strike
        MarketData highPriceData = new MarketData(
            "AAPL",
            LocalDateTime.now().plusDays(31),
            new BigDecimal("95.00"), // Above 85 strike
            new BigDecimal("94.50"),
            new BigDecimal("95.50"),
            1000L
        );

        List<Trade> trades = strategy.generateTrades(highPriceData);
        Trade putTrade = trades.get(1);
        assertEquals(BigDecimal.ZERO, putTrade.getPrice()); // Out of the money
    }

    @Test
    void testReset() {
        // Enter position
        strategy.generateTrades(marketData);

        // Reset strategy
        strategy.reset();

        // Should be able to enter new position
        List<Trade> newTrades = strategy.generateTrades(marketData);
        assertEquals(2, newTrades.size());
        assertTrue(newTrades.stream().allMatch(t -> t.getAction() == TradeAction.BUY));
    }

    @Test
    void testDifferentShareQuantities() {
        ProtectivePutStrategy strategy200 = new ProtectivePutStrategy(
            "AAPL", 30, new BigDecimal("5.00"), 200);

        List<Trade> trades = strategy200.generateTrades(marketData);

        assertEquals(2, trades.size());
        assertEquals(200, trades.get(0).getQuantity()); // Stock (can afford 211 shares with $19000, rounded down to 200)
        assertEquals(2, trades.get(1).getQuantity()); // Options (200/100 = 2 contracts)
    }

    @Test
    void testDifferentStrikeOffsets() {
        ProtectivePutStrategy strategy10Offset = new ProtectivePutStrategy(
            "AAPL", 30, new BigDecimal("10.00"), 100);

        List<Trade> trades = strategy10Offset.generateTrades(marketData);
        Trade putTrade = trades.get(1);

        // Strike should be 90 - 10 = 80
        assertTrue(putTrade.getSymbol().contains("80"));
    }

    @Test
    void testOptionContractQuantityCalculation() {
        // Test with share quantities that don't divide evenly by 100
        ProtectivePutStrategy strategy50 = new ProtectivePutStrategy(
            "AAPL", 30, new BigDecimal("5.00"), 50);

        List<Trade> trades = strategy50.generateTrades(marketData);

        // With capital-based sizing, we can't afford protective puts with only 50 max shares
        // ($4750 / $90 = 52 shares, but need 100+ for protective puts)
        assertEquals(0, trades.size()); // No trades since can't afford 100+ shares
    }
}
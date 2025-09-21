package com.optionsbacktester.strategy;

import com.optionsbacktester.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class BuyAndHoldStrategy implements OptionsStrategy {
    private static final Logger logger = LoggerFactory.getLogger(BuyAndHoldStrategy.class);

    private final String underlyingSymbol;
    private final int shareQuantity;
    private final LocalDate sellDate;

    private boolean hasPosition = false;
    private LocalDate entryDate;
    private BigDecimal entryPrice;

    public BuyAndHoldStrategy(String underlyingSymbol, int shareQuantity, LocalDate sellDate) {
        this.underlyingSymbol = underlyingSymbol;
        this.shareQuantity = shareQuantity;
        this.sellDate = sellDate;
    }

    @Override
    public String getName() {
        return "Buy and Hold Strategy";
    }

    @Override
    public List<Trade> generateTrades(MarketData marketData) {
        List<Trade> trades = new ArrayList<>();

        if (!marketData.getSymbol().equals(underlyingSymbol)) {
            return trades;
        }

        LocalDate currentDate = marketData.getTimestamp().toLocalDate();

        if (!hasPosition) {
            trades.addAll(enterPosition(marketData));
        } else if (sellDate != null && !currentDate.isBefore(sellDate)) {
            trades.addAll(exitPosition(marketData));
        }

        return trades;
    }

    private List<Trade> enterPosition(MarketData marketData) {
        List<Trade> trades = new ArrayList<>();

        Trade stockTrade = new Trade(
            underlyingSymbol,
            TradeAction.BUY,
            shareQuantity,
            marketData.getPrice(),
            marketData.getTimestamp()
        );
        trades.add(stockTrade);

        hasPosition = true;
        entryDate = marketData.getTimestamp().toLocalDate();
        entryPrice = marketData.getPrice();

        logger.info("Bought {} shares of {} at ${}", shareQuantity, underlyingSymbol, entryPrice);

        return trades;
    }

    private List<Trade> exitPosition(MarketData marketData) {
        List<Trade> trades = new ArrayList<>();

        Trade stockTrade = new Trade(
            underlyingSymbol,
            TradeAction.SELL,
            shareQuantity,
            marketData.getPrice(),
            marketData.getTimestamp()
        );
        trades.add(stockTrade);

        BigDecimal profitLoss = marketData.getPrice().subtract(entryPrice)
                                         .multiply(BigDecimal.valueOf(shareQuantity));

        long holdingDays = ChronoUnit.DAYS.between(entryDate, marketData.getTimestamp().toLocalDate());

        logger.info("Sold {} shares of {} at ${}, P&L: ${}, held for {} days",
                   shareQuantity, underlyingSymbol, marketData.getPrice(), profitLoss, holdingDays);

        hasPosition = false;

        return trades;
    }

    @Override
    public void reset() {
        hasPosition = false;
        entryDate = null;
        entryPrice = null;
    }
}
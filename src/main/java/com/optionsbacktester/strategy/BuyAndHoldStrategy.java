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
    private BigDecimal maxInvestment;
    private final LocalDate sellDate;

    private boolean hasPosition = false;
    private LocalDate entryDate;
    private BigDecimal entryPrice;
    private int shareQuantity;

    public BuyAndHoldStrategy(String underlyingSymbol, int maxShares, LocalDate sellDate) {
        this.underlyingSymbol = underlyingSymbol;
        // use 95% of max investment to leave some buffer for fees/spread
        this.maxInvestment = BigDecimal.valueOf(maxShares * 100).multiply(new BigDecimal("0.95"));
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

        // calculate how many shares we can afford
        BigDecimal sharePrice = marketData.getPrice();
        shareQuantity = maxInvestment.divide(sharePrice, 0, BigDecimal.ROUND_DOWN).intValue();

        // only trade if we can afford at least 1 share
        if (shareQuantity > 0) {
            Trade stockTrade = new Trade(
                underlyingSymbol,
                TradeAction.BUY,
                    shareQuantity,
                sharePrice,
                marketData.getTimestamp()
            );
            trades.add(stockTrade);

            hasPosition = true;
            entryDate = marketData.getTimestamp().toLocalDate();
            entryPrice = sharePrice;

            logger.info("Bought {} shares of {} at ${} (${} invested)",
                    shareQuantity, underlyingSymbol, entryPrice,
                       sharePrice.multiply(BigDecimal.valueOf(shareQuantity)));
        } else {
            logger.warn("Cannot afford any shares of {} at ${} with max investment ${}",
                       underlyingSymbol, sharePrice, maxInvestment);
        }

        return trades;
    }

    private List<Trade> exitPosition(MarketData marketData) {
        List<Trade> trades = new ArrayList<>();

        if (shareQuantity > 0) {
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
        }

        hasPosition = false;

        return trades;
    }

    @Override
    public void reset() {
        hasPosition = false;
        entryDate = null;
        entryPrice = null;
        shareQuantity = 0;
    }

    @Override
    public BigDecimal getMinimumCapitalRequired(MarketData marketData) {
        // buy and hold can work with any amount - just need to afford 1 share
        return marketData.getPrice();
    }

    @Override
    public void setAvailableCapital(BigDecimal availableCapital) {
        // use 95% of available capital to leave some buffer for fees/spread
        this.maxInvestment = availableCapital.multiply(new BigDecimal("0.95"));
    }
}
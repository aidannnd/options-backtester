package com.optionsbacktester.backtester;

import com.optionsbacktester.model.MarketData;
import com.optionsbacktester.model.Trade;
import com.optionsbacktester.model.TradeAction;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class Portfolio {
    private final BigDecimal initialCapital;
    private BigDecimal cash;
    private final Map<String, Integer> positions;
    private BigDecimal totalValue;

    public Portfolio(BigDecimal initialCapital) {
        this.initialCapital = initialCapital;
        this.cash = initialCapital;
        this.positions = new HashMap<>();
        this.totalValue = initialCapital;
    }

    public boolean canExecuteTrade(Trade trade) {
        if (trade.getAction() == TradeAction.BUY) {
            return cash.compareTo(trade.getTotalValue()) >= 0;
        } else {
            // for SELL trades, check if we have the position
            if (isOptionSymbol(trade.getSymbol())) {
                return canSellOption(trade);
            } else {
                // regular stock position
                int currentPosition = positions.getOrDefault(trade.getSymbol(), 0);
                return currentPosition >= trade.getQuantity();
            }
        }
    }

    private boolean isOptionSymbol(String symbol) {
        // option symbols contain underscores and option type indicators (C for Call, P for Put)
        return symbol.contains("_") && (symbol.contains("_C_") || symbol.contains("_P_"));
    }

    private boolean canSellOption(Trade trade) {
        String symbol = trade.getSymbol();
        int currentOptionPosition = positions.getOrDefault(symbol, 0);

        // check if we're closing a long position (selling options we own)
        if (currentOptionPosition >= trade.getQuantity()) {
            return true; // We own enough options to sell
        }

        // otherwise, check if we can write new options (covered calls or naked puts)
        if (symbol.contains("_C_")) {
            // writing a call option - check if we have enough underlying shares for covered call
            String underlyingSymbol = extractUnderlyingSymbol(symbol);
            int underlyingShares = positions.getOrDefault(underlyingSymbol, 0);
            int sharesNeeded = trade.getQuantity() * 100; // Each option contract = 100 shares
            return underlyingShares >= sharesNeeded;
        } else if (symbol.contains("_P_")) {
            // writing a put option - for now, assume we have enough cash/margin (naked put)
            // in a real system, this would check margin requirements
            return true;
        }

        return false;
    }

    private String extractUnderlyingSymbol(String optionSymbol) {
        // extract underlying symbol from option symbol like "AAPL_20240201_C_2106400"
        return optionSymbol.split("_")[0];
    }

    public void executeTrade(Trade trade) {
        if (!canExecuteTrade(trade)) {
            throw new IllegalStateException("Cannot execute trade: insufficient funds or position");
        }

        if (trade.getAction() == TradeAction.BUY) {
            cash = cash.subtract(trade.getTotalValue());
            int currentPosition = positions.getOrDefault(trade.getSymbol(), 0);
            positions.put(trade.getSymbol(), currentPosition + trade.getQuantity());
        } else {
            cash = cash.add(trade.getTotalValue());

            if (isOptionSymbol(trade.getSymbol())) {
                // for option sales, reduce the option position
                int currentOptionPosition = positions.getOrDefault(trade.getSymbol(), 0);
                int newPosition = currentOptionPosition - trade.getQuantity();
                if (newPosition == 0) {
                    positions.remove(trade.getSymbol());
                } else {
                    positions.put(trade.getSymbol(), newPosition);
                }
            } else {
                // regular stock sale
                int currentPosition = positions.getOrDefault(trade.getSymbol(), 0);
                int newPosition = currentPosition - trade.getQuantity();
                if (newPosition == 0) {
                    positions.remove(trade.getSymbol());
                } else {
                    positions.put(trade.getSymbol(), newPosition);
                }
            }
        }
    }

    public void updateValue(MarketData marketData) {
        // this method updates value for the given symbol, but we need to track
        // market prices for all symbols to calculate total portfolio value properly
        // for now, this is a simplified version that works for single-symbol backtests
        BigDecimal positionValue = BigDecimal.ZERO;
        Integer position = positions.get(marketData.getSymbol());
        if (position != null) {
            positionValue = marketData.getPrice().multiply(BigDecimal.valueOf(position));
        }
        totalValue = cash.add(positionValue);
    }

    public void reset() {
        this.cash = initialCapital;
        this.positions.clear();
        this.totalValue = initialCapital;
    }

    public BigDecimal getCash() {
        return cash;
    }

    public Map<String, Integer> getPositions() {
        return new HashMap<>(positions);
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public BigDecimal getInitialCapital() {
        return initialCapital;
    }

    public BigDecimal getPnL() {
        return totalValue.subtract(initialCapital);
    }

    public BigDecimal getReturnPercentage() {
        if (initialCapital.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getPnL().divide(initialCapital, 4, BigDecimal.ROUND_HALF_UP)
                      .multiply(BigDecimal.valueOf(100));
    }
}
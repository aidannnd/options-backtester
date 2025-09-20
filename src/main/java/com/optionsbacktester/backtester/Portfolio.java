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
            int currentPosition = positions.getOrDefault(trade.getSymbol(), 0);
            return currentPosition >= trade.getQuantity();
        }
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
            int currentPosition = positions.get(trade.getSymbol());
            int newPosition = currentPosition - trade.getQuantity();
            if (newPosition == 0) {
                positions.remove(trade.getSymbol());
            } else {
                positions.put(trade.getSymbol(), newPosition);
            }
        }
    }

    public void updateValue(MarketData marketData) {
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
package com.optionsbacktester.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Trade {
    private final String symbol;
    private final TradeAction action;
    private final int quantity;
    private final BigDecimal price;
    private final LocalDateTime timestamp;

    public Trade(String symbol, TradeAction action, int quantity,
                 BigDecimal price, LocalDateTime timestamp) {
        this.symbol = symbol;
        this.action = action;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = timestamp;
    }

    public String getSymbol() {
        return symbol;
    }

    public TradeAction getAction() {
        return action;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public BigDecimal getTotalValue() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public String toString() {
        return String.format("%s %s %d %s @ $%.2f on %s",
            action, quantity, symbol, symbol, price, timestamp);
    }
}
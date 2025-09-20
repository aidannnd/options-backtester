package com.optionsbacktester.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MarketData {
    private final String symbol;
    private final LocalDateTime timestamp;
    private final BigDecimal price;
    private final BigDecimal bid;
    private final BigDecimal ask;
    private final long volume;

    public MarketData(String symbol, LocalDateTime timestamp, BigDecimal price,
                      BigDecimal bid, BigDecimal ask, long volume) {
        this.symbol = symbol;
        this.timestamp = timestamp;
        this.price = price;
        this.bid = bid;
        this.ask = ask;
        this.volume = volume;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getBid() {
        return bid;
    }

    public BigDecimal getAsk() {
        return ask;
    }

    public long getVolume() {
        return volume;
    }

    public BigDecimal getSpread() {
        return ask.subtract(bid);
    }

    public BigDecimal getMidPrice() {
        return bid.add(ask).divide(BigDecimal.valueOf(2));
    }
}
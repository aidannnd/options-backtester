package com.optionsbacktester.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Option {
    private final String symbol;
    private final OptionType type;
    private final BigDecimal strikePrice;
    private final LocalDate expirationDate;
    private final String underlying;

    public Option(String symbol, OptionType type, BigDecimal strikePrice,
                  LocalDate expirationDate, String underlying) {
        this.symbol = symbol;
        this.type = type;
        this.strikePrice = strikePrice;
        this.expirationDate = expirationDate;
        this.underlying = underlying;
    }

    public String getSymbol() {
        return symbol;
    }

    public OptionType getType() {
        return type;
    }

    public BigDecimal getStrikePrice() {
        return strikePrice;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public String getUnderlying() {
        return underlying;
    }

    public boolean isExpired(LocalDate currentDate) {
        return currentDate.isAfter(expirationDate);
    }

    @Override
    public String toString() {
        return String.format("%s %s $%.2f %s",
            underlying, type, strikePrice, expirationDate);
    }
}
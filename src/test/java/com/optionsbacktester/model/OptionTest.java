package com.optionsbacktester.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OptionTest {

    @Test
    void shouldCreateOptionWithCorrectProperties() {
        String symbol = "SPY240315C450";
        OptionType type = OptionType.CALL;
        BigDecimal strikePrice = new BigDecimal("450.00");
        LocalDate expirationDate = LocalDate.of(2024, 3, 15);
        String underlying = "SPY";

        Option option = new Option(symbol, type, strikePrice, expirationDate, underlying);

        assertThat(option.getSymbol()).isEqualTo(symbol);
        assertThat(option.getType()).isEqualTo(type);
        assertThat(option.getStrikePrice()).isEqualTo(strikePrice);
        assertThat(option.getExpirationDate()).isEqualTo(expirationDate);
        assertThat(option.getUnderlying()).isEqualTo(underlying);
    }

    @Test
    void shouldReturnTrueWhenOptionIsExpired() {
        Option option = new Option("SPY240315C450", OptionType.CALL,
            new BigDecimal("450.00"), LocalDate.of(2024, 3, 15), "SPY");

        LocalDate currentDate = LocalDate.of(2024, 3, 16);

        assertThat(option.isExpired(currentDate)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenOptionIsNotExpired() {
        Option option = new Option("SPY240315C450", OptionType.CALL,
            new BigDecimal("450.00"), LocalDate.of(2024, 3, 15), "SPY");

        LocalDate currentDate = LocalDate.of(2024, 3, 14);

        assertThat(option.isExpired(currentDate)).isFalse();
    }
}
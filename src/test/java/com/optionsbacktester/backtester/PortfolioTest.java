package com.optionsbacktester.backtester;

import com.optionsbacktester.model.Trade;
import com.optionsbacktester.model.TradeAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioTest {

    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio(new BigDecimal("10000.00"));
    }

    @Test
    void shouldInitializeWithCorrectValues() {
        assertThat(portfolio.getInitialCapital()).isEqualTo(new BigDecimal("10000.00"));
        assertThat(portfolio.getCash()).isEqualTo(new BigDecimal("10000.00"));
        assertThat(portfolio.getTotalValue()).isEqualTo(new BigDecimal("10000.00"));
        assertThat(portfolio.getPositions()).isEmpty();
    }

    @Test
    void shouldExecuteBuyTradeWhenSufficientFunds() {
        Trade buyTrade = new Trade("SPY", TradeAction.BUY, 10,
            new BigDecimal("400.00"), LocalDateTime.now());

        boolean canExecute = portfolio.canExecuteTrade(buyTrade);
        assertThat(canExecute).isTrue();

        portfolio.executeTrade(buyTrade);

        assertThat(portfolio.getCash()).isEqualTo(new BigDecimal("6000.00"));
        assertThat(portfolio.getPositions().get("SPY")).isEqualTo(10);
    }

    @Test
    void shouldNotExecuteBuyTradeWhenInsufficientFunds() {
        Trade buyTrade = new Trade("SPY", TradeAction.BUY, 100,
            new BigDecimal("400.00"), LocalDateTime.now());

        boolean canExecute = portfolio.canExecuteTrade(buyTrade);
        assertThat(canExecute).isFalse();

        assertThatThrownBy(() -> portfolio.executeTrade(buyTrade))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot execute trade");
    }

    @Test
    void shouldExecuteSellTradeWhenSufficientPosition() {
        Trade buyTrade = new Trade("SPY", TradeAction.BUY, 10,
            new BigDecimal("400.00"), LocalDateTime.now());
        portfolio.executeTrade(buyTrade);

        Trade sellTrade = new Trade("SPY", TradeAction.SELL, 5,
            new BigDecimal("410.00"), LocalDateTime.now());

        boolean canExecute = portfolio.canExecuteTrade(sellTrade);
        assertThat(canExecute).isTrue();

        portfolio.executeTrade(sellTrade);

        assertThat(portfolio.getCash()).isEqualTo(new BigDecimal("8050.00"));
        assertThat(portfolio.getPositions().get("SPY")).isEqualTo(5);
    }

    @Test
    void shouldCalculateCorrectPnL() {
        Trade buyTrade = new Trade("SPY", TradeAction.BUY, 10,
            new BigDecimal("400.00"), LocalDateTime.now());
        portfolio.executeTrade(buyTrade);

        portfolio.updateValue(new com.optionsbacktester.model.MarketData(
            "SPY", LocalDateTime.now(), new BigDecimal("450.00"),
            new BigDecimal("449.50"), new BigDecimal("450.50"), 1000L));

        BigDecimal expectedPnL = new BigDecimal("500.00");
        assertThat(portfolio.getPnL()).isEqualTo(expectedPnL);
    }
}
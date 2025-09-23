package com.optionsbacktester.pricing;

import com.optionsbacktester.model.Option;
import com.optionsbacktester.model.OptionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BlackScholesCalculatorTest {

    @Test
    void shouldCalculateCallOptionPrice() {
        // standard Black-Scholes test case
        BigDecimal underlyingPrice = new BigDecimal("100.00");
        BigDecimal strikePrice = new BigDecimal("105.00");
        double timeToExpiration = 0.25; // 3 months
        double riskFreeRate = 0.05; // 5%
        double volatility = 0.20; // 20%

        BigDecimal callPrice = BlackScholesCalculator.calculateOptionPrice(
            underlyingPrice, strikePrice, timeToExpiration, riskFreeRate, volatility, OptionType.CALL
        );

        // expected value is approximately $2.48 based on Black-Scholes formula
        assertThat(callPrice.doubleValue()).isCloseTo(2.48, within(0.1));
        assertThat(callPrice.doubleValue()).isPositive();
    }

    @Test
    void shouldCalculatePutOptionPrice() {
        BigDecimal underlyingPrice = new BigDecimal("100.00");
        BigDecimal strikePrice = new BigDecimal("105.00");
        double timeToExpiration = 0.25; // 3 months
        double riskFreeRate = 0.05; // 5%
        double volatility = 0.20; // 20%

        BigDecimal putPrice = BlackScholesCalculator.calculateOptionPrice(
            underlyingPrice, strikePrice, timeToExpiration, riskFreeRate, volatility, OptionType.PUT
        );

        // expected value is approximately $6.17 based on Black-Scholes formula
        assertThat(putPrice.doubleValue()).isCloseTo(6.17, within(0.1));
        assertThat(putPrice.doubleValue()).isPositive();
    }

    @Test
    void shouldCalculateIntrinsicValueForExpiredCall() {
        BigDecimal underlyingPrice = new BigDecimal("110.00");
        BigDecimal strikePrice = new BigDecimal("105.00");
        double timeToExpiration = 0.0; // Expired

        BigDecimal callPrice = BlackScholesCalculator.calculateOptionPrice(
            underlyingPrice, strikePrice, timeToExpiration, 0.05, 0.20, OptionType.CALL
        );

        // should equal intrinsic value: max(110 - 105, 0) = 5
        assertThat(callPrice).isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void shouldCalculateIntrinsicValueForExpiredPut() {
        BigDecimal underlyingPrice = new BigDecimal("95.00");
        BigDecimal strikePrice = new BigDecimal("105.00");
        double timeToExpiration = 0.0; // Expired

        BigDecimal putPrice = BlackScholesCalculator.calculateOptionPrice(
            underlyingPrice, strikePrice, timeToExpiration, 0.05, 0.20, OptionType.PUT
        );

        // should equal intrinsic value: max(105 - 95, 0) = 10
        assertThat(putPrice).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    void shouldCalculateZeroForOutOfMoneyExpiredOptions() {
        BigDecimal underlyingPrice = new BigDecimal("95.00");
        BigDecimal strikePrice = new BigDecimal("105.00");

        // out-of-money expired call
        BigDecimal expiredCallPrice = BlackScholesCalculator.calculateIntrinsicValue(
            underlyingPrice, strikePrice, OptionType.CALL
        );
        assertThat(expiredCallPrice).isEqualByComparingTo(BigDecimal.ZERO);

        // out-of-money expired put
        BigDecimal expiredPutPrice = BlackScholesCalculator.calculateIntrinsicValue(
            new BigDecimal("110.00"), strikePrice, OptionType.PUT
        );
        assertThat(expiredPutPrice).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldCalculateTimeToExpiration() {
        LocalDateTime currentDate = LocalDateTime.of(2024, 1, 15, 9, 30);
        LocalDateTime expirationDate = LocalDateTime.of(2024, 4, 15, 16, 0);

        double timeToExpiration = BlackScholesCalculator.calculateTimeToExpiration(currentDate, expirationDate);

        // should be approximately 0.25 years (3 months)
        assertThat(timeToExpiration).isCloseTo(0.25, within(0.01));
    }

    @Test
    void shouldCalculateZeroTimeForPastExpiration() {
        LocalDateTime currentDate = LocalDateTime.of(2024, 4, 20, 9, 30);
        LocalDateTime expirationDate = LocalDateTime.of(2024, 4, 15, 16, 0);

        double timeToExpiration = BlackScholesCalculator.calculateTimeToExpiration(currentDate, expirationDate);

        assertThat(timeToExpiration).isEqualTo(0.0);
    }

    @Test
    void shouldCalculateOptionPriceWithOptionObject() {
        Option callOption = new Option(
            "SPY240315C00450000", // symbol
            OptionType.CALL,
            new BigDecimal("450.00"), // strike
            LocalDateTime.of(2024, 3, 15, 16, 0).toLocalDate(), // expiration
            "SPY" // underlying
        );

        BigDecimal underlyingPrice = new BigDecimal("445.00");
        LocalDateTime currentDate = LocalDateTime.of(2024, 1, 15, 9, 30);
        double riskFreeRate = 0.05;
        double volatility = 0.20;

        BigDecimal optionPrice = BlackScholesCalculator.calculateOptionPrice(
            underlyingPrice, callOption, currentDate, riskFreeRate, volatility
        );

        assertThat(optionPrice.doubleValue()).isPositive();
        assertThat(optionPrice.doubleValue()).isLessThan(underlyingPrice.doubleValue());
    }

    @Test
    void shouldCalculateDeltaForCallOption() {
        BigDecimal underlyingPrice = new BigDecimal("100.00");
        BigDecimal strikePrice = new BigDecimal("100.00");
        double timeToExpiration = 0.25;
        double riskFreeRate = 0.05;
        double volatility = 0.20;

        double delta = BlackScholesCalculator.calculateDelta(
            underlyingPrice, strikePrice, timeToExpiration, riskFreeRate, volatility, OptionType.CALL
        );

        // at-the-money call delta should be around 0.5-0.6
        assertThat(delta).isBetween(0.4, 0.7);
    }

    @Test
    void shouldCalculateDeltaForPutOption() {
        BigDecimal underlyingPrice = new BigDecimal("100.00");
        BigDecimal strikePrice = new BigDecimal("100.00");
        double timeToExpiration = 0.25;
        double riskFreeRate = 0.05;
        double volatility = 0.20;

        double delta = BlackScholesCalculator.calculateDelta(
            underlyingPrice, strikePrice, timeToExpiration, riskFreeRate, volatility, OptionType.PUT
        );

        // at-the-money put delta should be around -0.4 to -0.6
        assertThat(delta).isBetween(-0.7, -0.3);
    }

    @Test
    void shouldCalculateTheta() {
        BigDecimal underlyingPrice = new BigDecimal("100.00");
        BigDecimal strikePrice = new BigDecimal("100.00");
        double timeToExpiration = 0.25;
        double riskFreeRate = 0.05;
        double volatility = 0.20;

        double theta = BlackScholesCalculator.calculateTheta(
            underlyingPrice, strikePrice, timeToExpiration, riskFreeRate, volatility, OptionType.CALL
        );

        // theta should be negative (time decay)
        assertThat(theta).isNegative();
    }

    @Test
    void shouldHandleHighVolatilityScenario() {
        BigDecimal underlyingPrice = new BigDecimal("100.00");
        BigDecimal strikePrice = new BigDecimal("105.00");
        double timeToExpiration = 0.25;
        double riskFreeRate = 0.05;
        double volatility = 0.50; // High volatility

        BigDecimal callPrice = BlackScholesCalculator.calculateOptionPrice(
            underlyingPrice, strikePrice, timeToExpiration, riskFreeRate, volatility, OptionType.CALL
        );

        // higher volatility should result in higher option prices
        assertThat(callPrice.doubleValue()).isGreaterThan(5.0);
    }
}
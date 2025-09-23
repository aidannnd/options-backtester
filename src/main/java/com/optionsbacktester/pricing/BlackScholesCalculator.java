package com.optionsbacktester.pricing;

import com.optionsbacktester.model.Option;
import com.optionsbacktester.model.OptionType;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class BlackScholesCalculator {
    private static final NormalDistribution NORMAL_DISTRIBUTION = new NormalDistribution();
    private static final MathContext MATH_CONTEXT = new MathContext(10, RoundingMode.HALF_UP);

    public static BigDecimal calculateOptionPrice(
            BigDecimal underlyingPrice,
            BigDecimal strikePrice,
            double timeToExpiration, // in years
            double riskFreeRate,
            double volatility,
            OptionType optionType) {

        if (timeToExpiration <= 0) {
            return calculateIntrinsicValue(underlyingPrice, strikePrice, optionType);
        }

        double S = underlyingPrice.doubleValue();
        double K = strikePrice.doubleValue();
        double T = timeToExpiration;
        double r = riskFreeRate;
        double sigma = volatility;

        // calculate d1 and d2
        double d1 = (Math.log(S / K) + (r + 0.5 * sigma * sigma) * T) / (sigma * Math.sqrt(T));
        double d2 = d1 - sigma * Math.sqrt(T);

        double optionPrice;

        if (optionType == OptionType.CALL) {
            // call option: C = S * N(d1) - K * e^(-r*T) * N(d2)
            optionPrice = S * NORMAL_DISTRIBUTION.cumulativeProbability(d1)
                         - K * Math.exp(-r * T) * NORMAL_DISTRIBUTION.cumulativeProbability(d2);
        } else {
            // put option: P = K * e^(-r*T) * N(-d2) - S * N(-d1)
            optionPrice = K * Math.exp(-r * T) * NORMAL_DISTRIBUTION.cumulativeProbability(-d2)
                         - S * NORMAL_DISTRIBUTION.cumulativeProbability(-d1);
        }

        return BigDecimal.valueOf(Math.max(0.0, optionPrice)).round(MATH_CONTEXT);
    }

    public static BigDecimal calculateOptionPrice(
            BigDecimal underlyingPrice,
            Option option,
            LocalDateTime currentDate,
            double riskFreeRate,
            double volatility) {

        double timeToExpiration = calculateTimeToExpiration(currentDate.toLocalDate(), option.getExpirationDate());

        return calculateOptionPrice(
            underlyingPrice,
            option.getStrikePrice(),
            timeToExpiration,
            riskFreeRate,
            volatility,
            option.getType()
        );
    }

    public static double calculateTimeToExpiration(LocalDateTime currentDate, LocalDateTime expirationDate) {
        if (currentDate.isAfter(expirationDate)) {
            return 0.0;
        }

        long daysToExpiration = ChronoUnit.DAYS.between(currentDate, expirationDate);
        return daysToExpiration / 365.0; // Convert to years
    }

    public static double calculateTimeToExpiration(LocalDate currentDate, LocalDate expirationDate) {
        if (currentDate.isAfter(expirationDate)) {
            return 0.0;
        }

        long daysToExpiration = ChronoUnit.DAYS.between(currentDate, expirationDate);
        return daysToExpiration / 365.0; // Convert to years
    }

    public static BigDecimal calculateIntrinsicValue(
            BigDecimal underlyingPrice,
            BigDecimal strikePrice,
            OptionType optionType) {

        if (optionType == OptionType.CALL) {
            // call intrinsic value: max(S - K, 0)
            BigDecimal intrinsic = underlyingPrice.subtract(strikePrice);
            return intrinsic.compareTo(BigDecimal.ZERO) > 0 ? intrinsic : BigDecimal.ZERO;
        } else {
            // put intrinsic value: max(K - S, 0)
            BigDecimal intrinsic = strikePrice.subtract(underlyingPrice);
            return intrinsic.compareTo(BigDecimal.ZERO) > 0 ? intrinsic : BigDecimal.ZERO;
        }
    }

    /**
     * Calculate the Greeks - Delta (price sensitivity to underlying price)
     */
    public static double calculateDelta(
            BigDecimal underlyingPrice,
            BigDecimal strikePrice,
            double timeToExpiration,
            double riskFreeRate,
            double volatility,
            OptionType optionType) {

        if (timeToExpiration <= 0) {
            return 0.0;
        }

        double S = underlyingPrice.doubleValue();
        double K = strikePrice.doubleValue();
        double T = timeToExpiration;
        double r = riskFreeRate;
        double sigma = volatility;

        double d1 = (Math.log(S / K) + (r + 0.5 * sigma * sigma) * T) / (sigma * Math.sqrt(T));

        if (optionType == OptionType.CALL) {
            return NORMAL_DISTRIBUTION.cumulativeProbability(d1);
        } else {
            return NORMAL_DISTRIBUTION.cumulativeProbability(d1) - 1.0;
        }
    }

    /**
     * Calculate Theta (time decay)
     */
    public static double calculateTheta(
            BigDecimal underlyingPrice,
            BigDecimal strikePrice,
            double timeToExpiration,
            double riskFreeRate,
            double volatility,
            OptionType optionType) {

        if (timeToExpiration <= 0) {
            return 0.0;
        }

        double S = underlyingPrice.doubleValue();
        double K = strikePrice.doubleValue();
        double T = timeToExpiration;
        double r = riskFreeRate;
        double sigma = volatility;

        double d1 = (Math.log(S / K) + (r + 0.5 * sigma * sigma) * T) / (sigma * Math.sqrt(T));
        double d2 = d1 - sigma * Math.sqrt(T);

        double term1 = -S * NORMAL_DISTRIBUTION.density(d1) * sigma / (2 * Math.sqrt(T));

        if (optionType == OptionType.CALL) {
            double term2 = -r * K * Math.exp(-r * T) * NORMAL_DISTRIBUTION.cumulativeProbability(d2);
            return (term1 + term2) / 365.0; // Convert to daily theta
        } else {
            double term2 = r * K * Math.exp(-r * T) * NORMAL_DISTRIBUTION.cumulativeProbability(-d2);
            return (term1 + term2) / 365.0; // Convert to daily theta
        }
    }
}
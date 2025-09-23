package com.optionsbacktester.strategy;

import com.optionsbacktester.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class LongStraddleStrategy implements OptionsStrategy {
    private static final Logger logger = LoggerFactory.getLogger(LongStraddleStrategy.class);

    private final String underlyingSymbol;
    private final int daysToExpiration;
    private final int maxContracts;
    private BigDecimal maxInvestment;
    private final BigDecimal minimumProfitThreshold;

    private boolean hasPosition = false;
    private LocalDate positionEntryDate;
    private BigDecimal strikePrice;
    private BigDecimal entryPrice;
    private int contractQuantity;

    public LongStraddleStrategy(String underlyingSymbol, int daysToExpiration,
                               int maxContracts, BigDecimal minimumProfitThreshold) {
        this.underlyingSymbol = underlyingSymbol;
        this.daysToExpiration = daysToExpiration;
        this.maxContracts = maxContracts;
        // initialize with a default value - will be set by setAvailableCapital()
        this.maxInvestment = BigDecimal.valueOf(10000).multiply(new BigDecimal("0.95"));
        this.minimumProfitThreshold = minimumProfitThreshold;
    }

    @Override
    public String getName() {
        return "Long Straddle Strategy";
    }

    @Override
    public List<Trade> generateTrades(MarketData marketData) {
        List<Trade> trades = new ArrayList<>();

        if (!marketData.getSymbol().equals(underlyingSymbol)) {
            return trades;
        }

        LocalDate currentDate = marketData.getTimestamp().toLocalDate();

        if (!hasPosition) {
            trades.addAll(enterStraddlePosition(marketData));
        } else {
            trades.addAll(managePosition(marketData, currentDate));
        }

        return trades;
    }

    private List<Trade> enterStraddlePosition(MarketData marketData) {
        List<Trade> trades = new ArrayList<>();

        strikePrice = marketData.getPrice();
        LocalDate expirationDate = marketData.getTimestamp().toLocalDate().plusDays(daysToExpiration);

        BigDecimal callPrice = estimateOptionPrice(marketData.getPrice(), strikePrice,
                                                 daysToExpiration, OptionType.CALL);
        BigDecimal putPrice = estimateOptionPrice(marketData.getPrice(), strikePrice,
                                                daysToExpiration, OptionType.PUT);

        // calculate how many contracts we can afford
        BigDecimal straddleCost = callPrice.add(putPrice);
        int affordableContracts = maxInvestment.divide(straddleCost, 0, BigDecimal.ROUND_DOWN).intValue();

        // limit to maxContracts to prevent over-leveraging
        contractQuantity = Math.min(affordableContracts, maxContracts);

        // additional safety check - ensure we don't exceed 10% of available capital per contract
        BigDecimal capitalPerContract = maxInvestment.divide(new BigDecimal("10"), 2, BigDecimal.ROUND_DOWN);
        int safeMaxContracts = capitalPerContract.divide(straddleCost, 0, BigDecimal.ROUND_DOWN).intValue();
        contractQuantity = Math.min(contractQuantity, Math.max(1, safeMaxContracts));

        // only trade if we can afford at least 1 contract
        if (contractQuantity > 0) {
            String callSymbol = generateOptionSymbol(underlyingSymbol, OptionType.CALL,
                                                   strikePrice, expirationDate);
            String putSymbol = generateOptionSymbol(underlyingSymbol, OptionType.PUT,
                                                  strikePrice, expirationDate);

            Trade callTrade = new Trade(
                callSymbol,
                TradeAction.BUY,
                contractQuantity,
                callPrice,
                marketData.getTimestamp()
            );
            trades.add(callTrade);

            Trade putTrade = new Trade(
                putSymbol,
                TradeAction.BUY,
                contractQuantity,
                putPrice,
                marketData.getTimestamp()
            );
            trades.add(putTrade);

            entryPrice = callPrice.add(putPrice);
            hasPosition = true;
            positionEntryDate = marketData.getTimestamp().toLocalDate();

            logger.info("Entered long straddle position: {} contracts at strike ${}, entry cost ${}",
                       contractQuantity, strikePrice, entryPrice);
        } else {
            logger.warn("Cannot afford any straddle contracts: need ${} per contract but max investment is ${}",
                       straddleCost, maxInvestment);
        }

        return trades;
    }

    private List<Trade> managePosition(MarketData marketData, LocalDate currentDate) {
        List<Trade> trades = new ArrayList<>();

        LocalDate expirationDate = positionEntryDate.plusDays(daysToExpiration);

        BigDecimal currentValue = calculateCurrentValue(marketData.getPrice());
        BigDecimal profitLoss = currentValue.subtract(entryPrice);

        boolean shouldClose = false;

        if (profitLoss.compareTo(minimumProfitThreshold) >= 0) {
            logger.info("Closing straddle due to profit target reached: ${}", profitLoss);
            shouldClose = true;
        } else if (!currentDate.isBefore(expirationDate)) {
            logger.info("Closing straddle at expiration");
            shouldClose = true;
        }

        if (shouldClose) {
            trades.addAll(closePosition(marketData));
        }

        return trades;
    }

    private List<Trade> closePosition(MarketData marketData) {
        List<Trade> trades = new ArrayList<>();

        LocalDate expirationDate = positionEntryDate.plusDays(daysToExpiration);

        String callSymbol = generateOptionSymbol(underlyingSymbol, OptionType.CALL,
                                               strikePrice, expirationDate);
        String putSymbol = generateOptionSymbol(underlyingSymbol, OptionType.PUT,
                                              strikePrice, expirationDate);

        BigDecimal callValue = BigDecimal.ZERO;
        if (marketData.getPrice().compareTo(strikePrice) > 0) {
            callValue = marketData.getPrice().subtract(strikePrice);
        }

        BigDecimal putValue = BigDecimal.ZERO;
        if (strikePrice.compareTo(marketData.getPrice()) > 0) {
            putValue = strikePrice.subtract(marketData.getPrice());
        }

        Trade callTrade = new Trade(
            callSymbol,
            TradeAction.SELL,
            contractQuantity,
            callValue,
            marketData.getTimestamp()
        );
        trades.add(callTrade);

        Trade putTrade = new Trade(
            putSymbol,
            TradeAction.SELL,
            contractQuantity,
            putValue,
            marketData.getTimestamp()
        );
        trades.add(putTrade);

        BigDecimal exitValue = callValue.add(putValue);
        BigDecimal profitLoss = exitValue.subtract(entryPrice);

        logger.info("Closed long straddle position: P&L ${}", profitLoss);

        hasPosition = false;

        return trades;
    }

    private BigDecimal calculateCurrentValue(BigDecimal currentPrice) {
        BigDecimal callValue = BigDecimal.ZERO;
        if (currentPrice.compareTo(strikePrice) > 0) {
            callValue = currentPrice.subtract(strikePrice);
        }

        BigDecimal putValue = BigDecimal.ZERO;
        if (strikePrice.compareTo(currentPrice) > 0) {
            putValue = strikePrice.subtract(currentPrice);
        }

        return callValue.add(putValue);
    }

    private BigDecimal estimateOptionPrice(BigDecimal stockPrice, BigDecimal strikePrice,
                                         int daysToExp, OptionType optionType) {
        BigDecimal intrinsicValue = BigDecimal.ZERO;

        if (optionType == OptionType.CALL && stockPrice.compareTo(strikePrice) > 0) {
            intrinsicValue = stockPrice.subtract(strikePrice);
        } else if (optionType == OptionType.PUT && strikePrice.compareTo(stockPrice) > 0) {
            intrinsicValue = strikePrice.subtract(stockPrice);
        }

        // base time value on volatility and time decay
        double impliedVol = 0.25; // 25% implied volatility assumption
        double timeValueFactor = Math.sqrt(daysToExp / 365.0) * impliedVol * stockPrice.doubleValue() * 0.4;
        BigDecimal timeValue = BigDecimal.valueOf(Math.max(0.10, timeValueFactor));

        return intrinsicValue.add(timeValue);
    }

    private String generateOptionSymbol(String underlying, OptionType type,
                                      BigDecimal strike, LocalDate expiration) {
        return String.format("%s_%s_%s_%s",
                           underlying,
                           expiration.toString().replace("-", ""),
                           type.toString().charAt(0),
                           strike.toString().replace(".", ""));
    }

    @Override
    public void reset() {
        hasPosition = false;
        positionEntryDate = null;
        strikePrice = null;
        entryPrice = null;
        contractQuantity = 0;
    }

    @Override
    public BigDecimal getMinimumCapitalRequired(MarketData marketData) {
        // long straddle requires at least 1 call + 1 put contract
        BigDecimal callPrice = estimateOptionPrice(marketData.getPrice(), marketData.getPrice(),
                                                 daysToExpiration, OptionType.CALL);
        BigDecimal putPrice = estimateOptionPrice(marketData.getPrice(), marketData.getPrice(),
                                                daysToExpiration, OptionType.PUT);
        return callPrice.add(putPrice);
    }

    @Override
    public void setAvailableCapital(BigDecimal availableCapital) {
        // use only 50% of available capital for options strategies to manage risk
        // options are high-risk and can lose 100% of premium
        this.maxInvestment = availableCapital.multiply(new BigDecimal("0.50"));
    }
}
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
    private final int contractQuantity;
    private final BigDecimal minimumProfitThreshold;

    private boolean hasPosition = false;
    private LocalDate positionEntryDate;
    private BigDecimal strikePrice;
    private BigDecimal entryPrice;

    public LongStraddleStrategy(String underlyingSymbol, int daysToExpiration,
                               int contractQuantity, BigDecimal minimumProfitThreshold) {
        this.underlyingSymbol = underlyingSymbol;
        this.daysToExpiration = daysToExpiration;
        this.contractQuantity = contractQuantity;
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

        String callSymbol = generateOptionSymbol(underlyingSymbol, OptionType.CALL,
                                               strikePrice, expirationDate);
        String putSymbol = generateOptionSymbol(underlyingSymbol, OptionType.PUT,
                                              strikePrice, expirationDate);

        BigDecimal callPrice = estimateOptionPrice(marketData.getPrice(), strikePrice,
                                                 daysToExpiration, OptionType.CALL);
        BigDecimal putPrice = estimateOptionPrice(marketData.getPrice(), strikePrice,
                                                daysToExpiration, OptionType.PUT);

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

        logger.info("Entered long straddle position at strike ${}, entry cost ${}",
                   strikePrice, entryPrice);

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

        BigDecimal timeValue = BigDecimal.valueOf(daysToExp * 0.08);

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
    }
}
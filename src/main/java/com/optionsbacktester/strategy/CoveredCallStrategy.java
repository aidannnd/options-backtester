package com.optionsbacktester.strategy;

import com.optionsbacktester.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CoveredCallStrategy implements OptionsStrategy {
    private static final Logger logger = LoggerFactory.getLogger(CoveredCallStrategy.class);

    private final String underlyingSymbol;
    private final int daysToExpiration;
    private final BigDecimal strikeOffset;
    private final int shareQuantity;

    private boolean hasPosition = false;
    private LocalDate positionEntryDate;
    private BigDecimal callStrike;

    public CoveredCallStrategy(String underlyingSymbol, int daysToExpiration,
                              BigDecimal strikeOffset, int shareQuantity) {
        this.underlyingSymbol = underlyingSymbol;
        this.daysToExpiration = daysToExpiration;
        this.strikeOffset = strikeOffset;
        this.shareQuantity = shareQuantity;
    }

    @Override
    public String getName() {
        return "Covered Call Strategy";
    }

    @Override
    public List<Trade> generateTrades(MarketData marketData) {
        List<Trade> trades = new ArrayList<>();

        if (!marketData.getSymbol().equals(underlyingSymbol)) {
            return trades;
        }

        LocalDate currentDate = marketData.getTimestamp().toLocalDate();

        if (!hasPosition) {
            trades.addAll(enterCoveredCallPosition(marketData));
        } else {
            trades.addAll(managePosition(marketData, currentDate));
        }

        return trades;
    }

    private List<Trade> enterCoveredCallPosition(MarketData marketData) {
        List<Trade> trades = new ArrayList<>();

        Trade stockTrade = new Trade(
            underlyingSymbol,
            TradeAction.BUY,
            shareQuantity,
            marketData.getPrice(),
            marketData.getTimestamp()
        );
        trades.add(stockTrade);

        callStrike = marketData.getPrice().add(strikeOffset);
        LocalDate expirationDate = marketData.getTimestamp().toLocalDate().plusDays(daysToExpiration);

        String optionSymbol = generateOptionSymbol(underlyingSymbol, OptionType.CALL,
                                                 callStrike, expirationDate);

        BigDecimal optionPrice = estimateOptionPrice(marketData.getPrice(), callStrike,
                                                   daysToExpiration, OptionType.CALL);

        Trade optionTrade = new Trade(
            optionSymbol,
            TradeAction.SELL,
            shareQuantity / 100, // 1 option contract = 100 shares
            optionPrice,
            marketData.getTimestamp()
        );
        trades.add(optionTrade);

        hasPosition = true;
        positionEntryDate = marketData.getTimestamp().toLocalDate();

        logger.info("Entered covered call position: bought {} shares at ${}, sold call at strike ${}",
                   shareQuantity, marketData.getPrice(), callStrike);

        return trades;
    }

    private List<Trade> managePosition(MarketData marketData, LocalDate currentDate) {
        List<Trade> trades = new ArrayList<>();

        LocalDate expirationDate = positionEntryDate.plusDays(daysToExpiration);

        if (!currentDate.isBefore(expirationDate)) {
            trades.addAll(closePosition(marketData));
        }

        return trades;
    }

    private List<Trade> closePosition(MarketData marketData) {
        List<Trade> trades = new ArrayList<>();

        Trade stockTrade = new Trade(
            underlyingSymbol,
            TradeAction.SELL,
            shareQuantity,
            marketData.getPrice(),
            marketData.getTimestamp()
        );
        trades.add(stockTrade);

        LocalDate expirationDate = positionEntryDate.plusDays(daysToExpiration);
        String optionSymbol = generateOptionSymbol(underlyingSymbol, OptionType.CALL,
                                                 callStrike, expirationDate);

        BigDecimal optionPrice = BigDecimal.ZERO;
        if (marketData.getPrice().compareTo(callStrike) > 0) {
            optionPrice = marketData.getPrice().subtract(callStrike);
        }

        Trade optionTrade = new Trade(
            optionSymbol,
            TradeAction.BUY,
            shareQuantity / 100,
            optionPrice,
            marketData.getTimestamp()
        );
        trades.add(optionTrade);

        hasPosition = false;

        logger.info("Closed covered call position at stock price ${}", marketData.getPrice());

        return trades;
    }

    private BigDecimal estimateOptionPrice(BigDecimal stockPrice, BigDecimal strikePrice,
                                         int daysToExp, OptionType optionType) {
        BigDecimal intrinsicValue = BigDecimal.ZERO;

        if (optionType == OptionType.CALL && stockPrice.compareTo(strikePrice) > 0) {
            intrinsicValue = stockPrice.subtract(strikePrice);
        } else if (optionType == OptionType.PUT && strikePrice.compareTo(stockPrice) > 0) {
            intrinsicValue = strikePrice.subtract(stockPrice);
        }

        BigDecimal timeValue = BigDecimal.valueOf(daysToExp * 0.05);

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
        callStrike = null;
    }
}
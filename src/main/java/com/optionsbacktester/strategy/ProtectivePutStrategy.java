package com.optionsbacktester.strategy;

import com.optionsbacktester.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ProtectivePutStrategy implements OptionsStrategy {
    private static final Logger logger = LoggerFactory.getLogger(ProtectivePutStrategy.class);

    private final String underlyingSymbol;
    private final int daysToExpiration;
    private final BigDecimal strikeOffset;
    private BigDecimal maxInvestment;

    private boolean hasPosition = false;
    private LocalDate positionEntryDate;
    private BigDecimal putStrike;
    private int shareQuantity;

    public ProtectivePutStrategy(String underlyingSymbol, int daysToExpiration,
                                BigDecimal strikeOffset, int maxShares) {
        this.underlyingSymbol = underlyingSymbol;
        this.daysToExpiration = daysToExpiration;
        this.strikeOffset = strikeOffset;
        // use 95% of max investment to leave some buffer for fees/spread
        this.maxInvestment = BigDecimal.valueOf(maxShares * 100).multiply(new BigDecimal("0.95"));
    }

    @Override
    public String getName() {
        return "Protective Put Strategy";
    }

    @Override
    public List<Trade> generateTrades(MarketData marketData) {
        List<Trade> trades = new ArrayList<>();

        if (!marketData.getSymbol().equals(underlyingSymbol)) {
            return trades;
        }

        LocalDate currentDate = marketData.getTimestamp().toLocalDate();

        if (!hasPosition) {
            trades.addAll(enterProtectivePutPosition(marketData));
        } else {
            trades.addAll(managePosition(marketData, currentDate));
        }

        return trades;
    }

    private List<Trade> enterProtectivePutPosition(MarketData marketData) {
        List<Trade> trades = new ArrayList<>();

        // calculate how many shares we can afford
        BigDecimal sharePrice = marketData.getPrice();
        shareQuantity = maxInvestment.divide(sharePrice, 0, BigDecimal.ROUND_DOWN).intValue();

        // only trade if we can afford at least 100 shares (1 option contract)
        if (shareQuantity >= 100) {
            // round down to nearest 100 for protective puts (option contracts are 100 shares each)
            shareQuantity = (shareQuantity / 100) * 100;

            Trade stockTrade = new Trade(
                underlyingSymbol,
                TradeAction.BUY,
                shareQuantity,
                sharePrice,
                marketData.getTimestamp()
            );
            trades.add(stockTrade);

            putStrike = marketData.getPrice().subtract(strikeOffset);
            LocalDate expirationDate = marketData.getTimestamp().toLocalDate().plusDays(daysToExpiration);

            String optionSymbol = generateOptionSymbol(underlyingSymbol, OptionType.PUT,
                                                     putStrike, expirationDate);

            BigDecimal optionPrice = estimateOptionPrice(marketData.getPrice(), putStrike,
                                                       daysToExpiration, OptionType.PUT);

            Trade optionTrade = new Trade(
                optionSymbol,
                TradeAction.BUY,
                shareQuantity / 100,
                optionPrice,
                marketData.getTimestamp()
            );
            trades.add(optionTrade);

            hasPosition = true;
            positionEntryDate = marketData.getTimestamp().toLocalDate();

            logger.info("Entered protective put position: bought {} shares at ${}, bought put at strike ${}",
                       shareQuantity, sharePrice, putStrike);
        } else {
            logger.warn("Cannot afford enough shares for protective put (need 100+): can only afford {} shares of {} at ${} with max investment ${}",
                       shareQuantity, underlyingSymbol, sharePrice, maxInvestment);
        }

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
        String optionSymbol = generateOptionSymbol(underlyingSymbol, OptionType.PUT,
                                                 putStrike, expirationDate);

        BigDecimal optionValue = BigDecimal.ZERO;
        if (putStrike.compareTo(marketData.getPrice()) > 0) {
            optionValue = putStrike.subtract(marketData.getPrice());
        }

        Trade optionTrade = new Trade(
            optionSymbol,
            TradeAction.SELL,
            shareQuantity / 100,
            optionValue,
            marketData.getTimestamp()
        );
        trades.add(optionTrade);

        hasPosition = false;

        logger.info("Closed protective put position at stock price ${}", marketData.getPrice());

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
        putStrike = null;
        shareQuantity = 0;
    }

    @Override
    public BigDecimal getMinimumCapitalRequired(MarketData marketData) {
        // protective put requires 100 shares minimum + put option cost
        // add buffer for the 95% max investment limit
        BigDecimal stockCost = marketData.getPrice().multiply(BigDecimal.valueOf(100));
        BigDecimal putCost = estimateOptionPrice(marketData.getPrice(),
                                               marketData.getPrice().subtract(strikeOffset),
                                               daysToExpiration, OptionType.PUT);
        BigDecimal baseRequirement = stockCost.add(putCost);
        return baseRequirement.divide(new BigDecimal("0.95"), 2, BigDecimal.ROUND_UP);
    }

    @Override
    public void setAvailableCapital(BigDecimal availableCapital) {
        // use 95% of available capital to leave some buffer for fees/spread
        this.maxInvestment = availableCapital.multiply(new BigDecimal("0.95"));
    }
}
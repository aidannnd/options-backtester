package com.optionsbacktester.data;

import com.optionsbacktester.model.MarketData;

import java.time.LocalDate;
import java.util.List;

public interface DataProvider {
    List<MarketData> getMarketData(LocalDate startDate, LocalDate endDate);

    List<MarketData> getMarketData(String symbol, LocalDate startDate, LocalDate endDate);
}
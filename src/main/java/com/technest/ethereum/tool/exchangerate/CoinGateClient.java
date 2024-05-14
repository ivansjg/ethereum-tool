package com.technest.ethereum.tool.exchangerate;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.math.BigDecimal;
import java.util.Optional;

@HttpExchange
public interface CoinGateClient {
    @GetExchange("/v2/rates/merchant/{srcCurrency}/{destCurrency}/")
    Optional<BigDecimal> getExchangeRateFor(@PathVariable String srcCurrency, @PathVariable String destCurrency);
}

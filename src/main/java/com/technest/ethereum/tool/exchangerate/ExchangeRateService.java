package com.technest.ethereum.tool.exchangerate;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

@RequiredArgsConstructor
@Service
@Log4j2
public class ExchangeRateService {

    @Value("${exchange-rate.btc-to-usd:30607.10}")
    private BigDecimal btcUsdExchangeRateFallback;
    @Value("${exchange-rate.eth-to-usd:1922.51}")
    private BigDecimal ethUsdExchangeRateFallback;
    private final Integer DECIMAL_POSITIONS = 9;
    private final String USD_SYMBOL = "USD";
    private final String BITCOIN_SYMBOL = "BTC";
    private final String ETHER_SYMBOL = "ETH";
    private final CoinGateClient coinGateClient;
    private BigDecimal btcUsdExchangeRate;
    private BigDecimal ethUsdExchangeRate;

    @PostConstruct
    private void retrieveExchangeRates() {
        log.info("Loading exchange rates...");
        btcUsdExchangeRate = coinGateClient.getExchangeRateFor(BITCOIN_SYMBOL, USD_SYMBOL).orElseGet(() -> btcUsdExchangeRateFallback);
        ethUsdExchangeRate = coinGateClient.getExchangeRateFor(ETHER_SYMBOL, USD_SYMBOL).orElseGet(() -> ethUsdExchangeRateFallback);
        log.info("Exchange rates loaded: btcToUsd={} ethToUsd={}", btcUsdExchangeRate, ethUsdExchangeRate);
    }

    public BigDecimal fromSatoshisToUsd(Long satoshis) {
        return BigDecimal.valueOf(satoshis).multiply(btcUsdExchangeRate).divide(new BigDecimal("100000000.0"), DECIMAL_POSITIONS, RoundingMode.HALF_UP);
    }

    public Long fromUsdToSatoshis(BigDecimal usdAmount) {
        return usdAmount.multiply(new BigDecimal("100000000.0")).divide(btcUsdExchangeRate, DECIMAL_POSITIONS, RoundingMode.HALF_UP).longValue();
    }

    public BigInteger fromUsdtoWeis(BigDecimal usdAmount) {
        return usdAmount.multiply(new BigDecimal("1000000000000000000.0")).divide(ethUsdExchangeRate, DECIMAL_POSITIONS, RoundingMode.HALF_UP).toBigInteger();
    }

    public BigDecimal fromWeisToUsd(BigInteger weis) {
        return new BigDecimal(weis).multiply(ethUsdExchangeRate).divide(new BigDecimal("1000000000000000000.0"), DECIMAL_POSITIONS, RoundingMode.HALF_UP);
    }

    public BigInteger fromUsdtoGweis(BigDecimal usdAmount) {
        return usdAmount.multiply(new BigDecimal("1000000000.0")).divide(ethUsdExchangeRate, DECIMAL_POSITIONS, RoundingMode.HALF_UP).toBigInteger();
    }

    public BigDecimal fromGweisToUsd(BigInteger gweis) {
        return new BigDecimal(gweis).multiply(ethUsdExchangeRate).divide(new BigDecimal("1000000000.0"), DECIMAL_POSITIONS, RoundingMode.HALF_UP);
    }

}

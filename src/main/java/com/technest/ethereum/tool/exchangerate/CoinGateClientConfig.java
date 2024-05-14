package com.technest.ethereum.tool.exchangerate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;


@Configuration
public class CoinGateClientConfig {

    @Bean
    public CoinGateClient coinGateClient(@Value("${coingate-api.url}") String coinGateApiUrl) {
        WebClient webClient = WebClient.builder()
                .baseUrl(coinGateApiUrl)
                .filter(errorHandlingFilter())
                .build();

        return HttpServiceProxyFactory
                .builder(WebClientAdapter.forClient(webClient))
                .build()
                .createClient(CoinGateClient.class);
    }

    public static ExchangeFilterFunction errorHandlingFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            if (!clientResponse.statusCode().is2xxSuccessful()) {
                // If there is any error doing the HTTP request, we return an empty response, so fallback exchange rates will be used.
                return clientResponse.releaseBody().then(Mono.just(ClientResponse.create(HttpStatusCode.valueOf(200)).build()));
            } else {
                return Mono.just(clientResponse);
            }
        });
    }

}

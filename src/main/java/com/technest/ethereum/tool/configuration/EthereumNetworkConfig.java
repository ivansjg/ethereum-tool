package com.technest.ethereum.tool.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthChainId;
import org.web3j.protocol.http.HttpService;

@Configuration
public class EthereumNetworkConfig {

    @Bean
    public Web3j ethereumNetworkConnection(EthereumNetworkConfigProperties ethereumNetworkConfigProperties) {
        return Web3j.build(new HttpService(ethereumNetworkConfigProperties.getNodeUrl()));
    }

    @Bean
    public EthChainId ethereumNetworkChainId(EthereumNetworkConfigProperties ethereumNetworkConfigProperties) {
        final var ethChainId = new EthChainId();
        ethChainId.setResult(ethereumNetworkConfigProperties.getNetworkChainId());
        return ethChainId;
    }

}

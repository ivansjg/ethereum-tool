package com.technest.ethereum.tool.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("ethereum")
public class EthereumNetworkConfigProperties {
    private String networkChainId;
    private String nodeUrl;
}

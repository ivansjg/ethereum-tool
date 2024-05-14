package com.technest.ethereum.tool.service;

import lombok.RequiredArgsConstructor;
import org.bitcoinj.core.ECKey;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class EthereumAddressGeneratorService {
    public String generateAddressFrom(ECKey pubKey) {
        // Ethereum uses uncompressed public keys, but removing the leading "04" (in hex) at the beginning, which is saying that the key is uncompressed.
        // https://www.rfctools.com/ethereum-address-test-tool/
        final var uncompressedPublicKey = pubKey.decompress().getPubKey();
        return Numeric.toHexString(Keys.getAddress(Arrays.copyOfRange(uncompressedPublicKey, 1, uncompressedPublicKey.length)));
    }

}

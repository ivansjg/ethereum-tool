package com.technest.ethereum.tool.shell;

import com.technest.ethereum.tool.service.EthereumAddressGeneratorService;
import com.technest.ethereum.tool.service.EthereumTransactionService;
import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.web3j.crypto.CryptoUtils;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.crypto.transaction.type.TransactionType;
import org.web3j.protocol.core.methods.response.EthChainId;
import org.web3j.tx.ChainId;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkState;
import static org.web3j.crypto.TransactionEncoder.createEip155SignatureData;
import static org.web3j.crypto.TransactionEncoder.encode;

@Log4j2
@RequiredArgsConstructor
@ShellComponent("Transaction")
public class TransactionShellComponent {

    private final EthereumAddressGeneratorService ethereumAddressGeneratorService;
    private final EthereumTransactionService ethereumTransactionService;
    private final EthChainId ethChainId;

    @ShellMethod(key = "createUnsignedRawEthereumTx", value = "Create an unsigned raw Ethereum transaction.")
    public String createUnsignedRawEthereumTx(String pubKeyInHex, String destinationAddress, BigDecimal usdAmount, @ShellOption(defaultValue = ShellOption.NULL) String contractAddress) {
        final var pubKey = ECKey.fromPublicOnly(Utils.HEX.decode(pubKeyInHex));
        final var sourceAddress = ethereumAddressGeneratorService.generateAddressFrom(pubKey);
        final var amountToSend = ethereumTransactionService.getTransactionAmount(sourceAddress, usdAmount, Option.of(contractAddress));
        final var nonce = ethereumTransactionService.getNonceFor(sourceAddress);
        final var maxFeePerGas = ethereumTransactionService.getMaxFeePerGas();
        final var maxPriorityFeePerGas = ethereumTransactionService.getMaxPriorityFeePerGas();
        final var gasLimit = ethereumTransactionService.estimateGasFor(nonce, destinationAddress, amountToSend, sourceAddress, Option.of(contractAddress));
        final var transaction = Option.of(contractAddress).isEmpty() ?
                ethereumTransactionService.createTransaction(nonce, destinationAddress, amountToSend, maxPriorityFeePerGas, maxFeePerGas, gasLimit):
                ethereumTransactionService.createTransaction(nonce, destinationAddress, amountToSend, maxPriorityFeePerGas, maxFeePerGas, gasLimit, contractAddress);

        final var ethRawTransactionInHex = HexFormat.of().formatHex(TransactionEncoder.encode(transaction));
        log.info("Hash to sign: {}", Utils.HEX.encode(getEthereumHashToSign(transaction, ethChainId.getId())));
        log.info("You can decode the transaction in https://rawtxdecode.in/");
        return ethRawTransactionInHex;
    }

    @ShellMethod(key = "addSignToUnsignedRawEthereumTx", value = "Add signature to an unsigned raw Ethereum transaction.")
    public String addSignToUnsignedRawEthereumTx(String pubKeyInHex, String txInHex, String signatureInHex, String messageHashInHex) {
        final var pubKey = ECKey.fromPublicOnly(Utils.HEX.decode(pubKeyInHex));
        final var sourceAddress = ethereumAddressGeneratorService.generateAddressFrom(pubKey);

        // It has to be in canonical form because of:
        //   https://github.com/ethereum/EIPs/blob/master/EIPS/eip-2.md
        //   All transaction signatures whose s-value is greater than secp256k1n/2 are now considered invalid. The ECDSA recover precompiled contract remains unchanged
        //   and will keep accepting high s-values; this is useful e.g. if a contract recovers old Bitcoin signatures.
        final var ecdsaSignature = toECDSASignature(Utils.HEX.decode(signatureInHex.toLowerCase())).toCanonicalised();

        // Check if signature is correct, recovering the public key from the signature, checking if sourceAddress and recoveredAddress are the same.
        final var recoveredAddress = IntStream.rangeClosed(0, 3).boxed()
                .map(i -> Try.of(() -> Sign.recoverFromSignature(i, CryptoUtils.fromDerFormat(ecdsaSignature.encodeToDER()), Utils.HEX.decode(messageHashInHex))))
                .map(Try::getOrNull)
                .filter(Objects::nonNull)
                .map(Keys::getAddress)
                .filter(address -> Numeric.prependHexPrefix(address).equals(sourceAddress)).findFirst();
        checkState(recoveredAddress.isPresent(), "Something wrong happened while doing the signature");

        final var uncompressedPublicKey = pubKey.decompress().getPubKey();
        final var uncompressedPublicKeyWithoutPrefix = Arrays.copyOfRange(uncompressedPublicKey, 1, uncompressedPublicKey.length);

        final var signatureData = Sign.createSignatureData(CryptoUtils.fromDerFormat(ecdsaSignature.encodeToDER()), new BigInteger(Utils.HEX.encode(uncompressedPublicKeyWithoutPrefix), 16), Utils.HEX.decode(messageHashInHex));
        final var signedTransaction = addSignatureToEthereumTx(TransactionDecoder.decode(txInHex), signatureData, ethChainId.getId());

        log.info("You can decode the transaction in https://rawtxdecode.in/");
        return Numeric.toHexString(signedTransaction);
    }

    private static ECKey.ECDSASignature toECDSASignature(byte[] signature) {
        final var n = signature.length / 2;
        final var r = Arrays.copyOfRange(signature, 0, n);
        final var s = Arrays.copyOfRange(signature, n, 2 * n);

        return new ECKey.ECDSASignature(new BigInteger(1, r), new BigInteger(1, s));
    }

    private byte[] getEthereumHashToSign(RawTransaction rawTransaction, long chainId) {
        byte[] encodedTransaction;

        // Legacy tx is tx before Eip1559, should have chainId as an additional parameter.
        // After Eip1559 chainId is a part of tx.
        boolean isLegacy =
                chainId > ChainId.NONE && rawTransaction.getType().equals(TransactionType.LEGACY);

        if (isLegacy) {
            encodedTransaction = encode(rawTransaction, chainId);
        } else {
            encodedTransaction = encode(rawTransaction);
        }

        return Hash.sha3(encodedTransaction);
    }

    private byte[] addSignatureToEthereumTx(RawTransaction rawTransaction, Sign.SignatureData signatureData, long chainId) {
        boolean isLegacy =
                chainId > ChainId.NONE && rawTransaction.getType().equals(TransactionType.LEGACY);
        if (isLegacy) {
            signatureData = createEip155SignatureData(signatureData, chainId);
        }

        return encode(rawTransaction, signatureData);
    }

}

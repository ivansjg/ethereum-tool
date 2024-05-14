package com.technest.ethereum.tool.service;

import com.technest.ethereum.tool.exchangerate.ExchangeRateService;
import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthChainId;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Log4j2
public class EthereumTransactionService {

    private final Web3j web3j;
    private final EthChainId chainId;
    private final ERC20Service erc20Service;
    private final ExchangeRateService exchangeRateService;

    public RawTransaction createTransaction(BigInteger nonce, String destinationAddress, BigInteger amountToSend, BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas,
                                            BigInteger gasLimit, String contractAddress) {
        final var data = FunctionEncoder.encode(buildERC20TransferFunction(destinationAddress, amountToSend));
        return RawTransaction.createTransaction(chainId.getChainId().longValue(), nonce, gasLimit, contractAddress, BigInteger.ZERO, data, maxPriorityFeePerGas, maxFeePerGas);
    }

    public RawTransaction createTransaction(BigInteger nonce, String destinationAddress, BigInteger amountToSend, BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas,
                                            BigInteger gasLimit) {

        return RawTransaction.createEtherTransaction(chainId.getChainId().longValue(), nonce, gasLimit, destinationAddress, amountToSend, maxPriorityFeePerGas, maxFeePerGas);
    }

    public BigInteger estimateGasFor(BigInteger nonce, String destinationAddress, BigInteger amountToSend, String sourceAddress, Option<String> maybeContractAddress) {
        return maybeContractAddress.map(contractAddress ->
                estimateGasFor(nonce, contractAddress, BigInteger.ZERO, sourceAddress, FunctionEncoder.encode(buildERC20TransferFunction(destinationAddress, amountToSend)))
        ).getOrElse(() -> estimateGasFor(nonce, destinationAddress, amountToSend, sourceAddress, (String)null));
    }

    public BigInteger estimateGasFor(BigInteger nonce, String destinationAddress, BigInteger amountToSend, String sourceAddress, String data) {
        final var result = Try
                .of(() -> web3j.ethEstimateGas(
                                new Transaction(
                                        sourceAddress,
                                        nonce,
                                        null,
                                        null,
                                        destinationAddress,
                                        amountToSend,
                                        data,
                                        chainId.getChainId().longValue(),
                                        null,
                                        null)
                        ).send()
                )
                .getOrElseThrow(e -> new RuntimeException("Couldn't send transaction", e));

        if (result.hasError()) {
            throw new RuntimeException("Cannot estimate gas price for transaction: " + result.getError().getMessage());
        }

        return result.getAmountUsed();
    }

    // Returns baseFeePerGas in weis
    public BigInteger getBaseFeePerGas() {
        final var lastBlockBaseFeePerGas = Try
                .of(() -> web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getBaseFeePerGas())
                .getOrElseThrow(e -> new RuntimeException("Cannot get base fee per gas", e));

        // Sometimes, when we estimate gas for a transaction, we had an error because maxFeePerGas was smaller than last block baseFeePerGas, so adding 2% to last
        // baseFeePerGas (which is involved in maxFeePerGas calculation) solves the problem.
        final var twoPercent = new BigDecimal(lastBlockBaseFeePerGas).multiply(new BigDecimal("0.02")).toBigInteger();
        return lastBlockBaseFeePerGas.add(lastBlockBaseFeePerGas.add(twoPercent));
    }

    // Returns maxPriorityFeePerGas in weis
    public BigInteger getMaxPriorityFeePerGas() {
        return Try
                .of(() -> web3j.ethMaxPriorityFeePerGas().send().getMaxPriorityFeePerGas())
                .getOrElseThrow(e -> new RuntimeException("Cannot get max priority fee per gas", e));
    }

    // Returns maxFeePerGas in weis
    public BigInteger getMaxFeePerGas() {
        final var baseFeePerGas = getBaseFeePerGas();
        final var maxPriorityFeePerGas = getMaxPriorityFeePerGas();
        return baseFeePerGas.add(maxPriorityFeePerGas);
    }

    // Returns the tx amount either in weis (if tx doesn't interact with any smart contract) or token units scaled without decimals (if interacting with a smart contract)
    public BigInteger getTransactionAmount(String sourceAddress, BigDecimal amountToSend, Option<String> maybeContractAddress) {
        return maybeContractAddress
                .map(contractAddress -> erc20Service.scaleToTokenUnitsWithoutDecimals(contractAddress, sourceAddress, amountToSend))
                .getOrElse(exchangeRateService.fromUsdtoWeis(amountToSend));
    }

    public BigInteger estimateFee(String sourceAddress, String destinationAddress, BigInteger amountToSend, Option<String> maybeContractAddress) {
        final var nonce = getNonceFor(sourceAddress);
        final var maxFeePerGas = getMaxFeePerGas();
        final var gasLimit = estimateGasFor(nonce, destinationAddress, amountToSend, sourceAddress, maybeContractAddress);
        return maxFeePerGas.multiply(gasLimit);
    }

    public BigInteger getNonceFor(String address) {
        return Try.of(() -> web3j.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST).sendAsync().get().getTransactionCount())
                .getOrElseThrow(e -> new RuntimeException("Cannot get nonce for " + address, e));
    }

    private Function buildERC20TransferFunction(String to, BigInteger value) {
        return erc20Service.buildTransferFunction(to, value);
    }

}

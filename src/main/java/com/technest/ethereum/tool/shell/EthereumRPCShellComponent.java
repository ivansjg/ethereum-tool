package com.technest.ethereum.tool.shell;

import com.technest.ethereum.tool.exchangerate.ExchangeRateService;
import com.technest.ethereum.tool.service.ERC20Service;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;

@Log4j2
@RequiredArgsConstructor
@ShellComponent("EthereumRPC")
public class EthereumRPCShellComponent {

    private final Web3j web3j;
    private final ExchangeRateService exchangeRateService;
    private final ERC20Service erc20Service;

    @ShellMethod(key = "ethRpcSendEthers", value = "Transfer ethers from an Ethereum account to another.")
    public String ethRpcSendEthers(String privateKey, String destinationAddress, BigDecimal amountInEthers) {
        final var credentials = Credentials.create(privateKey);
        final var transactionReceipt = Try.of(
                        () ->
                                Transfer.sendFunds(
                                        web3j,
                                        credentials,
                                        destinationAddress,
                                        amountInEthers,
                                        Convert.Unit.ETHER
                                ).sendAsync().get())
                .getOrElseThrow(e -> new RuntimeException("Couldn't send funds", e));

        return transactionReceipt.toString();
    }

    @ShellMethod(key = "ethRpcSendRawTx", value = "Send a raw transaction to be published in an Ethereum blockchain.")
    public String ethRpcSendRawTx(String txInHex) throws InterruptedException {
        final var result = Try.of(() -> web3j.ethSendRawTransaction(txInHex).send()).getOrElseThrow(e -> new RuntimeException("Couldn't send transaction", e));
        if (!result.hasError()) {
            EthGetTransactionReceipt transactionReceipt;
            do {
                Thread.sleep(3000);
                transactionReceipt = Try.of(() -> web3j.ethGetTransactionReceipt(result.getTransactionHash()).send()).getOrElseThrow(e -> new RuntimeException("Couldn't get transaction receipt", e));
                log.info("Tx sent with hash: {}", result.getTransactionHash());
                log.info("Waiting for the receipt...");
            } while(transactionReceipt.hasError() || transactionReceipt.getResult() == null);

            final var feeCost = transactionReceipt.getResult().getGasUsed().multiply(Numeric.decodeQuantity(transactionReceipt.getResult().getEffectiveGasPrice()));
            return "Tx sent with hash: " + result.getTransactionHash() + " - Fee cost in USD: " + exchangeRateService.fromWeisToUsd(feeCost);
        } else {
            throw new RuntimeException(result.getError().getMessage());
        }
    }

    @ShellMethod(key = "ethRpcGetBalance", value = "See balance of an Ethereum account.")
    public String ethRpcGetBalance(String address) {
        final var result = Try.of(() -> web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send()).getOrElseThrow(e -> new RuntimeException("Couldn't check balance for " + address, e));
        return result.hasError() ?
                result.getError().getMessage() :
                "Balance in USD: " + exchangeRateService.fromWeisToUsd(result.getBalance());
    }

    @ShellMethod(key = "ethRpcGetBalanceFromERC20Token", value = "See balance of an Ethereum account within a given ERC-20 token.")
    public String ethRpcGetBalanceFromERC20Token(String contractAddress, String address) {
        final var tokenName = erc20Service.getTokenName(contractAddress, address);
        final var tokenSymbol = erc20Service.getTokenSymbol(contractAddress, address);
        final var tokenDecimals = erc20Service.getTokenDecimals(contractAddress, address);
        final var balance = erc20Service.getBalanceOf(contractAddress, address);
        return "Balance of token (" + tokenName + "): " + new BigDecimal(balance).divide(BigDecimal.TEN.pow(tokenDecimals.intValue())) + " " + tokenSymbol;
    }

}


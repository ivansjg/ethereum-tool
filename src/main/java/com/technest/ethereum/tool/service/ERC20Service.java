package com.technest.ethereum.tool.service;

import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.tx.exceptions.ContractCallException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ERC20Service {

    private final Web3j web3j;

    public Function buildTransferFunction(String to, BigInteger value) {
        return buildERC20Function(
                "transfer",
                List.of(new org.web3j.abi.datatypes.Address(to), new org.web3j.abi.datatypes.generated.Uint256(value)),
                Collections.emptyList()
        );
    }

    public String getTokenSymbol(String contractAddress, String address) {
        final var symbolFunction = buildERC20Function("symbol", Collections.emptyList(), List.of(new TypeReference<Utf8String>() {}));
        return executeERC20Call(symbolFunction, address, contractAddress, String.class);
    }

    public String getTokenName(String contractAddress, String address) {
        final var nameFunction = buildERC20Function("name", Collections.emptyList(), List.of(new TypeReference<Utf8String>() {}));
        return executeERC20Call(nameFunction, address, contractAddress, String.class);
    }

    public BigInteger getTokenDecimals(String contractAddress, String address) {
        final var decimalsFunction = buildERC20Function("decimals", Collections.emptyList(), List.of(new TypeReference<Uint8>() {}));
        return executeERC20Call(decimalsFunction, address, contractAddress, BigInteger.class);
    }

    public BigInteger getBalanceOf(String contractAddress, String address) {
        final var balanceFunction = buildERC20Function("balanceOf", List.of(new Address(address)), List.of(new TypeReference<Uint256>() {}));
        return executeERC20Call(balanceFunction, address, contractAddress, BigInteger.class);
    }

    public BigInteger scaleToTokenUnitsWithoutDecimals(String contractAddress, String address, BigDecimal value) {
        final var tokenDecimals = getTokenDecimals(contractAddress, address);
        return value.multiply(BigDecimal.TEN.pow(tokenDecimals.intValue())).toBigIntegerExact();
    }

    private Function buildERC20Function(String functionName, List<Type> inputParameters, List<TypeReference<?>> outputParameters) {
        return new Function(functionName, inputParameters, outputParameters);
    }

    private <T extends Type> Option<T> executeERC20Call(Function function, String fromAddress, String contractAddress) {
        final var encodedFunction = FunctionEncoder.encode(function);

        final var response = Try.of(() -> web3j.ethCall(
                        Transaction.createEthCallTransaction(fromAddress, contractAddress, encodedFunction), DefaultBlockParameterName.LATEST)
                .sendAsync()
                .get()
        ).getOrElseThrow(e -> new RuntimeException("Couldn't make ERC-20 call", e));

        return (Option<T>)io.vavr.collection.List.ofAll(FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters())).headOption();
    }

    private <T extends Type, R> R executeERC20Call(Function function, String fromAddress, String contractAddress, Class<R> returnType) {
        final var result = executeERC20Call(function, fromAddress, contractAddress);

        if (result.isEmpty()) {
            throw new ContractCallException("Empty value returned from contract");
        }

        final var value = result.get().getValue();
        if (returnType.isAssignableFrom(result.getClass())) {
            return (R) result;
        } else if (returnType.isAssignableFrom(value.getClass())) {
            return (R) value;
        } else if (result.getClass().equals(Address.class) && returnType.equals(String.class)) {
            return (R) result.toString();
        } else {
            throw new ContractCallException(
                    "Unable to convert response: "
                            + value
                            + " to expected type: "
                            + returnType.getSimpleName());
        }
    }

}

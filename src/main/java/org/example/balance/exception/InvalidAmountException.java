package org.example.balance.exception;

/**
 * The amount of an operation (or an initial balance) is not acceptable.
 */
public class InvalidAmountException extends BalanceException {
    private InvalidAmountException(String message) {
        super(message);
    }

    public static InvalidAmountException notPositive(long amount) {
        return new InvalidAmountException("Amount must be greater than zero but was " + amount);
    }

    public static InvalidAmountException negativeInitialBalance(long initialBalance) {
        return new InvalidAmountException("Initial balance must not be negative but was " + initialBalance);
    }
}

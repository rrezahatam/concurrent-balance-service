package org.example.balance.exception;

public class InsufficientFundsException extends BalanceException {

    public InsufficientFundsException(String accountId, long balance, long requested) {
        super("Insufficient funds on account " + accountId +
                ": balance=" + balance + ", requested=" + requested);
    }
}

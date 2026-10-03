package org.example.balance.exception;

/**
 * A transfer whose source and destination are the same account is rejected.
 */
public class SameAccountTransferException extends BalanceException {

    public SameAccountTransferException(String accountId) {
        super("Source and destination must differ but both were " + accountId);
    }
}

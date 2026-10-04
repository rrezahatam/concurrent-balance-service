package org.example.balance.exception;

/**
 * A transaction id was reused for a request with a different payload (other type, account or amount).
 */
public class IdempotencyConflictException extends BalanceException {

    public IdempotencyConflictException(String transactionId) {
        super("Transaction id " + transactionId + " was already used for a different request");
    }
}
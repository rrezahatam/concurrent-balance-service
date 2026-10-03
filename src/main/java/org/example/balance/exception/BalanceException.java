package org.example.balance.exception;

/**
 * Base type of every business-level failure raised by the service.
 */
public abstract class BalanceException extends RuntimeException {

    protected BalanceException(String message) {
        super(message);
    }
}

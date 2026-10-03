package org.example.balance.exception;

/**
 * An identifier (account id or transaction id) is null or blank.
 */
public class InvalidIdentifierException extends BalanceException {

    public InvalidIdentifierException(String fieldName) {
        super(fieldName + " must not be null or blank");
    }
}

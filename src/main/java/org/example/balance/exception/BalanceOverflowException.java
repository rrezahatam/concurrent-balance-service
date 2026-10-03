package org.example.balance.exception;

/**
 * Applying the operation would overflow the balance ({@code long}) of an account.
 */
public class BalanceOverflowException extends BalanceException {

    public BalanceOverflowException(String accountId) {
        super("Balance overflow on account " + accountId);
    }
}

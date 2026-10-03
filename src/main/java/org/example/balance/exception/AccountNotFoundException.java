package org.example.balance.exception;

public class AccountNotFoundException extends BalanceException {

    public AccountNotFoundException(String accountId) {
        super("Account not found: " + accountId);
    }
}

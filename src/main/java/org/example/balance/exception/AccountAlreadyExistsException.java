package org.example.balance.exception;

public class AccountAlreadyExistsException extends BalanceException {

    public AccountAlreadyExistsException(String accountId) {
        super("Account already exists: " + accountId);
    }
}

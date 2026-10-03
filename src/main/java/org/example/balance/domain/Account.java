package org.example.balance.domain;

import org.example.balance.exception.InsufficientFundsException;
import org.example.balance.exception.InvalidAmountException;
import org.example.balance.exception.InvalidIdentifierException;

public final class Account {
    private final String id;
    private long balance;

    public Account(String id, long initialBalance) {
        if (id == null || id.isBlank()) {
            throw new InvalidIdentifierException("accountId");
        }
        if (initialBalance < 0) {
            throw InvalidAmountException.negativeInitialBalance(initialBalance);
        }

        this.id = id;
        this.balance = initialBalance;
    }

    public String getId() {
        return id;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    public void deposit(long amount) {
        balance += amount;
    }

    public void withdraw(long amount) {
        if (balance < amount) throw new InsufficientFundsException(id, balance, amount);
        balance -= amount;
    }

}

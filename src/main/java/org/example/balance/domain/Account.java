package org.example.balance.domain;

import org.example.balance.exception.BalanceOverflowException;
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

    /** Fails if {@code amount} cannot be withdrawn. Has no side effects. */
    public void ensureCanWithdraw(long amount) {
        requirePositive(amount);
        if (balance < amount) {
            throw new InsufficientFundsException(id, balance, amount);
        }
    }

    /** Fails if {@code amount} cannot be deposited without overflow. Has no side effects. */
    public void ensureCanDeposit(long amount) {
        requirePositive(amount);
        if (balance > Long.MAX_VALUE - amount) {
            throw new BalanceOverflowException(id);
        }
    }

    public void withdraw(long amount) {
        ensureCanWithdraw(amount);
        balance -= amount;
    }

    public void deposit(long amount) {
        ensureCanDeposit(amount);
        balance += amount;
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw InvalidAmountException.notPositive(amount);
        }
    }
}

package org.example.balance.domain;

import org.example.balance.exception.BalanceOverflowException;
import org.example.balance.exception.InsufficientFundsException;
import org.example.balance.exception.InvalidAmountException;
import org.example.balance.exception.InvalidIdentifierException;

import java.util.concurrent.locks.ReentrantLock;


/**
 * A single account. The balance is <b>guarded by this account's own lock</b>: every read and write
 * must happen while the calling thread holds {@link #lock()}. Violations fail fast with an
 * {@link IllegalStateException} instead of silently racing.
 */
public final class Account {
    private final String id;
    private final ReentrantLock lock = new ReentrantLock();
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

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public String getId() {
        return id;
    }

    /**
     * True if any thread holds the lock. Observability only; never use it to decide whether to lock.
     */
    public boolean isLocked() {
        return lock.isLocked();
    }

    /**
     * Current balance. The caller must hold the lock.
     */
    public long getBalance() {
        requireLockHeld();
        return balance;
    }

    /**
     * Fails if {@code amount} cannot be withdrawn. Has no side effects. The caller must hold the lock.
     */
    public void ensureCanWithdraw(long amount) {
        requireLockHeld();
        requirePositive(amount);
        if (balance < amount) {
            throw new InsufficientFundsException(id, balance, amount);
        }
    }

    /**
     * Fails if {@code amount} cannot be deposited without overflow. Has no side effects. The caller must hold the lock.
     */
    public void ensureCanDeposit(long amount) {
        requireLockHeld();
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

    private void requireLockHeld() {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("Account " + id + " accessed without holding its lock");
        }
    }
}

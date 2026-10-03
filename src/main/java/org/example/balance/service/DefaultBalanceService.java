package org.example.balance.service;

import org.example.balance.domain.Account;
import org.example.balance.domain.AccountRepository;

public final class DefaultBalanceService implements BalanceService{

    private final AccountRepository accounts;

    public DefaultBalanceService(AccountRepository accounts) {
        this.accounts = accounts;
    }


    @Override
    public void credit(String accountId, long amount, String transactionId) {
        accounts.getOrThrow(accountId).deposit(amount);
    }

    @Override
    public void debit(String accountId, long amount, String transactionId) {
        accounts.getOrThrow(accountId).withdraw(amount);
    }

    @Override
    public void transfer(String sourceAccountId, String destinationAccountId, long amount, String transactionId) {
        Account source = accounts.getOrThrow(sourceAccountId);
        Account destination = accounts.getOrThrow( destinationAccountId );

        source.withdraw(amount);
        destination.deposit(amount);
    }

    @Override
    public long getBalance(String accountId) {
        return accounts.getOrThrow(accountId).getBalance();
    }
}

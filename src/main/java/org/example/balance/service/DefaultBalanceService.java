package org.example.balance.service;

import org.example.balance.Account;
import org.example.balance.AccountRepository;
import org.example.balance.exception.AccountNotFoundException;

public final class DefaultBalanceService implements BalanceService{

    private final AccountRepository accounts;

    public DefaultBalanceService(AccountRepository accounts) {
        this.accounts = accounts;
    }


    @Override
    public void credit(String accountId, long amount, String transactionId) {
        accounts.find(accountId).orElseThrow(
                ()-> new AccountNotFoundException(accountId)
        ).deposit(amount);
    }

    @Override
    public void debit(String accountId, long amount, String transactionId) {
        accounts.find(accountId).orElseThrow(
                ()-> new AccountNotFoundException(accountId)
        ).withdraw(amount);
    }

    @Override
    public void transfer(String sourceAccountId, String destinationAccountId, long amount, String transactionId) {
        Account source = accounts.find(sourceAccountId).orElseThrow(
                () -> new AccountNotFoundException( sourceAccountId )
        );

        Account destination = accounts.find( destinationAccountId ).orElseThrow(
                ()-> new AccountNotFoundException( destinationAccountId )
        );

        source.withdraw(amount);
        destination.deposit(amount);
    }

    @Override
    public long getBalance(String accountId) {
        return accounts.find(accountId).orElseThrow(
                ()-> new AccountNotFoundException(accountId)
        ).getBalance();
    }
}

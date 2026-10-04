package org.example.balance.service;

import org.example.balance.domain.Account;
import org.example.balance.domain.AccountRepository;
import org.example.balance.exception.SameAccountTransferException;

public final class DefaultBalanceService implements BalanceService {

    private final AccountRepository accounts;

    public DefaultBalanceService(AccountRepository accounts) {
        this.accounts = accounts;
    }


    @Override
    public void credit(String accountId, long amount, String transactionId) {
        Validation.identifier("accountId", accountId);
        Validation.identifier("transactionId", transactionId);
        Validation.positiveAmount(amount);

        Account account = accounts.getOrThrow(accountId);
        AccountLocking.run(account, () -> account.deposit(amount));
    }

    @Override
    public void debit(String accountId, long amount, String transactionId) {
        Validation.identifier("accountId", accountId);
        Validation.identifier("transactionId", transactionId);
        Validation.positiveAmount(amount);

        Account account = accounts.getOrThrow(accountId);
        AccountLocking.run(account, () -> account.withdraw(amount));
    }

    @Override
    public void transfer(String sourceAccountId, String destinationAccountId, long amount, String transactionId) {
        Validation.identifier("sourceAccountId", sourceAccountId);
        Validation.identifier("destinationAccountId", destinationAccountId);
        Validation.identifier("transactionId", transactionId);
        Validation.positiveAmount(amount);
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new SameAccountTransferException(sourceAccountId);
        }

        Account source = accounts.getOrThrow(sourceAccountId);
        Account destination = accounts.getOrThrow(destinationAccountId);

        AccountLocking.runOrdered(source, destination, () -> {
            source.ensureCanWithdraw(amount);
            destination.ensureCanDeposit(amount);
            source.withdraw(amount);
            destination.deposit(amount);
        });
    }

    @Override
    public long getBalance(String accountId) {
        Validation.identifier("accountId", accountId);
        Account account = accounts.getOrThrow(accountId);
        return AccountLocking.read(account, account::getBalance);
    }


}

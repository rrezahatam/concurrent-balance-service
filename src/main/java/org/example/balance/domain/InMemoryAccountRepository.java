package org.example.balance.domain;

import org.example.balance.exception.AccountAlreadyExistsException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryAccountRepository implements AccountRepository {

    private final ConcurrentMap<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public Account create(String accountId, long initialBalance) {
        Account account = new Account(accountId, initialBalance); // validates its arguments
        if (accounts.putIfAbsent(accountId, account) != null)     // atomic: no check-then-act
            throw new AccountAlreadyExistsException(accountId);

        return account;
    }

    @Override
    public Optional<Account> find(String accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }
}

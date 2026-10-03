package org.example.balance.domain;

import org.example.balance.exception.AccountAlreadyExistsException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> accounts = new HashMap<>();

    @Override
    public Account create(String accountId, long initialBalance) {
        if (accounts.containsKey(accountId))
            throw new AccountAlreadyExistsException(accountId);

        Account account = new Account(accountId, initialBalance);
        accounts.put(accountId, account);
        return account;
    }

    @Override
    public Optional<Account> find(String accountId) {
        return Optional.ofNullable(accounts.get(accountId));
    }
}

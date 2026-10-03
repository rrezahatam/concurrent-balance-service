package org.example.balance;

import java.util.Optional;

/**
 * Access to accounts. There is exactly one {@link Account} instance per account id for the lifetime
 * of the repository, which is what makes "lock the account object" equivalent to "lock the account".
 */
public interface AccountRepository {

    /** @throws org.example.balance.exception.AccountAlreadyExistsException if the id is taken */
    Account create(String accountId, long initialBalance);

    Optional<Account> find(String accountId);

}

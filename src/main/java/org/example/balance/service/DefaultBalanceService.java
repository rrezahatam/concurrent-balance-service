package org.example.balance.service;

import org.example.balance.domain.Account;
import org.example.balance.domain.AccountRepository;
import org.example.balance.exception.SameAccountTransferException;
import org.example.balance.idempotency.IdempotencyGuard;
import org.example.balance.idempotency.OperationFingerprint;

/**
 * In-memory implementation. Request handling always follows the same order:
 * <ol>
 *   <li>stateless validation (cheap, nothing is recorded);</li>
 *   <li>account lookup (unknown account: rejected, nothing is recorded);</li>
 *   <li>claim the transaction id via {@link IdempotencyGuard} (duplicates stop here);</li>
 *   <li>run the operation under the account lock(s).</li>
 * </ol>
 */
public final class DefaultBalanceService implements BalanceService {

    private final AccountRepository accounts;
    private final IdempotencyGuard idempotency;

    public DefaultBalanceService(AccountRepository accounts, IdempotencyGuard idempotency) {
        this.accounts = accounts;
        this.idempotency = idempotency;
    }


    @Override
    public void credit(String accountId, long amount, String transactionId) {
        Validation.identifier("accountId", accountId);
        Validation.identifier("transactionId", transactionId);
        Validation.positiveAmount(amount);

        Account account = accounts.getOrThrow(accountId);
        idempotency.execute(transactionId, OperationFingerprint.credit(accountId, amount),
                () -> AccountLocking.run(account, () -> account.deposit(amount)));
    }

    @Override
    public void debit(String accountId, long amount, String transactionId) {
        Validation.identifier("accountId", accountId);
        Validation.identifier("transactionId", transactionId);
        Validation.positiveAmount(amount);

        Account account = accounts.getOrThrow(accountId);
        idempotency.execute(transactionId, OperationFingerprint.debit(accountId, amount),
                () -> AccountLocking.run(account, () -> account.withdraw(amount)));
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

        idempotency.execute(transactionId,
                OperationFingerprint.transfer(sourceAccountId, destinationAccountId, amount),
                () -> AccountLocking.runOrdered(source, destination, () -> {
                    // Both accounts are locked. Every check comes BEFORE the first mutation, and the
                    // mutations below cannot fail once the checks passed, so a half transfer is impossible.
                    source.ensureCanWithdraw(amount);
                    destination.ensureCanDeposit(amount);
                    source.withdraw(amount);
                    destination.deposit(amount);
                }));
    }

    @Override
    public long getBalance(String accountId) {
        Validation.identifier("accountId", accountId);
        Account account = accounts.getOrThrow(accountId);
        return AccountLocking.read(account, account::getBalance);
    }


}

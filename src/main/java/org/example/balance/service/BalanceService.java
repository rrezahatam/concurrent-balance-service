package org.example.balance.service;

import org.example.balance.exception.AccountNotFoundException;
import org.example.balance.exception.IdempotencyConflictException;
import org.example.balance.exception.InsufficientFundsException;
import org.example.balance.exception.InvalidAmountException;
import org.example.balance.exception.InvalidIdentifierException;
import org.example.balance.exception.SameAccountTransferException;

/**
 * Account balance operations. All amounts are in the smallest currency unit.
 *
 * <p>Every mutating operation is <b>idempotent per {@code transactionId}</b>: repeating a request with
 * the same id and the same payload (sequentially or concurrently) has the effect of one execution.
 * Reusing an id with a different payload fails with {@link IdempotencyConflictException}.
 *
 * <p>Stateless argument errors ({@link InvalidAmountException}, {@link InvalidIdentifierException},
 * {@link SameAccountTransferException}) and {@link AccountNotFoundException} are raised before the
 * transaction id is claimed and are therefore never remembered.
 */
public interface BalanceService {

    void credit(String accountId, long amount, String transactionId);

    /** @throws InsufficientFundsException if the balance is lower than {@code amount} (nothing changes) */
    void debit(String accountId, long amount, String transactionId);

    /**
     * Atomically moves {@code amount} from source to destination.
     *
     * @throws SameAccountTransferException if both ids are equal
     * @throws InsufficientFundsException   if the source cannot cover the amount (neither account changes)
     */
    void transfer(String sourceAccountId, String destinationAccountId, long amount, String transactionId);

    /** Linearizable read of one account. There is no consistent snapshot across several accounts. */
    long getBalance(String accountId);
}

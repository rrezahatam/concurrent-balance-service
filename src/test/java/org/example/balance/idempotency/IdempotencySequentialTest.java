package org.example.balance.idempotency;

import org.example.balance.domain.AccountRepository;
import org.example.balance.domain.InMemoryAccountRepository;
import org.example.balance.exception.AccountNotFoundException;
import org.example.balance.exception.IdempotencyConflictException;
import org.example.balance.exception.InsufficientFundsException;
import org.example.balance.exception.InvalidAmountException;
import org.example.balance.exception.SameAccountTransferException;
import org.example.balance.service.DefaultBalanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Retries that arrive one after another.
 */
class IdempotencySequentialTest {
    private DefaultBalanceService service;
    private AccountRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAccountRepository();
        repository.create("A", 1_000);
        repository.create("B", 500);
        service = new DefaultBalanceService(repository, new IdempotencyGuard());

    }

    private void assertBalance(String accountId, long expected) {
        assertEquals(expected, service.getBalance(accountId), "balance of " + accountId);
    }

    @Test
    void credit_sentThreeTimes_isAppliedOnce() {
        service.credit("A", 100, "TX-1");
        service.credit("A", 100, "TX-1");
        service.credit("A", 100, "TX-1");
        assertBalance("A", 1_100);
    }

    @Test
    void debit_sentThreeTimes_isAppliedOnce() {
        service.debit("A", 100, "TX-1");
        service.debit("A", 100, "TX-1");
        service.debit("A", 100, "TX-1");
        assertBalance("A", 900);
    }

    @Test
    void transfer_sentThreeTimes_isAppliedOnce() {
        service.transfer("A", "B", 300, "TX-1");
        service.transfer("A", "B", 300, "TX-1");
        service.transfer("A", "B", 300, "TX-1");
        assertBalance("A", 700);
        assertBalance("B", 800);
    }

    @Test
    void sameTransactionId_withADifferentAmount_isAConflict() {
        service.credit("A", 100, "TX-1");
        assertThrows(IdempotencyConflictException.class, () -> service.credit("A", 200, "TX-1"));
        assertBalance("A", 1_100);
    }

    @Test
    void sameTransactionId_withADifferentOperationType_isAConflict() {
        service.credit("A", 100, "TX-1");
        assertThrows(IdempotencyConflictException.class, () -> service.debit("A", 100, "TX-1"));
        assertBalance("A", 1_100);
    }

    @Test
    void sameTransactionId_withADifferentAccount_isAConflict() {
        service.credit("A", 100, "TX-1");
        assertThrows(IdempotencyConflictException.class, () -> service.credit("B", 100, "TX-1"));
        assertBalance("B", 500);
    }

    @Test
    void aRejectedDebit_isReplayedAsRejected_evenIfFundsArriveLater() {
        assertThrows(InsufficientFundsException.class, () -> service.debit("A", 1_200, "TX-FAIL"));
        service.credit("A", 500, "TX-TOPUP");

        // Same id, same payload: the original outcome is replayed, the request is not re-evaluated.
        assertThrows(InsufficientFundsException.class, () -> service.debit("A", 1_200, "TX-FAIL"));
        assertBalance("A", 1_500);

        // A caller that really wants to try again uses a new transaction id.
        service.debit("A", 1_200, "TX-FAIL-RETRY");
        assertBalance("A", 300);
    }

    @Test
    void invalidRequests_areNotRemembered() {
        assertThrows(InvalidAmountException.class, () -> service.credit("A", 0, "TX-1"));
        service.credit("A", 100, "TX-1");
        assertBalance("A", 1_100);
    }

    @Test
    void requestsForUnknownAccounts_areNotRemembered() {
        assertThrows(AccountNotFoundException.class, () -> service.credit("C", 100, "TX-1"));
        repository.create("C", 0);
        service.credit("C", 100, "TX-1");
        assertBalance("C", 100);
    }

    @Test
    void sameAccountTransfers_areNotRemembered() {
        assertThrows(SameAccountTransferException.class, () -> service.transfer("A", "A", 100, "TX-1"));
        service.transfer("A", "B", 100, "TX-1");
        assertBalance("A", 900);
        assertBalance("B", 600);
    }
}
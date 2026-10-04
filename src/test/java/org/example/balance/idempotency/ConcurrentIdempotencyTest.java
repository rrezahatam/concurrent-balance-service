package org.example.balance.idempotency;

import org.example.balance.domain.AccountRepository;
import org.example.balance.domain.InMemoryAccountRepository;
import org.example.balance.exception.IdempotencyConflictException;
import org.example.balance.exception.InsufficientFundsException;
import org.example.balance.service.DefaultBalanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.example.balance.support.ConcurrentTestSupport.assertNoFailures;
import static org.example.balance.support.ConcurrentTestSupport.assertOnlyExpectedFailures;
import static org.example.balance.support.ConcurrentTestSupport.countOf;
import static org.example.balance.support.ConcurrentTestSupport.runConcurrently;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The "important note" of the task: duplicate requests that arrive AT THE SAME TIME. */
@Timeout(120)
class ConcurrentIdempotencyTest {

    private DefaultBalanceService service;

    @BeforeEach
    void setUp() {
        AccountRepository repository = new InMemoryAccountRepository();
        repository.create("A", 1_000);
        repository.create("B", 500);
        service = new DefaultBalanceService(repository, new IdempotencyGuard());
    }

    @RepeatedTest(50)
    void concurrentDuplicateCredits_areAppliedOnce() throws Exception {
        List<Throwable> results = runConcurrently(200, i -> service.credit("A", 100, "TX-1"));

        assertNoFailures(results); // duplicates are not errors: they observe the original success
        assertEquals(1_100, service.getBalance("A"));
    }

    @RepeatedTest(50)
    void concurrentDuplicateDebits_areAppliedOnce() throws Exception {
        List<Throwable> results = runConcurrently(200, i -> service.debit("A", 700, "TX-1"));

        assertNoFailures(results);
        assertEquals(300, service.getBalance("A"));
    }

    @RepeatedTest(50)
    void concurrentDuplicateTransfers_areAppliedOnce() throws Exception {
        List<Throwable> results = runConcurrently(200, i -> service.transfer("A", "B", 300, "TX-1"));

        assertNoFailures(results);
        assertEquals(700, service.getBalance("A"));
        assertEquals(800, service.getBalance("B"));
    }

    @Test
    void aFailingDebit_failsConsistentlyForEveryConcurrentDuplicate() throws Exception {
        List<Throwable> results = runConcurrently(100, i -> service.debit("A", 1_200, "TX-1"));

        assertOnlyExpectedFailures(results, InsufficientFundsException.class);
        assertEquals(100, countOf(results, InsufficientFundsException.class));
        assertEquals(1_000, service.getBalance("A"));
    }

    @Test
    void manyTransactions_eachSentTenTimesConcurrently_areEachAppliedOnce() throws Exception {
        // 1,000 requests = 100 distinct transactions x 10 duplicates each, all interleaved.
        List<Throwable> results = runConcurrently(1_000, i -> service.credit("A", 10, "TX-" + (i % 100)));

        assertNoFailures(results);
        assertEquals(1_000 + 100 * 10, service.getBalance("A"));
    }

    /** One id, two different payloads, racing: exactly one payload wins, the other is a conflict. */
    @RepeatedTest(50)
    void concurrentRequestsReusingOneIdWithDifferentPayloads_yieldExactlyOneWinner() throws Exception {
        List<Throwable> results = runConcurrently(100, i -> service.credit("A", i % 2 == 0 ? 100 : 200, "TX-1"));

        assertOnlyExpectedFailures(results, IdempotencyConflictException.class);
        assertEquals(50, countOf(results, IdempotencyConflictException.class));
        long balance = service.getBalance("A");
        assertTrue(balance == 1_100 || balance == 1_200, "exactly one of the two payloads may be applied, balance=" + balance);
    }
}

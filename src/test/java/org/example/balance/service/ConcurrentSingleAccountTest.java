package org.example.balance.service;

import org.example.balance.domain.AccountRepository;
import org.example.balance.domain.InMemoryAccountRepository;
import org.example.balance.exception.InsufficientFundsException;
import org.example.balance.idempotency.IdempotencyGuard;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.example.balance.support.ConcurrentTestSupport.assertNoFailures;
import static org.example.balance.support.ConcurrentTestSupport.assertOnlyExpectedFailures;
import static org.example.balance.support.ConcurrentTestSupport.countOf;
import static org.example.balance.support.ConcurrentTestSupport.runConcurrently;
import static org.example.balance.support.ConcurrentTestSupport.successes;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Many threads, ONE account, all with distinct transaction ids: lost updates and overdrafts must be impossible.
 */
@Timeout(120)
class ConcurrentSingleAccountTest {
    private static DefaultBalanceService serviceWithAccountA(long initialBalance) {
        AccountRepository repository = new InMemoryAccountRepository();
        repository.create("A", initialBalance);
        return new DefaultBalanceService(repository, new IdempotencyGuard());
    }

    /**
     * The example from the task: balance 1,000, two threads each debit 700 => exactly one may win.
     */
    @RepeatedTest(200)
    void twoConcurrentDebits_onlyOneSucceeds() throws Exception {
        DefaultBalanceService service = serviceWithAccountA(1_000);

        List<Throwable> results = runConcurrently(2, i -> service.debit("A", 700, "TX-" + i));

        assertOnlyExpectedFailures(results, InsufficientFundsException.class);
        assertEquals(1, successes(results));
        assertEquals(1, countOf(results, InsufficientFundsException.class));
        assertEquals(300, service.getBalance("A"));
    }

    @Test
    void manyConcurrentDebits_neverOverdraw() throws Exception {
        DefaultBalanceService service = serviceWithAccountA(1_000);

        List<Throwable> results = runConcurrently(100, i -> service.debit("A", 30, "TX-" + i));

        assertOnlyExpectedFailures(results, InsufficientFundsException.class);
        assertEquals(33, successes(results), "1,000 / 30 = 33 debits fit");
        assertEquals(67, countOf(results, InsufficientFundsException.class));
        assertEquals(10, service.getBalance("A"));
    }

    /**
     * 1,000 concurrent operations on 100,000: 600 credits of 50 and 400 debits of 30.
     */
    @Test
    void mixedConcurrentCreditsAndDebits_endWithTheExactExpectedBalance() throws Exception {
        DefaultBalanceService service = serviceWithAccountA(100_000);

        List<Throwable> results = runConcurrently(1_000, i -> {
            if (i % 5 < 3) {
                service.credit("A", 50, "CREDIT-" + i);
            } else {
                service.debit("A", 30, "DEBIT-" + i);
            }
        });

        assertNoFailures(results);
        assertEquals(100_000 + 600 * 50 - 400 * 30, service.getBalance("A")); // 118,000
    }

    /**
     * Lost update: 8 threads add 1, 25,000 times each. Every single credit must show up in the balance.
     */
    @Test
    void manyCreditsFromManyThreads_loseNoUpdate() throws Exception {
        DefaultBalanceService service = serviceWithAccountA(0);
        int threads = 8;
        int creditsPerThread = 25_000;

        List<Throwable> results = runConcurrently(threads, t -> {
            for (int n = 0; n < creditsPerThread; n++) {
                service.credit("A", 1, "CREDIT-" + t + "-" + n);
            }
        });

        assertNoFailures(results);
        assertEquals((long) threads * creditsPerThread, service.getBalance("A"));
    }

    /**
     * Overdraft: threads keep debiting 1 until the account is empty. Exactly the initial balance may be paid out.
     */
    @Test
    void debitsUntilTheAccountIsEmpty_payOutExactlyTheInitialBalance() throws Exception {
        long initialBalance = 100_000;
        DefaultBalanceService service = serviceWithAccountA(initialBalance);
        AtomicLong paidOut = new AtomicLong();

        List<Throwable> results = runConcurrently(8, t -> {
            for (int n = 0; ; n++) {
                try {
                    service.debit("A", 1, "DEBIT-" + t + "-" + n);
                    paidOut.incrementAndGet();
                } catch (InsufficientFundsException empty) {
                    return;
                }
            }
        });

        assertNoFailures(results);
        assertEquals(initialBalance, paidOut.get(), "more (or less) money was paid out than the account held");
        assertEquals(0, service.getBalance("A"));
    }
}
package org.example.balance.service;

import org.example.balance.domain.AccountRepository;
import org.example.balance.domain.InMemoryAccountRepository;
import org.example.balance.exception.InsufficientFundsException;
import org.example.balance.idempotency.IdempotencyGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.example.balance.support.ConcurrentTestSupport.assertNoFailures;
import static org.example.balance.support.ConcurrentTestSupport.runConcurrently;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Many threads, MANY accounts: atomic transfers, conservation of money, no deadlock, no global lock. */
@Timeout(180)
class ConcurrentMultiAccountTest {
    @Test
    void randomTransfers_conserveTheTotal_andNeverMakeABalanceNegative() throws Exception {
        int accountCount = 10;
        long initialBalance = 10_000;
        AccountRepository repository = new InMemoryAccountRepository();
        for (int i = 0; i < accountCount; i++) {
            repository.create("ACC-" + i, initialBalance);
        }
        DefaultBalanceService service = new DefaultBalanceService(repository, new IdempotencyGuard());

        AtomicInteger completedTransfers = new AtomicInteger();

        List<Throwable> results = runConcurrently(16, worker -> {
            Random random = new Random(42L + worker); // seeded per worker: a failure is reproducible
            for (int n = 0; n < 500; n++) {
                int from = random.nextInt(accountCount);
                int to = (from + 1 + random.nextInt(accountCount - 1)) % accountCount; // never equal to from
                long amount = 1 + random.nextInt(2_000);
                try {
                    service.transfer("ACC-" + from, "ACC-" + to, amount, "TX-" + worker + "-" + n);
                    completedTransfers.incrementAndGet();
                } catch (InsufficientFundsException expected) {
                    // allowed: the source simply did not have enough at that moment
                }
            }
        });

        assertNoFailures(results);
        assertTrue(completedTransfers.get() > 0, "the test must actually have moved money");
        long total = 0;
        for (int i = 0; i < accountCount; i++) {
            long balance = service.getBalance("ACC-" + i);
            assertTrue(balance >= 0, "ACC-" + i + " went negative: " + balance);
            total += balance;
        }
        assertEquals(accountCount * initialBalance, total, "money was created or destroyed");
    }

    /** Stronger than conservation: every single balance has an exactly predictable value. */
    @Test
    void manyAccountsPayingOneHub_leaveEveryBalanceExactlyAsPredicted() throws Exception {
        AccountRepository repository = new InMemoryAccountRepository();
        for (int i = 0; i < 50; i++) {
            repository.create("SPOKE-" + i, 1_000);
        }
        repository.create("HUB", 0);
        DefaultBalanceService service = new DefaultBalanceService(repository, new IdempotencyGuard());


        // 1,000 transfers of 10: every spoke is the source of exactly 20 of them.
        List<Throwable> results = runConcurrently(1_000, i ->
                service.transfer("SPOKE-" + (i % 50), "HUB", 10, "TX-" + i));

        assertNoFailures(results);
        for (int i = 0; i < 50; i++) {
            assertEquals(800, service.getBalance("SPOKE-" + i));
        }
        assertEquals(10_000, service.getBalance("HUB"));
    }

    /** A->B and B->A at the same time is the classic deadlock; the fixed lock order makes it impossible. */
    @Test
    void opposingTransfers_doNotDeadlock() {
        AccountRepository repository = new InMemoryAccountRepository();
        repository.create("A", 1_000_000);
        repository.create("B", 1_000_000);
        DefaultBalanceService service = new DefaultBalanceService(repository, new IdempotencyGuard());


        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            List<Throwable> results = runConcurrently(32, worker -> {
                for (int n = 0; n < 5_000; n++) {
                    String txId = "TX-" + worker + "-" + n;
                    if (worker % 2 == 0) {
                        service.transfer("A", "B", 1, txId);
                    } else {
                        service.transfer("B", "A", 1, txId);
                    }
                }
            });
            assertNoFailures(results);
        });

        assertEquals(1_000_000, service.getBalance("A"));
        assertEquals(1_000_000, service.getBalance("B"));
    }
}
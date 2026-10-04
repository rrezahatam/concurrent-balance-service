package org.example.balance.service;

import org.example.balance.domain.Account;
import org.example.balance.domain.InMemoryAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.example.balance.support.ConcurrentTestSupport.daemonThreads;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic tests of the locking protocol. They do not rely on lucky thread interleavings: the test
 * itself holds an account's lock and asserts that an operation touching that account cannot proceed
 * until the lock is released, while operations on other accounts are unaffected.
 */
class LockingProtocolTest {
    private InMemoryAccountRepository repository;
    private DefaultBalanceService service;
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAccountRepository();
        repository.create("A", 100);
        repository.create("B", 100);
        repository.create("C", 100);
        service = new DefaultBalanceService(repository);
        pool = Executors.newCachedThreadPool(daemonThreads());
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    /**
     * Deterministic check of the anti-deadlock rule. The test holds the lock of "B" (the HIGHER id) and
     * starts transfer(B -> A), whose SOURCE is B. A correct implementation locks the LOWER id first, so
     * while it waits for B it must already be holding A. An implementation that locks the source first
     * would be waiting for B with A still free, which is exactly the shape of a deadlock.
     */
    @Test
    void transfer_locksTheLowerAccountIdFirst_whicheverDirectionItGoes() throws Exception {
        Account lower = repository.getOrThrow("A");
        Account higher = repository.getOrThrow("B");
        Future<?> future;
        higher.lock();
        try {
            future = pool.submit(() -> service.transfer("B", "A", 1, "TX-1"));
            assertThrows(TimeoutException.class, () -> future.get(200, TimeUnit.MILLISECONDS));
            assertTrue(lower.isLocked(),
                    "while waiting for the higher id the transfer must already hold the lower id");
        } finally {
            higher.unlock();
        }
        future.get(5, TimeUnit.SECONDS);
        assertEquals(101, service.getBalance("A"));
        assertEquals(99, service.getBalance("B"));
    }
}
package org.example.balance.idempotency;

import org.example.balance.exception.BalanceException;
import org.example.balance.exception.IdempotencyConflictException;
import org.example.balance.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.example.balance.support.ConcurrentTestSupport.assertNoFailures;
import static org.example.balance.support.ConcurrentTestSupport.awaitUninterruptibly;
import static org.example.balance.support.ConcurrentTestSupport.daemonThreads;
import static org.example.balance.support.ConcurrentTestSupport.runConcurrently;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The guard in isolation, including the owner-failure paths that are hard to reach via the service. */
class IdempotencyGuardTest {

    private final IdempotencyGuard guard = new IdempotencyGuard();
    private final OperationFingerprint credit100 = OperationFingerprint.credit("A", 100);

    @Test
    void repeatedCalls_runTheOperationOnce() {
        AtomicInteger runs = new AtomicInteger();
        guard.execute("TX-1", credit100, runs::incrementAndGet);
        guard.execute("TX-1", credit100, runs::incrementAndGet);
        guard.execute("TX-1", credit100, runs::incrementAndGet);
        assertEquals(1, runs.get());
    }

    @Test
    void aBusinessRejection_isReplayedWithoutRunningTheOperationAgain() {
        AtomicInteger runs = new AtomicInteger();
        Runnable rejecting = () -> {
            runs.incrementAndGet();
            throw new InsufficientFundsException("A", 0, 100);
        };

        BalanceException first = assertThrows(BalanceException.class, () -> guard.execute("TX-1", credit100, rejecting));
        BalanceException second = assertThrows(BalanceException.class, () -> guard.execute("TX-1", credit100, rejecting));

        assertSame(first, second);
        assertEquals(1, runs.get());
    }

    @Test
    void aDifferentFingerprint_isAConflict_andDoesNotRun() {
        guard.execute("TX-1", credit100, () -> { });
        AtomicInteger runs = new AtomicInteger();

        assertThrows(IdempotencyConflictException.class,
                () -> guard.execute("TX-1", OperationFingerprint.credit("A", 200), runs::incrementAndGet));
        assertEquals(0, runs.get());
    }

    @Test
    void anUnexpectedFailure_isNotRemembered_soARetryRunsTheOperation() {
        assertThrows(IllegalStateException.class, () -> guard.execute("TX-1", credit100, () -> {
            throw new IllegalStateException("boom");
        }));

        AtomicInteger runs = new AtomicInteger();
        guard.execute("TX-1", credit100, runs::incrementAndGet);
        assertEquals(1, runs.get());
    }

    @Test
    void aWaitingDuplicate_takesOver_whenTheOwnerFailsUnexpectedly() throws Exception {
        CountDownLatch ownerRunning = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        AtomicInteger waiterRuns = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2, daemonThreads());
        try {
            Future<?> owner = pool.submit(() -> guard.execute("TX-1", credit100, () -> {
                ownerRunning.countDown();
                awaitUninterruptibly(releaseOwner);
                throw new IllegalStateException("boom");
            }));
            ownerRunning.await();

            Future<?> waiter = pool.submit(() -> guard.execute("TX-1", credit100, waiterRuns::incrementAndGet));
            Thread.sleep(100); // let the waiter park on the owner's attempt (correct in either ordering)
            releaseOwner.countDown();

            ExecutionException ownerFailure = assertThrows(ExecutionException.class, () -> owner.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, ownerFailure.getCause());
            waiter.get(5, TimeUnit.SECONDS);
            assertEquals(1, waiterRuns.get(), "the waiter must have taken over and run the operation exactly once");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Race-widening test. The guard must claim a transaction id with ONE atomic operation. This map
     * pauses right after every plain read, which stretches the window of any "check, then act" sequence
     * (get / containsKey followed by put) from nanoseconds to 20 ms. A correct guard never does a plain
     * read, so it is unaffected; a check-then-act guard lets all threads through and runs the operation
     * once per thread, on any hardware and on any run.
     */
    @Test
    void claimingATransactionId_isAtomic_evenWhenEveryPlainReadIsSlow() throws Exception {
        IdempotencyGuard guard = new IdempotencyGuard(new RaceWideningMap());
        AtomicInteger runs = new AtomicInteger();

        List<Throwable> results = runConcurrently(8, i -> guard.execute("TX-1", credit100, runs::incrementAndGet));

        assertNoFailures(results);
        assertEquals(1, runs.get(), "the operation must run exactly once");
    }

    private static final class RaceWideningMap extends ConcurrentHashMap<String, IdempotencyGuard.Attempt> {

        @Override
        public IdempotencyGuard.Attempt get(Object key) {
            IdempotencyGuard.Attempt value = super.get(key);
            pause();
            return value;
        }

        @Override
        public boolean containsKey(Object key) {
            boolean present = super.containsKey(key);
            pause();
            return present;
        }

        private static void pause() {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}

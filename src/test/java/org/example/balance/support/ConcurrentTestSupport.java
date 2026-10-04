package org.example.balance.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Helpers for tests that need many threads to hit the code under test at the very same moment. */
public final class ConcurrentTestSupport {

    private ConcurrentTestSupport() { }

    /**
     * Runs {@code task} on {@code threads} threads that are all released by a single starting gun, which
     * maximises the chance of a race. Returns one entry per thread: {@code null} if the task completed
     * normally, otherwise the Throwable it threw. The caller decides which failures are expected.
     */
    public static List<Throwable> runConcurrently(int threads, IntConsumer task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads, daemonThreads());
        try {
            CountDownLatch allReady = new CountDownLatch(threads);
            CountDownLatch startingGun = new CountDownLatch(1);
            List<Future<Throwable>> futures = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<Throwable> job = () -> {
                    allReady.countDown();
                    startingGun.await();
                    try {
                        task.accept(index);
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                };
                futures.add(pool.submit(job));
            }
            assertTrue(allReady.await(30, TimeUnit.SECONDS), "threads did not all start");
            startingGun.countDown();

            List<Throwable> results = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    public static long successes(List<Throwable> results) {
        return results.stream().filter(r -> r == null).count();
    }

    public static long countOf(List<Throwable> results, Class<? extends Throwable> type) {
        return results.stream().filter(type::isInstance).count();
    }

    public static void assertNoFailures(List<Throwable> results) {
        assertOnlyExpectedFailures(results);
    }

    /** Fails the test, with the original stack trace, if any task failed with a type that is not allowed. */
    @SafeVarargs
    public static void assertOnlyExpectedFailures(List<Throwable> results, Class<? extends Throwable>... allowed) {
        for (Throwable failure : results) {
            if (failure != null && Arrays.stream(allowed).noneMatch(type -> type.isInstance(failure))) {
                fail("Unexpected failure in a concurrent task: " + failure, failure);
            }
        }
    }

    public static ThreadFactory daemonThreads() {
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true); // a deadlocked test must never keep the JVM alive
            return thread;
        };
    }

    public static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting", e);
        }
    }
}
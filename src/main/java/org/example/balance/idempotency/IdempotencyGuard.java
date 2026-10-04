package org.example.balance.idempotency;

import org.example.balance.exception.BalanceException;
import org.example.balance.exception.IdempotencyConflictException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Guarantees that the effect of a transaction id is applied at most once, even when duplicates
 * arrive concurrently.
 *
 * <p>The first caller to claim a transaction id (an atomic {@code putIfAbsent}) becomes its
 * <em>owner</em> and runs the operation. Every other caller with the same id never runs the
 * operation; it waits for the owner's outcome and replays it:
 * <ul>
 *   <li>applied: return normally;</li>
 *   <li>rejected with a {@link BalanceException} (a deterministic business outcome such as
 *       insufficient funds): the same rejection is replayed and is final for this id;</li>
 *   <li>failed unexpectedly (a bug or infrastructure error): the claim is released and waiting
 *       duplicates compete again, so a retry can still succeed.</li>
 * </ul>
 * Waiters hold no account lock while waiting, so this component cannot take part in a deadlock.
 */
public final class IdempotencyGuard {

    private sealed interface Outcome {
        record Applied() implements Outcome {
        }

        record Rejected(BalanceException reason) implements Outcome {
        }

        record Abandoned() implements Outcome {
        }
    }

    /**
     * Package-private so tests can inject a map that widens race windows (see IdempotencyGuardTest).
     */
    record Attempt(OperationFingerprint fingerprint, CompletableFuture<Outcome> outcome) {
        Attempt(OperationFingerprint fingerprint) {
            this(fingerprint, new CompletableFuture<>());
        }
    }

    private final ConcurrentMap<String, Attempt> attempts;

    public IdempotencyGuard() {
        this(new ConcurrentHashMap<>());
    }

    IdempotencyGuard(ConcurrentMap<String, Attempt> attempts) {
        this.attempts = attempts;
    }

    public void execute(String transactionId, OperationFingerprint fingerprint, Runnable operation) {
        while (true) {
            Attempt mine = new Attempt(fingerprint);
            Attempt existing = attempts.putIfAbsent(transactionId, mine);

            if (existing == null) {
                runAsOwner(transactionId, mine, operation);
                return;
            }
            if (!existing.fingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException(transactionId);
            }
            switch (existing.outcome().join()) {
                case Outcome.Applied applied -> {
                    return;
                }
                case Outcome.Rejected rejected -> throw rejected.reason();
                case Outcome.Abandoned abandoned -> {
                    // The owner failed unexpectedly and released the id: compete for ownership again.
                }
            }
        }
    }

    private void runAsOwner(String transactionId, Attempt attempt, Runnable operation) {
        try {
            operation.run();
        } catch (BalanceException rejection) {
            attempt.outcome().complete(new Outcome.Rejected(rejection));
            throw rejection;
        } catch (Throwable unexpected) {
            attempts.remove(transactionId, attempt);            // release first, then wake the waiters
            attempt.outcome().complete(new Outcome.Abandoned());
            throw unexpected;
        }
        attempt.outcome().complete(new Outcome.Applied());
    }
}
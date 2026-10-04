# Concurrent Balance Service

A Java 21 / Spring Boot in-memory balance service designed for correctness under concurrent access. It supports:

- `credit` and `debit`
- atomic `transfer`
- per-`transactionId` idempotency, including concurrent duplicates
- thread-safe balance reads and updates

The implementation focuses on concurrency correctness, explicit invariants, and testability. No database or external infrastructure is required.


## Quick Start

Prerequisite: JDK 21. The Gradle wrapper is included.

```bash
./gradlew test          # Windows: gradlew.bat test
```


## Design At A Glance

```text
org.example.balance
├── service
│   ├── BalanceService
│   ├── DefaultBalanceService
│   ├── AccountLocking
│   └── Validation
├── domain
│   ├── Account
│   ├── AccountRepository
│   └── InMemoryAccountRepository
├── idempotency
│   ├── IdempotencyGuard
│   └── OperationFingerprint
├── exception
│   └── BalanceException hierarchy
└── config
    └── BalanceConfiguration
```



`BalanceService` is the application API. The core contains no Spring annotations. `BalanceConfiguration` provides the wiring, keeping the core easy to test without a Spring context.

## Concurrency Model

Each account owns a `ReentrantLock`. `AccountLocking` is the only component allowed to acquire account locks, and every balance read or write occurs while the corresponding lock is held.

- Operations targeting the same account are serialized, preventing lost updates and negative balances.
- Operations on unrelated accounts can proceed independently; there is no global service lock.
- Account creation uses `ConcurrentMap.putIfAbsent`, so concurrent creation cannot replace an existing account.
- `getBalance` locks the account, making a single-account read linearizable.




Per-account locking preserves correctness without serializing unrelated accounts. `AtomicLong` suits isolated balance changes but does not provide a natural atomic protocol for transfers across two accounts.

## Idempotency

`IdempotencyGuard` claims each `transactionId` with an atomic `putIfAbsent` and stores an operation fingerprint containing the relevant payload.

1. The first caller becomes the owner and executes the operation.
2. Concurrent or later duplicates wait for and replay the owner's outcome.
3. A duplicate never applies the balance change again.
4. Reusing an ID with a different payload fails with `IdempotencyConflictException`.

The request flow is intentionally ordered as:

```text
stateless validation -> account lookup -> transaction claim -> account lock(s) -> operation
```


Business rejections such as `InsufficientFundsException` are stored and replayed. Unexpected failures release the claim so later attempts can retry, distinguishing expected business outcomes from transient or programming failures.

## Atomic Transfer And Deadlocks


Transfers acquire both account locks in ascending `accountId` order. Before mutating either balance, the service validates source funds and destination overflow while holding both locks. Only then are the two balance updates performed.

This guarantees:

- no partial transfer is observable by another operation;
- a rejected transfer leaves both accounts unchanged;
- opposing transfers cannot deadlock, because all multi-account operations use the same lock order.

`transfer(source, source, ...)` is rejected with `SameAccountTransferException`; treating it as a silent no-op would hide a client error.

## Validation And Errors

The service rejects invalid or unsafe operations through a domain-specific exception hierarchy, including:

- non-positive amounts and blank transaction IDs;
- unknown accounts and same-account transfers;
- insufficient funds and arithmetic overflow;
- idempotency payload conflicts.

Validation and account lookup happen before the transaction ID is claimed where appropriate, so malformed requests do not create permanent idempotency entries.

## Verification

The tests are organized around the required behavioral properties:

- sequential credit, debit, transfer, validation, and overflow behavior;
- repeated and concurrent idempotency for all money operations;
- concurrent single-account operations and multi-account transfers;
- balance conservation, exact outcomes, and opposing-transfer deadlock checks;
- deterministic lock-protocol checks and Spring context wiring.

Concurrency tests use latches, seeded randomness, repeated runs, and controlled race widening. Mutation checks cover locking, idempotency, transfer ordering, preconditions, and global locking.

## Engineering Trade-offs

- **In-memory state:** simple and fast, but lost on restart.
- **Single-JVM locking:** strong within one process, but does not coordinate multiple instances.
- **Unbounded idempotency registry:** reliable replay, but needs TTL or eviction in production.
- **No global lock:** preserves parallelism, but provides no consistent multi-account snapshot.
- **Synchronous duplicate waiting:** suitable for short in-memory work; slow operations would need timeouts.
- **No REST layer:** keeps the focus on the service contract; an adapter can add HTTP status mapping and `Idempotency-Key` support.


For production, the natural extensions are database transactions with ordered row locks, a unique transaction-ID constraint, an append-only ledger, retention for idempotency records, metrics, and an HTTP layer.
## Status

| Capability | Status |
|---|---|
| Credit, debit, transfer, and balance reads | Implemented |
| Thread safety and per-account concurrency | Implemented and tested |
| Atomic transfers and deadlock prevention | Implemented and tested |
| Concurrent idempotency | Implemented and tested |
| Validation and overflow handling | Implemented and tested |
| REST API | Not implemented|
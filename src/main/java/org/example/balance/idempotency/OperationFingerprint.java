package org.example.balance.idempotency;

/**
 * The identity of a request apart from its transaction id. Two requests with the same transaction id
 * are "the same request" (a retry) only if their fingerprints are equal; otherwise it is a conflict.
 *
 * @param destinationAccountId only set for transfers ({@code accountId} is then the source)
 */
public record OperationFingerprint(Type type, String accountId, String destinationAccountId, long amount) {

    public enum Type {CREDIT, DEBIT, TRANSFER}

    public static OperationFingerprint credit(String accountId, long amount) {
        return new OperationFingerprint(Type.CREDIT, accountId, null, amount);
    }

    public static OperationFingerprint debit(String accountId, long amount) {
        return new OperationFingerprint(Type.DEBIT, accountId, null, amount);
    }

    public static OperationFingerprint transfer(String sourceAccountId,
                                                String destinationAccountId, long amount) {
        return new OperationFingerprint(Type.TRANSFER, sourceAccountId, destinationAccountId, amount);
    }
}
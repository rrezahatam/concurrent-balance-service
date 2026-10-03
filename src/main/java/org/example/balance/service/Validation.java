package org.example.balance.service;

import org.example.balance.exception.InvalidAmountException;
import org.example.balance.exception.InvalidIdentifierException;

/**
 * Stateless request validation, performed before any state is touched or any id is claimed.
 */
final class Validation {

    private Validation() {
    }

    static void identifier(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidIdentifierException(fieldName);
        }
    }

    static void positiveAmount(long amount) {
        if (amount <= 0) {
            throw InvalidAmountException.notPositive(amount);
        }
    }
}
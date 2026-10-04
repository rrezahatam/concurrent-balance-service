package org.example.balance.service;

import org.example.balance.domain.Account;
import java.util.function.LongSupplier;

/** The only place where account locks are acquired. */
final class AccountLocking {

    private AccountLocking() { }

    static void run(Account account, Runnable action) {
        account.lock();
        try {
            action.run();
        } finally {
            account.unlock();
        }
    }

    static long read(Account account, LongSupplier reader) {
        account.lock();
        try {
            return reader.getAsLong();
        } finally {
            account.unlock();
        }
    }

    /** First attempt: lock the first account, then the second, then run the action. */
    static void runBoth(Account first, Account second, Runnable action) {
        first.lock();
        try {
            second.lock();
            try {
                action.run();
            } finally {
                second.unlock();
            }
        } finally {
            first.unlock();
        }
    }
}
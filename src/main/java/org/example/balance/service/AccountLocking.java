package org.example.balance.service;

import org.example.balance.domain.Account;
import java.util.function.LongSupplier;

/**
 * The only place where account locks are acquired. Keeping it in one small class makes the locking
 * protocol easy to review and to test:
 * <ul>
 *   <li>always {@code lock()} followed by {@code try/finally unlock()};</li>
 *   <li>when two accounts are needed they are locked in ascending account-id order, so two threads can
 *       never wait for each other in a cycle (no circular wait, hence no deadlock);</li>
 *   <li>critical sections contain only in-memory arithmetic: no I/O, no blocking calls, no callbacks.</li>
 * </ul>
 */final class AccountLocking {

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

    static void runOrdered(Account a, Account b, Runnable action) {
        if (a == b) {
            run(a, action);
            return;
        }
        Account first = a.getId().compareTo(b.getId()) < 0 ? a : b;
        Account second = (first == a) ? b : a;

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
package org.example.balance.service;

import org.example.balance.domain.AccountRepository;
import org.example.balance.domain.InMemoryAccountRepository;
import org.example.balance.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class BalanceServiceBehaviourTest {

    private DefaultBalanceService service;

    @BeforeEach
    void setUp() {
        AccountRepository repository = new InMemoryAccountRepository();
        repository.create("A", 1000);
        repository.create("B", 500);

        service = new DefaultBalanceService(repository);
    }

    private void assertBalance(String accountId, long expected) {
        assertEquals(expected, service.getBalance(accountId), "balance of " + accountId);
    }

    @Test
    void getBalance_returnsInitialBalance() {
        assertEquals(1000, service.getBalance("A"));
        assertEquals(500, service.getBalance("B"));
    }

    @Test
    void getBalance_ofUnknownAccount_isRejected() {
        assertThrows(AccountNotFoundException.class,
                () -> service.getBalance("Nope"));
    }

    @Test
    void credit_increaseBalance() {
        service.credit("A", 500, "TX-1");
        assertEquals(1500, service.getBalance("A"));
    }

    @Test
    void debit_decreasesBalance() {
        service.debit("A", 700, "TX-1");
        assertBalance("A", 300);
    }

    @Test
    void debit_ofTheWholeBalance_isAllowed() {
        service.debit("A", 1_000, "TX-1");
        assertBalance("A", 0);
    }

    @Test
    void debit_withInsufficientFunds_failsAndLeavesTheBalanceUnchanged() {
        assertThrows(InsufficientFundsException.class,
                () -> service.debit("A", 1_200, "TX-1"));

        assertBalance("A", 1000);
    }

    @Test
    void transfer_movesMoneyFromSourceToDestination() {
        service.transfer("A", "B", 300, "TX-1");

        assertBalance("A", 700);
        assertBalance("B", 800);
    }

    @Test
    void transfer_withInsufficientFunds_leavesBothAccountsUntouched() {
        assertThrows(InsufficientFundsException.class,
                () -> service.transfer("A", "B", 1_200, "TX-1"));

        assertBalance("A", 1000);
        assertBalance("B", 500);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void nonPositiveAmounts_areRejectedForEveryOperation(long amount) {
        assertThrows(InvalidAmountException.class,
                () -> service.credit("A", amount, "TX-1"));

        assertThrows(InvalidAmountException.class,
                () -> service.debit("A", amount, "TX-2"));

        assertThrows(InvalidAmountException.class,
                () -> service.transfer("A", "B", amount, "TX-3"));

        assertBalance("A", 1_000);
        assertBalance("B", 500);
    }

    @Test
    void unknownAccount_isRejectedForEveryOperation() {
        assertThrows(AccountNotFoundException.class,
                () -> service.credit("NOPE", 10, "TX-1"));

        assertThrows(AccountNotFoundException.class,
                () -> service.debit("NOPE", 10, "TX-2"));

        assertThrows(AccountNotFoundException.class,
                () -> service.getBalance("NOPE"));
    }

    @Test
    void transfer_withUnknownAccount_isRejectedAndChangesNothing() {
        assertThrows(AccountNotFoundException.class,
                () -> service.transfer("NOPE", "B", 10, "TX-1"));

        assertThrows(AccountNotFoundException.class,
                () -> service.transfer("A", "NOPE", 10, "TX-2"));

        assertBalance("A", 1000);
        assertBalance("B", 500);
    }

    @Test
    void transfer_toTheSameAccount_isRejectedAndChangesNothing() {
        assertThrows(SameAccountTransferException.class, () -> service.transfer("A", "A", 100, "TX-1"));
        assertBalance("A", 1_000);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void blankIdentifiers_areRejected(String blank) {
        assertThrows(InvalidIdentifierException.class,
                () -> service.credit(blank, 10, "TX-1"));

        assertThrows(InvalidIdentifierException.class,
                () -> service.credit("A", 10, blank));

        assertThrows(InvalidIdentifierException.class,
                () -> service.transfer("A", blank, 10, "TX-2"));

        assertThrows(InvalidIdentifierException.class,
                () -> service.getBalance(blank));
    }
}

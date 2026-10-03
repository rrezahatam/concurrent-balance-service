package org.example.balance.service;

import org.example.balance.AccountRepository;
import org.example.balance.InMemoryAccountRepository;
import org.example.balance.exception.AccountNotFoundException;
import org.example.balance.exception.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void credit_increaseBalance(){
        service.credit("A", 500, "TX-1");
        assertEquals(1500, service.getBalance("A"));
    }

    @Test
    void debit_decreasesBalance() {
        service.debit("A", 700, "TX-1");
        assertEquals(300, service.getBalance("A"), "balance of " + "A");

    }
    @Test
    void debit_ofTheWholeBalance_isAllowed() {
        service.debit("A", 1_000, "TX-1");
        assertEquals(0, service.getBalance("A"), "balance of " + "A");

    }
    @Test
    void debit_withInsufficientFunds_failsAndLeavesTheBalanceUnchanged() {
        assertThrows(InsufficientFundsException.class, () -> service.debit("A", 1_200, "TX-1"));
        assertEquals(1000, service.getBalance("A"), "balance of " + "A");

    }

    @Test
    void transfer_movesMoneyFromSourceToDestination() {
        service.transfer("A", "B", 300, "TX-1");

        assertEquals(700, service.getBalance("A"), "balance of " + "A");
        assertEquals(800, service.getBalance("B"), "balance of " + "B");

    }
    @Test
    void transfer_withInsufficientFunds_leavesBothAccountsUntouched() {
        assertThrows(InsufficientFundsException.class, () -> service.transfer("A", "B", 1_200, "TX-1"));

        assertEquals(1000, service.getBalance("A"), "balance of " + "A");
        assertEquals(500, service.getBalance("B"), "balance of " + "B");

    }

    @Test
    void unknownAccount_isRejectedForEveryOperation() {
        assertThrows(AccountNotFoundException.class, () -> service.credit("NOPE", 10, "TX-1"));
        assertThrows(AccountNotFoundException.class, () -> service.debit("NOPE", 10, "TX-2"));
        assertThrows(AccountNotFoundException.class, () -> service.getBalance("NOPE"));
    }

    @Test
    void transfer_withUnknownAccount_isRejectedAndChangesNothing() {
        assertThrows(AccountNotFoundException.class, () -> service.transfer("NOPE", "B", 10, "TX-1"));
        assertThrows(AccountNotFoundException.class, () -> service.transfer("A", "NOPE", 10, "TX-2"));

        assertEquals(1000, service.getBalance("A"), "balance of " + "A");
        assertEquals(500, service.getBalance("B"), "balance of " + "B");
    }

}

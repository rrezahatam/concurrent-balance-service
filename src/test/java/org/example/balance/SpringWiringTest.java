package org.example.balance;

import org.example.balance.domain.AccountRepository;
import org.example.balance.service.BalanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smoke test: the Spring context starts and the service bean is wired to the repository bean.
 */
@SpringBootTest
class SpringWiringTest {

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void contextStarts_andTheServiceWorks() {
        accountRepository.create("WIRING-A", 100);
        balanceService.credit("WIRING-A", 50, "WIRING-TX-1");
        assertEquals(150, balanceService.getBalance("WIRING-A"));
    }
}
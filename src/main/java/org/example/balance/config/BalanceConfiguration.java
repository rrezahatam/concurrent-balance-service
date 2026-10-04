package org.example.balance.config;

import org.example.balance.domain.AccountRepository;
import org.example.balance.domain.InMemoryAccountRepository;
import org.example.balance.idempotency.IdempotencyGuard;
import org.example.balance.service.DefaultBalanceService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free core into Spring. The core classes carry no Spring annotations, so they can
 * be unit tested (and reasoned about) without a container.
 */
@Configuration(proxyBeanMethods = false)
public class BalanceConfiguration {

    @Bean
    AccountRepository accountRepository() {
        return new InMemoryAccountRepository();
    }

    @Bean
    IdempotencyGuard idempotencyGuard() {
        return new IdempotencyGuard();
    }

    /** Exposed under its concrete type so it can be injected as BalanceService and as AccountAdministration. */
    @Bean
    DefaultBalanceService balanceService(AccountRepository accountRepository, IdempotencyGuard idempotencyGuard) {
        return new DefaultBalanceService(accountRepository, idempotencyGuard);
    }
}
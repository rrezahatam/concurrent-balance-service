package org.example.balance.domain;

import org.example.balance.Account;
import org.example.balance.InMemoryAccountRepository;
import org.example.balance.exception.AccountAlreadyExistsException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;


public class InMemoryAccountRepositoryTest {
    private final InMemoryAccountRepository repository = new InMemoryAccountRepository();

    @Test
    void create_makesTheAccountFindable(){
        Account created = repository.create("A", 10);

        assertEquals("A", created.getId());
        assertSame(created, repository.find("A").orElse(null));
    }

    @Test
    void create_rejectsDuplicateId(){
        repository.create("A", 100);
        assertThrows(AccountAlreadyExistsException.class,
                () -> repository.create("A", 5));
    }

    @Test
    void find_returnsEmptyForUnknownId(){
        assertTrue(repository.find("Nope!").isEmpty());
    }


}

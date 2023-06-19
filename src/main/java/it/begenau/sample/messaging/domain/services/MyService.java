package it.begenau.sample.messaging.domain.services;

import it.begenau.sample.messaging.domain.models.Message;
import it.begenau.sample.messaging.domain.ports.MessageStore;
import it.begenau.sample.messaging.domain.ports.StoreMessageInDatabase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@ApplicationScoped
@Slf4j
public class MyService implements StoreMessageInDatabase {

    private final MessageStore store;

    @Override
    @Transactional
    public void store(Message message, boolean fail) {
        store.saveMessage(message);
        if (fail) {
            throw new RuntimeException("Synthetic RTE");
        }
    }
}

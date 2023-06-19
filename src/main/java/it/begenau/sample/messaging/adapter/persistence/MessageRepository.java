package it.begenau.sample.messaging.adapter.persistence;

import it.begenau.sample.messaging.domain.models.Message;
import it.begenau.sample.messaging.domain.ports.MessageStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class MessageRepository implements MessageStore {

    private final EntityManager em;

    @Override
    public void saveMessage(Message message) {
        em.persist(MessageEntity.builder().payload(message.payload()).executionId(message.executionId()).build());
        em.flush();
    }
}

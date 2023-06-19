package it.begenau.sample.messaging.domain.ports;

import it.begenau.sample.messaging.domain.models.Message;

public interface MessageStore {
    void saveMessage(Message message);
}

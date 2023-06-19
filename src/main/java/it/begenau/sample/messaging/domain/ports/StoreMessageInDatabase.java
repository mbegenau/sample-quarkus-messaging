package it.begenau.sample.messaging.domain.ports;

import it.begenau.sample.messaging.domain.models.Message;

public interface StoreMessageInDatabase {
    void store(Message message, boolean fail);
}

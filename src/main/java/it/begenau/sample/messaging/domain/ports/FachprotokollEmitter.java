package it.begenau.sample.messaging.domain.ports;

import it.begenau.sample.messaging.domain.models.FachprotokollEintrag;
import jakarta.jms.JMSException;
import jakarta.validation.Valid;

public interface FachprotokollEmitter {
    void emit(@Valid FachprotokollEintrag entry);
}

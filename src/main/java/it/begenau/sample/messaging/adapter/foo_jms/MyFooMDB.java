package it.begenau.sample.messaging.adapter.foo_jms;

import it.begenau.sample.messaging.MyModel;
import it.begenau.sample.messaging.domain.ports.StoreMessageInDatabase;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.json.bind.Jsonb;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue"),
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "queue.mdb.name")
})
@Slf4j
@RequiredArgsConstructor
public class MyFooMDB implements MessageListener {

    private final AtomicInteger counter = new AtomicInteger(0);

    private final Jsonb jsonb;

    private final StoreMessageInDatabase service;

    @Override
    @Counted(name = "foo.incoming.messages",
            absolute = true,
            tags = {
                    "scope", "technical",
                    "area", "infrastructure",
                    "adapter", "messaging"
            },
            description = "Number of incoming messages processed by Foo listeners")
    @Transactional
    public void onMessage(Message message) {
        int i = counter.incrementAndGet();
        try (var ignored = MDC.putCloseable("executionNo", String.valueOf(i))) {
            String messageBody = message.getBody(String.class);
            log.info("Received {}", messageBody);
            if ((i % 3) != 0) {
                service.store(new it.begenau.sample.messaging.domain.models.Message(messageBody, i), (i % 3) == 2);
            }
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }
}

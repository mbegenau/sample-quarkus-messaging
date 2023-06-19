package it.begenau.sample.messaging.adapter.foo_jms;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import it.begenau.sample.messaging.domain.models.FachprotokollEintrag;
import it.begenau.sample.messaging.domain.ports.FachprotokollEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.jms.*;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.time.temporal.ChronoUnit;

@ApplicationScoped
@RequiredArgsConstructor
public class FachprotokollProducer implements FachprotokollEmitter, ExceptionListener {

    private final ConnectionFactory cf;

    private final FachprotokollMapper mapper;

    private final Span span;

    private JMSContext context;

    private JMSProducer producer;
    private Destination destination;


    @Override
    @WithSpan
    public void emit(FachprotokollEintrag entry) {
        ensureProducer();
        try {
            this.producer.send(this.destination, mapper.toMessage(entry, this.context::createTextMessage));
        } catch (JMSException e) {
            throw new RuntimeException("Could not send message.", e);
        }
    }

    @Retry(
            maxRetries = 5,
            delay = 3, delayUnit = ChronoUnit.SECONDS,
            maxDuration = 25, durationUnit = ChronoUnit.SECONDS
    )
    protected void ensureProducer() {
        if (this.context == null) {
            this.context = cf.createContext();
            this.context.setExceptionListener(this);
            this.destination = this.context.createQueue("queue-name-filled-from-properties");
        }

        if (this.producer == null) {
            this.producer = this.context.createProducer();
            // Broker shall create messageID
            this.producer.setDisableMessageID(true);
        }
    }

    @Override
    public void onException(JMSException exception) {
        this.context = null;
        this.producer = null;
    }
}

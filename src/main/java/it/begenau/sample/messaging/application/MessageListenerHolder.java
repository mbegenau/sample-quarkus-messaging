package it.begenau.sample.messaging.application;

import jakarta.jms.*;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
class MessageListenerHolder {
    static final String ACKNOWLEDGE_MODE = "acknowledgeMode";
    private static final String MESSAGE_SELECTOR = "messageSelector";
    private static final String DESTINATION_TYPE = "destinationType";
    private static final String DESTINATION_LOOKUP = "destinationLookup";
    private static final String CONNECTION_FACTORY_LOOKUP = "connectionFactoryLookup";
    private static final String SUBSCRIPTION_DURABILITY = "subscriptionDurability";
    private static final String SUBSCRIPTION_NAME = "subscriptionName";
    private static final String CLIENT_ID = "clientId";
    private final Map<String, String> props;
    private final MessageListener listener;
    private XAJMSContext context;
    private JMSConsumer consumer;
    private final String listenerName;

    private boolean started = false;
    private final boolean autoAcknowledge;
    private CompletableFuture<Void> cf;
    private final XAConnectionFactory factory;
    private final ExecutorService executor;
    private final TransactionManager tm;

    public MessageListenerHolder(String listenerName, Map<String, String> props, MessageListener listener, XAConnectionFactory factory, ExecutorService executorService, TransactionManager tm) {
        this.listenerName = listenerName;
        this.props = props;
        this.listener = listener;
        this.autoAcknowledge = isAutoAcknowledge(props);
        this.factory = factory;
        this.executor = executorService;
        this.tm = tm;
    }

    private static JMSConsumer createConsumer(XAJMSContext context, String listenerName, Map<String, String> props, String destinationName, Optional<String> selector) throws JMSException {
        final JMSConsumer jmsConsumer;
        if (isTopic(props)) {
            final Topic topic = context.createTopic(destinationName);
            if (isDurable(props)) {
                if (selector.isPresent()) {
                    jmsConsumer = context.createDurableConsumer(topic, listenerName, selector.get(), false);
                    log.info("Consumer {} is connected to topic {} with selector {}", listenerName, destinationName, selector.get());
                } else {
                    jmsConsumer = context.createDurableConsumer(topic, listenerName);
                    log.info("Consumer {} is connected to topic {}", listenerName, destinationName);
                }
            } else {
                if (selector.isPresent()) {
                    jmsConsumer = context.createConsumer(topic, selector.get());
                    log.info("Consumer {} is connected to topic {} with selector {}", listenerName, destinationName, selector.get());
                } else {
                    jmsConsumer = context.createConsumer(topic);
                    log.info("Consumer {} is connected to topic {}", listenerName, destinationName);
                }
            }
        } else {
            if (selector.isPresent()) {
                jmsConsumer = context.createConsumer(context.createQueue(destinationName), selector.get());
                log.info("Consumer {} is connected to queue {} with selector {}", listenerName, destinationName, selector.get());
            } else {
                jmsConsumer = context.createConsumer(context.createQueue(destinationName));
                log.info("Consumer {} is connected to queue {}", listenerName, destinationName);
            }
        }
        return jmsConsumer;
    }

    private static boolean isDurable(Map<String, String> props) {
        return "NonDurable".equals(props.getOrDefault(SUBSCRIPTION_DURABILITY, "NonDurable"));
    }

    private static boolean isTopic(Map<String, String> props) {
        return switch (props.get(DESTINATION_TYPE)) {
            case "jakarta.jms.Queue" -> false;
            case "jakarta.jms.Topic" -> true;
            default -> throw new IllegalArgumentException("No or unknown destinationType in ActivationConfig");
        };
    }

    private static Optional<String> getSelector(Map<String, String> props) {
        return Optional.ofNullable(props.get(MESSAGE_SELECTOR));
    }

    private static String getDestinationName(Map<String, String> props) {
        // This is a JNDI-Name. For Quarkus, we assume there is a matching Property with the Prefix FOO
        return ConfigProvider.getConfig().getValue(props.get(DESTINATION_LOOKUP), String.class);
    }

    public void listen() {
        this.cf = CompletableFuture.runAsync(this::receiveLoop, executor)

                .handle((v, t) -> {
                    started = false;
                    // TODO: Should we handle connection closed by restart?
                    if (t != null && !(t.getCause() instanceof jakarta.jms.IllegalStateRuntimeException)) {
                        log.error("Failing stop.", t);
                    }
                    log.info("Shutting down listener {}", listenerName);
                    consumer.close();
                    MessageListenerHolder.this.context.close();
                    return v;
                });

        // TODO: Implemented reconnect
    }

    @Retry(delay = 3, delayUnit = ChronoUnit.SECONDS, maxDuration = 30, durationUnit = ChronoUnit.SECONDS, maxRetries = 5)
    private void ensureStarted() {
        if (started) return;
        try {
            this.context = factory.createXAContext();
            String destinationName = getDestinationName(this.props);
            Optional<String> selector = getSelector(this.props);
            this.consumer = createConsumer(context, listenerName, this.props, destinationName, selector);
            started = true;
        } catch (JMSException e) {
            log.error("", e);
            throw new RuntimeException(e);
        }
    }

    public void close() {
        log.info("{} shutting down context.", this.listenerName);
        if (cf != null && !cf.isDone()) {
            cf.cancel(true);
        }
        if (this.context != null) {
            this.context.close();
        }
    }

    public boolean isConnected() {
        try {
            this.context.start();
            return true;
        } catch (Exception e) {
            started = false;
            return false;
        }
    }

    private void receiveLoop() {

        ensureStarted();

        //noinspection InfiniteLoopStatement
        for (; ; ) {
            final Message message = consumer.receive();
            if (message == null) continue;

            try {
                log.trace("Processing message {}", message.getJMSMessageID());

                tm.begin();
                tm.getTransaction().enlistResource(this.context.getXAResource());

                listener.onMessage(message);

                if (autoAcknowledge) {
                    message.acknowledge();
                }

                tm.commit();

                log.trace("Done processing message {}", message.getJMSMessageID());
            } catch (IllegalStateRuntimeException rte) {
                // retry reconnect
                ensureStarted();
            } catch (Throwable t) {
                try {
                    tm.rollback();
                    log.error("Exception processing message {}", message.getJMSMessageID(), t);
                } catch (JMSException e) {
                    log.error("Exception processing message.", t);
                    log.error("Error trying to obtain message ID", e);
                } catch (SystemException e) {
                    log.error("Exception processing message.", t);
                    log.error("Error rolling back.", e);
                }
                // context.getXAResource().end();
            }
        }
    }

    private boolean isAutoAcknowledge(Map<String, String> props) {
        return Optional.ofNullable(props.get(MessageListenerHolder.ACKNOWLEDGE_MODE)).map("AUTO_ACKNOWLEDGE"::equals).orElse(true);
    }

}
package it.begenau.sample.messaging;

import io.quarkus.arc.All;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.Subclass;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Shutdown;
import jakarta.enterprise.event.Startup;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Topic;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
@Slf4j
public class QuarkusMessageDrivenBeanActivation {

    private final ConnectionFactory factory;

    private final List<MessageListener> listeners;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public QuarkusMessageDrivenBeanActivation(ConnectionFactory factory, @All List<MessageListener> listeners) {
        this.factory = factory;
        this.listeners = listeners;
    }

    public void init(@Observes Startup startup) {
        for (MessageListener listener : listeners) {
            var context = factory.createContext();
            Class<?> clazz = ClientProxy.unwrap(listener).getClass();
            if (Subclass.class.isAssignableFrom(clazz)) {
                clazz = clazz.getSuperclass();
            }
            String listenerName = clazz.getSimpleName();
            log.info("Initializing message listener {}", listenerName);

            Map<String, String> props = Arrays.stream(clazz.getAnnotation(MessageDriven.class)
                    .activationConfiguration()).collect(Collectors.toMap(ActivationConfigProperty::propertyName, ActivationConfigProperty::propertyValue));

            String destinationName = getDestinationName(props);

            Optional<String> selector = getSelector(props);

            final JMSConsumer jmsConsumer;
            if (isTopic(props)) {
                final Topic topic = context.createTopic(destinationName);
                if (isDurable(props)) {
                    if (selector.isPresent()) {
                        log.info("Consumer {} is connected to topic {} with selector {}", listenerName, destinationName, selector.get());
                        jmsConsumer = context.createDurableConsumer(topic, listenerName, selector.get(), false);
                    } else {
                        log.info("Consumer {} is connected to topic {}", listenerName, destinationName);
                        jmsConsumer = context.createDurableConsumer(topic, listenerName);
                    }
                } else {
                    if (selector.isPresent()) {
                        log.info("Consumer {} is connected to topic {} with selector {}", listenerName, destinationName, selector.get());
                        jmsConsumer = context.createConsumer(topic, selector.get());
                    } else {
                        log.info("Consumer {} is connected to topic {}", listenerName, destinationName);
                        jmsConsumer = context.createConsumer(topic);
                    }
                }
            } else {
                if (selector.isPresent()) {
                    log.info("Consumer {} is connected to queue {} with selector {}", listenerName, destinationName, selector.get());
                    jmsConsumer = context.createConsumer(context.createQueue(destinationName), selector.get());
                } else {
                    log.info("Consumer {} is connected to queue {}", listenerName, destinationName);
                    jmsConsumer = context.createConsumer(context.createQueue(destinationName));
                }
            }

            CompletableFuture.runAsync(() -> {
                //noinspection EndlessStream // We know what we are doing here.
                Stream.generate(jmsConsumer::receive)
                        .filter(Objects::nonNull)
                        .forEach(listener::onMessage);
            }, executorService).handle((v, t) -> {
                log.info("Shutting down listener {}", listenerName);
                context.close();
                return v;
            });

        }
    }

    private static boolean isDurable(Map<String, String> props) {
        return "NonDurable".equals(props.getOrDefault(ActivationConfigProperty.SUBSCRIPTION_DURABILITY, "NonDurable"));
    }

    private static boolean isTopic(Map<String, String> props) {
        return switch (props.get(ActivationConfigProperty.DESTINATION_TYPE)) {
            case "jakarta.jms.Queue" -> false;
            case "jakarta.jms.Topic" -> true;
            default -> throw new IllegalArgumentException("No or unknown destinationType in ActivationConfig");
        };
    }

    private static Optional<String> getSelector(Map<String, String> props) {
        return Optional.ofNullable(props.get(ActivationConfigProperty.MESSAGE_SELECTOR));
    }

    private static String getDestinationName(Map<String, String> props) {
        // This is a JNDI-Name. For Quarkus, we assume there is a matching Property with the Prefix FOO

        return ConfigProvider.getConfig().getValue(props.get(ActivationConfigProperty.DESTINATION_LOOKUP), String.class);
    }

    public void tearDown(@Observes Shutdown shutdown) {
        executorService.shutdown();
    }


}

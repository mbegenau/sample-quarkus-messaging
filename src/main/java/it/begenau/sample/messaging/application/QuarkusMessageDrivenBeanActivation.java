package it.begenau.sample.messaging.application;

import io.opentelemetry.api.trace.Tracer;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.Subclass;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Shutdown;
import jakarta.enterprise.event.Startup;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.jms.*;
import jakarta.transaction.TransactionManager;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
@Readiness
public class QuarkusMessageDrivenBeanActivation implements HealthCheck {

    private final XAConnectionFactory factory;

    private final Instance<MessageListener> listeners;

    private final TransactionManager tm;

    private final Map<String, MessageListenerHolder> holders = new ConcurrentHashMap<>();

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Tracer tracer;

    public QuarkusMessageDrivenBeanActivation(XAConnectionFactory factory, @Any Instance<MessageListener> listeners, TransactionManager tm, Tracer tracer) {
        this.factory = factory;
        this.listeners = listeners;
        this.tm = tm;
        this.tracer = tracer;
    }

    public void init(@Observes Startup startup) {
        listeners.forEach(listener -> {
            Class<?> clazz = ClientProxy.unwrap(listener).getClass();
            if (Subclass.class.isAssignableFrom(clazz)) {
                clazz = clazz.getSuperclass();
            }
            String listenerName = clazz.getSimpleName();
            log.info("Initializing message listener {}", listenerName);

            Map<String, String> props = Arrays.stream(clazz.getAnnotation(MessageDriven.class).activationConfig()).collect(Collectors.toMap(ActivationConfigProperty::propertyName, ActivationConfigProperty::propertyValue));

            MessageListenerHolder holder = new MessageListenerHolder(listenerName, props, listener, factory, executorService, tm, tracer);
            holders.put(listenerName, holder);

            holder.listen();
            log.info("Listen invoked...");

        });
    }

    public void tearDown(@Observes Shutdown shutdown) {
        log.info("Shutting down.");
        holders.values().forEach(MessageListenerHolder::close);
        executorService.shutdown();
    }


    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder responseBuilder = HealthCheckResponse.named("Message Beans");
        AtomicInteger failed = new AtomicInteger(0);
        holders.forEach((name, holder) -> {

            boolean connected = holder.isConnected();
            responseBuilder.withData(name, connected ? "[OK]" : "[KO]");
            if (!connected) failed.incrementAndGet();
        });
        responseBuilder.status(failed.get() == 0);
        return responseBuilder.build();
    }
}

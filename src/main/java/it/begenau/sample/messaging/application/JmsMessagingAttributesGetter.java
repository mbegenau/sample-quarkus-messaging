package it.begenau.sample.messaging.application;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import jakarta.jms.*;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

@Slf4j
enum JmsMessagingAttributesGetter implements MessagingAttributesGetter<Message, Void>, TextMapGetter<Message> {
    INSTANCE;

    @Override
    public String getSystem(Message message) {
        return "jms";
    }

    @Nullable
    @Override
    public String getDestinationKind(Message message) {
        try {
            return message.getJMSDestination() instanceof Queue ? "queue" : "topic";
        } catch (JMSException e) {
            return null;
        }
    }

    @Nullable
    @Override
    public String getDestination(Message message) {
        try {
            if (isTemporaryDestination(message)) {
                return "(temporary)";
            }
            Destination d = message.getJMSDestination();
            if (d instanceof Queue q) return q.getQueueName();
            if (d instanceof Topic t) return t.getTopicName();
            return null;
        } catch (JMSException e) {
            return null;
        }
    }

    @Override
    public boolean isTemporaryDestination(Message message) {
        try {
            Destination d = message.getJMSDestination();
            return (d instanceof TemporaryQueue || d instanceof TemporaryTopic);
        } catch (JMSException e) {
            return false;
        }
    }

    @Nullable
    @Override
    public String getConversationId(Message message) {
        try {
            return message.getJMSCorrelationID();
        } catch (JMSException e) {
            return null;
        }
    }

    @Nullable
    @Override
    public Long getMessagePayloadSize(Message message) {
        return null;
    }

    @Nullable
    @Override
    public Long getMessagePayloadCompressedSize(Message message) {
        return null;
    }

    @Nullable
    @Override
    public String getMessageId(Message message, @Nullable Void unused) {
        try {
            return message.getJMSMessageID();
        } catch (JMSException e) {
            return null;
        }
    }

    @Override
    public List<String> getMessageHeader(Message message, String name) {
        try {
            String stringProperty = message.getStringProperty(name);
            log.trace("property {} = {}", name, stringProperty);
            return stringProperty == null ? List.of() : List.of(stringProperty);
        } catch (JMSException e) {
            return List.of();
        }
    }

    @Override
    public Iterable<String> keys(Message carrier) {
        try {
            return Collections.list((Enumeration<? extends Object>) carrier.getPropertyNames())
                    .stream().map(String::valueOf).toList();
        } catch (JMSException e) {
            return List.of();
        }
    }

    @Nullable
    @Override
    public String get(@Nullable Message carrier, String key) {
        if (carrier == null) return null;
        try {
            String stringProperty = carrier.getStringProperty(key);
            log.trace("property {} = {}", key, stringProperty);
            return stringProperty;
        } catch (JMSException e) {
            return null;
        }
    }
}

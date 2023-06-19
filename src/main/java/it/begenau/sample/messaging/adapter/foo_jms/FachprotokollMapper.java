package it.begenau.sample.messaging.adapter.foo_jms;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import it.begenau.sample.messaging.domain.models.FachprotokollEintrag;
import jakarta.inject.Inject;
import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import jakarta.json.bind.Jsonb;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;

import java.util.function.Supplier;

@Mapper(componentModel = "cdi")
public abstract class FachprotokollMapper {

    @Inject
    Jsonb jsonb;

    @Mapping(target = "JMSMessageID", ignore = true)
    @Mapping(target = "JMSTimestamp", ignore = true)
    @Mapping(target = "JMSCorrelationIDAsBytes", ignore = true)
    @Mapping(target = "JMSCorrelationID", ignore = true)
    @Mapping(target = "JMSReplyTo", ignore = true)
    @Mapping(target = "JMSDestination", ignore = true)
    @Mapping(target = "JMSDeliveryMode", ignore = true)
    @Mapping(target = "JMSRedelivered", ignore = true)
    @Mapping(target = "JMSType", ignore = true)
    @Mapping(target = "JMSExpiration", ignore = true)
    @Mapping(target = "JMSDeliveryTime", ignore = true)
    @Mapping(target = "JMSPriority", ignore = true)
    @Mapping(target = "text", source = ".")
    public abstract TextMessage toMessage(FachprotokollEintrag entry, @Context Supplier<TextMessage> messageCreator) throws JMSException;

    protected String toSerializedJson(FachprotokollEintrag entry) {
        return jsonb.toJson(entry);
    }

    @ObjectFactory
    protected TextMessage createTextMessage(@Context Supplier<TextMessage> messageCreator) {
        return messageCreator.get();
    }
}

package it.begenau.sample.messaging.adapter.foo_jms;

import it.begenau.sample.messaging.MyModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
@Path("/send-message")
@Slf4j
public class TestAmqpConnector {

    AtomicInteger sendCounter = new AtomicInteger(0);
    AtomicInteger receiveCounter = new AtomicInteger(0);

    @Incoming("queue-name-filled-from-properties")
    public CompletionStage<Void> react(Message<String> m) {
        log.info("{} Received message {}", receiveCounter.incrementAndGet(), m.getPayload());
        return m.ack();
    }

    @Inject
    @Channel("jms-q")
    Emitter<MyModel> emitterQ;

    @GET
    public Response invoke() throws ExecutionException, InterruptedException {
        emitterQ.send(new MyModel("from-quarkus-%03d".formatted(sendCounter.incrementAndGet()))).toCompletableFuture().get();
        log.info("{} Send message", sendCounter.get());
        return Response.ok("done: %03d".formatted(sendCounter.get())).build();
    }
}

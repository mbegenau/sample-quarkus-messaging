package it.begenau.sample.messaging.adapter.foo_jms;

import io.vertx.core.json.JsonObject;
import it.begenau.sample.messaging.MyModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
//import org.eclipse.microprofile.reactive.messaging.*;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
@Path("/send-message")
public class MyProducer {

        @Incoming("test-q1")
        public CompletionStage<Void> react(Message<MyModel> m) {
            System.out.printf("Received message %s%n", m.getPayload().getVal());
            return m.ack();
        }

        @Incoming("test-q")
        public void react(JsonObject m) {
            System.out.printf("Received message %s%n", m.mapTo(MyModel.class).getVal());
        }

        @Inject
        @Channel("test-l")
        Emitter<MyModel> emitter;
        @Inject
        @Channel("jms-q")
        Emitter<MyModel> emitterQ;

        @GET
        public Response invoke() throws ExecutionException, InterruptedException {
            emitterQ.send(new MyModel("from-quarkus")).toCompletableFuture().get();
            emitter.send(new MyModel("from-quarkus")).toCompletableFuture().get();
            return Response.ok("done").build();
        }
}

package com.github.dreamhead.moco;

import org.junit.Test;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.github.dreamhead.moco.Moco.by;
import static com.github.dreamhead.moco.Moco.text;
import static com.github.dreamhead.moco.Runner.running;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class MocoWebsocketTest {
    @Test
    public void should_connect() throws Exception {
        HttpServer server = Moco.httpServer(12306);
        WebSocketServer webSocketServer = server.websocket("/ws");
        webSocketServer.connected(text("hello"));

        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            assertThat(endpoint.getMessage(), is("hello"));
        });
    }

    @Test
    public void should_response_based_on_request() throws Exception {
        HttpServer server = Moco.httpServer(12306);
        WebSocketServer webSocketServer = server.websocket("/ws");
        webSocketServer.request(by("foo")).response("bar");

        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            endpoint.sendMessage("foo");
            assertThat(endpoint.getMessage(), is("bar"));
        });
    }

    @Test
    public void should_response_any_response() throws Exception {
        HttpServer server = Moco.httpServer(12306);
        WebSocketServer webSocketServer = server.websocket("/ws");
        webSocketServer.request(by("foo")).response("bar");
        webSocketServer.response("any");

        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            endpoint.sendMessage("blah");
            assertThat(endpoint.getMessage(), is("any"));
        });
    }

    @ClientEndpoint
    public static class Endpoint {
        private Session userSession;
        private CompletableFuture<String> message = new CompletableFuture<>();

        public Endpoint(final URI uri) {
            try {
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                container.connectToServer(this, uri);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @OnOpen
        public void onOpen(final Session userSession) {
            this.userSession = userSession;
        }

        @OnClose
        public void onClose(final Session userSession, final CloseReason reason) {
            this.userSession = null;
        }

        @OnMessage
        public void onMessage(final String message) {
            this.message.complete(message);
        }

        public void sendMessage(final String message) {
            this.userSession.getAsyncRemote().sendText(message);
        }

        public String getMessage() {
            try {
                return message.get(1, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException e) {
                return "";
            } catch (TimeoutException e) {
                throw new IllegalStateException("No message found");
            }
        }
    }
}

package com.ggukmoney.beanzip.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DelayedHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService executor;

    private DelayedHttpServer(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    public static DelayedHttpServer start(Duration delay, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool();
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(delay);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client is expected to close the exchange after its read timeout.
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(executor);
        server.start();
        return new DelayedHttpServer(server, executor);
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}

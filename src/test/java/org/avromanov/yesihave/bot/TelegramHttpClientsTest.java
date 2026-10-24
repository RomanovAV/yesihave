package org.avromanov.yesihave.bot;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramHttpClientsTest {
    @Test
    void totalDeadlineStopsTricklingResponseAndNextRequestStillWorks() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool();
        var http = TelegramHttpClients.apiClient().newBuilder()
                .readTimeout(Duration.ofSeconds(1))
                .callTimeout(Duration.ofMillis(450)).build();
        server.setExecutor(executor);
        server.createContext("/slow", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 100);
                for (int i = 0; i < 100; i++) {
                    exchange.getResponseBody().write('a');
                    exchange.getResponseBody().flush();
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } catch (java.io.IOException ignored) {
                // Cancellation closes the socket while the test server is writing.
            }
        });
        server.createContext("/fast", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 2);
                exchange.getResponseBody().write("ok".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        });
        server.start();
        try {
            var rest = RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .requestFactory(new OkHttp3ClientHttpRequestFactory(http)).build();
            long started = System.nanoTime();
            assertThatThrownBy(() -> rest.get().uri("/slow").retrieve().body(String.class))
                    .isInstanceOf(RestClientException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
            assertThat(rest.get().uri("/fast").retrieve().body(String.class)).isEqualTo("ok");
        } finally {
            server.stop(0);
            executor.shutdownNow();
            http.connectionPool().evictAll();
            http.dispatcher().executorService().shutdownNow();
        }
    }

    @Test
    void pollingDeadlineLeavesRoomForFiftySecondLongPoll() {
        var polling = TelegramHttpClients.pollingClient();
        assertThat(polling.connectTimeoutMillis()).isEqualTo(5_000);
        assertThat(polling.readTimeoutMillis()).isEqualTo(65_000);
        assertThat(polling.callTimeoutMillis()).isEqualTo(70_000);
        var api = TelegramHttpClients.apiClient();
        assertThat(api.callTimeoutMillis()).isEqualTo(25_000);
        assertThat(api.writeTimeoutMillis()).isEqualTo(10_000);
        assertThat(api.retryOnConnectionFailure()).isFalse();
    }
}

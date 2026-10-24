package org.avromanov.yesihave.bot;

import okhttp3.OkHttpClient;

import java.time.Duration;

final class TelegramHttpClients {
    private TelegramHttpClients() {
    }

    static OkHttpClient apiClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .writeTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(20))
                .callTimeout(Duration.ofSeconds(25))
                // A timed-out upload may already have reached Telegram.
                .retryOnConnectionFailure(false)
                .build();
    }

    static OkHttpClient pollingClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .writeTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(65))
                // GetUpdates waits up to 50 seconds; leave time for transport,
                // but also bound the entire call, including HTTP/2 stream waits.
                .callTimeout(Duration.ofSeconds(70))
                .build();
    }
}

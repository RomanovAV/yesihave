package org.avromanov.yesihave.bot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.telegram")
public record TelegramProperties(
        String token,
        String username,
        Duration sessionTtl
) {
    public TelegramProperties {
        if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("app.telegram.session-ttl must be positive");
        }
    }
}

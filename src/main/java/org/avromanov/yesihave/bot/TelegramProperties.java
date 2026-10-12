package org.avromanov.yesihave.bot;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Set;

@ConfigurationProperties(prefix = "app.telegram")
public record TelegramProperties(
        String token,
        String username,
        Duration sessionTtl,
        Set<Long> allowedUserIds
) {
    public TelegramProperties {
        if (sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("app.telegram.session-ttl must be positive");
        }
        allowedUserIds = allowedUserIds == null ? Set.of() : Set.copyOf(allowedUserIds);
        if (allowedUserIds.stream().anyMatch(id -> id <= 0)) {
            throw new IllegalArgumentException("app.telegram.allowed-user-ids must be positive");
        }
    }

    @Override
    public String toString() {
        return "TelegramProperties[token=[REDACTED], username=" + username
                + ", sessionTtl=" + sessionTtl + ", allowedUserIds=" + allowedUserIds + "]";
    }
}

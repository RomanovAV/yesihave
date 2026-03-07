package org.avromanov.yesihave.bot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.telegram")
public record TelegramProperties(
        String token,
        String username
) {
}

package org.avromanov.yesihave.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${app.telegram.token:}'.length() > 0")
public class TelegramPollingConfiguration {
    @Bean
    TelegramBotsLongPollingApplication telegramBotsApplication(ObjectMapper objectMapper) {
        return new TelegramBotsLongPollingApplication(() -> objectMapper, TelegramHttpClients::pollingClient);
    }
}

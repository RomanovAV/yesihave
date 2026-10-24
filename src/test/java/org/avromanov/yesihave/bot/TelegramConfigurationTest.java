package org.avromanov.yesihave.bot;

import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.avromanov.yesihave.image.ImageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.starter.TelegramBotStarterConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("app.telegram.token=fake-secret", "app.telegram.session-ttl=15m");

    @Test
    void bindsAllowlistAndConstructsApiClientWithoutNetworkCalls() {
        runner.withPropertyValues("app.telegram.allowed-user-ids=123,456").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(TelegramApiClient.class);
            assertThat(context.getBean(TelegramProperties.class).allowedUserIds()).containsExactlyInAnyOrder(123L, 456L);
        });
    }

    @Test
    void missingAllowlistDoesNotGrantAccess() {
        runner.run(context -> assertThat(context.getBean(TelegramProperties.class).allowedUserIds()).isEmpty());
    }

    @Test
    void starterUsesTheBoundedPollingApplicationWithoutDuplicateBeans() {
        runner.withConfiguration(AutoConfigurations.of(TelegramBotStarterConfiguration.class))
                .withUserConfiguration(TelegramPollingConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(TelegramBotsLongPollingApplication.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TelegramProperties.class)
    @Import(TelegramApiClient.class)
    static class Config {
        @Bean
        ImageProperties images() {
            return new ImageProperties(10 * 1024 * 1024, 13_000_000, 6_000, 1);
        }

        @Bean
        ImageBytesNormalizer normalizer(ImageProperties images) {
            return new ImageBytesNormalizer(images);
        }
    }
}

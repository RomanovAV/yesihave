package org.avromanov.yesihave.bot;

import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.avromanov.yesihave.image.ImageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TelegramApiClientTest {
    private MockRestServiceServer server;
    private TelegramApiClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var images = new ImageProperties(10 * 1024 * 1024, 13_000_000, 6_000, 1);
        client = new TelegramApiClient(new TelegramProperties("fake-secret", "bot", Duration.ofMinutes(15), Set.of(123L)),
                new ImageBytesNormalizer(images), images, builder.build());
    }

    @Test
    void sendsNativeReplyKeyboard() {
        server.expect(requestTo("https://api.telegram.org/botfake-secret/sendMessage"))
                .andExpect(jsonPath("$.chat_id").value(123))
                .andExpect(jsonPath("$.reply_markup.keyboard[0][0]").value("Проверить"))
                .andExpect(jsonPath("$.reply_markup.resize_keyboard").value(true))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        client.sendMessage(123L, "Выберите", List.of(List.of("Проверить", "Добавить")));
        server.verify();
    }

    @Test
    void sanitizesSendErrorsInsteadOfLeakingRequestToken() {
        server.expect(requestTo("https://api.telegram.org/botfake-secret/sendMessage"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.sendMessage(123L, "Hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Telegram sendMessage failed").hasNoCause();
        server.verify();
    }

    @Test
    void sanitizesDownloadErrorsInsteadOfLeakingRequestToken() {
        server.expect(requestTo("https://api.telegram.org/botfake-secret/getFile"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThatThrownBy(() -> client.downloadFile("file"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Telegram file request failed").hasNoCause();
        server.verify();
    }
}

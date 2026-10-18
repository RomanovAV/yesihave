package org.avromanov.yesihave.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.avromanov.yesihave.image.ImageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void uploadsBothPhotosAsMultipartAlbumWithNumberedSideCaptions() {
        server.expect(requestTo("https://api.telegram.org/botfake-secret/sendMediaGroup"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
                    assertThat(body).contains("name=\"chat_id\"", "\r\n\r\n123\r\n",
                            "name=\"front\"; filename=\"front.jpg\"", "name=\"back\"; filename=\"back.jpg\"",
                            "\r\n\r\nfront-image\r\n", "\r\n\r\nback-image\r\n");
                    JsonNode media = mediaFrom(body);
                    assertThat(media.size()).isEqualTo(2);
                    assertThat(media.get(0).path("type").asText()).isEqualTo("photo");
                    assertThat(media.get(1).path("type").asText()).isEqualTo("photo");
                    assertThat(media.get(0).path("media").asText()).isEqualTo("attach://front");
                    assertThat(media.get(1).path("media").asText()).isEqualTo("attach://back");
                    assertThat(media.get(0).path("caption").asText()).isEqualTo("Совпадение 1: Бирдекель\nЛицевая сторона");
                    assertThat(media.get(1).path("caption").asText()).isEqualTo("Совпадение 1: Бирдекель\nОбратная сторона");
                })
                .andRespond(withSuccess("{\"ok\":true,\"result\":[]}", MediaType.APPLICATION_JSON));
        client.sendPhotoPair(123L, "Совпадение 1: Бирдекель", "front-image".getBytes(StandardCharsets.UTF_8),
                "back-image".getBytes(StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void longUnicodeCaptionsStayWithinTelegramLimit() {
        server.expect(requestTo("https://api.telegram.org/botfake-secret/sendMediaGroup"))
                .andExpect(request -> {
                    JsonNode media = mediaFrom(((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8));
                    for (JsonNode photo : media) {
                        String caption = photo.path("caption").asText();
                        assertThat(caption.codePointCount(0, caption.length())).isLessThanOrEqualTo(1024);
                        assertThat(caption).startsWith("Совпадение 1:").contains("…\n").doesNotContain("�");
                    }
                })
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        client.sendPhotoPair(123L, "Совпадение 1: " + "🍺".repeat(1100), new byte[]{1}, new byte[]{2});
        server.verify();
    }

    @Test
    void sanitizesAlbumErrorsInsteadOfLeakingRequestToken() {
        server.expect(requestTo("https://api.telegram.org/botfake-secret/sendMediaGroup"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.sendPhotoPair(123L, "Candidate", new byte[]{1}, new byte[]{2}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Telegram sendMediaGroup failed").hasNoCause();
        server.verify();
    }

    @Test
    void rejectsUnsuccessfulAlbumResponse() {
        server.expect(requestTo("https://api.telegram.org/botfake-secret/sendMediaGroup"))
                .andRespond(withSuccess("{\"ok\":false}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.sendPhotoPair(123L, "Candidate", new byte[]{1}, new byte[]{2}))
                .hasMessage("Telegram sendMediaGroup failed");
        server.verify();
    }

    private JsonNode mediaFrom(String body) throws java.io.IOException {
        var match = Pattern.compile("name=\"media\"\\r\\n.*?\\r\\n\\r\\n(.*?)\\r\\n--", Pattern.DOTALL).matcher(body);
        assertThat(match.find()).isTrue();
        return new ObjectMapper().readTree(match.group(1));
    }
}

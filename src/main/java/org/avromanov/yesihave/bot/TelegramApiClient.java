package org.avromanov.yesihave.bot;

import com.fasterxml.jackson.databind.JsonNode;
import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.avromanov.yesihave.image.ImageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Component
@ConditionalOnExpression("'${app.telegram.token:}'.length() > 0")
public class TelegramApiClient {
    private final RestClient restClient;
    private final String token;
    private final ImageBytesNormalizer imageBytesNormalizer;
    private final ImageProperties imageProperties;

    @Autowired
    public TelegramApiClient(TelegramProperties properties,
                             ImageBytesNormalizer imageBytesNormalizer,
                             ImageProperties imageProperties) {
        this(properties, imageBytesNormalizer, imageProperties, defaultRestClient());
    }

    TelegramApiClient(TelegramProperties properties,
                      ImageBytesNormalizer imageBytesNormalizer,
                      ImageProperties imageProperties,
                      RestClient restClient) {
        this.token = properties.token();
        this.imageBytesNormalizer = imageBytesNormalizer;
        this.imageProperties = imageProperties;
        this.restClient = restClient;
    }

    private static RestClient defaultRestClient() {
        var factory = new OkHttp3ClientHttpRequestFactory(TelegramHttpClients.apiClient());
        return RestClient.builder().requestFactory(factory).build();
    }

    public void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, List.of());
    }

    public void sendMessage(Long chatId, String text, List<List<String>> keyboard) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("reply_markup", keyboard.isEmpty() ? Map.of("remove_keyboard", true)
                : Map.of("keyboard", keyboard, "resize_keyboard", true));
        try {
            restClient.post()
                .uri("https://api.telegram.org/bot{token}/sendMessage", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException e) {
            // RestClient exceptions can include the request URI and bearer token.
            throw new IllegalStateException("Telegram sendMessage failed");
        }
    }

    public void sendPhotoPair(Long chatId, String caption, byte[] front, byte[] back) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("chat_id", chatId.toString());
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Leave room for the side label within Telegram's 1024-character caption limit.
        String title = caption.codePointCount(0, caption.length()) > 900
                ? caption.substring(0, caption.offsetByCodePoints(0, 899)) + "…" : caption;
        body.add("media", new HttpEntity<>(List.of(
                Map.of("type", "photo", "media", "attach://front", "caption", title + "\nЛицевая сторона"),
                Map.of("type", "photo", "media", "attach://back", "caption", title + "\nОбратная сторона")
        ), headers));
        body.add("front", photoResource(front, "front.jpg"));
        body.add("back", photoResource(back, "back.jpg"));
        try {
            JsonNode response = restClient.post()
                    .uri("https://api.telegram.org/bot{token}/sendMediaGroup", token)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.path("ok").asBoolean(false)) {
                throw new IllegalStateException("Telegram sendMediaGroup failed");
            }
        } catch (RestClientException e) {
            throw new IllegalStateException("Telegram sendMediaGroup failed");
        }
    }

    private ByteArrayResource photoResource(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

    public byte[] downloadFile(String fileId) {
        try {
            return downloadFileUnchecked(fileId);
        } catch (RestClientException e) {
            throw new IllegalStateException("Telegram file request failed");
        }
    }

    private byte[] downloadFileUnchecked(String fileId) {
        JsonNode response = restClient.post()
                .uri("https://api.telegram.org/bot{token}/getFile", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GetFileRequest(fileId))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("ok").asBoolean(false) == false) {
            throw new IllegalStateException("Telegram getFile failed");
        }

        String filePath = response.path("result").path("file_path").asText();
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalStateException("Telegram file_path is empty");
        }

        long declaredSize = response.path("result").path("file_size").asLong(-1L);
        if (declaredSize > 0) {
            imageBytesNormalizer.validateEncodedSize(declaredSize);
        }

        byte[] bytes = restClient.get()
                .uri("https://api.telegram.org/file/bot{token}/{filePath}", token, filePath)
                .exchange((request, fileResponse) -> {
                    if (fileResponse.getStatusCode().isError()) {
                        throw new IllegalStateException(
                                "Telegram file download failed with status " + fileResponse.getStatusCode()
                        );
                    }
                    return fileResponse.getBody().readNBytes((int) imageProperties.maxFileBytes() + 1);
                });
        if (bytes == null) {
            throw new IllegalStateException("Telegram returned an empty file response");
        }
        imageBytesNormalizer.validateEncodedSize(bytes.length);
        return bytes;
    }

    private record GetFileRequest(String file_id) {
    }
}

package org.avromanov.yesihave.bot;

import com.fasterxml.jackson.databind.JsonNode;
import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.avromanov.yesihave.image.ImageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnExpression("'${app.telegram.token:}'.length() > 0")
public class TelegramApiClient {
    private final RestClient restClient;
    private final String token;
    private final ImageBytesNormalizer imageBytesNormalizer;
    private final ImageProperties imageProperties;

    public TelegramApiClient(TelegramProperties properties,
                             ImageBytesNormalizer imageBytesNormalizer,
                             ImageProperties imageProperties) {
        this.token = properties.token();
        this.imageBytesNormalizer = imageBytesNormalizer;
        this.imageProperties = imageProperties;
        this.restClient = RestClient.create();
    }

    public void sendMessage(Long chatId, String text) {
        restClient.post()
                .uri("https://api.telegram.org/bot{token}/sendMessage", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SendMessageRequest(chatId, text))
                .retrieve()
                .toBodilessEntity();
    }

    public byte[] downloadFile(String fileId) {
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

    private record SendMessageRequest(Long chat_id, String text) {
    }

    private record GetFileRequest(String file_id) {
    }
}

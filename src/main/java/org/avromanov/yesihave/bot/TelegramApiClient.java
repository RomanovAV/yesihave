package org.avromanov.yesihave.bot;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnExpression("'${app.telegram.token:}'.length() > 0")
public class TelegramApiClient {
    private final RestClient restClient;
    private final String token;

    public TelegramApiClient(TelegramProperties properties) {
        this.token = properties.token();
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

        return restClient.get()
                .uri("https://api.telegram.org/file/bot{token}/{filePath}", token, filePath)
                .retrieve()
                .body(byte[].class);
    }

    private record SendMessageRequest(Long chat_id, String text) {
    }

    private record GetFileRequest(String file_id) {
    }
}

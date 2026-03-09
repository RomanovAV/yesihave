package org.avromanov.yesihave.bot;

import org.avromanov.yesihave.application.AddCoasterUseCase;
import org.avromanov.yesihave.application.CheckPairUseCase;
import org.avromanov.yesihave.application.model.CandidateDto;
import org.avromanov.yesihave.application.model.MatchResultDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnExpression("'${app.telegram.token:}'.length() > 0")
public class YesIHaveTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private final TelegramProperties telegramProperties;
    private final TelegramApiClient telegramApiClient;
    private final CheckPairUseCase checkPairUseCase;
    private final AddCoasterUseCase addCoasterUseCase;
    private final Map<Long, BotSession> sessions = new ConcurrentHashMap<>();

    public YesIHaveTelegramBot(TelegramProperties telegramProperties,
                               TelegramApiClient telegramApiClient,
                               CheckPairUseCase checkPairUseCase,
                               AddCoasterUseCase addCoasterUseCase) {
        this.telegramProperties = telegramProperties;
        this.telegramApiClient = telegramApiClient;
        this.checkPairUseCase = checkPairUseCase;
        this.addCoasterUseCase = addCoasterUseCase;
    }

    @Override
    public String getBotToken() {
        return telegramProperties.token();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        Long chatId = message.getChatId();
        if (chatId == null) {
            return;
        }

        BotSession session = sessions.computeIfAbsent(chatId, id -> new BotSession());
        try {
            if (message.hasText()) {
                handleText(chatId, message.getText().trim(), session);
                return;
            }

            if (message.hasPhoto()) {
                handlePhoto(chatId, message, session);
                return;
            }

            telegramApiClient.sendMessage(chatId, "Пришлите команду /check или /add.");
        } catch (Exception e) {
            session.reset();
            telegramApiClient.sendMessage(chatId, "Произошла ошибка. Попробуйте снова с /check или /add.");
        }
    }

    private void handleText(Long chatId, String text, BotSession session) {
        if (text.startsWith("/start")) {
            session.reset();
            telegramApiClient.sendMessage(chatId, "Привет. Используй /check для проверки и /add для добавления.");
            return;
        }

        if (text.startsWith("/help")) {
            telegramApiClient.sendMessage(chatId, "/check - проверить по front/back\n/add - добавить новый бирдекель");
            return;
        }

        if (text.startsWith("/check")) {
            session.reset();
            session.setAction(BotAction.CHECK);
            session.setState(TelegramSessionState.WAIT_FRONT);
            telegramApiClient.sendMessage(chatId, "Пришли фото лицевой стороны (front).");
            return;
        }

        if (text.startsWith("/add")) {
            session.reset();
            session.setAction(BotAction.ADD);
            session.setState(TelegramSessionState.WAIT_FRONT);
            telegramApiClient.sendMessage(chatId, "Пришли фото лицевой стороны (front).");
            return;
        }

        if (session.action() == BotAction.ADD && session.state() == TelegramSessionState.WAIT_NAME) {
            String name = text.isBlank() ? "Unnamed coaster" : text;
            var coasterId = addCoasterUseCase.add(name, session.frontImage(), session.backImage());
            session.reset();
            telegramApiClient.sendMessage(chatId, "Добавлено: " + name + " (id: " + coasterId + ").");
            return;
        }

        telegramApiClient.sendMessage(chatId, "Не понял команду. Используй /check или /add.");
    }

    private void handlePhoto(Long chatId, Message message, BotSession session) {
        if (session.state() != TelegramSessionState.WAIT_FRONT && session.state() != TelegramSessionState.WAIT_BACK) {
            telegramApiClient.sendMessage(chatId, "Сначала отправь /check или /add.");
            return;
        }

        PhotoSize bestPhoto = message.getPhoto()
                .stream()
                .max(Comparator.comparingInt(photo -> photo.getFileSize() == null ? 0 : photo.getFileSize()))
                .orElseThrow();

        byte[] bytes = telegramApiClient.downloadFile(bestPhoto.getFileId());

        if (session.state() == TelegramSessionState.WAIT_FRONT) {
            session.setFrontImage(bytes);
            session.setState(TelegramSessionState.WAIT_BACK);
            telegramApiClient.sendMessage(chatId, "Теперь пришли фото обратной стороны (back).");
            return;
        }

        session.setBackImage(bytes);
        if (session.action() == BotAction.CHECK) {
            MatchResultDto result = checkPairUseCase.check(chatId, session.frontImage(), session.backImage());
            session.reset();
            telegramApiClient.sendMessage(chatId, formatMatchResult(result));
            return;
        }

        if (session.action() == BotAction.ADD) {
            session.setState(TelegramSessionState.WAIT_NAME);
            telegramApiClient.sendMessage(chatId, "Введи name для бирдекеля.");
        }
    }

    private String formatMatchResult(MatchResultDto result) {
        StringBuilder builder = new StringBuilder();
        builder.append("Результат: ").append(result.decision()).append('\n')
                .append("front=").append(formatScore(result.scoreFront())).append(", ")
                .append("back=").append(formatScore(result.scoreBack())).append(", ")
                .append("pair=").append(formatScore(result.pairScore())).append('\n');

        if (result.topCandidates() == null || result.topCandidates().isEmpty()) {
            builder.append("Кандидаты: нет");
            return builder.toString();
        }

        builder.append("Top кандидаты:\n");
        for (CandidateDto candidate : result.topCandidates()) {
            builder.append(candidate.rank())
                    .append(". ")
                    .append(candidate.name())
                    .append(" id=")
                    .append(candidate.coasterId())
                    .append(" pair=")
                    .append(formatScore(candidate.pairScore()))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private String formatScore(Double score) {
        if (score == null) {
            return "n/a";
        }
        return String.format("%.4f", score);
    }
}

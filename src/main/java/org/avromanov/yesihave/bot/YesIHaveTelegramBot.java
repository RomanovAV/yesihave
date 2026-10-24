package org.avromanov.yesihave.bot;

import org.avromanov.yesihave.application.AddCoasterUseCase;
import org.avromanov.yesihave.application.AddCheckedCoasterUseCase;
import org.avromanov.yesihave.application.CheckPairUseCase;
import org.avromanov.yesihave.application.CollectionStatisticsService;
import org.avromanov.yesihave.application.DuplicateCoasterException;
import org.avromanov.yesihave.application.RecordCheckFeedbackUseCase;
import org.avromanov.yesihave.application.model.CandidateDto;
import org.avromanov.yesihave.application.model.FeedbackSource;
import org.avromanov.yesihave.application.model.MatchResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnExpression("'${app.telegram.token:}'.length() > 0")
public class YesIHaveTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {
    private static final Logger log = LoggerFactory.getLogger(YesIHaveTelegramBot.class);

    private final TelegramProperties telegramProperties;
    private final TelegramApiClient telegramApiClient;
    private final CheckPairUseCase checkPairUseCase;
    private final AddCoasterUseCase addCoasterUseCase;
    private final RecordCheckFeedbackUseCase recordCheckFeedbackUseCase;
    private final AddCheckedCoasterUseCase addCheckedCoasterUseCase;
    private final CandidatePhotoService candidatePhotoService;
    private final CollectionStatisticsService collectionStatisticsService;
    private static final List<List<String>> MAIN_MENU = List.of(List.of("Проверить", "Добавить"));
    private static final List<List<String>> CANCEL_MENU = List.of(List.of("Отмена"));
    private final Map<Long, BotSession> sessions = new ConcurrentHashMap<>();

    public YesIHaveTelegramBot(TelegramProperties telegramProperties,
                               TelegramApiClient telegramApiClient,
                               CheckPairUseCase checkPairUseCase,
                               AddCoasterUseCase addCoasterUseCase,
                               RecordCheckFeedbackUseCase recordCheckFeedbackUseCase,
                               AddCheckedCoasterUseCase addCheckedCoasterUseCase,
                               CandidatePhotoService candidatePhotoService,
                               CollectionStatisticsService collectionStatisticsService) {
        this.telegramProperties = telegramProperties;
        this.telegramApiClient = telegramApiClient;
        this.checkPairUseCase = checkPairUseCase;
        this.addCoasterUseCase = addCoasterUseCase;
        this.recordCheckFeedbackUseCase = recordCheckFeedbackUseCase;
        this.addCheckedCoasterUseCase = addCheckedCoasterUseCase;
        this.candidatePhotoService = candidatePhotoService;
        this.collectionStatisticsService = collectionStatisticsService;
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
        long started = System.nanoTime();
        purgeExpiredSessions();
        if (!update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        Long chatId = message.getChatId();
        if (chatId == null || message.getChat() == null || !"private".equals(message.getChat().getType())
                || message.getFrom() == null || Boolean.TRUE.equals(message.getFrom().getIsBot())
                || !chatId.equals(message.getFrom().getId())
                || !telegramProperties.allowedUserIds().contains(message.getFrom().getId())) {
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

            menu(chatId, "Выберите проверку или добавление. Фото отправляйте как фотографии, не как файлы.");
        } catch (DuplicateCoasterException e) {
            sessions.remove(chatId);
            menu(chatId, "Такой бирдекель уже есть в коллекции.");
        } catch (Exception e) {
            sessions.remove(chatId);
            log.error("Telegram update processing failed for chat {}, type={}", chatId, e.getClass().getSimpleName());
            menu(chatId, "Не удалось обработать запрос. Начните заново: проверка или добавление.");
        } finally {
            log.info("Telegram update completed updateId={} elapsedMs={}", update.getUpdateId(), elapsedMs(started));
        }
    }

    private void handleText(Long chatId, String text, BotSession session) {
        String command = text.split("\\s+", 2)[0];
        if (command.equals("/start") || command.equals("/cancel") || text.equals("Отмена")) {
            sessions.remove(chatId);
            menu(chatId, "Ваша коллекция бирдекелей — здесь и на сайте. Для проверки просто пришлите фото одной стороны, затем второй. Для добавления выберите «Добавить».");
            return;
        }

        if (command.equals("/help")) {
            telegramApiClient.sendMessage(
                    chatId,
                    "Для проверки просто пришлите фото одной стороны, затем второй.\n\n"
                            + "/check - проверить по двум фото\n"
                            + "/add - добавить новый бирдекель\n"
                            + "/match <id> - подтвердить найденный дубликат\n"
                            + "/new - сохранить новый по этим же фото\n"
                            + "/skip - не сохранять находку\n"
                            + "/cancel - отменить текущее действие"
            );
            return;
        }

        if (command.equals("/match")) {
            handleMatchFeedback(chatId, text, session);
            return;
        }

        if (text.startsWith("Совпадение ") && session.state() == TelegramSessionState.WAIT_FEEDBACK) {
            try {
                int index = Integer.parseInt(text.substring("Совпадение ".length())) - 1;
                if (index < 0 || index >= session.candidateOrder().size()) {
                    throw new IllegalArgumentException();
                }
                handleMatchFeedback(chatId, "/match " + session.candidateOrder().get(index), session);
            } catch (IllegalArgumentException e) {
                telegramApiClient.sendMessage(chatId, "Выберите кандидата из последней проверки.");
            }
            return;
        }

        if (command.equals("/new") || text.equals("Сохранить новый")) {
            if (session.state() != TelegramSessionState.WAIT_FEEDBACK) {
                telegramApiClient.sendMessage(chatId, "Сначала выполните проверку.");
                return;
            }
            session.setState(TelegramSessionState.WAIT_CHECKED_NAME);
            telegramApiClient.sendMessage(chatId, "Введите название. Использую фото из этой проверки — отправлять их повторно не нужно.", CANCEL_MENU);
            return;
        }

        if (command.equals("/skip") || text.equals("Не сохранять")) {
            handleNoMatchFeedback(chatId, session);
            return;
        }

        if (command.equals("/check") || text.equals("Проверить")) {
            session.reset();
            session.setAction(BotAction.CHECK);
            session.setState(TelegramSessionState.WAIT_FRONT);
            telegramApiClient.sendMessage(chatId, "Пришлите фото лицевой стороны бирдекеля.", CANCEL_MENU);
            return;
        }

        if (command.equals("/add") || text.equals("Добавить")) {
            session.reset();
            session.setAction(BotAction.ADD);
            session.setState(TelegramSessionState.WAIT_FRONT);
            telegramApiClient.sendMessage(chatId, "Пришлите фото лицевой стороны бирдекеля.", CANCEL_MENU);
            return;
        }

        if ((session.action() == BotAction.ADD && session.state() == TelegramSessionState.WAIT_NAME)
                || session.state() == TelegramSessionState.WAIT_CHECKED_NAME) {
            if (text.isBlank() || text.startsWith("/")) {
                telegramApiClient.sendMessage(chatId, "Введите название текстом или отмените действие.", CANCEL_MENU);
                return;
            }
            if (session.state() == TelegramSessionState.WAIT_CHECKED_NAME) {
                addCheckedCoasterUseCase.add(text, session.checkRequestId(), FeedbackSource.TELEGRAM);
            } else {
                addCoasterUseCase.add(text, session.frontImage(), session.backImage());
            }
            sessions.remove(chatId);
            menu(chatId, "Сохранено в коллекцию: " + text
                    + "\nВсего уникальных бирдекелей: " + collectionStatisticsService.countUniqueCoasters() + ".");
            return;
        }

        menu(chatId, "Выберите действие кнопкой или отправьте /check или /add.");
    }

    private void handlePhoto(Long chatId, Message message, BotSession session) {
        if (session.state() == TelegramSessionState.IDLE || session.state() == TelegramSessionState.WAIT_FEEDBACK) {
            session.reset();
            session.setAction(BotAction.CHECK);
            session.setState(TelegramSessionState.WAIT_FRONT);
        }
        if (session.state() != TelegramSessionState.WAIT_FRONT && session.state() != TelegramSessionState.WAIT_BACK) {
            telegramApiClient.sendMessage(chatId, "Сейчас жду название бирдекеля текстом. Для новой проверки отправьте /check или нажмите «Отмена».", CANCEL_MENU);
            return;
        }

        PhotoSize bestPhoto = message.getPhoto()
                .stream()
                .max(Comparator.comparingLong(photo -> (long) photo.getWidth() * photo.getHeight()))
                .orElseThrow();

        long downloadStarted = System.nanoTime();
        byte[] bytes = telegramApiClient.downloadFile(bestPhoto.getFileId());
        log.info("Telegram photo downloaded bytes={} elapsedMs={}", bytes.length, elapsedMs(downloadStarted));

        if (session.state() == TelegramSessionState.WAIT_FRONT) {
            session.setFrontImage(bytes);
            session.setState(TelegramSessionState.WAIT_BACK);
            telegramApiClient.sendMessage(chatId, "Теперь пришлите фото обратной стороны.", CANCEL_MENU);
            return;
        }

        session.setBackImage(bytes);
        if (session.action() == BotAction.CHECK) {
            MatchResultDto result = checkPairUseCase.check(chatId, session.frontImage(), session.backImage());
            List<CandidateDto> candidates = result.topCandidates() == null ? List.of()
                    : result.topCandidates().stream().limit(3).toList();
            session.prepareFeedback(result.checkRequestId(), candidates.stream().map(CandidateDto::coasterId).toList());
            List<List<String>> keyboard = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                keyboard.add(List.of("Совпадение " + (i + 1)));
            }
            keyboard.add(List.of("Сохранить новый", "Не сохранять"));
            keyboard.add(List.of("Отмена"));
            long previewsStarted = System.nanoTime();
            List<Integer> unavailablePhotos = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                if (elapsedMs(previewsStarted) >= 30_000) {
                    for (int remaining = i; remaining < candidates.size(); remaining++) {
                        unavailablePhotos.add(remaining + 1);
                    }
                    break;
                }
                CandidateDto candidate = candidates.get(i);
                long previewStarted = System.nanoTime();
                try {
                    var photos = candidate.referenceCheckRequestId() == null
                            ? candidatePhotoService.load(candidate.coasterId())
                            : candidatePhotoService.load(candidate.coasterId(), candidate.referenceCheckRequestId());
                    telegramApiClient.sendPhotoPair(chatId,
                            "Совпадение " + (i + 1) + ": " + candidate.name()
                                    + " — сходство " + formatScore(candidate.pairScore()),
                            photos.front(), photos.back());
                } catch (RuntimeException e) {
                    unavailablePhotos.add(i + 1);
                    log.warn("Candidate photos unavailable for coaster {}, type={}",
                            candidate.coasterId(), e.getClass().getSimpleName());
                } finally {
                    log.info("Telegram candidate preview checkId={} rank={} elapsedMs={}",
                            result.checkRequestId(), i + 1, elapsedMs(previewStarted));
                }
            }
            telegramApiClient.sendMessage(chatId,
                    formatMatchResult(result)
                            + (unavailablePhotos.isEmpty() ? "" : "\nНе удалось показать фото вариантов: "
                            + unavailablePhotos.stream().map(String::valueOf)
                            .collect(java.util.stream.Collectors.joining(", ")) + ".")
                            + "\n\nВыберите совпадение или сохраните новую находку.", keyboard);
            return;
        }

        if (session.action() == BotAction.ADD) {
            session.setState(TelegramSessionState.WAIT_NAME);
            telegramApiClient.sendMessage(chatId, "Введите название бирдекеля.", CANCEL_MENU);
        }
    }

    private String formatMatchResult(MatchResultDto result) {
        StringBuilder builder = new StringBuilder();
        builder.append(switch (result.decision()) {
            case MATCH -> "Похоже, такой бирдекель уже есть в коллекции.";
            case NO_MATCH -> "Уверенного совпадения не нашлось.";
            case UNCERTAIN -> "Есть похожие экземпляры — нужно ваше подтверждение.";
        }).append('\n').append("Сходство пары: ").append(formatScore(result.pairScore())).append('\n');

        if (result.topCandidates() == null || result.topCandidates().isEmpty()) {
            builder.append("Кандидаты: нет");
            return builder.toString();
        }

        builder.append("Похожие экземпляры:\n");
        int index = 0;
        for (CandidateDto candidate : result.topCandidates().stream().limit(3).toList()) {
            builder.append(++index)
                    .append(". ")
                    .append(candidate.name())
                    .append(" — сходство ")
                    .append(formatScore(candidate.pairScore()))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    private void handleMatchFeedback(Long chatId, String text, BotSession session) {
        if (session.state() != TelegramSessionState.WAIT_FEEDBACK) {
            telegramApiClient.sendMessage(chatId, "Сначала выполни /check.");
            return;
        }
        String[] parts = text.split("\\s+", 2);
        if (parts.length != 2) {
            telegramApiClient.sendMessage(chatId, "Укажи id: /match <id>.");
            return;
        }

        UUID coasterId;
        try {
            coasterId = UUID.fromString(parts[1].trim());
        } catch (IllegalArgumentException e) {
            telegramApiClient.sendMessage(chatId, "Некорректный id кандидата.");
            return;
        }
        if (!session.candidateIds().contains(coasterId)) {
            telegramApiClient.sendMessage(chatId, "Этот id не входил в кандидаты последней проверки.");
            return;
        }

        recordCheckFeedbackUseCase.confirmMatch(
                session.checkRequestId(),
                coasterId,
                FeedbackSource.TELEGRAM
        );
        sessions.remove(chatId);
        menu(chatId, "Совпадение подтверждено. Повторно в коллекцию не добавляю.");
    }

    private void handleNoMatchFeedback(Long chatId, BotSession session) {
        if (session.state() != TelegramSessionState.WAIT_FEEDBACK
                && session.state() != TelegramSessionState.WAIT_CHECKED_NAME) {
            telegramApiClient.sendMessage(chatId, "Сначала выполни /check.");
            return;
        }
        recordCheckFeedbackUseCase.confirmNoMatch(
                session.checkRequestId(),
                null,
                FeedbackSource.TELEGRAM
        );
        sessions.remove(chatId);
        menu(chatId, "Находку не сохраняю. Отсутствие совпадения отмечено.");
    }

    @Scheduled(fixedDelayString = "${app.telegram.session-cleanup-interval:1m}")
    void purgeExpiredSessions() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry ->
                entry.getValue().isExpired(now, telegramProperties.sessionTtl())
        );
    }

    private String formatScore(Double score) {
        if (score == null) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.1f%%", score * 100);
    }

    private static long elapsedMs(long started) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private void menu(Long chatId, String text) {
        telegramApiClient.sendMessage(chatId, text, MAIN_MENU);
    }
}

package org.avromanov.yesihave.bot;

import org.avromanov.yesihave.application.*;
import org.avromanov.yesihave.application.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;

class YesIHaveTelegramBotTest {
    private static final long OWNER = 123L;
    private static final UUID CHECK_ID = UUID.randomUUID();
    private static final UUID CANDIDATE_ID = UUID.randomUUID();
    private TelegramApiClient api;
    private CheckPairUseCase check;
    private AddCoasterUseCase add;
    private AddCheckedCoasterUseCase checked;
    private RecordCheckFeedbackUseCase feedback;
    private YesIHaveTelegramBot bot;

    @BeforeEach
    void setUp() {
        api = mock(TelegramApiClient.class);
        check = mock(CheckPairUseCase.class);
        add = mock(AddCoasterUseCase.class);
        checked = mock(AddCheckedCoasterUseCase.class);
        feedback = mock(RecordCheckFeedbackUseCase.class);
        bot = createBot(Set.of(OWNER));
        when(api.downloadFile("front")).thenReturn(new byte[]{1});
        when(api.downloadFile("back")).thenReturn(new byte[]{2});
        when(check.check(eq(OWNER), any(), any())).thenReturn(new MatchResultDto(
                MatchDecision.UNCERTAIN, .85, .9, .875, null,
                List.of(new CandidateDto(CANDIDATE_ID, "Guinness", .85, .9, .875, 1)), CHECK_ID));
    }

    private YesIHaveTelegramBot createBot(Set<Long> allowed) {
        return new YesIHaveTelegramBot(new TelegramProperties("test-token", "testbot", Duration.ofMinutes(15), allowed),
                api, check, add, feedback, checked);
    }

    @Test
    void deniesUnknownUsersBeforeAnyDownloadsOrCollectionAccess() {
        bot.consume(text(456L, "/check"));
        bot.consume(photo(456L, "front"));
        verifyNoInteractions(api, check, add, checked, feedback);
    }

    @Test
    void emptyAllowlistFailsClosed() {
        createBot(Set.of()).consume(text(OWNER, "/start"));
        verifyNoInteractions(api, check, add, checked, feedback);
    }

    @Test
    void ownerCannotUseBotFromGroupOrSpoofedChat() {
        Update group = text(OWNER, "/check");
        group.getMessage().getChat().setType("group");
        bot.consume(group);
        Update spoof = text(OWNER, "/check");
        spoof.getMessage().getChat().setId(456L);
        bot.consume(spoof);
        verifyNoInteractions(api, check, add, checked, feedback);
    }

    @Test
    void startDisplaysActionButtonsAndAcceptsLinkParameter() {
        bot.consume(text(OWNER, "/start pairing-code"));
        verify(api).sendMessage(eq(OWNER), anyString(), eq(List.of(List.of("Проверить", "Добавить"))));
    }

    @Test
    void checkedPhotosCanBeSavedWithoutAnotherDownload() {
        performCheck();
        bot.consume(text(OWNER, "Сохранить новый"));
        bot.consume(text(OWNER, "New coaster"));
        verify(checked).add("New coaster", CHECK_ID, FeedbackSource.TELEGRAM);
        verify(api, times(2)).downloadFile(anyString());
        verifyNoInteractions(add);
        verify(api).sendMessage(eq(OWNER), contains("Сохранено"), anyList());
    }

    @Test
    void candidateButtonsConfirmOnlyLastCheckCandidate() {
        performCheck();
        bot.consume(text(OWNER, "Совпадение 1"));
        bot.consume(text(OWNER, "Совпадение 1"));
        verify(feedback, times(1)).confirmMatch(CHECK_ID, CANDIDATE_ID, FeedbackSource.TELEGRAM);
        verifyNoInteractions(add, checked);
    }

    @Test
    void rejectsForeignCandidateAndInvalidCandidateButton() {
        performCheck();
        bot.consume(text(OWNER, "/match " + UUID.randomUUID()));
        bot.consume(text(OWNER, "Совпадение 4"));
        verifyNoInteractions(feedback);
    }

    @Test
    void cancellationDoesNotSaveCheckedPhotos() {
        performCheck();
        bot.consume(text(OWNER, "Сохранить новый"));
        bot.consume(text(OWNER, "Отмена"));
        bot.consume(text(OWNER, "Must not save"));
        verifyNoInteractions(add, checked, feedback);
    }

    @Test
    void explicitSkipRecordsFeedbackWithoutAdding() {
        performCheck();
        bot.consume(text(OWNER, "Не сохранять"));
        verify(feedback).confirmNoMatch(CHECK_ID, null, FeedbackSource.TELEGRAM);
        verifyNoInteractions(add, checked);
    }

    @Test
    void addButtonsKeepTwoPhotoAndNameFlow() {
        bot.consume(text(OWNER, "Добавить"));
        bot.consume(photo(OWNER, "front"));
        bot.consume(photo(OWNER, "back"));
        bot.consume(text(OWNER, "My coaster"));
        verify(add).add(eq("My coaster"), aryEq(new byte[]{1}), aryEq(new byte[]{2}));
        verifyNoInteractions(check, checked);
    }

    @Test
    void commandsWithWrongPrefixCannotStartPhotoProcessing() {
        bot.consume(text(OWNER, "/checkanything"));
        bot.consume(photo(OWNER, "front"));
        verify(api, never()).downloadFile(anyString());
    }

    @Test
    void propertiesNeverIncludeTokenInDebugString() {
        assertThat(new TelegramProperties("secret-value", "test", Duration.ofMinutes(15), null).toString())
                .doesNotContain("secret-value").contains("[REDACTED]");
    }

    private void performCheck() {
        bot.consume(text(OWNER, "Проверить"));
        bot.consume(photo(OWNER, "front"));
        bot.consume(photo(OWNER, "back"));
    }

    private Update text(long id, String text) {
        Update update = message(id);
        update.getMessage().setText(text);
        return update;
    }

    private Update photo(long id, String fileId) {
        Update update = message(id);
        PhotoSize photo = new PhotoSize();
        photo.setFileId(fileId);
        photo.setFileSize(100);
        photo.setWidth(100);
        photo.setHeight(100);
        update.getMessage().setPhoto(List.of(photo));
        return update;
    }

    private Update message(long id) {
        User user = new User(id, "Test", false);
        Chat chat = Chat.builder().id(id).type("private").build();
        Message message = new Message();
        message.setFrom(user);
        message.setChat(chat);
        Update update = new Update();
        update.setMessage(message);
        return update;
    }
}

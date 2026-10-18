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
import static org.mockito.AdditionalMatchers.not;
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
    private CandidatePhotoService photos;
    private YesIHaveTelegramBot bot;

    @BeforeEach
    void setUp() {
        api = mock(TelegramApiClient.class);
        check = mock(CheckPairUseCase.class);
        add = mock(AddCoasterUseCase.class);
        checked = mock(AddCheckedCoasterUseCase.class);
        feedback = mock(RecordCheckFeedbackUseCase.class);
        photos = mock(CandidatePhotoService.class);
        when(photos.load(any())).thenReturn(new CandidatePhotoService.PhotoPair(new byte[]{3}, new byte[]{4}));
        bot = createBot(Set.of(OWNER));
        when(api.downloadFile("front")).thenReturn(new byte[]{1});
        when(api.downloadFile("back")).thenReturn(new byte[]{2});
        when(check.check(eq(OWNER), any(), any())).thenReturn(new MatchResultDto(
                MatchDecision.UNCERTAIN, .85, .9, .875, null,
                List.of(new CandidateDto(CANDIDATE_ID, "Guinness", .85, .9, .875, 1)), CHECK_ID));
    }

    private YesIHaveTelegramBot createBot(Set<Long> allowed) {
        return new YesIHaveTelegramBot(new TelegramProperties("test-token", "testbot", Duration.ofMinutes(15), allowed),
                api, check, add, feedback, checked, photos);
    }

    @Test
    void deniesUnknownUsersBeforeAnyDownloadsOrCollectionAccess() {
        bot.consume(text(456L, "/check"));
        bot.consume(photo(456L, "front"));
        verifyNoInteractions(api, check, add, checked, feedback, photos);
    }

    @Test
    void emptyAllowlistFailsClosed() {
        createBot(Set.of()).consume(text(OWNER, "/start"));
        verifyNoInteractions(api, check, add, checked, feedback, photos);
    }

    @Test
    void ownerCannotUseBotFromGroupOrSpoofedChat() {
        Update group = text(OWNER, "/check");
        group.getMessage().getChat().setType("group");
        bot.consume(group);
        Update spoof = text(OWNER, "/check");
        spoof.getMessage().getChat().setId(456L);
        bot.consume(spoof);
        verifyNoInteractions(api, check, add, checked, feedback, photos);
    }

    @Test
    void startDisplaysActionButtonsAndAcceptsLinkParameter() {
        bot.consume(text(OWNER, "/start pairing-code"));
        verify(api).sendMessage(eq(OWNER), anyString(), eq(List.of(List.of("Проверить", "Добавить"))));
    }

    @Test
    void firstPhotoWithoutCommandAsksForOtherSideThenChecksBothPhotos() {
        bot.consume(photo(OWNER, "front"));
        verify(api).sendMessage(eq(OWNER), contains("фото обратной стороны"), eq(List.of(List.of("Отмена"))));
        verifyNoInteractions(check, add, checked, feedback, photos);

        bot.consume(photo(OWNER, "back"));
        verify(check).check(eq(OWNER), aryEq(new byte[]{1}), aryEq(new byte[]{2}));
        verify(api).sendPhotoPair(eq(OWNER), startsWith("Совпадение 1:"), any(), any());
        bot.consume(text(OWNER, "Совпадение 1"));
        verify(feedback).confirmMatch(CHECK_ID, CANDIDATE_ID, FeedbackSource.TELEGRAM);
        verifyNoInteractions(add, checked);
    }

    @Test
    void photoAfterStartOrCancellationStartsFreshCheck() {
        bot.consume(text(OWNER, "/start"));
        bot.consume(photo(OWNER, "front"));
        bot.consume(text(OWNER, "Отмена"));
        bot.consume(photo(OWNER, "back"));
        verifyNoInteractions(check);
        bot.consume(photo(OWNER, "front"));
        verify(check).check(eq(OWNER), aryEq(new byte[]{2}), aryEq(new byte[]{1}));
    }

    @Test
    void photoAfterResultStartsAnotherCheckAndDiscardsPreviousCandidateSelection() {
        performCheck();
        bot.consume(photo(OWNER, "back"));
        bot.consume(text(OWNER, "/match " + CANDIDATE_ID));
        verifyNoInteractions(feedback);
        bot.consume(photo(OWNER, "front"));
        verify(check).check(eq(OWNER), aryEq(new byte[]{2}), aryEq(new byte[]{1}));
        verify(check, times(2)).check(eq(OWNER), any(), any());
    }

    @Test
    void photosDoNotReplaceNameOrImagesDuringExplicitAdd() {
        bot.consume(text(OWNER, "Добавить"));
        bot.consume(photo(OWNER, "front"));
        bot.consume(photo(OWNER, "back"));
        bot.consume(photo(OWNER, "unwanted-photo"));
        verify(api, never()).downloadFile("unwanted-photo");
        verify(api).sendMessage(eq(OWNER), contains("жду название"), anyList());
        bot.consume(text(OWNER, "My coaster"));
        verify(add).add(eq("My coaster"), aryEq(new byte[]{1}), aryEq(new byte[]{2}));
        verifyNoInteractions(check, checked);
    }

    @Test
    void photoWhileNamingCheckedCoasterKeepsOriginalCheck() {
        performCheck();
        bot.consume(text(OWNER, "Сохранить новый"));
        bot.consume(photo(OWNER, "unwanted-photo"));
        verify(api, never()).downloadFile("unwanted-photo");
        bot.consume(text(OWNER, "New coaster"));
        verify(checked).add("New coaster", CHECK_ID, FeedbackSource.TELEGRAM);
        verify(check, times(1)).check(eq(OWNER), any(), any());
        verifyNoInteractions(add);
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
    void sendsStoredFrontAndBackBeforeSelectionButtons() {
        performCheck();
        var order = inOrder(photos, api);
        order.verify(photos).load(CANDIDATE_ID);
        order.verify(api).sendPhotoPair(eq(OWNER), eq("Совпадение 1: Guinness — сходство 87.5%"),
                aryEq(new byte[]{3}), aryEq(new byte[]{4}));
        order.verify(api).sendMessage(eq(OWNER), contains("1. Guinness"),
                eq(List.of(List.of("Совпадение 1"), List.of("Сохранить новый", "Не сохранять"), List.of("Отмена"))));
    }

    @Test
    void albumsAndButtonsKeepSameOrderAndLimitToThreeCandidates() {
        var candidates = java.util.stream.IntStream.rangeClosed(1, 4)
                .mapToObj(i -> new CandidateDto(UUID.randomUUID(), "Coaster " + i, .9, .9, .9, i)).toList();
        when(check.check(eq(OWNER), any(), any())).thenReturn(new MatchResultDto(
                MatchDecision.MATCH, .9, .9, .9, candidates.getFirst().coasterId(), candidates, CHECK_ID));
        performCheck();
        var order = inOrder(photos, api);
        for (int i = 0; i < 3; i++) {
            order.verify(photos).load(candidates.get(i).coasterId());
            order.verify(api).sendPhotoPair(eq(OWNER), startsWith("Совпадение " + (i + 1) + ": Coaster " + (i + 1)),
                    any(), any());
        }
        verify(photos, never()).load(candidates.get(3).coasterId());
        verify(api).sendMessage(eq(OWNER), not(contains("4. Coaster")),
                eq(List.of(List.of("Совпадение 1"), List.of("Совпадение 2"), List.of("Совпадение 3"),
                        List.of("Сохранить новый", "Не сохранять"), List.of("Отмена"))));
        bot.consume(text(OWNER, "Совпадение 2"));
        verify(feedback).confirmMatch(CHECK_ID, candidates.get(1).coasterId(), FeedbackSource.TELEGRAM);
    }

    @Test
    void missingPhotosDoNotDiscardCheckOrPreventSaving() {
        when(photos.load(CANDIDATE_ID)).thenThrow(new IllegalStateException("Storage unavailable"));
        performCheck();
        verify(api).sendMessage(eq(OWNER), contains("Не удалось показать фото вариантов: 1."), anyList());
        bot.consume(text(OWNER, "Сохранить новый"));
        bot.consume(text(OWNER, "New coaster"));
        verify(checked).add("New coaster", CHECK_ID, FeedbackSource.TELEGRAM);
    }

    @Test
    void failedAlbumDoesNotPreventOtherPhotosOrConfirmation() {
        UUID secondId = UUID.randomUUID();
        when(check.check(eq(OWNER), any(), any())).thenReturn(new MatchResultDto(
                MatchDecision.UNCERTAIN, .85, .9, .875, null,
                List.of(new CandidateDto(CANDIDATE_ID, "First", .85, .9, .875, 1),
                        new CandidateDto(secondId, "Second", .8, .8, .8, 2)), CHECK_ID));
        doThrow(new IllegalStateException("Telegram sendMediaGroup failed")).when(api)
                .sendPhotoPair(eq(OWNER), startsWith("Совпадение 1:"), any(), any());
        performCheck();
        verify(api).sendPhotoPair(eq(OWNER), startsWith("Совпадение 2:"), any(), any());
        verify(api).sendMessage(eq(OWNER), contains("Не удалось показать фото вариантов: 1."), anyList());
        bot.consume(text(OWNER, "Совпадение 2"));
        verify(feedback).confirmMatch(CHECK_ID, secondId, FeedbackSource.TELEGRAM);
    }

    @Test
    void emptyCandidatesDoNotLoadOrSendPhotos() {
        when(check.check(eq(OWNER), any(), any())).thenReturn(new MatchResultDto(
                MatchDecision.NO_MATCH, null, null, null, null, List.of(), CHECK_ID));
        performCheck();
        verifyNoInteractions(photos);
        verify(api, never()).sendPhotoPair(anyLong(), anyString(), any(), any());
        verify(api).sendMessage(eq(OWNER), contains("Кандидаты: нет"),
                eq(List.of(List.of("Сохранить новый", "Не сохранять"), List.of("Отмена"))));
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
    void commandsWithWrongPrefixDoNotRequestOrDownloadPhotos() {
        bot.consume(text(OWNER, "/checkanything"));
        verify(api, never()).downloadFile(anyString());
        verify(api, never()).sendMessage(eq(OWNER), eq("Пришлите фото лицевой стороны бирдекеля."), anyList());
        verifyNoInteractions(check, add, checked);
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

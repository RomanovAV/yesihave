package org.avromanov.yesihave.web;

import org.avromanov.yesihave.application.AddCheckedCoasterUseCase;
import org.avromanov.yesihave.application.AddCoasterUseCase;
import org.avromanov.yesihave.application.CheckPairUseCase;
import org.avromanov.yesihave.application.RecordCheckFeedbackUseCase;
import org.avromanov.yesihave.application.model.CandidateDto;
import org.avromanov.yesihave.application.model.FeedbackSource;
import org.avromanov.yesihave.application.model.MatchDecision;
import org.avromanov.yesihave.application.model.MatchResultDto;
import org.avromanov.yesihave.image.ImageBytesNormalizer;
import org.avromanov.yesihave.image.ImageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebPagesControllerTest {
    private MockMvc mvc;
    private RecordingFeedbackUseCase feedback;

    @BeforeEach
    void setUp() {
        UploadedImageNormalizer uploadedImageNormalizer = new UploadedImageNormalizer(new ImageBytesNormalizer(
                new ImageProperties(10 * 1024 * 1024, 13_000_000, 6_000, 2)
        )) {
            @Override
            public byte[] normalizeToJpegIfHeic(org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
                return file.getBytes();
            }
        };
        CheckPairUseCase checkPairUseCase = (telegramUserId, frontImage, backImage) -> new MatchResultDto(
                MatchDecision.UNCERTAIN,
                0.1,
                0.2,
                0.15,
                null,
                List.of(new CandidateDto(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "Stub",
                        0.1,
                        0.2,
                        0.15,
                        1
                )),
                UUID.fromString("00000000-0000-0000-0000-000000000003")
        );
        AddCoasterUseCase addCoasterUseCase =
                (name, frontImage, backImage) -> UUID.fromString("00000000-0000-0000-0000-000000000002");
        AddCheckedCoasterUseCase addCheckedCoasterUseCase =
                (name, checkRequestId, source) ->
                        UUID.fromString("00000000-0000-0000-0000-000000000002");
        feedback = new RecordingFeedbackUseCase();

        mvc = MockMvcBuilders.standaloneSetup(new WebPagesController(
                checkPairUseCase,
                addCoasterUseCase,
                addCheckedCoasterUseCase,
                feedback,
                uploadedImageNormalizer
        )).setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    static class RecordingFeedbackUseCase implements RecordCheckFeedbackUseCase {
        private UUID checkRequestId;
        private UUID coasterId;
        private FeedbackSource source;

        @Override
        public void confirmMatch(UUID checkRequestId, UUID coasterId, FeedbackSource source) {
            this.checkRequestId = checkRequestId;
            this.coasterId = coasterId;
            this.source = source;
        }

        @Override
        public void confirmNoMatch(UUID checkRequestId, UUID addedCoasterId, FeedbackSource source) {
            this.checkRequestId = checkRequestId;
            this.coasterId = null;
            this.source = source;
        }
    }

    @Test
    void homeRenders() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("YesIHave")));
    }

    @Test
    void checkFlowRendersResult() throws Exception {
        MockMultipartFile front = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[]{2});

        mvc.perform(multipart("/web/check").file(front).file(back))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("UNCERTAIN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("3 лучших кандидата")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Это новый костер")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Подтвердить дубликат")));
    }

    @Test
    void addFromCheckReusesUploadedPhotos() throws Exception {
        MockMultipartFile front = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[]{2});

        MvcResult result = mvc.perform(multipart("/web/check").file(front).file(back))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();
        String draftId = extractDraftId(html);
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

        mvc.perform(get("/web/add-from-check")
                        .session(session)
                        .param("draftId", draftId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Добавить как новый костер")));

        mvc.perform(post("/web/add-from-check")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Reused photos")
                        .param("draftId", draftId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reused photos")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Использованы фото из последней проверки")));
    }

    @Test
    void addFlowRendersConfirmation() throws Exception {
        MockMultipartFile front = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[]{2});

        mvc.perform(multipart("/web/add").file(front).file(back).param("name", "Test"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("00000000-0000-0000-0000-000000000002")));
    }

    @Test
    void matchFeedbackIsBoundToCheckDraftAndCandidate() throws Exception {
        MockMultipartFile front = new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile back = new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[]{2});
        MvcResult result = mvc.perform(multipart("/web/check").file(front).file(back))
                .andExpect(status().isOk())
                .andReturn();
        String draftId = extractDraftId(result.getResponse().getContentAsString());
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

        mvc.perform(post("/web/feedback/match")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("draftId", draftId)
                        .param("coasterId", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("подтверждение сохранено")));

        org.assertj.core.api.Assertions.assertThat(feedback.checkRequestId)
                .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        org.assertj.core.api.Assertions.assertThat(feedback.coasterId)
                .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        org.assertj.core.api.Assertions.assertThat(feedback.source).isEqualTo(FeedbackSource.WEB);
    }

    @Test
    void newerCheckEvictsOlderSessionDraft() throws Exception {
        MvcResult first = mvc.perform(multipart("/web/check")
                        .file(new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[]{1}))
                        .file(new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[]{2})))
                .andExpect(status().isOk())
                .andReturn();
        String oldDraftId = extractDraftId(first.getResponse().getContentAsString());
        MockHttpSession session = (MockHttpSession) first.getRequest().getSession(false);

        mvc.perform(multipart("/web/check")
                        .file(new MockMultipartFile("front", "front.jpg", "image/jpeg", new byte[]{3}))
                        .file(new MockMultipartFile("back", "back.jpg", "image/jpeg", new byte[]{4}))
                        .session(session))
                .andExpect(status().isOk());

        mvc.perform(get("/web/add-from-check")
                        .session(session)
                        .param("draftId", oldDraftId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Черновик не найден")));
    }

    private String extractDraftId(String html) {
        Matcher matcher = Pattern.compile("name=\"draftId\" type=\"hidden\" value=\"([^\"]+)\"").matcher(html);
        org.junit.jupiter.api.Assertions.assertTrue(matcher.find(), "draftId hidden input not found");
        return matcher.group(1);
    }
}

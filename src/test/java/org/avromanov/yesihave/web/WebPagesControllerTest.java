package org.avromanov.yesihave.web;

import org.avromanov.yesihave.application.AddCoasterUseCase;
import org.avromanov.yesihave.application.CheckPairUseCase;
import org.avromanov.yesihave.application.model.CandidateDto;
import org.avromanov.yesihave.application.model.MatchDecision;
import org.avromanov.yesihave.application.model.MatchResultDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebPagesControllerTest {
    @Autowired
    MockMvc mvc;

    @TestConfiguration
    static class Stubs {
        @Bean
        UploadedImageNormalizer uploadedImageNormalizer() {
            return new UploadedImageNormalizer() {
                @Override
                public byte[] normalizeToJpegIfHeic(org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
                    return file.getBytes();
                }
            };
        }

        @Bean
        CheckPairUseCase checkPairUseCase() {
            return (telegramUserId, frontImage, backImage) -> new MatchResultDto(
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
                    ))
            );
        }

        @Bean
        AddCoasterUseCase addCoasterUseCase() {
            return (name, frontImage, backImage) -> UUID.fromString("00000000-0000-0000-0000-000000000002");
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("UNCERTAIN")));
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
}


package org.avromanov.yesihave.api;

import org.avromanov.yesihave.application.CheckPairUseCase;
import org.avromanov.yesihave.application.model.MatchResultDto;
import org.avromanov.yesihave.web.UploadedImageNormalizer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
public class CheckController {
    private final CheckPairUseCase checkPairUseCase;
    private final UploadedImageNormalizer uploadedImageNormalizer;

    public CheckController(CheckPairUseCase checkPairUseCase,
                           UploadedImageNormalizer uploadedImageNormalizer) {
        this.checkPairUseCase = checkPairUseCase;
        this.uploadedImageNormalizer = uploadedImageNormalizer;
    }

    @PostMapping(value = "/api/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MatchResultDto check(@RequestPart("front") MultipartFile front,
                                @RequestPart("back") MultipartFile back) throws IOException {
        return checkPairUseCase.check(
                0L,
                uploadedImageNormalizer.normalizeToJpegIfHeic(front),
                uploadedImageNormalizer.normalizeToJpegIfHeic(back)
        );
    }
}

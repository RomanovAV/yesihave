package org.avromanov.yesihave.api;

import org.avromanov.yesihave.application.CheckPairUseCase;
import org.avromanov.yesihave.application.model.MatchResultDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
public class CheckController {
    private final CheckPairUseCase checkPairUseCase;

    public CheckController(CheckPairUseCase checkPairUseCase) {
        this.checkPairUseCase = checkPairUseCase;
    }

    @PostMapping(value = "/api/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MatchResultDto check(@RequestPart("front") MultipartFile front,
                                @RequestPart("back") MultipartFile back) throws IOException {
        return checkPairUseCase.check(0L, front.getBytes(), back.getBytes());
    }
}

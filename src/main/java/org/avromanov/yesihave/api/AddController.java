package org.avromanov.yesihave.api;

import org.avromanov.yesihave.application.AddCoasterUseCase;
import org.avromanov.yesihave.web.UploadedImageNormalizer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
public class AddController {
    private final AddCoasterUseCase addCoasterUseCase;
    private final UploadedImageNormalizer uploadedImageNormalizer;

    public AddController(AddCoasterUseCase addCoasterUseCase,
                         UploadedImageNormalizer uploadedImageNormalizer) {
        this.addCoasterUseCase = addCoasterUseCase;
        this.uploadedImageNormalizer = uploadedImageNormalizer;
    }

    @PostMapping(value = "/api/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AddCoasterResponse add(@RequestParam("name") String name,
                                  @RequestParam("front") MultipartFile front,
                                  @RequestParam("back") MultipartFile back) throws IOException {
        UUID coasterId = addCoasterUseCase.add(
                name,
                uploadedImageNormalizer.normalizeToJpegIfHeic(front),
                uploadedImageNormalizer.normalizeToJpegIfHeic(back)
        );
        return new AddCoasterResponse(coasterId);
    }

    public record AddCoasterResponse(UUID coasterId) {
    }
}

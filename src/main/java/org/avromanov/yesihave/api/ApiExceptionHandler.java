package org.avromanov.yesihave.api;

import org.avromanov.yesihave.application.DuplicateCoasterException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.util.UUID;

@RestControllerAdvice(basePackages = "org.avromanov.yesihave.api")
public class ApiExceptionHandler {

    @ExceptionHandler(DuplicateCoasterException.class)
    public ResponseEntity<ApiError> duplicate(DuplicateCoasterException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "DUPLICATE_COASTER",
                exception.getMessage(),
                exception.existingCoasterId()
        ));
    }

    @ExceptionHandler({IllegalArgumentException.class, IOException.class})
    public ResponseEntity<ApiError> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError(
                "INVALID_REQUEST",
                exception.getMessage(),
                null
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> uploadTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiError(
                "IMAGE_TOO_LARGE",
                "Uploaded image exceeds the configured size limit",
                null
        ));
    }

    public record ApiError(String code, String message, UUID coasterId) {
    }
}

package org.avromanov.yesihave.web;

import org.avromanov.yesihave.image.ImageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ImageRequestConcurrencyFilterTest {

    @Test
    void rejectsAnotherImageRequestWhileCapacityIsExhausted() throws Exception {
        ImageRequestConcurrencyFilter filter = new ImageRequestConcurrencyFilter(
                new ImageProperties(1024, 100, 100, 1)
        );
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> {
                MockHttpServletRequest request = imageRequest();
                filter.doFilter(request, new MockHttpServletResponse(), (ignoredRequest, ignoredResponse) -> {
                    entered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                return null;
            });
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            MockHttpServletResponse rejected = new MockHttpServletResponse();
            filter.doFilter(imageRequest(), rejected, (request, response) -> { });

            assertThat(rejected.getStatus()).isEqualTo(429);
            assertThat(rejected.getContentAsString()).contains("IMAGE_PROCESSING_BUSY");
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private MockHttpServletRequest imageRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/check");
        request.setRequestURI("/api/check");
        return request;
    }
}

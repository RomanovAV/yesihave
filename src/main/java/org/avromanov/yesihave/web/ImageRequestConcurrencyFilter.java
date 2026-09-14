package org.avromanov.yesihave.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.avromanov.yesihave.image.ImageProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.Semaphore;

@Component
public class ImageRequestConcurrencyFilter extends OncePerRequestFilter {
    private static final Set<String> IMAGE_WRITE_PATHS = Set.of(
            "/api/check",
            "/api/add",
            "/web/check",
            "/web/add",
            "/web/add-from-check"
    );

    private final Semaphore permits;

    public ImageRequestConcurrencyFilter(ImageProperties properties) {
        this.permits = new Semaphore(properties.maxConcurrentRequests(), true);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !IMAGE_WRITE_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!permits.tryAcquire()) {
            response.setStatus(429);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":\"IMAGE_PROCESSING_BUSY\",\"message\":\"Try again later\"}");
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            permits.release();
        }
    }
}

package org.avromanov.yesihave.persistence;

import org.avromanov.yesihave.application.model.FeedbackSource;
import org.avromanov.yesihave.application.model.FeedbackVerdict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class CheckFeedbackRepository {
    private final JdbcTemplate jdbcTemplate;

    public CheckFeedbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean checkRequestExists(UUID checkRequestId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM check_requests WHERE id = ?)",
                Boolean.class,
                checkRequestId
        );
        return Boolean.TRUE.equals(exists);
    }

    public boolean coasterExists(UUID coasterId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM coasters WHERE id = ?)",
                Boolean.class,
                coasterId
        );
        return Boolean.TRUE.equals(exists);
    }

    public void upsert(UUID checkRequestId,
                       FeedbackVerdict verdict,
                       UUID matchedCoasterId,
                       UUID addedCoasterId,
                       FeedbackSource source) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                """
                INSERT INTO check_feedback(
                  check_request_id, verdict, matched_coaster_id,
                  added_coaster_id, source, created_at, updated_at
                )
                VALUES (?, ?::check_feedback_verdict, ?, ?, ?::check_feedback_source, ?, ?)
                ON CONFLICT (check_request_id) DO UPDATE SET
                  verdict = EXCLUDED.verdict,
                  matched_coaster_id = EXCLUDED.matched_coaster_id,
                  added_coaster_id = EXCLUDED.added_coaster_id,
                  source = EXCLUDED.source,
                  updated_at = EXCLUDED.updated_at
                """,
                checkRequestId,
                verdict.name(),
                matchedCoasterId,
                addedCoasterId,
                source.name(),
                now,
                now
        );
    }
}

package org.avromanov.yesihave.persistence;

import org.avromanov.yesihave.application.model.CandidateDto;
import org.avromanov.yesihave.application.model.MatchDecision;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class CheckAuditRepository {
    private final JdbcTemplate jdbcTemplate;

    public CheckAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID insertCheckRequest(long telegramUserId,
                                   String frontImageKey,
                                   String backImageKey,
                                   Double frontScore,
                                   Double backScore,
                                   Double pairScore,
                                   MatchDecision decision,
                                   UUID matchedCoasterId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO check_requests(
                  id, telegram_user_id, front_image_key, back_image_key,
                  front_score, back_score, pair_score, decision, matched_coaster_id, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::match_decision, ?, ?)
                """,
                id,
                telegramUserId,
                frontImageKey,
                backImageKey,
                frontScore,
                backScore,
                pairScore,
                decision.name(),
                matchedCoasterId,
                Timestamp.from(OffsetDateTime.now().toInstant())
        );
        return id;
    }

    public void insertCandidates(UUID checkRequestId, List<CandidateDto> candidates) {
        for (CandidateDto candidate : candidates) {
            jdbcTemplate.update(
                    """
                    INSERT INTO check_candidates(
                      id, check_request_id, coaster_id, score_front, score_back, pair_score, rank_num
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    checkRequestId,
                    candidate.coasterId(),
                    candidate.scoreFront(),
                    candidate.scoreBack(),
                    candidate.pairScore(),
                    candidate.rank()
            );
        }
    }
}

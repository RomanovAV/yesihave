package org.avromanov.yesihave.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class CoasterReadRepository {
    private final JdbcTemplate jdbcTemplate;

    public CoasterReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CoasterImagePair> findAllPairs() {
        return jdbcTemplate.query(
                """
                SELECT c.id,
                       f.object_key AS front_key,
                       b.object_key AS back_key
                FROM coasters c
                JOIN coaster_images f ON f.coaster_id = c.id AND f.side = 'FRONT'
                JOIN coaster_images b ON b.coaster_id = c.id AND b.side = 'BACK'
                """,
                (rs, rowNum) -> new CoasterImagePair(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("front_key"),
                        rs.getString("back_key")
                )
        );
    }

    public record CoasterImagePair(UUID coasterId, String frontKey, String backKey) {
    }
}

-- Query vectors are cached with the same version lifecycle as collection vectors.
-- Photos remain owned by check_requests; deleting a check also removes its cache.
CREATE TABLE check_embeddings (
  check_request_id UUID NOT NULL REFERENCES check_requests(id) ON DELETE CASCADE,
  model_version TEXT NOT NULL REFERENCES embedding_model_versions(model_version),
  image_pair_key TEXT NOT NULL,
  front_vector VECTOR(512) NOT NULL,
  back_vector VECTOR(512) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (check_request_id, model_version)
);
CREATE INDEX check_embeddings_model_version_idx ON check_embeddings(model_version);
CREATE INDEX check_embeddings_image_pair_idx ON check_embeddings(image_pair_key);

-- Current human feedback is the authority: revoking/correcting it takes effect
-- immediately, without a background job or a second copy of the photos.
-- Identical originals and retries are excluded. Conflicting labels for exactly
-- the same photo pair are excluded until the labels have been resolved.
CREATE VIEW coaster_matching_embeddings AS
WITH confirmed AS (
  SELECT e.*, f.matched_coaster_id AS coaster_id, f.updated_at AS confirmed_at
  FROM check_embeddings e
  JOIN check_feedback f ON f.check_request_id = e.check_request_id
  WHERE f.verdict = 'MATCH'
    AND NOT EXISTS (
      SELECT 1 FROM coaster_image_pairs p WHERE p.image_pair_key = e.image_pair_key
    )
), unambiguous AS (
  SELECT model_version, image_pair_key
  FROM confirmed
  GROUP BY model_version, image_pair_key
  HAVING COUNT(DISTINCT coaster_id) = 1
), references_deduplicated AS (
  SELECT DISTINCT ON (c.model_version, c.image_pair_key)
         c.coaster_id, c.model_version, c.front_vector, c.back_vector,
         c.check_request_id, c.confirmed_at
  FROM confirmed c
  JOIN unambiguous u USING (model_version, image_pair_key)
  ORDER BY c.model_version, c.image_pair_key, c.confirmed_at, c.check_request_id
)
SELECT e.coaster_id, e.model_version, e.front_vector, e.back_vector,
       NULL::uuid AS source_check_request_id, c.created_at AS available_at
FROM coaster_embeddings e
JOIN coasters c ON c.id = e.coaster_id
UNION ALL
SELECT coaster_id, model_version, front_vector, back_vector,
       check_request_id AS source_check_request_id, confirmed_at AS available_at
FROM references_deduplicated;

ALTER TABLE check_candidates ADD COLUMN reference_check_request_id UUID
  REFERENCES check_requests(id) ON DELETE SET NULL;

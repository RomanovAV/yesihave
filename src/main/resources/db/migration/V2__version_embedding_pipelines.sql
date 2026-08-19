CREATE TYPE embedding_version_status AS ENUM ('BUILDING', 'ACTIVE', 'RETIRED');

CREATE TABLE embedding_model_versions (
  model_version TEXT PRIMARY KEY,
  provider TEXT,
  model_fingerprint TEXT,
  preprocessing_profile TEXT,
  dimension INT NOT NULL CHECK (dimension > 0),
  status embedding_version_status NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  activated_at TIMESTAMPTZ
);

INSERT INTO embedding_model_versions(
  model_version,
  dimension,
  status,
  created_at
)
SELECT model_version,
       512,
       'RETIRED'::embedding_version_status,
       MIN(updated_at)
FROM coaster_embeddings
GROUP BY model_version;

WITH latest_version AS (
  SELECT model_version
  FROM coaster_embeddings
  GROUP BY model_version
  ORDER BY MAX(updated_at) DESC, model_version
  LIMIT 1
)
UPDATE embedding_model_versions versions
SET status = 'ACTIVE',
    activated_at = now()
FROM latest_version
WHERE versions.model_version = latest_version.model_version;

CREATE UNIQUE INDEX embedding_model_versions_one_active_idx
  ON embedding_model_versions ((status))
  WHERE status = 'ACTIVE';

-- The old ANN indexes mixed pipeline versions. Post-filtering approximate results
-- can drop the correct candidate, so matching uses an exact per-version scan
-- until version-specific ANN indexes are introduced for a larger collection.
DROP INDEX coaster_embeddings_front_idx;
DROP INDEX coaster_embeddings_back_idx;

ALTER TABLE coaster_embeddings
  DROP CONSTRAINT coaster_embeddings_pkey;

ALTER TABLE coaster_embeddings
  ADD CONSTRAINT coaster_embeddings_pkey PRIMARY KEY (coaster_id, model_version);

CREATE INDEX coaster_embeddings_model_version_idx
  ON coaster_embeddings(model_version);

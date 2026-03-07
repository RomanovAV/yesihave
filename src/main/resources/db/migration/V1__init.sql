CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE coasters (
  id UUID PRIMARY KEY,
  name TEXT NOT NULL,
  series TEXT,
  brand TEXT,
  country TEXT,
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TYPE coaster_side AS ENUM ('FRONT', 'BACK');

CREATE TABLE coaster_images (
  id UUID PRIMARY KEY,
  coaster_id UUID NOT NULL REFERENCES coasters(id) ON DELETE CASCADE,
  side coaster_side NOT NULL,
  object_key TEXT NOT NULL,
  sha256 TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (coaster_id, side)
);

CREATE TABLE coaster_embeddings (
  coaster_id UUID PRIMARY KEY REFERENCES coasters(id) ON DELETE CASCADE,
  model_version TEXT NOT NULL,
  front_vector VECTOR(512) NOT NULL,
  back_vector VECTOR(512) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX coaster_embeddings_front_idx
  ON coaster_embeddings USING ivfflat (front_vector vector_cosine_ops) WITH (lists = 100);

CREATE INDEX coaster_embeddings_back_idx
  ON coaster_embeddings USING ivfflat (back_vector vector_cosine_ops) WITH (lists = 100);

CREATE TYPE match_decision AS ENUM ('MATCH', 'UNCERTAIN', 'NO_MATCH');

CREATE TABLE check_requests (
  id UUID PRIMARY KEY,
  telegram_user_id BIGINT NOT NULL,
  front_image_key TEXT NOT NULL,
  back_image_key TEXT NOT NULL,
  front_score DOUBLE PRECISION,
  back_score DOUBLE PRECISION,
  pair_score DOUBLE PRECISION,
  decision match_decision,
  matched_coaster_id UUID REFERENCES coasters(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE check_candidates (
  id UUID PRIMARY KEY,
  check_request_id UUID NOT NULL REFERENCES check_requests(id) ON DELETE CASCADE,
  coaster_id UUID NOT NULL REFERENCES coasters(id) ON DELETE CASCADE,
  score_front DOUBLE PRECISION NOT NULL,
  score_back DOUBLE PRECISION NOT NULL,
  pair_score DOUBLE PRECISION NOT NULL,
  rank_num INT NOT NULL
);

CREATE UNIQUE INDEX check_candidates_unique_rank
  ON check_candidates(check_request_id, rank_num);

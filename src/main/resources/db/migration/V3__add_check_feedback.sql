CREATE TYPE check_feedback_verdict AS ENUM ('MATCH', 'NO_MATCH');
CREATE TYPE check_feedback_source AS ENUM ('WEB', 'API', 'TELEGRAM');

CREATE TABLE check_feedback (
  check_request_id UUID PRIMARY KEY REFERENCES check_requests(id) ON DELETE CASCADE,
  verdict check_feedback_verdict NOT NULL,
  matched_coaster_id UUID REFERENCES coasters(id),
  added_coaster_id UUID REFERENCES coasters(id),
  source check_feedback_source NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (
    (verdict = 'MATCH' AND matched_coaster_id IS NOT NULL AND added_coaster_id IS NULL)
    OR
    (verdict = 'NO_MATCH' AND matched_coaster_id IS NULL)
  )
);

CREATE INDEX check_feedback_verdict_idx ON check_feedback(verdict);
CREATE INDEX check_feedback_matched_coaster_idx ON check_feedback(matched_coaster_id);

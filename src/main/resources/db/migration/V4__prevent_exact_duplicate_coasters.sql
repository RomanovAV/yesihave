CREATE TABLE coaster_image_pairs (
  image_pair_key TEXT PRIMARY KEY,
  coaster_id UUID NOT NULL UNIQUE REFERENCES coasters(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

WITH existing_pairs AS (
  SELECT c.id AS coaster_id,
         c.created_at,
         LEAST(f.sha256, b.sha256) || ':' || GREATEST(f.sha256, b.sha256) AS image_pair_key
  FROM coasters c
  JOIN coaster_images f ON f.coaster_id = c.id AND f.side = 'FRONT'
  JOIN coaster_images b ON b.coaster_id = c.id AND b.side = 'BACK'
  WHERE f.sha256 IS NOT NULL AND b.sha256 IS NOT NULL
), canonical_pairs AS (
  SELECT DISTINCT ON (image_pair_key)
         image_pair_key,
         coaster_id,
         created_at
  FROM existing_pairs
  ORDER BY image_pair_key, created_at, coaster_id
)
INSERT INTO coaster_image_pairs(image_pair_key, coaster_id, created_at)
SELECT image_pair_key, coaster_id, created_at
FROM canonical_pairs;

CREATE INDEX coaster_images_object_key_idx ON coaster_images(object_key);
CREATE INDEX check_requests_front_image_key_idx ON check_requests(front_image_key);
CREATE INDEX check_requests_back_image_key_idx ON check_requests(back_image_key);

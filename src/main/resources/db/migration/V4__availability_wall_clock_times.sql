-- Store availability wall-clock times as HH:mm:ss strings so Hibernate's
-- hibernate.jdbc.time_zone=UTC does not shift LocalTime on JDBC TIME bindings.
-- Renamed from V3 (CTL-49 occupies V3 on main).
--
-- Rebaseline note: if your DB previously applied this file as V3__availability_wall_clock_times.sql,
-- reset the local volume (docker compose down -v && docker compose up -d) before starting Core.

ALTER TABLE guide_availability_rules
  DROP CONSTRAINT IF EXISTS guide_availability_rules_check;

ALTER TABLE guide_availability_rules
  ALTER COLUMN start_local TYPE VARCHAR(8) USING to_char(start_local, 'HH24:MI:SS'),
  ALTER COLUMN end_local TYPE VARCHAR(8) USING to_char(end_local, 'HH24:MI:SS');

ALTER TABLE availability_exceptions
  ALTER COLUMN start_local TYPE VARCHAR(8)
    USING CASE
      WHEN start_local IS NULL THEN NULL
      ELSE to_char(start_local, 'HH24:MI:SS')
    END,
  ALTER COLUMN end_local TYPE VARCHAR(8)
    USING CASE
      WHEN end_local IS NULL THEN NULL
      ELSE to_char(end_local, 'HH24:MI:SS')
    END;

-- Explicit constraint names (V1 used an implicit CHECK name on rules only).
ALTER TABLE guide_availability_rules
  ADD CONSTRAINT guide_availability_rules_start_local_format_check
    CHECK (start_local ~ '^([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$'),
  ADD CONSTRAINT guide_availability_rules_end_local_format_check
    CHECK (end_local ~ '^([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$'),
  ADD CONSTRAINT guide_availability_rules_time_range_check
    CHECK (start_local::time < end_local::time);

ALTER TABLE availability_exceptions
  ADD CONSTRAINT availability_exceptions_start_local_format_check
    CHECK (
      start_local IS NULL
      OR start_local ~ '^([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$'
    ),
  ADD CONSTRAINT availability_exceptions_end_local_format_check
    CHECK (
      end_local IS NULL
      OR end_local ~ '^([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$'
    ),
  ADD CONSTRAINT availability_exceptions_time_range_check
    CHECK (
      (start_local IS NULL AND end_local IS NULL)
      OR (
        start_local IS NOT NULL
        AND end_local IS NOT NULL
        AND start_local::time < end_local::time
      )
    );

-- Backstop for concurrent rule creates (service-layer overlap check is read-then-write).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE OR REPLACE FUNCTION wall_clock_local_seconds(wall_clock VARCHAR)
RETURNS INTEGER
LANGUAGE sql
IMMUTABLE
STRICT
AS $$
  SELECT (
    substring(wall_clock from 1 for 2)::integer * 3600
    + substring(wall_clock from 4 for 2)::integer * 60
    + substring(wall_clock from 7 for 2)::integer
  );
$$;

ALTER TABLE guide_availability_rules
  ADD CONSTRAINT guide_availability_rules_no_overlap
  EXCLUDE USING gist (
    guide_id WITH =,
    day_of_week WITH =,
    daterange(
      effective_from,
      COALESCE(effective_to, '9999-12-31'::date),
      '[]'
    ) WITH &&,
    int4range(
      wall_clock_local_seconds(start_local),
      wall_clock_local_seconds(end_local),
      '[)'
    ) WITH &&
  ) WHERE (active = true);
